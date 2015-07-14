package com.android.tools.fd.runtime.testapp;

import android.app.Application;

import com.android.tools.fd.runtime.Server;

public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // <inject>
//        Server.startCrashCatcher(this);
        // </inject>
    }
}
