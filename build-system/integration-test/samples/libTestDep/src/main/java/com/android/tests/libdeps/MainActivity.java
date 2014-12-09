package com.android.tests.libdeps;

import android.app.Activity;
import android.os.Bundle;

import com.android.tests.libdeps.R;

public class MainActivity extends Activity {
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.lib1_main);
    }
}
