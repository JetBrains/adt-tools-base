package com.android.tests.conditionalApiUse;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        List<String> strings;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            strings = listCameras();
        } else {
            strings = new ArrayList<>();
            strings.add("Running on pre 21");
        }
        for (String s : strings) {
            Log.d("ConditionalApiUse", s);
        }
    }

    private List<String> listCameras() {
        AccessToApi21AndAbove accessTo21AndAbove = new AccessToApi21AndAbove();
        return accessTo21AndAbove.listCameras(getBaseContext());
    }
}
