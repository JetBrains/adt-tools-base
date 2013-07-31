package com.test.android.traceview;

import android.os.Debug;

public class Exceptions {
    public static void start() {
        // load runtime exception once so that the trace is not cluttered with
        // classloader methods attempting to load RuntimeException
        try {
            throw new RuntimeException();
        } catch (Exception e) {
        }

        try {
            Debug.startMethodTracing("exception");
            foo();
        } catch (RuntimeException e) {

        } finally {
            Debug.stopMethodTracing();
        }
    }

    private static void foo() {
        bar();
    }

    private static void bar() {
        baz();
    }

    private static void baz() {
        throw new RuntimeException();
    }
}
