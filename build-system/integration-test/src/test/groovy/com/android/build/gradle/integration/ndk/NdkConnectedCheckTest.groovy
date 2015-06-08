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

package com.android.build.gradle.integration.ndk

import com.android.build.gradle.integration.common.category.DeviceTests
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.TestProject
import com.android.build.gradle.integration.common.fixture.app.AndroidTestApp
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp
import com.android.build.gradle.integration.common.fixture.app.TestSourceFile
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test
import org.junit.experimental.categories.Category

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatZip

/**
 * Test AndroidTest with NDK.
 */
class NdkConnectedCheckTest {

    private static AndroidTestApp app = new HelloWorldJniApp();
    static {
        app.addFile(new TestSourceFile("src/androidTest/jni", "hello-jni-test.c",
"""
#include <string.h>
#include <jni.h>

jstring
Java_com_example_hellojni_HelloJniTest_expectedString(JNIEnv* env, jobject thiz)
{
    return (*env)->NewStringUTF(env, "hello world!");
}
"""));
        app.addFile(new TestSourceFile("src/androidTest/java/com/example/hellojni", "HelloJniTest.java",
"""
package com.example.hellojni;

import android.test.ActivityInstrumentationTestCase;

public class HelloJniTest extends ActivityInstrumentationTestCase<HelloJni> {

    public HelloJniTest() {
        super("com.example.hellojni", HelloJni.class);
    }

    // Get expected string from JNI.
    public native String expectedString();

    static {
        System.loadLibrary("hello-jni_test");
    }

    public void testJniName() {
        final HelloJni a = getActivity();
        // ensure a valid handle to the activity has been returned
        assertNotNull(a);

        assertTrue(expectedString().equals(a.stringFromJNI()));
    }
}
"""));
    }

    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder()
            .fromTestApp(app)
            .addGradleProperties("android.useDeprecatedNdk=true")
            .create()


    @BeforeClass
    static void setUp() {
        project.getBuildFile() <<
"""
apply plugin: 'com.android.application'

android {
    compileSdkVersion $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
    buildToolsVersion "$GradleTestProject.DEFAULT_BUILD_TOOL_VERSION"
    defaultConfig {
        ndk {
            moduleName "hello-jni"
        }
    }
}
"""
        project.execute("clean", "assembleAndroidTest");
    }


    @Test
    void "check test lib is packaged"() {
        File apk = project.getApk("debug", "androidTest", "unaligned")
        assertThatZip(apk).contains("lib/x86/libhello-jni_test.so")
    }

    @Test
    @Category(DeviceTests.class)
    void connectedCheck() {
        project.executeConnectedCheck()
    }
}
