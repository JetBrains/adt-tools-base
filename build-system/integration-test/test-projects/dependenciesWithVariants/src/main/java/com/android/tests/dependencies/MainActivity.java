package com.android.tests.dependencies;

import com.android.tests.dependencies.jar.StringHelper;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        TextView tv = (TextView) findViewById(R.id.text);
        tv.setText((String) StringHelper.getString("Foo"));
    }
}
