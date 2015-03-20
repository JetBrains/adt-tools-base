package com.example.android.multiproject.library2;

import android.content.Context;
import android.widget.TextView;

class PersonView2 extends TextView {
    public PersonView(Context context, String name) {
        super(context);
        setTextSize(20);
        setText(name);
    }
}
