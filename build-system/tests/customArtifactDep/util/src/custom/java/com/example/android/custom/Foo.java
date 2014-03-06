package com.example.android.custom;

public class Foo {
    public static String getString() {
        return Integer.toString(new java.util.Random(System.currentTimeMillis()).nextInt());
    }
}
