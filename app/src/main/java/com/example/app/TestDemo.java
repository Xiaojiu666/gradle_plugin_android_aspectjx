package com.example.app;

import java.io.FileNotFoundException;

public class TestDemo {
    static class Father {
        public void say() {
            System.out.println("i am fater");
            System.out.println(this);
            this.hello();
            this.hi();
        }

        private void hello() {
            System.out.println("father say hello");
        }

        public void hi() {
            System.out.println("father say hi");
        }
    }

    static class Son extends Father {

        public void hello() {
            System.out.println("son say hello");
        }

        @Override
        public void hi() {
            System.out.println("son say hi");
        }
    }
}
