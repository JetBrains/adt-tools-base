package com.android.tests.libdeps;

import com.android.tests.libdeps.R;
import com.google.common.base.Splitter;

import android.app.Activity;
import android.os.Bundle;

import java.lang.CharSequence;

public class MainActivity extends Activity {
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.lib1_main);
    }

    public static Iterable<String> split(String on, CharSequence target) {
        return Splitter.on("-").split(target);
    }
}
