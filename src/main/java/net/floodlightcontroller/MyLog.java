package net.floodlightcontroller;


public class MyLog {

    public static void info(String mess){
        System.out.println("----MyLog info---- "+mess);
    }
    public static void warn(String mess){
        System.out.println("----MyLog warn---- "+mess);
    }
    public static void error(String mess){
        System.out.println("----MyLog error---- "+mess);
    }
}