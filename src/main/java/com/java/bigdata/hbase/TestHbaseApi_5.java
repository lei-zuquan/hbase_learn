package com.java.bigdata.hbase;

import com.java.bigdata.util.HbaseUtil;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.*;
import org.apache.hadoop.hbase.client.*;
import org.apache.hadoop.hbase.util.Bytes;

/**
 * 测试Hbase API
 *
 */
public class TestHbaseApi_5 {

    public static void main(String[] args) throws Exception {

        Configuration conf = HBaseConfiguration.create();

        Connection connection = ConnectionFactory.createConnection(conf);

        Admin admin = connection.getAdmin();

         try {
             admin.getNamespaceDescriptor("my_namespace");
         } catch (NamespaceNotFoundException e){
             // 创建表空间
             NamespaceDescriptor nd = NamespaceDescriptor.create("my_namespace").build();
             admin.createNamespace(nd);
         }

         TableName tableName = TableName.valueOf("my_namespace:emp");
        // 判断hbase中是否存在某张表
        boolean flag = admin.tableExists(tableName);
        if (flag) {
            admin.disableTable(tableName);
            admin.deleteTable(tableName);
        }
         // 创建表
         // 创建表描述对象
         HTableDescriptor hd = new HTableDescriptor(tableName);
         // 增加列族
         HColumnDescriptor cd = new HColumnDescriptor("info");
         hd.addFamily(cd);

         // [0, 1]
         // [byte[], byte[]]
        //byte[][] bs = new byte[2][];
        /**
         * |是除{外对应的anix第二大的字符
         * 为什么不用{呢，因为{通常不用来作业连接字符串的
         * 比如：012322123肯定在0号分区中
         *
         */
        //bs[0] = Bytes.toBytes("0|");
        //bs[1] = Bytes.toBytes("1|");

        byte[][] bs = HbaseUtil.genRegionKeys(3);
        // 创建表的同时，增加预分区
        admin.createTable(hd, bs);
        System.out.println("表创建成功...");

        // 增加数据
        Table empTable = connection.getTable(TableName.valueOf("emp"));
        String rowkey = "0_zhangsan";
        //String rowkey = "1_lisi";

        // hashMap
        // 将rowkey均匀的分配的不同的分区中，效果和hashmap数据存储的规则是一样

        // HashMap
        rowkey = HbaseUtil.genRegionNum(rowkey, 3);
        Put put = new Put(Bytes.toBytes(rowkey));
        put.addColumn(Bytes.toBytes("info"), Bytes.toBytes("age"),Bytes.toBytes("20"));
        empTable.put(put);
    }
}
