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
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.fixture.app.TestSourceFile
import groovy.transform.CompileStatic
import org.junit.AfterClass
import org.junit.ClassRule
import org.junit.Test

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatZip

/**
 * Basic tests for NdkStandalone plugin.
 */
@CompileStatic
class NdkStandaloneSoTest {
    static MultiModuleTestProject base = new MultiModuleTestProject(
            app: new EmptyAndroidTestApp("com.example.app"), lib: new EmptyAndroidTestApp())

    static {
        AndroidTestApp app = (AndroidTestApp) base.getSubproject("app")
        app.addFile(new TestSourceFile("", "build.gradle", """
apply plugin: "com.android.model.application"

model {
    android {
        compileSdkVersion = $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
        buildToolsVersion = "$GradleTestProject.DEFAULT_BUILD_TOOL_VERSION"
    }
    android.sources {
        main {
            jniLibs {
                dependencies {
                    project ":lib" buildType "debug"
                }
            }
        }
    }
}
"""))

        AndroidTestApp lib = (AndroidTestApp) base.getSubproject("lib")
        lib.addFile(new TestSourceFile("src/main/jni/", "hello.c", """
char* hello() {
    return "hello world!";
}
"""))
        lib.addFile(new TestSourceFile("", "build.gradle", """
apply plugin: "com.android.model.native"

model {
    android {
        compileSdkVersion = $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
    }
    android.ndk {
        moduleName = "hello-jni"
    }
}
"""))
    }

    @ClassRule
    static public GradleTestProject project = GradleTestProject.builder()
            .fromTestApp(base)
            .forExpermimentalPlugin(true)
            .create()

    @AfterClass
    static void cleanUp() {
        project = null
        base = null
    }

    @Test
    void "check standalone lib properly creates library"() {
        project.execute("clean", ":lib:assembleDebug");

        GradleTestProject lib = project.getSubproject("lib")
        assertThat(lib.file("build/outputs/native/debug/lib/x86/libhello-jni.so")).exists();
        assertThat(lib.file("build/outputs/native/debug/lib/x86/gdbserver")).exists();
        assertThat(lib.file("build/outputs/native/debug/lib/x86/gdb.setup")).exists();
    }

    @Test
    void "check app contains compiled .so"() {
        project.execute("clean", ":app:assembleRelease");

        GradleTestProject lib = project.getSubproject("lib")
        assertThat(lib.file("build/intermediates/binaries/debug/lib/x86/libhello-jni.so")).exists();

        // Check that release lib is not compiled.
        assertThat(lib.file("build/intermediates/binaries/release/lib/x86/libhello-jni.so")).doesNotExist();

        File apk = project.getSubproject("app").getApk("release", "unsigned")
        assertThatZip(apk).contains("lib/x86/libhello-jni.so");
    }
}
