/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.build.gradle.integration.common.fixture.app

import com.android.annotations.NonNull
import com.android.annotations.Nullable
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.TestProject

/**
 * Simple test application with an Android library that prints "hello world!".
 */
class HelloWorldLibraryApp extends MultiModuleTestProject implements TestProject {
    public HelloWorldLibraryApp() {
        super(":app" : new EmptyAndroidTestApp(), ":lib" : new HelloWorldApp());

        AndroidTestApp app = (AndroidTestApp) getSubproject(":app");

        // Create AndroidManifest.xml that uses the Activity from the library.
        app.addFile(new TestSourceFile("src/main", "AndroidManifest.xml",
"""<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
      package="com.example.app"
      android:versionCode="1"
      android:versionName="1.0">

    <uses-sdk android:minSdkVersion="3" />
    <application android:label="@string/app_name">
        <activity
            android:name="com.example.helloworld.HelloWorld"
            android:label="@string/app_name">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
"""));

    }
}
