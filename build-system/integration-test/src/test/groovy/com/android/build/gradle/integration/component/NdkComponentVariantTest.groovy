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
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test
import org.junit.experimental.categories.Category

import java.util.zip.ZipFile

import static com.google.common.truth.Truth.assertThat
import static org.junit.Assert.assertNotNull
import static org.junit.Assert.assertNull

/**
 * Integration test of the native plugin with multiple variants.
 */
class NdkComponentVariantTest {

    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder().create();

    @BeforeClass
    public static void setUp() {
        new HelloWorldJniApp(jniDir: "c").write(project.testDir, null)

        project.getBuildFile() << """
apply plugin: 'com.android.model.application'

model {
    android.config {
        compileSdkVersion $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
        buildToolsVersion "$GradleTestProject.DEFAULT_BUILD_TOOL_VERSION"
    }
    android.ndk {
        moduleName "hello-jni"
    }
    android.buildTypes {
        debug {
            jniDebuggable true
        }
    }
    android.productFlavors {
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

    @AfterClass
    static void cleanUp() {
        project = null
    }

    @Test
    public void "check old ndk tasks are not created"() {
        List<String> tasks = project.getTaskList()
        assertThat(tasks).containsNoneOf(
                "compileArmDebugNdk",
                "compileX86DebugNdk",
                "compileMipsDebugNdk",
                "compileArmReleaseNdk",
                "compileX86ReleaseNdk",
                "compileMipsReleaseNdk")
    }

    @Test
    public void assembleX86Debug() {
        project.execute("assembleX86Debug");

        // Verify .so are built for all platform.
        ZipFile apk = new ZipFile(project.getApk("x86", "debug"));
        assertNotNull(apk.getEntry("lib/x86/libhello-jni.so"));
        assertNull(apk.getEntry("lib/mips/libhello-jni.so"));
        assertNull(apk.getEntry("lib/armeabi/libhello-jni.so"));
        assertNull(apk.getEntry("lib/armeabi-v7a/libhello-jni.so"));
    }

    @Test
    public void assembleArmDebug() {
        project.execute("assembleArmDebug");

        // Verify .so are built for all platform.
        ZipFile apk = new ZipFile(project.getApk("arm", "debug"));
        assertNull(apk.getEntry("lib/x86/libhello-jni.so"));
        assertNull(apk.getEntry("lib/mips/libhello-jni.so"));
        assertNotNull(apk.getEntry("lib/armeabi/libhello-jni.so"));
        assertNotNull(apk.getEntry("lib/armeabi-v7a/libhello-jni.so"));
    }

    @Test
    public void assembleMipsDebug() {
        project.execute("assembleMipsDebug");

        // Verify .so are built for all platform.
        ZipFile apk = new ZipFile(project.getApk("mips", "debug"));
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
