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
 * Integration test of the native plugin with multiple variants.
 */
@CompileStatic
class NdkComponentSplitTest {

    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder()
            .fromTestApp(new HelloWorldJniApp(jniDir: "c"))
            .forExpermimentalPlugin(true)
            .create()

    @BeforeClass
    public static void setUp() {
        project.getBuildFile() << """
apply plugin: 'com.android.model.application'

model {
    android.config {
        compileSdkVersion $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
        buildToolsVersion "$GradleTestProject.DEFAULT_BUILD_TOOL_VERSION"
        generatePureSplits true

        defaultConfig {
            minSdkVersion 21
        }

        splits {
            abi {
                enable true
                reset()
                include "x86", "armeabi-v7a", "mips"
            }
        }
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
    public void assembleDebug() {
        project.execute("assembleDebug");

        // Verify .so are built for all platform.
        File apk = project.getApk("debug")
        assertThatZip(apk).doesNotContain("lib/armeabi-v7a/libhello-jni.so")
        assertThatZip(apk).doesNotContain("lib/mips/libhello-jni.so")
        assertThatZip(apk).doesNotContain("lib/x86/libhello-jni.so")

        File armApk = project.getApk("debug_armeabi-v7a")
        assertThatZip(armApk).contains("lib/armeabi-v7a/libhello-jni.so")
        assertThatZip(armApk).doesNotContain("lib/mips/libhello-jni.so")
        assertThatZip(armApk).doesNotContain("lib/x86/libhello-jni.so")

        File mipsApk = project.getApk("debug_mips")
        assertThatZip(mipsApk).doesNotContain("lib/armeabi-v7a/libhello-jni.so")
        assertThatZip(mipsApk).contains("lib/mips/libhello-jni.so")
        assertThatZip(mipsApk).doesNotContain("lib/x86/libhello-jni.so")

        File x86Apk = project.getApk("debug_x86")
        assertThatZip(x86Apk).doesNotContain("lib/armeabi-v7a/libhello-jni.so")
        assertThatZip(x86Apk).doesNotContain("lib/mips/libhello-jni.so")
        assertThatZip(x86Apk).contains("lib/x86/libhello-jni.so")
    }

    @Test
    @Category(DeviceTests.class)
    public void connectedAndroidTest() {
        project.executeConnectedCheck();
    }
}
