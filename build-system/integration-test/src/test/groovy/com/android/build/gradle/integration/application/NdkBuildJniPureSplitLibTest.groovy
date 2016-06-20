/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle.integration.application
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp
import com.android.build.gradle.integration.common.utils.AssumeUtil
import groovy.transform.CompileStatic
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk
/**
 * Assemble tests for pure splits under ndk-build.
 */
@CompileStatic
class NdkBuildJniPureSplitLibTest {
    @ClassRule
    static public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("ndkJniPureSplitLib")
            .addFile(HelloWorldJniApp.androidMkC("lib/src/main/jni"))
            .create()

    @BeforeClass
    static void setUp() {
        new File(project.getTestDir(), "lib/src/main/jni")
            .renameTo(new File(project.getTestDir(), "lib/src/main/cxx"));
        AssumeUtil.assumeBuildToolsAtLeast(21)
        GradleTestProject lib = project.getSubproject("lib")
        lib.buildFile <<
"""apply plugin: 'com.android.library'
android {
    compileSdkVersion rootProject.latestCompileSdk
    buildToolsVersion = rootProject.buildToolsVersion

    externalNativeBuild {
        ndkBuild {
            path file("src/main/cxx/Android.mk")
        }
    }
}
"""
        project.execute("clean", ":app:assembleDebug")
    }

    @AfterClass
    static void cleanUp() {
        project = null
    }

    @Test
    void "check version code"() {
        GradleTestProject app = project.getSubproject("app")
        assertThatApk(app.getApk("free", "debug_armeabi-v7a")).hasVersionCode(123)
        assertThatApk(app.getApk("free", "debug_mips")).hasVersionCode(123)
        assertThatApk(app.getApk("free", "debug_x86")).hasVersionCode(123)
        assertThatApk(app.getApk("paid", "debug_armeabi-v7a")).hasVersionCode(123)
        assertThatApk(app.getApk("paid", "debug_mips")).hasVersionCode(123)
        assertThatApk(app.getApk("paid", "debug_x86")).hasVersionCode(123)
    }

    @Test
    void "check so"() {
        GradleTestProject app = project.getSubproject("app")
        assertThatApk(app.getApk("free", "debug_armeabi-v7a")).contains("lib/armeabi-v7a/libhello-jni.so");
        assertThatApk(app.getApk("paid", "debug_mips")).contains("lib/mips/libhello-jni.so");
    }
}
