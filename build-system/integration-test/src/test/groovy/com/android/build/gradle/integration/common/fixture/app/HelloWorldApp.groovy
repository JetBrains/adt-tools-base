/*
 * Copyright (C) 2014 The Android Open Source Project
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
import com.android.build.gradle.integration.common.fixture.GradleTestProject
/**
 * Simple test application that prints "hello world!".
 */
public class HelloWorldApp extends AbstractAndroidTestApp implements AndroidTestApp {

    static private final TestSourceFile javaSource =
            new TestSourceFile("src/main/java/com/example/helloworld", "HelloWorld.java",
    """
package com.example.helloworld;

import android.app.Activity;
import android.os.Bundle;

public class HelloWorld extends Activity {
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
    }
}
""");

    static private final TestSourceFile resValuesSource =
            new TestSourceFile("src/main/res/values", "strings.xml",
"""<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">HelloWorld</string>
</resources>
""");

    static private final TestSourceFile resLayoutSource =
            new TestSourceFile("src/main/res/layout", "main.xml",
"""<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:orientation="vertical"
    android:layout_width="fill_parent"
    android:layout_height="fill_parent"
    >
<TextView
    android:layout_width="fill_parent"
    android:layout_height="wrap_content"
    android:text="hello world!"
    android:id="@+id/text"
    />
</LinearLayout>
""");

    static private final TestSourceFile manifest =
            new TestSourceFile("src/main", "AndroidManifest.xml",
"""<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
      package="com.example.helloworld"
      android:versionCode="1"
      android:versionName="1.0">

    <uses-sdk android:minSdkVersion="3" />
    <application android:label="@string/app_name">
        <activity android:name=".HelloWorld"
                  android:label="@string/app_name">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
""");


    static private final TestSourceFile androidTestSource =
            new TestSourceFile("src/androidTest/java/com/example/helloworld", "HelloWorldTest.java",
"""
package com.example.helloworld;

import android.test.ActivityInstrumentationTestCase2;
import android.test.suitebuilder.annotation.MediumTest;
import android.widget.TextView;

public class HelloWorldTest extends ActivityInstrumentationTestCase2<HelloWorld> {
    private TextView mTextView;

    public HelloWorldTest() {
        super("com.example.helloworld", HelloWorld.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        final HelloWorld a = getActivity();
        // ensure a valid handle to the activity has been returned
        assertNotNull(a);
        mTextView = (TextView) a.findViewById(R.id.text);

    }

    @MediumTest
    public void testPreconditions() {
        assertNotNull(mTextView);
    }
}
""");

    private HelloWorldApp() {
        addFiles(javaSource, resValuesSource, resLayoutSource, manifest, androidTestSource);
    }

    private HelloWorldApp(String plugin) {
        this();

        TestSourceFile buildFile = new TestSourceFile("", "build.gradle",
            """
            apply plugin: '$plugin'

            android {
                compileSdkVersion $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                buildToolsVersion '$GradleTestProject.DEFAULT_BUILD_TOOL_VERSION'
            }

            """);

        addFile(buildFile)
    }

    public static HelloWorldApp noBuildFile() {
        return new HelloWorldApp()
    }

    public static HelloWorldApp forPlugin(String plugin) {
        return new HelloWorldApp(plugin)
    }
}
