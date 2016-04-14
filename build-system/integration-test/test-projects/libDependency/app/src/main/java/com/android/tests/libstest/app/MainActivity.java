package com.android.tests.libstest.app;

import com.android.tests.libstest.lib.Lib;

import android.app.Activity;
import android.os.Bundle;
import java.util.logging.Logger;

public class MainActivity extends Activity {
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        App.handleTextView(this);
        Logger.getLogger(Logger.GLOBAL_LOGGER_NAME).warning(Lib.someString());
    }
}
