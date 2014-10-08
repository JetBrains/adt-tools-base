package com.example.android.multiproject.library.base.test;

import android.test.ActivityInstrumentationTestCase2;
import android.test.suitebuilder.annotation.MediumTest;
import android.widget.Button;

import com.sample.android.multiproject.library.PersonView;

public class TestActivityTest extends ActivityInstrumentationTestCase2<TestActivity> {

    public TestActivityTest() {
        super(TestActivity.class);
    }

    public void testPreconditions() {
        TestActivity activity = getActivity();
        PersonView view = (PersonView) activity.findViewById(R.id.view);

        assertNotNull(view);
        assertEquals(20.0f, view.getTextSize());
    }
}

