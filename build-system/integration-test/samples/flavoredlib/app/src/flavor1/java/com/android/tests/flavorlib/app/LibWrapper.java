package com.android.tests.flavorlib.app;

import android.app.Activity;

import com.android.tests.flavorlib.lib.flavor1.Lib;

/**
 */
public class LibWrapper {

    public static void handleTextView(Activity a) {
        Lib.handleTextView(a);
    }
}
