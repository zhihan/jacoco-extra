package me.zhihan.jacoco;
    
public class MyRun implements Runnable {
    public void run() {
        System.out.println("hello");
        int i = 2;
        if (i > 0) {
            System.out.println("ok");
        }
    }
}
