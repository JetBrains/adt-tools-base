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

package com.android.build.gradle.integration.component

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.AndroidTestApp
import com.android.build.gradle.integration.common.fixture.app.EmptyAndroidTestApp
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.fixture.app.TestSourceFile
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatAar

/**
 * Integration test library plugin with JNI sources.
 */
class NdkJniLib2Test {
    private static MultiModuleTestProject testApp = new MultiModuleTestProject(
            ":app" : new EmptyAndroidTestApp(),
            ":lib" : new HelloWorldJniApp());

    static {
        AndroidTestApp app = (AndroidTestApp) testApp.getSubproject(":app")
        app.addFile(new TestSourceFile("", "build.gradle", """
apply plugin: "com.android.model.application"

dependencies {
    compile project(":lib")
}

model {
    android {
        compileSdkVersion = $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
        buildToolsVersion = "$GradleTestProject.DEFAULT_BUILD_TOOL_VERSION"
    }
}
"""))

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
            android:name="com.example.hellojni.HelloJni"
            android:label="@string/app_name">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
"""));

        AndroidTestApp lib = (AndroidTestApp) testApp.getSubproject(":lib")
        lib.addFile(new TestSourceFile("", "build.gradle",
                """
apply plugin: "com.android.model.library"

model {
    android {
        compileSdkVersion = $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
        buildToolsVersion = "$GradleTestProject.DEFAULT_BUILD_TOOL_VERSION"
    }
    android.ndk {
        moduleName = "hello-jni"
    }
}
"""))
    }

    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder()
            .fromTestApp(testApp)
            .forExperimentalPlugin(true)
            .create();

    @BeforeClass
    public static void assemble() {
        project.execute("clean", ":app:assembleDebug")
    }

    @AfterClass
    static void cleanUp() {
        project = null
        testApp = null
    }

    @Test
    void "check .so are included in both app and library"() {
        File releaseAar = project.getSubproject("lib").getAar("release")
        assertThatAar(releaseAar).contains("jni/x86/libhello-jni.so")

        File app = project.getSubproject("app").getApk("debug")
        assertThatAar(app).contains("lib/x86/libhello-jni.so")
    }
}
