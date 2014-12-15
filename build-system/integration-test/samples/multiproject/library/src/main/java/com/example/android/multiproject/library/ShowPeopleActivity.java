package com.example.android.multiproject.library;

import com.example.android.multiproject.person.People;
import com.example.android.multiproject.person.Person;
import com.sample.android.multiproject.library.PersonView;

import android.app.Activity;
import android.os.Bundle;
import android.widget.LinearLayout;

public class ShowPeopleActivity extends Activity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout group = new LinearLayout(this);
        group.setOrientation(LinearLayout.VERTICAL);
        group.setId(R.id.rootView);

        Iterable<Person> people = new People();
        for (Person person : people) {
            group.addView(new PersonView(this, person));
        }

        setContentView(group);
    }
}
