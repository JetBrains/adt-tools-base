package com.test.android.traceview;

import android.os.Debug;

/** Extremely simple test case - just profile entry and exit out of foo() and bar() */
public class Basic {
    public static void start() {
        Debug.startMethodTracing("basic");
        foo();
        Debug.stopMethodTracing();
    }

    private static void foo() {
        bar();
    }

    private static int bar() {
        return 42;
    }
}
