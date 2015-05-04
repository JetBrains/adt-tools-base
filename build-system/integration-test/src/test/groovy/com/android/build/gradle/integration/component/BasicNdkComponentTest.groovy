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

package com.android.build.gradle.integration.component

import com.android.build.gradle.integration.common.category.DeviceTests
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp
import groovy.transform.CompileStatic
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test
import org.junit.experimental.categories.Category

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatZip

/**
 * Basic integration test for native plugin.
 */
@CompileStatic
class BasicNdkComponentTest {

    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder()
            .fromTestApp(new HelloWorldJniApp(jniDir: "cpp", useCppSource: true))
            .forExpermimentalPlugin(true)
            .withHeap("20148m")
            .create();

    @BeforeClass
    public static void setUp() {
        project.getBuildFile() << """
apply plugin: 'com.android.model.application'

model {
    android.config {
        compileSdkVersion $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
        buildToolsVersion "$GradleTestProject.DEFAULT_BUILD_TOOL_VERSION"
    }
    android.ndk {
        moduleName = "hello-jni"
    }
    android.buildTypes {
        afterEach {
            if (name == "debug") {
                isJniDebuggable = true
            }
        }
    }
}
"""
    }

    @AfterClass
    static void cleanUp() {
        project = null
    }

    @Test
    public void assemble() {
        project.execute("assemble")
    }

    @Test
    public void assembleRelease() {
        project.execute("assembleRelease")

        // Verify .so are built for all platform.
        File apk = project.getApk("release", "unsigned")
        assertThatZip(apk).contains("lib/x86/libhello-jni.so")
        assertThatZip(apk).contains("lib/mips/libhello-jni.so")
        assertThatZip(apk).contains("lib/armeabi/libhello-jni.so")
        assertThatZip(apk).contains("lib/armeabi-v7a/libhello-jni.so")
    }

    @Test
    public void assembleDebug() {
        project.execute("assembleDebug")

        // Verify .so are built for all platform.
        File apk = project.getApk("debug")
        assertThatZip(apk).contains("lib/x86/libhello-jni.so")
        assertThatZip(apk).contains("lib/x86/gdbserver")
        assertThatZip(apk).contains("lib/x86/gdb.setup")
        assertThatZip(apk).contains("lib/mips/libhello-jni.so")
        assertThatZip(apk).contains("lib/mips/gdbserver")
        assertThatZip(apk).contains("lib/mips/gdb.setup")
        assertThatZip(apk).contains("lib/armeabi/libhello-jni.so")
        assertThatZip(apk).contains("lib/armeabi/gdbserver")
        assertThatZip(apk).contains("lib/armeabi/gdb.setup")
        assertThatZip(apk).contains("lib/armeabi-v7a/libhello-jni.so")
        assertThatZip(apk).contains("lib/armeabi-v7a/gdbserver")
        assertThatZip(apk).contains("lib/armeabi-v7a/gdb.setup")
    }

    @Test
    @Category(DeviceTests.class)
    public void connnectedAndroidTest() {
        project.executeConnectedCheck();
    }
}
