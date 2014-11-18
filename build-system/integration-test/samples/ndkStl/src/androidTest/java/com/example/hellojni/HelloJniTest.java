package com.example.hellojni;

import android.test.ActivityInstrumentationTestCase2;

/**
 * This is a simple framework for a test of an Application.  See
 * {@link android.test.ApplicationTestCase ApplicationTestCase} for more information on
 * how to write and extend Application tests.
 * <p/>
 * To run this test, you can type:
 * adb shell am instrument -w \
 * -e class com.example.hellojni.HelloJniTest \
 * com.example.hellojni.tests/android.test.InstrumentationTestRunner
 */
public class HelloJniTest extends ActivityInstrumentationTestCase2<HelloJni> {

    public HelloJniTest() {
        super(HelloJni.class);
    }


    public void testJniName() {
        final HelloJni a = getActivity();
        // ensure a valid handle to the activity has been returned
        assertNotNull(a);

        assertTrue("HELLO WORLD!".equals(a.stringFromJNI()));
    }
}
