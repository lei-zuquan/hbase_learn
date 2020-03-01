package com.java.bigdata.hbase;

import com.java.bigdata.util.HbaseUtil;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.CellUtil;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.*;
import org.apache.hadoop.hbase.filter.*;
import org.apache.hadoop.hbase.util.Bytes;

/**
 * 查询数据
 *
 * 过滤器
 *
 * 所谓的过滤，其实每条数据都会筛选过滤，性能比较低
 *
 * hbase还是可以加索引，hbase是没有索引，模拟索引
 *
 * user
 *      info : name     zhangsan
 *
 * user_index(二级索引)，后面还有一引框架fnix，就是为了优化hbase二级索引的
 *      因为可能很多人叫张三，所以rowkey又不允许同时多个zhangsan。这时就体现了hbase一个特性动态列
 *      rowkey      info:xxxxxx, info:yyyyyy
 *      zhangsan
 *
 */
public class TestHbaseAPI_4 {

    public static void main(String[] args) throws Exception {

        Configuration conf = HBaseConfiguration.create();

        Connection connection = ConnectionFactory.createConnection(conf);

        TableName tableName = TableName.valueOf("my_namespace:test");

        // 扫描数据
        Table table = connection.getTable(tableName);

        Scan scan = new Scan();
        //scan.addFamily(Bytes.toBytes("info")); // 指定列族
        BinaryComparator bc = new BinaryComparator(Bytes.toBytes("2001"));
        RegexStringComparator rsc = new RegexStringComparator("^\\d{3}$");
        //Filter f = new RowFilter(CompareFilter.CompareOp.GREATER_OR_EQUAL, bc);
        Filter f = new RowFilter(CompareFilter.CompareOp.GREATER_OR_EQUAL, rsc);

        RowFilter rf = new RowFilter(CompareFilter.CompareOp.EQUAL, bc);

        // FilterList.Operator.MUST_PASS_ALL : and
        // FilterList.Operator.MUST_PASS_ONE : or
        FilterList list = new FilterList(FilterList.Operator.MUST_PASS_ONE); // and
        list.addFilter(f);
        list.addFilter(rf);
        // 扫描时增加过滤器
        scan.setFilter(list);

        ResultScanner scanner = table.getScanner(scan);

        for (Result result : scanner) {
            for (Cell cell : result.rawCells()) {
                System.out.println("value = " + Bytes.toString(CellUtil.cloneValue(cell)));
                System.out.println("rowkey = " + Bytes.toString(CellUtil.cloneRow(cell)));
                System.out.println("family = " + Bytes.toString(CellUtil.cloneFamily(cell)));
                System.out.println("column = " + Bytes.toString(CellUtil.cloneQualifier(cell)));

            }
        }

        table.close();
        connection.close();
    }
}
