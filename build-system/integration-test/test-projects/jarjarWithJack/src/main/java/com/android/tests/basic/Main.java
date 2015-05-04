package com.android.tests.basic;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
import com.google.gson.Gson;

import java.lang.String;

public class Main extends Activity
{
    private static final String[] strings = { "some", "radom", "strings"};
    
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        TextView tv = (TextView) findViewById(R.id.text);
        Gson gson = new Gson();
        String jsonString = gson.toJson(strings);
        tv.setText(jsonString);
    }
}
