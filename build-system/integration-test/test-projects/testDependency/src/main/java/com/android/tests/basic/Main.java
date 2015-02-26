package com.android.tests.basic;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

import com.google.common.collect.Lists;

import java.util.List;

public class Main extends Activity
{
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        TextView tv = (TextView) findViewById(R.id.text);
        List<String> list = Lists.newArrayList("foo", "bar");
        tv.setText(list.toString());
    }
}
