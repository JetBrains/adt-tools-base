package com.android.tests.basic;

import com.example.android.multiproject.person.People;
import com.example.android.multiproject.person.Person;

public class StringGetter{

    public static String getString() {
         return getStringInternal();
    }

    private static String getStringInternal() {
        StringBuffer sb = new StringBuffer();

        Iterable<Person> people = new People();
        for (Person person : people) {
            sb.append(person.getName());
        }
        return sb.toString();
    }
}
