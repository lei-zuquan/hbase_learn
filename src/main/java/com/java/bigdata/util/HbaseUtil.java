package com.java.bigdata.util;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.util.Bytes;


/**
 * Hbase操作工具类
 *
 * 工具类，异常一般不用自己做处理
 *
 */
public class HbaseUtil {

    // ThreadLocal，线程存储中有一块内存空间可以放数据ThreadLocal。解决线程操作hbase
    // ThreadLocal解决了同一个线程中共享数据问题，效率会更高
    // ThreadLocal解决不了多线程安全
    private static ThreadLocal<Connection> connHolder = new ThreadLocal<>();

    //private static Connection conn = null;

    private HbaseUtil() {

    }

    /**
     * 获取Hbase连接对象
     * @return
     * @throws Exception
     */
    public static void makeHbaseConnection() throws Exception {
        //Configuration conf = HBaseConfiguration.create();
        //conn = ConnectionFactory.createConnection(conf);
        //return conn;
        Connection conn = connHolder.get();
        if (conn == null){
            Configuration conf = HBaseConfiguration.create();
            conn = ConnectionFactory.createConnection(conf);
            connHolder.set(conn);
        }
    }

    /**
     * 生成分区键
     * @param regionCount
     * @return
     */
    public static byte[][] genRegionKeys(int regionCount){
        byte[][] bs = new byte[regionCount - 1][];

        // 3个分区 ==》对应2个分区键 ==》0，1
        for (int i = 0; i < regionCount - 1; i++) {
            bs[i] = Bytes.toBytes(i + "|");
        }

        return bs;
    }

    /**
     * 生成分区号
     * @param rowkey
     * @param regionCount
     * @return
     */
    public static String genRegionNum(String rowkey, int regionCount){
        int regionNum;
        int hash = rowkey.hashCode();

        if (regionCount > 0 && (regionCount & (regionCount - 1)) == 0){
            // 2 n
            regionNum = hash & (regionCount - 1);
        } else {
            regionNum = hash % (regionCount);
        }
        return regionNum + "_" + rowkey;
    }

    /**
     * 反转rowkey字符串
     * @param rowKey
     * @return
     */
    public static String reverseRowkey(String rowKey){
        return new StringBuilder(rowKey).reverse().toString();
    }

    public static void main(String[] args) {
        /**
         * 测试分区号
         */
        System.out.println(genRegionNum("lisi1", 3));
        System.out.println(genRegionNum("lisi2", 3));
        System.out.println(genRegionNum("lisi3", 3));
        System.out.println(genRegionNum("lisi4", 3));
        System.out.println(genRegionNum("lisi5", 3));
        System.out.println(genRegionNum("lisi6", 3));
        System.out.println(genRegionNum("lisi7", 3));

        /**
         * 测试分区键

        byte[][] bytes = genRegionKeys(6);
        for (byte[] aByte : bytes) {
            System.out.println(Bytes.toString(aByte));
        }
         */

        /**
         * 测试反转rowkey字符串
         */
        System.out.println(reverseRowkey("zhangsan121"));
    }


    public static void insertData(String tableName, String rowKey, String family, String colum, String value) throws Exception{
        Connection conn = connHolder.get();
        Table table = conn.getTable(TableName.valueOf(tableName));

        Put put = new Put(Bytes.toBytes(rowKey));
        put.addColumn(Bytes.toBytes(family), Bytes.toBytes(colum), Bytes.toBytes(value));

        table.put(put);
        table.close();
    }

    public static void close() throws Exception{
        Connection conn = connHolder.get();
         if (conn != null){
             conn.close();
             connHolder.remove();
         }
    }
}
