package com.android.tests;

import com.android.tests.MainActivity;

import org.junit.Ignore;
import org.junit.Test;

import android.app.Activity;

public class UnitTest {
    @Test
    public void referenceProductionCode() {
        // Reference production code:
        Foo foo = new Foo();
    }

    @Test
    public void referenceAndroidCode() {
        // Reference android code:
        Activity a = null;
    }

    @Test
    public void referenceProductionAndroidCode() {
        // Reference production android code:
        MainActivity ma = null;
    }

    @Test
    @Ignore
    public void thisIsIgnored() {
    }
}
