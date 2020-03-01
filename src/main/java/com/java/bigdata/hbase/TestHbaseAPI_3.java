package com.java.bigdata.hbase;

import com.java.bigdata.util.HbaseUtil;
import org.apache.hadoop.hbase.client.Connection;

public class TestHbaseAPI_3 {

    public static void main(String[] args) throws Exception {

        // 插入数据
        HbaseUtil.makeHbaseConnection();

        // 增加数据
        HbaseUtil.insertData("my_namespace:test","1001", "info", "name", "zhangsan");

        // 关闭连接
        HbaseUtil.close();
    }
}
