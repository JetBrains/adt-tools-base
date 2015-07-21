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
import com.android.build.gradle.integration.common.fixture.app.AndroidTestApp
import com.android.build.gradle.integration.common.fixture.app.EmptyAndroidTestApp
import com.android.build.gradle.integration.common.fixture.app.TestSourceFile
import groovy.transform.CompileStatic
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat

/**
 * Basic tests for NdkStandalone plugin.
 */
@CompileStatic
class NdkStandaloneSoTest {
    static AndroidTestApp nativeLib = new EmptyAndroidTestApp();
    static {
        nativeLib.addFile(new TestSourceFile("src/main/jni/", "hello.c", """
char* hello() {
    return "hello world!";
}
"""))

    }

    @ClassRule
    static public GradleTestProject project = GradleTestProject.builder()
            .fromTestApp(nativeLib)
            .forExpermimentalPlugin(true)
            .create()


    @BeforeClass
    static void setUp() {
        project.buildFile << """
apply plugin: "com.android.model.native"

model {
    android {
        compileSdkVersion = $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
    }
    android.ndk {
        moduleName = "hello-world"
    }
}
"""
        project.execute("clean", "assembleDebug");
    }

    @AfterClass
    static void cleanUp() {
        project = null
        nativeLib = null
    }

    @Test
    void "check file is compiled"() {
        assertThat(project.file("build/outputs/native/debug/lib/x86/libhello-world.so")).exists();
        assertThat(project.file("build/outputs/native/debug/lib/x86/gdbserver")).exists();
        assertThat(project.file("build/outputs/native/debug/lib/x86/gdb.setup")).exists();
    }
}
