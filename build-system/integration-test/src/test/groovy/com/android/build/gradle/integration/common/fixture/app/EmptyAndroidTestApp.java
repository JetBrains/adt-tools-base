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

package com.android.build.gradle.integration.common.fixture.app;

/**
 * An empty app.
 */
public class EmptyAndroidTestApp extends AbstractAndroidTestApp implements AndroidTestApp {

    public EmptyAndroidTestApp() {

    }

    public EmptyAndroidTestApp(String packageName) {
        TestSourceFile manifest = new TestSourceFile("src/main", "AndroidManifest.xml",
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                "        package=\"" + packageName + "\"\n" +
                "        android:versionCode=\"1\"\n" +
                "        android:versionName=\"1.0\">\n" +
                "    <application/>\n" +
                "</manifest>\n");

        addFiles(manifest);
    }
}
