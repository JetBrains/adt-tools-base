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

package com.android.build.gradle.integration.nativebuild
import com.android.build.gradle.integration.common.category.DeviceTests
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp
import groovy.transform.CompileStatic
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test
import org.junit.experimental.categories.Category

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk
/**
 * Assemble tests for CMake.
 */
@CompileStatic
class CmakeJniLibTest {

    @ClassRule
    static public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("ndkJniLib")
            .addFile(HelloWorldJniApp.cmakeLists("lib"))
            .create()

    @BeforeClass
    static void setUp() {
        new File(project.getTestDir(), "src/main/jni")
            .renameTo(new File(project.getTestDir(), "src/main/cxx"));
        GradleTestProject lib = project.getSubproject("lib")
        lib.buildFile <<
"""
apply plugin: 'com.android.library'
android {
    compileSdkVersion rootProject.latestCompileSdk
    buildToolsVersion = rootProject.buildToolsVersion

    defaultConfig {
        externalNativeBuild {
          cmake {
            path file(".")
          }
        }
    }
}
"""
        project.execute("clean", "assembleDebug")
    }

    @AfterClass
    static void cleanUp() {
        project = null
    }

    @Test
    void "check apk content"() {
        GradleTestProject app = project.getSubproject("app");
        File gingerbreadUniversal = app.getApk("gingerbread", "universal", "debug");
        if (!gingerbreadUniversal.exists()) {
            throw new RuntimeException(String.format("Could not find %s", gingerbreadUniversal.canonicalPath));
        }
        assertThatApk(app.getApk("gingerbread", "universal", "debug")).contains("lib/armeabi-v7a/libhello-jni.so");
        assertThatApk(app.getApk("icecreamSandwich", "armeabi-v7a", "debug")).contains("lib/armeabi-v7a/libhello-jni.so");
        assertThatApk(app.getApk("icecreamSandwich", "x86", "debug")).doesNotContain("lib/armeabi-v7a/libhello-jni.so");
    }

    @Test
    @Category(DeviceTests.class)
    void connectedCheck() {
        project.executeConnectedCheck()
    }
}
