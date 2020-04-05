package com.java.bigdata.a01_hbase_src_read;

/**
 * @Author: Lei
 * @E-mail: 843291011@qq.com
 * @Date: Created in 5:25 下午 2020/4/4
 * @Version: 1.0
 * @Modified By:
 * @Description:
 */
//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
// package org.apache.hadoop.hbase.regionserver;

import com.google.common.base.Preconditions;
import java.io.IOException;
import java.lang.Thread.UncaughtExceptionHandler;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.DroppedSnapshotException;
import org.apache.hadoop.hbase.RemoteExceptionHandler;
import org.apache.hadoop.hbase.classification.InterfaceAudience.Private;
import org.apache.hadoop.hbase.client.RegionReplicaUtil;
import org.apache.hadoop.hbase.io.util.HeapMemorySizeUtil;
import org.apache.hadoop.hbase.regionserver.*;
import org.apache.hadoop.hbase.regionserver.Region.FlushResult;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.util.Counter;
import org.apache.hadoop.hbase.util.EnvironmentEdgeManager;
import org.apache.hadoop.hbase.util.HasThread;
import org.apache.hadoop.hbase.util.ServerRegionReplicaUtil;
import org.apache.hadoop.hbase.util.Threads;
import org.apache.hadoop.util.StringUtils;
import org.apache.hadoop.util.StringUtils.TraditionalBinaryPrefix;
import org.apache.htrace.Trace;
import org.apache.htrace.TraceScope;

@Private
class MemStoreFlusher implements FlushRequester {
    private static final Log LOG = LogFactory.getLog(MemStoreFlusher.class);
    private Configuration conf;
    private final BlockingQueue<MemStoreFlusher.FlushQueueEntry> flushQueue = new DelayQueue();
    private final Map<Region, MemStoreFlusher.FlushRegionEntry> regionsInQueue = new HashMap();
    private AtomicBoolean wakeupPending = new AtomicBoolean();
    private final long threadWakeFrequency;
    private final HRegionServer server;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Object blockSignal = new Object();
    protected long globalMemStoreLimit;
    protected float globalMemStoreLimitLowMarkPercent;
    protected long globalMemStoreLimitLowMark;
    private long blockingWaitTime;
    private final Counter updatesBlockedMsHighWater = new Counter();
    private final MemStoreFlusher.FlushHandler[] flushHandlers;
    private List<FlushRequestListener> flushRequestListeners = new ArrayList(1);

    public MemStoreFlusher(Configuration conf, HRegionServer server) {
        this.conf = conf;
        this.server = server;
        this.threadWakeFrequency = conf.getLong("hbase.server.thread.wakefrequency", 10000L);

        // 获取最大堆内存大小，// -Xmx 1/4
        long max = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getMax();
        //     <!-- regionServer的全局memstore的大小，超过该大小会触发flush到磁盘的操作,默认是堆大小的40%,而且regionserver级别的
        //        flush会阻塞客户端读写 -->
        float globalMemStorePercent = HeapMemorySizeUtil.getGlobalMemStorePercent(conf, true);
        this.globalMemStoreLimit = (long)((float)max * globalMemStorePercent);
        // 默认0.95
        this.globalMemStoreLimitLowMarkPercent = HeapMemorySizeUtil.getGlobalMemStoreLowerMark(conf, globalMemStorePercent);
        // 0.4 * 0.95 = 0.38
        this.globalMemStoreLimitLowMark = (long)((float)this.globalMemStoreLimit * this.globalMemStoreLimitLowMarkPercent);
        this.blockingWaitTime = (long)conf.getInt("hbase.hstore.blockingWaitTime", 90000);
        // 刷写线程数
        int handlerCount = conf.getInt("hbase.hstore.flusher.count", 2);
        this.flushHandlers = new MemStoreFlusher.FlushHandler[handlerCount];
        LOG.info("globalMemStoreLimit=" + TraditionalBinaryPrefix.long2String(this.globalMemStoreLimit, "", 1) + ", globalMemStoreLimitLowMark=" + TraditionalBinaryPrefix.long2String(this.globalMemStoreLimitLowMark, "", 1) + ", maxHeap=" + TraditionalBinaryPrefix.long2String(max, "", 1));
    }

    public Counter getUpdatesBlockedMsHighWater() {
        return this.updatesBlockedMsHighWater;
    }

    private boolean flushOneForGlobalPressure() {
        SortedMap<Long, Region> regionsBySize = null; //this.server.getCopyOfOnlineRegionsSortedBySize();
        Set<Region> excludedRegions = new HashSet();
        double secondaryMultiplier = ServerRegionReplicaUtil.getRegionReplicaStoreFileRefreshMultiplier(this.conf);
        boolean flushedOne = false;

        while(true) {
            while(!flushedOne) {
                Region bestFlushableRegion = this.getBiggestMemstoreRegion(regionsBySize, excludedRegions, true);
                Region bestAnyRegion = this.getBiggestMemstoreRegion(regionsBySize, excludedRegions, false);
                Region bestRegionReplica = this.getBiggestMemstoreOfRegionReplica(regionsBySize, excludedRegions);
                if (bestAnyRegion == null && bestRegionReplica == null) {
                    LOG.error("Above memory mark but there are no flushable regions!");
                    return false;
                }

                Region regionToFlush;
                if (bestFlushableRegion != null && bestAnyRegion.getMemstoreSize() > 2L * bestFlushableRegion.getMemstoreSize()) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Under global heap pressure: Region " + bestAnyRegion.getRegionInfo().getRegionNameAsString() + " has too many " + "store files, but is " + TraditionalBinaryPrefix.long2String(bestAnyRegion.getMemstoreSize(), "", 1) + " vs best flushable region's " + TraditionalBinaryPrefix.long2String(bestFlushableRegion.getMemstoreSize(), "", 1) + ". Choosing the bigger.");
                    }

                    regionToFlush = bestAnyRegion;
                } else if (bestFlushableRegion == null) {
                    regionToFlush = bestAnyRegion;
                } else {
                    regionToFlush = bestFlushableRegion;
                }

                Preconditions.checkState(regionToFlush != null && regionToFlush.getMemstoreSize() > 0L || bestRegionReplica != null && bestRegionReplica.getMemstoreSize() > 0L);
                if (regionToFlush != null && (bestRegionReplica == null || !ServerRegionReplicaUtil.isRegionReplicaStoreFileRefreshEnabled(this.conf) || (double)bestRegionReplica.getMemstoreSize() <= secondaryMultiplier * (double)regionToFlush.getMemstoreSize())) {
                    LOG.info("Flush of region " + regionToFlush + " due to global heap pressure. " + "Total Memstore size=" + StringUtils.humanReadableInt(this.server.getRegionServerAccounting().getGlobalMemstoreSize()) + ", Region memstore size=" + StringUtils.humanReadableInt(regionToFlush.getMemstoreSize()));
                    flushedOne = this.flushRegion(regionToFlush, true, true);
                    if (!flushedOne) {
                        LOG.info("Excluding unflushable region " + regionToFlush + " - trying to find a different region to flush.");
                        excludedRegions.add(regionToFlush);
                    }
                } else {
                    LOG.info("Refreshing storefiles of region " + bestRegionReplica + " due to global heap pressure. memstore size=" + StringUtils.humanReadableInt(this.server.getRegionServerAccounting().getGlobalMemstoreSize()));
                    flushedOne = this.refreshStoreFilesAndReclaimMemory(bestRegionReplica);
                    if (!flushedOne) {
                        LOG.info("Excluding secondary region " + bestRegionReplica + " - trying to find a different region to refresh files.");
                        excludedRegions.add(bestRegionReplica);
                    }
                }
            }

            return true;
        }
    }

    private void wakeupFlushThread() {
        if (this.wakeupPending.compareAndSet(false, true)) {
            this.flushQueue.add(new MemStoreFlusher.WakeupFlushThread());
        }

    }

    private Region getBiggestMemstoreRegion(SortedMap<Long, Region> regionsBySize, Set<Region> excludedRegions, boolean checkStoreFileCount) {
        synchronized(this.regionsInQueue) {
            Iterator i$ = regionsBySize.values().iterator();

            Region region;
            do {
                //do {
                    //do {
                        do {
                            if (!i$.hasNext()) {
                                return null;
                            }

                            region = (Region)i$.next();
                        } while(excludedRegions.contains(region));
                    //} while(((HRegion)region).writestate.flushing);
                //} while(!((HRegion)region).writestate.writesEnabled);
            } while(checkStoreFileCount && this.isTooManyStoreFiles(region));

            return region;
        }
    }

    private Region getBiggestMemstoreOfRegionReplica(SortedMap<Long, Region> regionsBySize, Set<Region> excludedRegions) {
        synchronized(this.regionsInQueue) {
            Iterator i$ = regionsBySize.values().iterator();

            Region region;
            do {
                if (!i$.hasNext()) {
                    return null;
                }

                region = (Region)i$.next();
            } while(excludedRegions.contains(region) || RegionReplicaUtil.isDefaultReplica(region.getRegionInfo()));

            return region;
        }
    }

    private boolean refreshStoreFilesAndReclaimMemory(Region region) {
        try {
            return region.refreshStoreFiles();
        } catch (IOException var3) {
            LOG.warn("Refreshing store files failed with exception", var3);
            return false;
        }
    }

    private boolean isAboveHighWaterMark() {
        return this.server.getRegionServerAccounting().getGlobalMemstoreSize() >= this.globalMemStoreLimit;
    }

    private boolean isAboveLowWaterMark() {
        return this.server.getRegionServerAccounting().getGlobalMemstoreSize() >= this.globalMemStoreLimitLowMark;
    }

    public void requestFlush(Region r, boolean forceFlushAllStores) {
        synchronized(this.regionsInQueue) {
            if (!this.regionsInQueue.containsKey(r)) {
                MemStoreFlusher.FlushRegionEntry fqe = new MemStoreFlusher.FlushRegionEntry(r, forceFlushAllStores);
                this.regionsInQueue.put(r, fqe);
                this.flushQueue.add(fqe);
            }

        }
    }

    public void requestDelayedFlush(Region r, long delay, boolean forceFlushAllStores) {
        synchronized(this.regionsInQueue) {
            if (!this.regionsInQueue.containsKey(r)) {
                MemStoreFlusher.FlushRegionEntry fqe = new MemStoreFlusher.FlushRegionEntry(r, forceFlushAllStores);
                fqe.requeue(delay);
                this.regionsInQueue.put(r, fqe);
                this.flushQueue.add(fqe);
            }

        }
    }

    public int getFlushQueueSize() {
        return this.flushQueue.size();
    }

    void interruptIfNecessary() {
        this.lock.writeLock().lock();

        try {
            MemStoreFlusher.FlushHandler[] arr$ = this.flushHandlers;
            int len$ = arr$.length;

            for(int i$ = 0; i$ < len$; ++i$) {
                MemStoreFlusher.FlushHandler flushHander = arr$[i$];
                if (flushHander != null) {
                    flushHander.interrupt();
                }
            }
        } finally {
            this.lock.writeLock().unlock();
        }

    }

    synchronized void start(UncaughtExceptionHandler eh) {
        ThreadFactory flusherThreadFactory = Threads.newDaemonThreadFactory(this.server.getServerName().toShortString() + "-MemStoreFlusher", eh);

        for(int i = 0; i < this.flushHandlers.length; ++i) {
            this.flushHandlers[i] = new MemStoreFlusher.FlushHandler("MemStoreFlusher." + i);
            flusherThreadFactory.newThread(this.flushHandlers[i]);
            this.flushHandlers[i].start();
        }

    }

    boolean isAlive() {
        MemStoreFlusher.FlushHandler[] arr$ = this.flushHandlers;
        int len$ = arr$.length;

        for(int i$ = 0; i$ < len$; ++i$) {
            MemStoreFlusher.FlushHandler flushHander = arr$[i$];
            if (flushHander != null && flushHander.isAlive()) {
                return true;
            }
        }

        return false;
    }

    void join() {
        MemStoreFlusher.FlushHandler[] arr$ = this.flushHandlers;
        int len$ = arr$.length;

        for(int i$ = 0; i$ < len$; ++i$) {
            MemStoreFlusher.FlushHandler flushHander = arr$[i$];
            if (flushHander != null) {
                Threads.shutdown(flushHander.getThread());
            }
        }

    }

    private boolean flushRegion(MemStoreFlusher.FlushRegionEntry fqe) {
        Region region = fqe.region;
        if (!region.getRegionInfo().isMetaRegion() && this.isTooManyStoreFiles(region)) {
            if (!fqe.isMaximumWait(this.blockingWaitTime)) {
                if (fqe.getRequeueCount() <= 0) {
                    LOG.warn("Region " + region.getRegionInfo().getRegionNameAsString() + " has too many " + "store files; delaying flush up to " + this.blockingWaitTime + "ms");
                    if (!this.server.compactSplitThread.requestSplit(region)) {
                        try {
                            this.server.compactSplitThread.requestSystemCompaction(region, Thread.currentThread().getName());
                        } catch (IOException var4) {
                            LOG.error("Cache flush failed for region " + Bytes.toStringBinary(region.getRegionInfo().getRegionName()), RemoteExceptionHandler.checkIOException(var4));
                        }
                    }
                }

                this.flushQueue.add(fqe.requeue(this.blockingWaitTime / 100L));
                return true;
            }

            LOG.info("Waited " + (EnvironmentEdgeManager.currentTime() - fqe.createTime) + "ms on a compaction to clean up 'too many store files'; waited " + "long enough... proceeding with flush of " + region.getRegionInfo().getRegionNameAsString());
        }

        return this.flushRegion(region, false, fqe.isForceFlushAllStores());
    }

    private boolean flushRegion(Region region, boolean emergencyFlush, boolean forceFlushAllStores) {
        long startTime = 0L;
        synchronized(this.regionsInQueue) {
            MemStoreFlusher.FlushRegionEntry fqe = (MemStoreFlusher.FlushRegionEntry)this.regionsInQueue.remove(region);
            if (fqe != null) {
                startTime = fqe.createTime;
            }

            if (fqe != null && emergencyFlush) {
                this.flushQueue.remove(fqe);
            }
        }

        if (startTime == 0L) {
            startTime = EnvironmentEdgeManager.currentTime();
        }

        this.lock.readLock().lock();

        try {
            boolean shouldCompact;
            try {
                this.notifyFlushRequest(region, emergencyFlush);
                FlushResult flushResult = region.flush(forceFlushAllStores);
                shouldCompact = flushResult.isCompactionNeeded();
                boolean shouldSplit = ((HRegion)region).checkSplit() != null;
                if (shouldSplit) {
                    this.server.compactSplitThread.requestSplit(region);
                } else if (shouldCompact) {
                    this.server.compactSplitThread.requestSystemCompaction(region, Thread.currentThread().getName());
                }

                if (flushResult.isFlushSucceeded()) {
                    long endTime = EnvironmentEdgeManager.currentTime();
                    //this.server.metricsRegionServer.updateFlushTime(endTime - startTime);
                }

                return true;
            } catch (DroppedSnapshotException var16) {
                this.server.abort("Replay of WAL required. Forcing server shutdown", var16);
                shouldCompact = false;
                return shouldCompact;
            } catch (IOException var17) {
                LOG.error("Cache flush failed" + (region != null ? " for region " + Bytes.toStringBinary(region.getRegionInfo().getRegionName()) : ""), RemoteExceptionHandler.checkIOException(var17));
                if (this.server.checkFileSystem()) {
                    return true;
                } else {
                    shouldCompact = false;
                    return shouldCompact;
                }
            }
        } finally {
            this.lock.readLock().unlock();
            this.wakeUpIfBlocking();
        }
    }

    private void notifyFlushRequest(Region region, boolean emergencyFlush) {
        //FlushType type = FlushType.NORMAL;
        if (emergencyFlush) {
            //type = this.isAboveHighWaterMark() ? FlushType.ABOVE_HIGHER_MARK : FlushType.ABOVE_LOWER_MARK;
        }

        Iterator i$ = this.flushRequestListeners.iterator();

        while(i$.hasNext()) {
            FlushRequestListener listener = (FlushRequestListener)i$.next();
            //listener.flushRequested(type, region);
        }

    }

    private void wakeUpIfBlocking() {
        synchronized(this.blockSignal) {
            this.blockSignal.notifyAll();
        }
    }

    private boolean isTooManyStoreFiles(Region region) {
        Iterator i$ = region.getStores().iterator();

        Store store;
        do {
            if (!i$.hasNext()) {
                return false;
            }

            store = (Store)i$.next();
        } while(!store.hasTooManyStoreFiles());

        return true;
    }

    public void reclaimMemStoreMemory() {
        TraceScope scope = Trace.startSpan("MemStoreFluser.reclaimMemStoreMemory");
        if (this.isAboveHighWaterMark()) {
            if (Trace.isTracing()) {
                scope.getSpan().addTimelineAnnotation("Force Flush. We're above high water mark.");
            }

            long start = EnvironmentEdgeManager.currentTime();
            synchronized(this.blockSignal) {
                boolean blocked = false;
                long startTime = 0L;
                boolean interrupted = false;

                long totalTime;
                try {
                    while(this.isAboveHighWaterMark() && !this.server.isStopped()) {
                        if (!blocked) {
                            startTime = EnvironmentEdgeManager.currentTime();
                            LOG.info("Blocking updates on " + this.server.toString() + ": the global memstore size " + TraditionalBinaryPrefix.long2String(this.server.getRegionServerAccounting().getGlobalMemstoreSize(), "", 1) + " is >= than blocking " + TraditionalBinaryPrefix.long2String(this.globalMemStoreLimit, "", 1) + " size");
                        }

                        blocked = true;
                        this.wakeupFlushThread();

                        try {
                            this.blockSignal.wait(5000L);
                        } catch (InterruptedException var16) {
                            LOG.warn("Interrupted while waiting");
                            interrupted = true;
                        }

                        totalTime = EnvironmentEdgeManager.currentTime() - start;
                        LOG.warn("Memstore is above high water mark and block " + totalTime + "ms");
                    }
                } finally {
                    if (interrupted) {
                        Thread.currentThread().interrupt();
                    }

                }

                if (blocked) {
                    totalTime = EnvironmentEdgeManager.currentTime() - startTime;
                    if (totalTime > 0L) {
                        this.updatesBlockedMsHighWater.add(totalTime);
                    }

                    LOG.info("Unblocking updates for server " + this.server.toString());
                }
            }
        } else if (this.isAboveLowWaterMark()) {
            this.wakeupFlushThread();
        }

        scope.close();
    }

    public String toString() {
        return "flush_queue=" + this.flushQueue.size();
    }

    public String dumpQueue() {
        StringBuilder queueList = new StringBuilder();
        queueList.append("Flush Queue Queue dump:\n");
        queueList.append("  Flush Queue:\n");
        Iterator it = this.flushQueue.iterator();

        while(it.hasNext()) {
            queueList.append("    " + ((MemStoreFlusher.FlushQueueEntry)it.next()).toString());
            queueList.append("\n");
        }

        return queueList.toString();
    }

    public void registerFlushRequestListener(FlushRequestListener listener) {
        this.flushRequestListeners.add(listener);
    }

    public boolean unregisterFlushRequestListener(FlushRequestListener listener) {
        return this.flushRequestListeners.remove(listener);
    }

    public void setGlobalMemstoreLimit(long globalMemStoreSize) {
        this.globalMemStoreLimit = globalMemStoreSize;
        this.globalMemStoreLimitLowMark = (long)(this.globalMemStoreLimitLowMarkPercent * (float)globalMemStoreSize);
        this.reclaimMemStoreMemory();
    }

    public long getMemoryLimit() {
        return this.globalMemStoreLimit;
    }

    static class FlushRegionEntry implements MemStoreFlusher.FlushQueueEntry {
        private final Region region;
        private final long createTime;
        private long whenToExpire;
        private int requeueCount = 0;
        private boolean forceFlushAllStores;

        FlushRegionEntry(Region r, boolean forceFlushAllStores) {
            this.region = r;
            this.createTime = EnvironmentEdgeManager.currentTime();
            this.whenToExpire = this.createTime;
            this.forceFlushAllStores = forceFlushAllStores;
        }

        public boolean isMaximumWait(long maximumWait) {
            return EnvironmentEdgeManager.currentTime() - this.createTime > maximumWait;
        }

        public int getRequeueCount() {
            return this.requeueCount;
        }

        public boolean isForceFlushAllStores() {
            return this.forceFlushAllStores;
        }

        public MemStoreFlusher.FlushRegionEntry requeue(long when) {
            this.whenToExpire = EnvironmentEdgeManager.currentTime() + when;
            ++this.requeueCount;
            return this;
        }

        public long getDelay(TimeUnit unit) {
            return unit.convert(this.whenToExpire - EnvironmentEdgeManager.currentTime(), TimeUnit.MILLISECONDS);
        }

        public int compareTo(Delayed other) {
            int ret = Long.valueOf(this.getDelay(TimeUnit.MILLISECONDS) - other.getDelay(TimeUnit.MILLISECONDS)).intValue();
            if (ret != 0) {
                return ret;
            } else {
                MemStoreFlusher.FlushQueueEntry otherEntry = (MemStoreFlusher.FlushQueueEntry)other;
                return this.hashCode() - otherEntry.hashCode();
            }
        }

        public String toString() {
            return "[flush region " + Bytes.toStringBinary(this.region.getRegionInfo().getRegionName()) + "]";
        }

        public int hashCode() {
            int hash = (int)this.getDelay(TimeUnit.MILLISECONDS);
            return hash ^ this.region.hashCode();
        }

        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            } else if (obj != null && this.getClass() == obj.getClass()) {
                Delayed other = (Delayed)obj;
                return this.compareTo(other) == 0;
            } else {
                return false;
            }
        }
    }

    static class WakeupFlushThread implements MemStoreFlusher.FlushQueueEntry {
        WakeupFlushThread() {
        }

        public long getDelay(TimeUnit unit) {
            return 0L;
        }

        public int compareTo(Delayed o) {
            return -1;
        }

        public boolean equals(Object obj) {
            return this == obj;
        }
    }

    interface FlushQueueEntry extends Delayed {
    }

    private class FlushHandler extends HasThread {
        private FlushHandler(String name) {
            super(name);
        }

        public void run() {
            while(!MemStoreFlusher.this.server.isStopped()) {
                MemStoreFlusher.FlushQueueEntry fqe = null;

                try {
                    MemStoreFlusher.this.wakeupPending.set(false);
                    fqe = (MemStoreFlusher.FlushQueueEntry) MemStoreFlusher.this.flushQueue.poll(MemStoreFlusher.this.threadWakeFrequency, TimeUnit.MILLISECONDS);
                    if (fqe != null && !(fqe instanceof MemStoreFlusher.WakeupFlushThread)) {
                        MemStoreFlusher.FlushRegionEntry fre = (MemStoreFlusher.FlushRegionEntry)fqe;
                        if (MemStoreFlusher.this.flushRegion(fre)) {
                            continue;
                        }
                        break;
                    } else if (MemStoreFlusher.this.isAboveLowWaterMark()) {
                        MemStoreFlusher.LOG.debug("Flush thread woke up because memory above low water=" + TraditionalBinaryPrefix.long2String(MemStoreFlusher.this.globalMemStoreLimitLowMark, "", 1));
                        if (!MemStoreFlusher.this.flushOneForGlobalPressure()) {
                            Thread.sleep(1000L);
                            MemStoreFlusher.this.wakeUpIfBlocking();
                        }

                        MemStoreFlusher.this.wakeupFlushThread();
                    }
                } catch (InterruptedException var5) {
                } catch (ConcurrentModificationException var6) {
                } catch (Exception var7) {
                    MemStoreFlusher.LOG.error("Cache flusher failed for entry " + fqe, var7);
                    if (MemStoreFlusher.this.server.checkFileSystem()) {
                        continue;
                    }
                    break;
                }
            }

            synchronized(MemStoreFlusher.this.regionsInQueue) {
                MemStoreFlusher.this.regionsInQueue.clear();
                MemStoreFlusher.this.flushQueue.clear();
            }

            MemStoreFlusher.this.wakeUpIfBlocking();
            MemStoreFlusher.LOG.info(this.getName() + " exiting");
        }
    }
}
