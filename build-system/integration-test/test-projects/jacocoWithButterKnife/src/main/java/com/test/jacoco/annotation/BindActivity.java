package com.test.jacoco.annotation;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

import butterknife.Bind;

public class BindActivity extends Activity {

    @Bind(R.id.textView)
    TextView textView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_layout);
        textView.setText("foo");
    }
}
