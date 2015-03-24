package com.android.tests.basic;

import android.test.ActivityInstrumentationTestCase2;
import android.test.suitebuilder.annotation.MediumTest;
import android.widget.TextView;

public class MainTest extends ActivityInstrumentationTestCase2<Main> {

    private TextView mTextView;
    private StringProvider stringProvider;

    /**
     * Creates an {@link ActivityInstrumentationTestCase2} that tests the {@link Main} activity.
     */
    public MainTest() {
        super(Main.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        final Main a = getActivity();
        // ensure a valid handle to the activity has been returned
        assertNotNull(a);
        mTextView = (TextView) a.findViewById(R.id.dateText);
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

    public void testTextViewContent() {
        assertEquals(
                "1234,com.android.tests.basic.IndirectlyReferencedClass",
                mTextView.getText().toString());
    }

    /** Test using a obfuscated class */
    public void testObfuscatedCode() {
        final Main a = getActivity();
        StringProvider sp = a.getStringProvider();
        assertEquals("42", sp.getString(42));
        assertEquals("com.android.tests.basic.a", StringProvider.class.getName());
    }

    public void testUseTestClass() {
        UsedTestClass o = new UsedTestClass();
        o.doSomething();

        assertEquals("com.android.tests.basic.UsedTestClass", UsedTestClass.class.getName());
    }
}

