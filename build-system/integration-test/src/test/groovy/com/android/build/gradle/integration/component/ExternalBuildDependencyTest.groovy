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

package com.android.build.gradle.integration.component

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.AndroidTestApp
import com.android.build.gradle.integration.common.fixture.app.EmptyAndroidTestApp
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.fixture.app.TestSourceFile
import org.junit.AfterClass
import org.junit.ClassRule
import org.junit.Test

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatZip

/**
 * Tests for native dependencies
 */
class ExternalBuildDependencyTest {
    static MultiModuleTestProject base = new MultiModuleTestProject(
            app: new HelloWorldJniApp(),
            lib: new EmptyAndroidTestApp())

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
                    project ":lib"
                }
            }
        }
    }
}
"""))

        AndroidTestApp lib = (AndroidTestApp) base.getSubproject("lib")
        lib.addFile(new TestSourceFile("src/main/jni", "hello-jni.c",
                """
#include <string.h>
#include <jni.h>

jstring
Java_com_example_hellojni_HelloJni_stringFromJNI(JNIEnv* env, jobject thiz)
{
    return (*env)->NewStringUTF(env, "hello world!");
}
"""));

        lib.addFile(new TestSourceFile("", "Android.mk",
                """
LOCAL_PATH := \$(call my-dir)
include \$(CLEAR_VARS)

LOCAL_MODULE := hello-jni

LOCAL_SRC_FILES := src/main/jni/hello-jni.c

include \$(BUILD_SHARED_LIBRARY)
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
        // File a clang compiler.  Doesn't matter which one.
        File compiler = new File(project.getNdkDir(), "ndk-build");
        GradleTestProject lib = project.getSubproject("lib")
        lib.buildFile << """
apply plugin: "com.android.model.external"

model {
    nativeBuild.libraries {
        create("foo") {
            executable = "${compiler.getPath()}"
            args.addAll([
                "APP_BUILD_SCRIPT=Android.mk",
                "NDK_PROJECT_PATH=null",
                "NDK_OUT=build/intermediate",
                "NDK_LIBS_OUT=build/output",
                "APP_ABI=x86"])
            toolchain = "gcc"
            abi = "x86"
            output = file("build/output/x86/libhello-jni.so")
            files.with {
                create() {
                    src = file("src/main/jni/hello-jni.c")
                }
            }

        }
    }
    nativeBuild.toolchains {
        create("gcc") {
            CCompilerExecutable = file("${compiler.getPath()}")

        }
    }
}
"""
        project.execute("clean", ":app:assembleDebug");

        File apk = project.getSubproject("app").getApk("debug")
        assertThatZip(apk).contains("lib/x86/libhello-jni.so");
    }
}
