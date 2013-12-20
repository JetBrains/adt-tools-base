package com.example.android.multiproject;

import android.app.Activity;
import android.view.View;
import android.content.Intent;
import android.os.Bundle;

import java.util.List;
import com.example.android.multiproject.person.Person;
import com.google.common.collect.Lists;

import com.example.android.multiproject.library.ShowPeopleActivity;

public class MainActivity extends Activity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        // some random code to test dependencies on util and guava
        Person p = new Person("foo");
        List<Person> persons = Lists.newArrayList();
        persons.add(p);
    }

    public void sendMessage(View view) {
        Intent intent = new Intent(this, ShowPeopleActivity.class);
        startActivity(intent);
    }
}
