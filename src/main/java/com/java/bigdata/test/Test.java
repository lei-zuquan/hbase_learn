package com.java.bigdata.test;

import org.apache.hadoop.hbase.io.util.HeapMemorySizeUtil;

import java.lang.management.MemoryUsage;

public class Test {

    public static void main(String[] args) {
        System.out.println(User.age);
        System.out.println(Emp.age);

        // 多态传递
        // A a = new C(); // B b = new C();

        // System.out.println(C.class.getInterfaces().length);
        // new java.Lang.String();

        final MemoryUsage usage = HeapMemorySizeUtil.safeGetHeapMemoryUsage();
        // -Xms 1/64
        System.out.println(usage.getInit());
        // -Xmx 1/4
        System.out.println(usage.getMax());
    }
}
