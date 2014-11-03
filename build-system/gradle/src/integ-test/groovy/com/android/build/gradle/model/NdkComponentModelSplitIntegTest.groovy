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



package com.android.build.gradle.model

import com.android.build.gradle.internal.test.category.DeviceTests
import com.android.build.gradle.internal.test.fixture.GradleProjectTestRule
import com.android.build.gradle.internal.test.fixture.app.HelloWorldJniApp
import org.gradle.tooling.model.GradleProject
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test
import org.junit.experimental.categories.Category

import java.util.zip.ZipFile

//import static org.junit.Assert.assertNotNull
import static org.junit.Assert.assertNotNull
import static org.junit.Assert.assertNull

/**
 * Integration test of the native plugin with multiple variants.
 */
class NdkComponentModelSplitIntegTest {

    @ClassRule
    static public GradleProjectTestRule fixture = new GradleProjectTestRule();

    @BeforeClass
    static public void setup() {
        new HelloWorldJniApp().writeSources(fixture.getSourceDir())

        fixture.getBuildFile() << """
apply plugin: 'com.android.model.application'

model {
    android {
        compileSdkVersion $GradleProjectTestRule.DEFAULT_COMPILE_SDK_VERSION
        buildToolsVersion "$GradleProjectTestRule.DEFAULT_BUILD_TOOL_VERSION"
        splits {
            abi {
                enable true
                reset()
                include "x86", "armeabi-v7a", "mips"
            }
        }
    }
    android.ndk {
        moduleName "hello-jni"
    }
    android.buildTypes {
        debug {
            jniDebuggable true
        }
    }
}
"""
    }

    @Test
    public void assembleX86Debug() {
        fixture.execute("assembleX86Debug");
        ZipFile apk = new ZipFile(
                fixture.file(
                        "build/outputs/apk/${fixture.testDir.getName()}-x86-debug.apk"));

        // Verify .so are built for all platform.
        assertNotNull(apk.getEntry("lib/x86/libhello-jni.so"));
        assertNull(apk.getEntry("lib/mips/libhello-jni.so"));
        assertNull(apk.getEntry("lib/armeabi/libhello-jni.so"));
        assertNull(apk.getEntry("lib/armeabi-v7a/libhello-jni.so"));
    }

    @Test
    public void assembleArmDebug() {

        GradleProject project = fixture.execute("assembleArmeabi-v7aDebug");
        ZipFile apk = new ZipFile(
                fixture.file(
                        "build/outputs/apk/${fixture.testDir.getName()}-armeabi-v7a-debug.apk"));

        // Verify .so are built for all platform.
        assertNull(apk.getEntry("lib/x86/libhello-jni.so"));
        assertNull(apk.getEntry("lib/mips/libhello-jni.so"));
        assertNull(apk.getEntry("lib/armeabi/libhello-jni.so"));
        assertNotNull(apk.getEntry("lib/armeabi-v7a/libhello-jni.so"));
    }

    @Test
    public void assembleMipsDebug() {
        fixture.execute("assembleMipsDebug");
        ZipFile apk = new ZipFile(
                fixture.file(
                        "build/outputs/apk/${fixture.testDir.getName()}-mips-debug.apk"));

        // Verify .so are built for all platform.
        assertNull(apk.getEntry("lib/x86/libhello-jni.so"));
        assertNotNull(apk.getEntry("lib/mips/libhello-jni.so"));
        assertNull(apk.getEntry("lib/armeabi/libhello-jni.so"));
        assertNull(apk.getEntry("lib/armeabi-v7a/libhello-jni.so"));
    }

    @Test
    @Category(DeviceTests.class)
    public void connectedAndroidTest() {
        fixture.execute("connectedAndroidTest");
    }
}
