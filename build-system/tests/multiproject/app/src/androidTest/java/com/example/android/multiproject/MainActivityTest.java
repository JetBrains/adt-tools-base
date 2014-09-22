package com.example.android.multiproject;

import android.test.ActivityInstrumentationTestCase2;
import android.test.suitebuilder.annotation.MediumTest;
import android.widget.Button;

public class MainActivityTest extends ActivityInstrumentationTestCase2<MainActivity> {

    private Button mButton;

    public MainActivityTest() {
        super(MainActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        final MainActivity a = getActivity();
        // ensure a valid handle to the activity has been returned
        assertNotNull(a);
        mButton = (Button) a.findViewById(R.id.foo);
    }

    @MediumTest
    public void testPreconditions() {
        assertNotNull(mButton);
    }
}

