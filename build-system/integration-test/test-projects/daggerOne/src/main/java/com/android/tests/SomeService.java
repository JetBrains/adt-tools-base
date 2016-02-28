package com.android.tests;

public class SomeService {
    public final String message;

    @javax.inject.Inject
    public SomeService(String message) {
        this.message = message;
    }
}
