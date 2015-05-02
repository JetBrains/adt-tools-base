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

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp
import groovy.transform.CompileStatic
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatZip

/**
 * Test split DSL with API level < 21.
 */
@CompileStatic
class Pre21SplitTest {
    @ClassRule
    static public GradleTestProject project = GradleTestProject.builder()
            .fromTestApp(new HelloWorldJniApp())
            .create()

    @BeforeClass
    static public void setUp() {
        project.getBuildFile() << """
apply plugin: 'com.android.application'

android {
    compileSdkVersion $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
    buildToolsVersion "$GradleTestProject.DEFAULT_BUILD_TOOL_VERSION"

    generatePureSplits false

    defaultConfig {
        minSdkVersion 15
        ndk {
            moduleName "hello-jni"
        }
    }

    splits {
        abi {
            enable true
            reset()
            include 'x86', 'armeabi-v7a', 'mips'
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
    public void "check splits DSL works with with api level < 21"() {
        project.execute("assembleX86Debug")

        // Verify .so are built for all platform.
        File apk = project.getApk("x86", "debug")
        assertThatZip(apk).doesNotContain("lib/armeabi-v7a/libhello-jni.so")
        assertThatZip(apk).doesNotContain("lib/mips/libhello-jni.so")
        assertThatZip(apk).contains("lib/x86/libhello-jni.so")
    }
}
