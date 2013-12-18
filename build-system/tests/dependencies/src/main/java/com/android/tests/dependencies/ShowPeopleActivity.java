package com.android.tests.dependencies;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
import com.google.common.collect.Lists;

import java.lang.String;

public class ShowPeopleActivity extends Activity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String message = "People:";

        Iterable<Person> people = Lists.newArrayList(new Person("fred"));
        for (Person person : people) {
            message += "\n * ";
            message += person.getName();
        }

        TextView textView = new TextView(this);
        textView.setTextSize(20);
        textView.setText(message);

        setContentView(textView);
    }
}
