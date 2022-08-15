package com.example.test;

public class InjectTest {
    public static void main(String arg[]) {
        System.out.println("今晚上山打老虎");
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void sayGo(){
        System.out.println("大家好aaaaaaaaaaaa");
    }
}
