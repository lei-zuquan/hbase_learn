package com.java.bigdata.hbase.mr.tool;

import com.java.bigdata.hbase.mr.mapper.ReadFileMapper;
import com.java.bigdata.hbase.mr.reducer.InsertDataReducer;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.io.ImmutableBytesWritable;
import org.apache.hadoop.hbase.mapreduce.TableMapReduceUtil;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.JobStatus;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.util.Tool;

public class File2TableTool implements Tool {
    @Override
    public int run(String[] strings) throws Exception {
        Job job = Job.getInstance();
        job.setJarByClass(File2TableTool.class);

        // hbase(util) ==> hbase(util)
        // hdfd ==> hbase(util)

        // format
        /**
         * vim student.csv
         * 1001,zhangsan
         * 1002,lisi
         * 1003,wangwu
         *
         * hadoop fs -put student.csv /data/
         */
        Path path = new Path("hdfd://linux01:9000/data/student.csv");
        FileInputFormat.addInputPath(job, path);

        // mapper
        job.setMapperClass(ReadFileMapper.class);
        job.setMapOutputKeyClass(ImmutableBytesWritable.class);
        job.setMapOutputValueClass(Put.class);

        // reducer
        TableMapReduceUtil.initTableReducerJob(
                "my_namespace:student",
                InsertDataReducer.class,
                job
        );

        return job.waitForCompletion(true) ? JobStatus.State.SUCCEEDED.getValue() :
                JobStatus.State.FAILED.getValue();
    }

    @Override
    public void setConf(Configuration configuration) {

    }

    @Override
    public Configuration getConf() {
        return null;
    }
}
