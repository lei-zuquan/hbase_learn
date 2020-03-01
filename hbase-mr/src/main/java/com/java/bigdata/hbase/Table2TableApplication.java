package com.java.bigdata.hbase;

import com.java.bigdata.hbase.tool.HbaseMapperReduceTool;
import org.apache.hadoop.util.ToolRunner;

public class Table2TableApplication {

    /**
     * 数据迁移
     *
     * 打jar包有2种方式：
     *      1.依赖jar包
     *      2.可运行jar包，就是含有main方法
     *
     * 打包成可运行jar包
     *
     * 提交到linux服务器上
     *
     * 提交到yarn中运行：yarn jar /opt/module/hbase_mr_jar/hbase-mr.jar
     *
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        // ToolRunner可以运行MR
        ToolRunner.run(new HbaseMapperReduceTool(), args);
    }
}
