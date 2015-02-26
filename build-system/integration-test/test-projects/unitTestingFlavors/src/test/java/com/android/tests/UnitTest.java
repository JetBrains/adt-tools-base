package com.android.tests;

import static org.junit.Assert.*;

import org.junit.Test;
import org.junit.Ignore;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import android.app.Activity;

import com.android.tests.MainActivity;

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
