package com.example.android.multiproject.library;

import android.widget.TextView;
import android.content.Context;
import com.example.android.multiproject.person.Person;

class PersonView extends TextView {
    public PersonView(Context context, Person person) {
        super(context);
        setTextSize(20);
        setText(person.getName());
    }
}
