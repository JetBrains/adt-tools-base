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

import com.android.build.gradle.integration.common.fixture.GradleTestProject

/**
 * Tests for NdkComponentModelPlugin
 */
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp
import groovy.transform.CompileStatic
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat

/**
 * Basic integration test for ndk component plugin.
 */
@CompileStatic
class NdkComponentPluginTest {

    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder()
            .fromTestApp(new HelloWorldJniApp())
            .useExperimentalGradleVersion(true)
            .create()

    @BeforeClass
    public static void setUp() {
        project.getBuildFile() << """
import com.android.build.gradle.model.NdkComponentModelPlugin
apply plugin: NdkComponentModelPlugin

model {
    android {
        compileSdkVersion $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
        ndk {
            moduleName "hello-jni"
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
        project.execute("assemble");
        assertThat(project.file("build/intermediates/binaries/debug/obj/x86/libhello-jni.so")).exists()
        assertThat(project.file("build/intermediates/binaries/debug/lib/x86/libhello-jni.so")).exists()
        assertThat(project.file("build/intermediates/binaries/release/lib/x86/libhello-jni.so")).exists()
    }
}
