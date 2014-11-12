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

package com.android.test.ndk

import com.android.test.common.category.DeviceTests

import static org.junit.Assert.assertNotNull
import static org.junit.Assert.assertNull

import com.android.test.common.fixture.GradleTestProject
import com.android.test.common.fixture.app.HelloWorldJniApp
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test
import org.junit.experimental.categories.Category

import java.util.zip.ZipFile

/**
 * Integration test of the native plugin with multiple variants.
 */
class NdkVariantIntegTest {

    @ClassRule
    static public GradleTestProject project = GradleTestProject.builder().create();

    @BeforeClass
    static public void setup() {
        new HelloWorldJniApp().writeSources(project.getSourceDir())

        project.getBuildFile() << """
apply plugin: 'com.android.application'

android {
    compileSdkVersion $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
    buildToolsVersion "$GradleTestProject.DEFAULT_BUILD_TOOL_VERSION"
    defaultConfig {
        ndk {
            moduleName "hello-jni"
        }
    }
    buildTypes {
        release
        debug {
            jniDebuggable true
        }
    }
    productFlavors {
        x86 {
            ndk {
                abiFilter "x86"
            }
        }
        arm {
            ndk {
                abiFilters "armeabi-v7a", "armeabi"
            }
        }
        mips {
            ndk {
                abiFilter "mips"
            }
        }
    }
}
"""
    }

    @Test
    public void assembleX86Release() {
        project.execute("assembleX86Release");
        ZipFile apk = new ZipFile(
                project.file(
                        "build/outputs/apk/${project.name}-x86-release-unsigned.apk"));

        // Verify .so are built for all platform.
        assertNotNull(apk.getEntry("lib/x86/libhello-jni.so"));
        assertNull(apk.getEntry("lib/mips/libhello-jni.so"));
        assertNull(apk.getEntry("lib/armeabi/libhello-jni.so"));
        assertNull(apk.getEntry("lib/armeabi-v7a/libhello-jni.so"));
    }

    @Test
    public void assembleArmRelease() {
        project.execute("assembleArmRelease");
        ZipFile apk = new ZipFile(
                project.file(
                        "build/outputs/apk/${project.name}-arm-release-unsigned.apk"));

        // Verify .so are built for all platform.
        assertNull(apk.getEntry("lib/x86/libhello-jni.so"));
        assertNull(apk.getEntry("lib/mips/libhello-jni.so"));
        assertNotNull(apk.getEntry("lib/armeabi/libhello-jni.so"));
        assertNotNull(apk.getEntry("lib/armeabi-v7a/libhello-jni.so"));
    }

    @Test
    public void assembleMipsRelease() {
        project.execute("assembleMipsRelease");
        ZipFile apk = new ZipFile(
                project.file(
                        "build/outputs/apk/${project.name}-mips-release-unsigned.apk"));

        // Verify .so are built for all platform.
        assertNull(apk.getEntry("lib/x86/libhello-jni.so"));
        assertNotNull(apk.getEntry("lib/mips/libhello-jni.so"));
        assertNull(apk.getEntry("lib/armeabi/libhello-jni.so"));
        assertNull(apk.getEntry("lib/armeabi-v7a/libhello-jni.so"));
    }

    @Test
    @Category(DeviceTests.class)
    public void connectedAndroidTest() {
        project.execute("connectedAndroidTestArmDebug");
    }
}
