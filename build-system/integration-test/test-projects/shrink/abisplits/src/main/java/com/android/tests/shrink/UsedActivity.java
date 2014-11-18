package com.android.tests.shrink;

import android.app.Activity;
import android.os.Bundle;

public class UsedActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.used);
    }
}
