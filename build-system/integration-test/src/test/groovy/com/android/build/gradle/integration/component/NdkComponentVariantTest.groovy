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
import com.android.build.gradle.integration.common.utils.DeviceHelper
import com.android.builder.core.BuilderConstants
import groovy.transform.CompileStatic
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test
import org.junit.experimental.categories.Category

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatZip
import static com.google.common.truth.Truth.assertThat

/**
 * Integration test of the native plugin with multiple variants.
 */
@CompileStatic
class NdkComponentVariantTest {

    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder()
            .fromTestApp(new HelloWorldJniApp())
            .forExperimentalPlugin(true)
            .create()

    @BeforeClass
    public static void setUp() {

        project.getBuildFile() << """
apply plugin: 'com.android.model.application'

model {
    android {
        compileSdkVersion = $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
        buildToolsVersion = "$GradleTestProject.DEFAULT_BUILD_TOOL_VERSION"
    }
    android.ndk {
        moduleName = "hello-jni"
    }
    android.buildTypes {
        create("jniDebug") {
            ndk.debuggable = true;
        }
    }
    android.productFlavors {
        create("x86") {
            ndk.abiFilters.add("x86")
        }
        create("arm") {
            ndk.abiFilters.add("armeabi-v7a")
            ndk.abiFilters.add("armeabi")
        }
        create("mips") {
            ndk.abiFilters.add("mips")
        }
    }
    android.abis {
        create("x86") {
            CFlags.add("-DX86")
        }
        create("armeabi-v7a") {
            CFlags.add("-DARMEABI_V7A")
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
        project.execute("assembleX86Debug")

        // Verify .so are built for all platform.
        File apk = project.getApk("x86", "debug")
        assertThatZip(apk).contains("lib/x86/libhello-jni.so")
        assertThatZip(apk).doesNotContain("lib/mips/libhello-jni.so")
        assertThatZip(apk).doesNotContain("lib/armeabi/libhello-jni.so")
        assertThatZip(apk).doesNotContain("lib/armeabi-v7a/libhello-jni.so")
    }

    @Test
    public void assembleArmDebug() {
        project.execute("assembleArmDebug")

        // Verify .so are built for all platform.
        File apk = project.getApk("arm", "debug")
        assertThatZip(apk).doesNotContain("lib/x86/libhello-jni.so")
        assertThatZip(apk).doesNotContain("lib/mips/libhello-jni.so")
        assertThatZip(apk).contains("lib/armeabi/libhello-jni.so")
        assertThatZip(apk).contains("lib/armeabi-v7a/libhello-jni.so")
    }

    @Test
    public void assembleMipsDebug() {
        project.execute("assembleMipsDebug")

        // Verify .so are built for all platform.
        File apk = project.getApk("mips", "debug")
        assertThatZip(apk).doesNotContain("lib/x86/libhello-jni.so")
        assertThatZip(apk).contains("lib/mips/libhello-jni.so")
        assertThatZip(apk).doesNotContain("lib/armeabi/libhello-jni.so")
        assertThatZip(apk).doesNotContain("lib/armeabi-v7a/libhello-jni.so")
    }

    @Test
    public void "check setting isDebuggable generates gdbserver and gdb.setup"() {
        project.execute("assembleArmJniDebug")

        File apk = project.getApk("arm", "jniDebug", "unsigned")
        assertThatZip(apk).contains("lib/armeabi/libhello-jni.so")
        assertThatZip(apk).contains("lib/armeabi/gdbserver")
        assertThatZip(apk).contains("lib/armeabi/gdb.setup")
        assertThatZip(apk).contains("lib/armeabi-v7a/libhello-jni.so")
        assertThatZip(apk).contains("lib/armeabi-v7a/gdbserver")
        assertThatZip(apk).contains("lib/armeabi-v7a/gdb.setup")
    }

    @Test
    public void "check release build does not contain gdbserver and gdb.setup"() {
        project.execute("assembleArmRelease")

        File apk = project.getApk("arm", "release", "unsigned")
        assertThatZip(apk).contains("lib/armeabi/libhello-jni.so")
        assertThatZip(apk).doesNotContain("lib/armeabi/gdbserver")
        assertThatZip(apk).doesNotContain("lib/armeabi/gdb.setup")
        assertThatZip(apk).contains("lib/armeabi-v7a/libhello-jni.so")
        assertThatZip(apk).doesNotContain("lib/armeabi-v7a/gdbserver")
        assertThatZip(apk).doesNotContain("lib/armeabi-v7a/gdb.setup")
    }

    @Test
    @Category(DeviceTests.class)
    public void connectedAndroidTest() {
        if (GradleTestProject.DEVICE_PROVIDER_NAME.equals(BuilderConstants.CONNECTED)) {
            Collection<String> abis = DeviceHelper.getDeviceAbis()
            if (abis.contains("x86")) {
                project.execute(GradleTestProject.DEVICE_PROVIDER_NAME + "x86DebugAndroidTest")
            } else {
                project.execute(GradleTestProject.DEVICE_PROVIDER_NAME + "ArmDebugAndroidTest")
            }
        } else {
            project.execute(GradleTestProject.DEVICE_PROVIDER_NAME + "X86DebugAndroidTest")
        }
    }
}
