package com.android.tests.libstest.app;

import android.app.Activity;
import android.os.Bundle;

import com.android.tests.libstest.libapp.R;

public class MainActivityLibApp extends Activity {
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.libapp_main);
        
        LibApp.handleTextView(this);
    }
}
