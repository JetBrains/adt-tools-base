package com.android.tests.flavors;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public class MainActivity extends Activity {
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        TextView tv;

        tv = (TextView) findViewById(R.id.buildconfig1);
        tv.setText(BuildConfig.FLAVOR_group1);

        tv = (TextView) findViewById(R.id.buildconfig2);
        tv.setText(BuildConfig.FLAVOR_group2);

        tv = (TextView) findViewById(R.id.codeoverlay1);
        tv.setText(com.android.tests.flavors.group1.SomeClass.getString());

        tv = (TextView) findViewById(R.id.codeoverlay2);
        tv.setText(com.android.tests.flavors.group2.SomeClass.getString());

        tv = (TextView) findViewById(R.id.codeoverlay3);
        tv.setText(com.android.tests.flavors.CustomizedClass.getString());
    }
}