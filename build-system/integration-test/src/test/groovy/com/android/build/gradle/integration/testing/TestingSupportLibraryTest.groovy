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

package com.android.build.gradle.integration.testing

import com.android.build.gradle.integration.common.category.DeviceTests
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.AndroidTestApp
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.fixture.app.TestSourceFile
import groovy.transform.CompileStatic
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatZip

/**
 * Test project to cover the Android Gradle plugin's interaction with the testing support library.
 */
@CompileStatic
class TestingSupportLibraryTest {

    public static final AndroidTestApp helloWorldApp = HelloWorldApp.noBuildFile();
    static {
        /* Junit 4 now maps tests annotated with @Ignore and tests that throw
           AssumptionFailureExceptions as skipped. */
        helloWorldApp.addFile(new  TestSourceFile("src/androidTest/java/com/example/helloworld", "FailureAssumptionTest.java",
                """
package com.example.helloworld;

import android.support.test.runner.AndroidJUnit4;
import android.test.suitebuilder.annotation.SmallTest;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class FailureAssumptionTest {
    @Test
    public void checkAssumptionIsSkipped() {
        assumeTrue(false);
        fail("Tests with failing assumptions should be skipped");
    }

    @Test
    @Ignore
    public void checkIgnoreTestsArePossible() {
        fail("Tests with @Ignore annotation should be skipped");
    }

    @Test
    public void checkThisTestPasses() {
        System.err.println("Test executed");
    }
}
"""))
        helloWorldApp.addFile(new TestSourceFile("src/main", "AndroidManifest.xml",
                """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
      package="com.example.helloworld"
      android:versionCode="1"
      android:versionName="1.0">

    <uses-sdk android:minSdkVersion="18" />
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
"""))
    }

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
        .fromTestApp(helloWorldApp)
        .create()

    @Before
    public void setUp() {
        project
        project.getBuildFile() << """

apply plugin: 'com.android.application'

android {
    compileSdkVersion $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
    buildToolsVersion "$GradleTestProject.DEFAULT_BUILD_TOOL_VERSION"
    defaultConfig {
        testInstrumentationRunner "android.support.test.runner.AndroidJUnitRunner"
    }
    dependencies {
        androidTestCompile 'com.android.support.test:runner:0.3'
        androidTestCompile 'com.android.support.test:rules:0.3'
    }
}
"""
    }

    @Test
    public void "check compile"() {
        project.execute("assembleDebugAndroidTest")
    }

    @Test
    @Category(DeviceTests.class)
    public void "test ignored tests are not run"() {
        project.executeConnectedCheck()
    }
}
