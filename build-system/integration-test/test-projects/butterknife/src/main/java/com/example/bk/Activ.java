package com.example.bk;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class Activ extends Activity {

    @Bind(R.id.someText) TextView text;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activ_layout);
        ButterKnife.bind(this);
        text.setText("original");
        android.util.Log.d("butterknife", text.getText().toString());
    }

    @OnClick(R.id.click)
    public void clicked() {
        text.setText("clicked");
    }
}
