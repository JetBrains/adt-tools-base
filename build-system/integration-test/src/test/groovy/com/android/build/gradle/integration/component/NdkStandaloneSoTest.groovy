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
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.fixture.app.TestSourceFile
import groovy.transform.CompileStatic
import org.junit.AfterClass
import org.junit.ClassRule
import org.junit.Test

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatZip

/**
 * Tests for standalone NDK plugin and native dependencies.
 *
 * Test project consists of an app project that depends on an NDK project that depends on another
 * NDK project.
 */
@CompileStatic
class NdkStandaloneSoTest {
    static MultiModuleTestProject base = new MultiModuleTestProject(
            app: new HelloWorldJniApp(),
            lib1: new EmptyAndroidTestApp(),
            lib2: new EmptyAndroidTestApp())

    static {
        AndroidTestApp app = (HelloWorldJniApp) base.getSubproject("app")
        app.removeFile(app.getFile("hello-jni.c"))
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
                    project ":lib1" buildType "debug"
                    library file("prebuilt.so") abi "x86"
                }
            }
        }
    }
}
"""))
        // An empty .so just to check if it can be packaged
        app.addFile(new TestSourceFile("", "prebuilt.so", ""));

        AndroidTestApp lib1 = (AndroidTestApp) base.getSubproject("lib1")
        lib1.addFile(new TestSourceFile("src/main/jni", "hello-jni.c",
                """
#include <string.h>
#include <jni.h>

jstring
Java_com_example_hellojni_HelloJni_stringFromJNI(JNIEnv* env, jobject thiz)
{
    return (*env)->NewStringUTF(env, getString());
}
"""));

        lib1.addFile(new TestSourceFile("", "build.gradle", """
apply plugin: "com.android.model.native"

model {
    android {
        compileSdkVersion = $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
    }
    android.ndk {
        moduleName = "hello-jni"
    }
    android.sources {
        main {
            jni {
                dependencies {
                    project ":lib2"
                }
            }
        }
    }
}
"""));

        AndroidTestApp lib2 = (AndroidTestApp) base.getSubproject("lib2")
        lib2.addFile(new TestSourceFile("src/main/headers/", "hello.h", """
#ifndef HELLO_H
#define HELLO_H

char* getString();

#endif
"""))
        lib2.addFile(new TestSourceFile("src/main/jni/", "hello.c", """
char* getString() {
    return "hello world!";
}
"""))
        lib2.addFile(new TestSourceFile("", "build.gradle", """
apply plugin: "com.android.model.native"

model {
    android {
        compileSdkVersion = $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
    }
    android.ndk {
        moduleName = "hello-jni"
    }
    android.sources {
        main {
            jni {
                exportedHeaders {
                    srcDir "src/main/headers"
                }
            }
        }
    }
}
"""))
    }

    @ClassRule
    static public GradleTestProject project = GradleTestProject.builder()
            .fromTestApp(base)
            .forExperimentalPlugin(true)
            .create()

    @AfterClass
    static void cleanUp() {
        project = null
        base = null
    }

    @Test
    void "check standalone lib properly creates library"() {
        project.execute("clean", ":lib1:assembleDebug");

        GradleTestProject lib = project.getSubproject("lib1")
        assertThat(lib.file("build/outputs/native/debug/lib/x86/libhello-jni.so")).exists();
        assertThat(lib.file("build/outputs/native/debug/lib/x86/gdbserver")).exists();
        assertThat(lib.file("build/outputs/native/debug/lib/x86/gdb.setup")).exists();
    }

    @Test
    void "check app contains compiled .so"() {
        project.execute("clean", ":app:assembleRelease");

        GradleTestProject lib1 = project.getSubproject("lib1")
        assertThat(lib1.file("build/intermediates/binaries/debug/obj/x86/libhello-jni.so")).exists();

        // Check that release lib is not compiled.
        assertThat(lib1.file("build/intermediates/binaries/release/obj/x86/libhello-jni.so")).doesNotExist();

        File apk = project.getSubproject("app").getApk("release", "unsigned")
        assertThatZip(apk).contains("lib/x86/libhello-jni.so");
        assertThatZip(apk).contains("lib/x86/prebuilt.so");
    }
}
