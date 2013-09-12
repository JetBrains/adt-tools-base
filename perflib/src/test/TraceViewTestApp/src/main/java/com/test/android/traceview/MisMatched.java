package com.test.android.traceview;

import android.os.Debug;

/**
 * Profile condition showing how start of trace may be at a different stack depth compared to
 * the end of the trace.
 */
public class MisMatched {
    public static void start() {
        foo();
        Debug.stopMethodTracing();
    }

    private static void foo() {
        bar();
    }

    private static void bar() {
        Debug.startMethodTracing("mismatched");
        baz();
    }

    private static int baz() {
        return 42;
    }
}
