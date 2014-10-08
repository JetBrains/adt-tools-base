package com.example.android.multiproject;

import android.app.Activity;
import android.view.View;
import android.content.Intent;
import android.os.Bundle;

import android.widget.TextView;
import com.example.android.custom.Foo;

public class MainActivity extends Activity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        TextView tv = (TextView) findViewById(R.id.text);
        tv.setText(Foo.getString());
    }
}
