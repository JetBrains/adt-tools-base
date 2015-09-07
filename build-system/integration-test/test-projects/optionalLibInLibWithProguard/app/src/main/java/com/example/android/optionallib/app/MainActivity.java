package com.example.android.optionallib.app;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

import com.example.android.optionallib.library.HttpUser;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        TextView tv = (TextView) findViewById(R.id.text);
        tv.setText(HttpUser.downloadStuff());
    }
}

