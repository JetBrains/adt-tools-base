package com.android.tests.libstest.lib;

import android.app.Activity;
import android.content.Context;
import android.content.res.TypedArray;
import android.widget.TextView;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.Readable;
import java.lang.String;

public class Lib {

    public static String getStringFromStyle(Context context){
        TypedArray array = context.obtainStyledAttributes(R.style.Example, R.styleable.StyleableExample);
        String result =  array.getString(R.styleable.StyleableExample_d_common_attr);
        array.recycle();
        return result;
    }
}
