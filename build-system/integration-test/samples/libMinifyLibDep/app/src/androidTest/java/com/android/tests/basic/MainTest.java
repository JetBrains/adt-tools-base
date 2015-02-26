package com.android.tests.basic;

import android.test.ActivityInstrumentationTestCase2;
import android.test.suitebuilder.annotation.MediumTest;
import android.widget.TextView;

import java.lang.reflect.Method;

public class MainTest extends ActivityInstrumentationTestCase2<Main> {

    private Main mainActivity;
    private TextView mTextView;

    /**
     * Creates an {@link ActivityInstrumentationTestCase2} that tests the {@link Main} activity.
     */
    public MainTest() {
        super(Main.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mainActivity = getActivity();
        // ensure a valid handle to the activity has been returned
        assertNotNull(mainActivity);
        mTextView = (TextView) mainActivity.findViewById(R.id.dateText);
    }

    /**
     * The name 'test preconditions' is a convention to signal that if this
     * test doesn't pass, the test case was not set up properly and it might
     * explain any and all failures in other tests.  This is not guaranteed
     * to run before other tests, as junit uses reflection to find the tests.
     */
    @MediumTest
    public void testPreconditions() {
        assertNotNull(mTextView);
    }

    public void testNonObfuscatedMethod1() {
        // check we can call the method since it shouldn't be obfuscated.
        mainActivity.setUpTextView1();

        // then test we can actually find the lib method
        String className = "com.android.tests.basic.StringGetter";
        String methodName = "getString";

        searchMethod(className, methodName, true /*shouldExist*/);
    }

    public void testNonObfuscatedMethod2() {
        // check we can call the method since it shouldn't be obfuscated.
        mainActivity.setUpTextView2();

        // then test we cannot find the lib method since it should be
        // obfuscated by the app.
        String className = "com.android.tests.basic.StringGetter";
        String methodName = "getString2";

        searchMethod(className, methodName, false /*shouldExist*/);
    }

    /**
     * use reflection to get a method that should be obfuscated
     */
    public void testConsumerProguardRules() {
        String className = "com.android.tests.basic.StringGetter";
        String methodName = "getStringInternal";

        searchMethod(className, methodName, false /*shouldExist*/);
    }

    public void testLib2ObfuscatedClass() {
        // in this case the whole class has been obfuscated.
        String className = "com.android.tests.basic.StringProvider";
        try {
            Class<?> theClass = Class.forName(className);
            fail("Found " + className);
        } catch (ClassNotFoundException e) {
            // expected
        }
    }

    private void searchMethod(String className, String methodName, boolean shouldExist) {
        try {
            Class<?> theClass = Class.forName(className);
            Method method = theClass.getDeclaredMethod(methodName, int.class);
            if (!shouldExist) {
                fail("Found " + className + "." + methodName);
            }
        } catch (ClassNotFoundException e) {
            fail("Did not find " + className);
        } catch (NoSuchMethodException e) {
            if (shouldExist) {
                fail("Did not find " + className + "." + methodName);
            }
        }
    }
}

