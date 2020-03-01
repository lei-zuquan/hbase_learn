package com.java.bigdata.hbase.mr.mapper;

import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.io.ImmutableBytesWritable;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;

import java.io.IOException;

public class ReadFileMapper extends Mapper<LongWritable, Text, ImmutableBytesWritable, Put> {

    @Override
    protected void map(LongWritable key, Text line, Context context) throws IOException, InterruptedException {

        String[] values = line.toString().split(",");
        String rowkey = values[0];

        byte[] bs = Bytes.toBytes(rowkey);

        Put put = new Put(bs);
        put.addColumn(Bytes.toBytes("info"), Bytes.toBytes("name"), Bytes.toBytes(values[1]));
        context.write(new ImmutableBytesWritable(bs), put);
    }
}
