package com.java.bigdata.hbase;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.*;
import org.apache.hadoop.hbase.client.*;
import org.apache.hadoop.hbase.util.Bytes;

import java.io.IOException;
import java.util.ArrayList;

/**
 * 测试Hbase API
 *
 */
public class TestHbaseApi_1 {

    public static void main(String[] args) throws Exception {
        /**
         * 通过java代码访问mysql数据库
         * 1)加载数据库驱动
         * 2)获取数据库连接（url,user,password）
         * 3)获取数据库操作对象
         * 4)sql
         * 5)执行数据库操作
         * 6)获取查询结果ResultSet
         */


        /**
         * 通过java代码访问hbase数据库
         */
        // 0）创建配置对象，获取Hbase的连接



        Configuration conf = HBaseConfiguration.create();

        // 1）获取hbase连接对象
        // classLoader : Thread.currentThread.getContextClassLoader
        // classpath : hbase-default.xml,hbase-site.xml
        //Connection connection = ConnectionFactory.createConnection(conf);
        Connection connection = ConnectionFactory.createConnection(conf);
        System.out.println(connection);

        // 2）获取操作对象 : Admin
        // new HBaseAdmin(conf); 方法过时，不建议使用
        Admin admin = connection.getAdmin();

        // 3）操作数据库 : 判断hbase中是否存在某张表

        // 3-1) 判断命名空间
         try {
             //admin.getNamespaceDescriptor("my_namespace");
             admin.getNamespaceDescriptor("my_namespace");
         } catch (NamespaceNotFoundException e){
             // 创建表空间
             NamespaceDescriptor nd = NamespaceDescriptor.create("my_namespace").build();
             admin.createNamespace(nd);
         }

         // 3-2)判断hbase中是否存在某张表
         TableName tableName = TableName.valueOf("my_namespace:student");
         boolean flag = admin.tableExists(tableName);

         if (flag){
             // 获取指定的表对象
             Table table = connection.getTable(tableName);

             // 查询数据
             // DDL(create drop alter),DML(update,insert,delete),DQL(select)
             String rowkey = "1001";
             // string ==> byte[]
             // Get get = new Get(rowkey.getBytes()); // 这样写是有问题的，需要考虑字符编码
             Get get = new Get(Bytes.toBytes(rowkey)); // 需要考虑字符编码

             // 查询结果
             Result result = table.get(get);
             boolean empty = result.isEmpty();
             System.out.println("1001数据是否存在："+ !empty);
             if (empty){
                 // 新增数据
                 Put put = new Put(Bytes.toBytes(rowkey));
                 String family = "info";
                 String column = "name";
                 String val = "zhangsan";

                 put.addColumn(Bytes.toBytes(family),Bytes.toBytes(column),Bytes.toBytes(val));
                 table.put(put);
                 System.out.println("增加数据...");
             }else {
                // 展示数据
                 for (Cell cell : result.rawCells()) {
                     //cell.
                     System.out.println("value = " + Bytes.toString(CellUtil.cloneValue(cell)));
                     System.out.println("rowkey = " + Bytes.toString(CellUtil.cloneRow(cell)));
                     System.out.println("value = " + Bytes.toString(CellUtil.cloneFamily(cell)));
                     System.out.println("value = " + Bytes.toString(CellUtil.cloneQualifier(cell)));
                 }
             }


         } else {
             // 创建表

             // 创建表描述对象
             HTableDescriptor hd = new HTableDescriptor(tableName);

             hd.addCoprocessor("com.java.bigdata.hbase.coprocesser" +
                     ".InsertMynamespaceStudentCoprocesser"); // 协处理器
             // 增加列族
             HColumnDescriptor cd = new HColumnDescriptor("info");
             cd.setMaxVersions(3); // 设计表的最大版本数，默认最大版本为1
             cd.setTimeToLive(2 * 24 * 60 * 60); // 设计表中的数据存储生命周期，2天
             hd.addFamily(cd);
             admin.createTable(hd);
             System.out.println("表格创建成功...");
         }


        // 4）获取操作结果

        // 5）关闭数据库连接




    }
}
