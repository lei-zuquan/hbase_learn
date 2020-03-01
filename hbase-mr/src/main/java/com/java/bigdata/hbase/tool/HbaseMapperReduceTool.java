package com.java.bigdata.hbase.tool;

import com.java.bigdata.hbase.mapper.ScanDataMapper;
import com.java.bigdata.hbase.reducer.InsertDataReducer;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.io.ImmutableBytesWritable;
import org.apache.hadoop.hbase.mapreduce.TableMapReduceUtil;
import org.apache.hadoop.mapred.JobStatus;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.util.Tool;

public class HbaseMapperReduceTool implements Tool {


    @Override
    public int run(String[] strings) throws Exception {
        // 作业
        Job job = Job.getInstance();
        job.setJarByClass(HbaseMapperReduceTool.class);

        // mapper
        TableMapReduceUtil.initTableMapperJob(
                "my_namespace:test",
                new Scan(),
                ScanDataMapper.class,
                ImmutableBytesWritable.class,
                Put.class,
                job);

        // reducer
        TableMapReduceUtil.initTableReducerJob(
                "my_namespace:user",
                InsertDataReducer.class,
                job
        );

        // 执行作业
        boolean flg = job.waitForCompletion(true);

        return flg ? JobStatus.State.SUCCEEDED.getValue() : JobStatus.State.FAILED.getValue();
    }

    @Override
    public void setConf(Configuration configuration) {

    }

    @Override
    public Configuration getConf() {
        return null;
    }
}
