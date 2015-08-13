package com.example.android.multiproject.library;

import android.test.ActivityInstrumentationTestCase2;
import android.view.View;
import android.widget.LinearLayout;

public class ShowPeopleActivityTest extends ActivityInstrumentationTestCase2<ShowPeopleActivity> {
    public ShowPeopleActivityTest() {
        super(ShowPeopleActivity.class);
    }

    public void testContentView() {
        ShowPeopleActivity activity = getActivity();

        View view = activity.findViewById(R.id.rootView);

        assertTrue(view instanceof LinearLayout);
    }
}
