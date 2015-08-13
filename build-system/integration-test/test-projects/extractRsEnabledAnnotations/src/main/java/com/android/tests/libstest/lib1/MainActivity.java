package com.android.tests.libstest.lib1;

import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.StringDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public class MainActivity extends Activity {
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.lib1_main);
        Lib1.handleTextView(this);
    }

    @StringDef({STRING_1, STRING_2, "literalValue", "conc" + "atenated"})
    @Retention(RetentionPolicy.SOURCE)
    public @interface StringMode {}

    public static final String STRING_1 = "String1";
    public static final String STRING_2 = "String2";

    @StringMode
    public String getStringMode(int visibility) {
        return STRING_1;
    }
}
