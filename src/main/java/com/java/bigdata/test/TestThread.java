package com.java.bigdata.test;

import com.sun.org.apache.xpath.internal.operations.String;

public class TestThread {

    public static void main(String[] args) throws Exception{

        // sleep
        // Monitor监听器
//        TestThread testThread = new TestThread();
//        synchronized (testThread){
//            new Thread().wait();
//        }

        // 多例
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            s.append(i);
        }

        System.out.println(s);

    }
}
