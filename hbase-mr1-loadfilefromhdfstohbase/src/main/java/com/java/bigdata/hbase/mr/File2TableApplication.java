package com.java.bigdata.hbase.mr;

import com.java.bigdata.hbase.mr.tool.File2TableTool;
import org.apache.hadoop.util.ToolRunner;

public class File2TableApplication {

    /**
     * 提交到yarn中运行：yarn jar /opt/module/data/hbase_mr_1_jar/hbase-mr_1.jar
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception{
        ToolRunner.run(new File2TableTool(), args);
    }
}
