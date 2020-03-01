package com.java.bigdata.hbase.coprocesser;

import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Durability;
import org.apache.hadoop.hbase.client.HTableInterface;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.coprocessor.BaseRegionObserver;
import org.apache.hadoop.hbase.coprocessor.ObserverContext;
import org.apache.hadoop.hbase.coprocessor.RegionCoprocessorEnvironment;
import org.apache.hadoop.hbase.regionserver.wal.WALEdit;

import java.io.IOException;

/**
 * 协处理器(Hbase自己的功能)
 * 1）创建类，继承BaseRegionObserver
 * 2）重写方法：postPut
 * 3) 实现逻辑：
 *      增加student的数据
 *      同时增加my_namespace:student中数据
 * 4) 将项目打包（依赖）后上传到hbase中，让hbase可以识别协处理器
 *      将hbase_coprocesser.jar上传到$HBASE_HOME/lib
 *      因为hbase是集群，需要将hbase_coprocesser.jar分发到其他节点
 *      重新启动hbase集群
 *
 * 5）先删除原始表，创建2张新表: "my_namespace:student", "student"
 *      同时设定协处理器
 *      hd.addCoprocessor("com.java.bigdata.hbase.coprocesser" +
 *                      ".InsertMynamespaceStudentCoprocesser"); // 协处理器
 *
 *      describe "my_namespace:student"
 *
 *      put 'student', '1001', 'info:name', 'zhangsan'
 *      scan 'student'
 *      scan 'my_namespace:student' // 会发现数据自动同步了
 */
public class InsertMynamespaceStudentCoprocesser extends BaseRegionObserver {
    // prePut
    // doPut
    // postPut
    @Override
    public void postPut(ObserverContext<RegionCoprocessorEnvironment> e, Put put, WALEdit edit, Durability durability) throws IOException {
        //super.postPut(e, put, edit, durability);

        // 获取表
        Table table = e.getEnvironment().getTable(TableName.valueOf("my_namespace:student"));

        // 增加数据
        table.put(put);

        // 关闭表
        table.close();
    }
}
