package com.sample.android.multiproject.library;

import android.util.AttributeSet;
import android.util.TypedValue;
import android.widget.TextView;
import android.content.Context;
import com.example.android.multiproject.person.Person;

public class PersonView extends TextView {

    public PersonView(Context context) {
        super(context);
        init();
    }

    public PersonView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public PersonView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        setTextSize(TypedValue.COMPLEX_UNIT_PX, 20);
    }

    public PersonView(Context context, Person person) {
        super(context);

        setText(person.getName());
    }
}
