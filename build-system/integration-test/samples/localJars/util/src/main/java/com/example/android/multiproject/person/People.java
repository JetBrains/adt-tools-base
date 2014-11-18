package com.example.android.multiproject.person;

import java.util.Iterator;
import com.google.common.collect.Lists;

public class People implements Iterable<Person> {
    public Iterator<Person> iterator() {
        return Lists.newArrayList(new Person("Fred"), new Person("Barney")).iterator();
    }
}
