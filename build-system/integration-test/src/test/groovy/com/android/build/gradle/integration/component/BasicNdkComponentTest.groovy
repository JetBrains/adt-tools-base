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
import com.android.build.gradle.integration.common.category.SmokeTests
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp
import com.android.build.gradle.integration.common.utils.ZipHelper
import groovy.transform.CompileStatic
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatNativeLib
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatZip

/**
 * Basic integration test for native plugin.
 */
@RunWith(Parameterized.class)
@Category(SmokeTests.class)
@CompileStatic
class BasicNdkComponentTest {

    @Parameterized.Parameter(value = 0)
    public String toolchain;

    @Parameterized.Parameters(name="{0}")
    public static Collection<Object[]> data() {
        return [
                ["gcc"].toArray(),
                ["clang"].toArray(),
        ];
    }

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestApp(HelloWorldJniApp.builder().useCppSource().build())
            .useExperimentalGradleVersion(true)
            .withHeap("2048m")
            .create();

    @Before
    public void setUp() {
        project.getBuildFile() << """
apply plugin: 'com.android.model.application'

model {
    android {
        compileSdkVersion $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
        buildToolsVersion "$GradleTestProject.DEFAULT_BUILD_TOOL_VERSION"

        ndk {
            moduleName "hello-jni"
            platformVersion 19
            toolchain "$toolchain"
        }
    }
}
"""
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

        File lib = ZipHelper.extractFile(apk, "lib/armeabi-v7a/libhello-jni.so");
        assertThatNativeLib(lib).isStripped();
        lib = ZipHelper.extractFile(apk, "lib/x86/libhello-jni.so");
        assertThatNativeLib(lib).isStripped();
    }

    @Test
    public void assembleDebug() {
        project.execute("assembleDebug")

        // Verify .so are built for all platform.
        File apk = project.getApk("debug")
        assertThatZip(apk).contains("lib/x86/libhello-jni.so")
        assertThatZip(apk).contains("lib/mips/libhello-jni.so")
        assertThatZip(apk).contains("lib/armeabi/libhello-jni.so")
        assertThatZip(apk).contains("lib/armeabi-v7a/libhello-jni.so")

        // 64-bits binaries will not be produced if platform version 19 is used.
        assertThatZip(apk).doesNotContain("lib/x86_64/libhello-jni.so")
        assertThatZip(apk).doesNotContain("lib/arm64-v8a/libhello-jni.so")
        assertThatZip(apk).doesNotContain("lib/mips64/libhello-jni.so")

        File lib = ZipHelper.extractFile(apk, "lib/armeabi-v7a/libhello-jni.so");
        assertThatNativeLib(lib).isStripped();
        lib = ZipHelper.extractFile(apk, "lib/x86/libhello-jni.so");
        assertThatNativeLib(lib).isStripped();
    }

    @Test
    @Category(DeviceTests.class)
    public void connnectedAndroidTest() {
        project.executeConnectedCheck();
    }
}
