package com.android.tests.basic;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public class Main extends Activity
{

    private int foo = 1234;
    private TextView textView;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        textView = (TextView) findViewById(R.id.dateText);
    }

    public void setUpTextView1() {
        // doesn't actually set the value on the view since we're calling
        // this from the tes.
        String value = StringGetter.getString(foo);
    }

    public void setUpTextView2() {
        String value = StringGetter.getString2(foo);
    }
}
