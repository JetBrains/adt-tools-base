package com.android.tests.libstest.lib.test;

import android.test.ActivityInstrumentationTestCase2;
import android.test.suitebuilder.annotation.MediumTest;
import android.widget.TextView;

public class TestActivityTest extends ActivityInstrumentationTestCase2<TestActivity> {

    private TextView mTextView;

    /**
     * Creates an {@link android.test.ActivityInstrumentationTestCase2} that tests the {@link TestActivity} activity.
     */
    public TestActivityTest() {
        super(TestActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        final TestActivity a = getActivity();
        // ensure a valid handle to the activity has been returned
        assertNotNull(a);
        mTextView = (TextView) a.findViewById(R.id.text);
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

   @MediumTest
   public void testText() {
        assertEquals("Hello, world!", mTextView.getText().toString());
   }
}

