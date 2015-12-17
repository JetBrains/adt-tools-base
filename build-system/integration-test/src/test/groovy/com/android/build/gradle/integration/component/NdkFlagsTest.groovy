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

import com.android.build.gradle.integration.common.category.DeviceTests
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.AndroidTestApp
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp
import com.android.build.gradle.integration.common.fixture.app.TestSourceFile
import groovy.transform.CompileStatic
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test
import org.junit.experimental.categories.Category

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatZip

/**
 * Tests C/C++/ld flags in an NDK project.
 */
@CompileStatic
class NdkFlagsTest {

    static AndroidTestApp cApp = new HelloWorldJniApp()
    static {
        TestSourceFile orig = cApp.getFile("hello-jni.c")
        cApp.removeFile(orig)
        cApp.addFile(new TestSourceFile(orig.path, orig.name,
                """
#include <string.h>
#include <jni.h>

// This is a trivial JNI example where we use a native method
// to return a new VM String.
jstring
Java_com_example_hellojni_HelloJni_stringFromJNI(JNIEnv* env, jobject thiz)
{
    return (*env)->NewStringUTF(env, HELLO_WORLD EXCLAMATION_MARK);
}
"""
        ))
    }


    @ClassRule
    public static GradleTestProject cProject = GradleTestProject.builder()
            .withName("c_project")
            .fromTestApp(cApp)
            .forExperimentalPlugin(true)
            .create();

    static AndroidTestApp cppApp = new HelloWorldJniApp(useCppSource: true)
    static {
        TestSourceFile orig = cppApp.getFile("hello-jni.cpp")
        cppApp.removeFile(orig)
        cppApp.addFile(new TestSourceFile(orig.path, orig.name,
                """
#include <string.h>
#include <jni.h>

// This is a trivial JNI example where we use a native method
// to return a new VM String.
extern "C"
jstring
Java_com_example_hellojni_HelloJni_stringFromJNI(JNIEnv* env, jobject thiz)
{
    // HELLO_WORLD and EXCLAMATION_MARK must be defined as follows during compilation.
    // #define HELLO_WORLD "hello world"
    // #define EXCLAMATION_MARK "!"
    return env->NewStringUTF(HELLO_WORLD EXCLAMATION_MARK);
}
"""
        ))

    }

    @ClassRule
    public static GradleTestProject cppProject = GradleTestProject.builder()
            .withName("cpp_project")
            .fromTestApp(cppApp)
            .forExperimentalPlugin(true)
            .create();

    static AndroidTestApp ldApp = new HelloWorldJniApp()
    static {
        ldApp.addFile(new TestSourceFile("src/main/jni", "log.c",
                """
#include <android/log.h>

// Simple function that uses function from an external library.  Should fail unless -llog is set
// when linking.
void log() {
    __android_log_print(ANDROID_LOG_INFO, "hello-world", "Hello World!");
}
"""))
    }

    @ClassRule
    public static GradleTestProject ldProject = GradleTestProject.builder()
            .withName("ld_project")
            .fromTestApp(ldApp)
            .forExperimentalPlugin(true)
            .create();


    @BeforeClass
    public static void setUp() {
        cProject.getBuildFile() << """
apply plugin: 'com.android.model.application'

model {
    android {
        compileSdkVersion = $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
        buildToolsVersion = "$GradleTestProject.DEFAULT_BUILD_TOOL_VERSION"
    }
    android.ndk {
        moduleName = "hello-jni"
        CFlags.addAll(['-DHELLO_WORLD="hello world"', '-DEXCLAMATION_MARK="!"'])
        CFlags.add(' -DFLAG_WITH_LEADING_SPACE')
    }
}
"""

        cppProject.getBuildFile() << """
apply plugin: 'com.android.model.application'

model {
    android {
        compileSdkVersion = $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
        buildToolsVersion = "$GradleTestProject.DEFAULT_BUILD_TOOL_VERSION"
    }
    android.ndk {
        moduleName = "hello-jni"
        cppFlags.addAll(['-DHELLO_WORLD="hello world"', '-DEXCLAMATION_MARK="!"'])
    }
}
"""

        ldProject.getBuildFile() << """
apply plugin: 'com.android.model.application'

model {
    android {
        compileSdkVersion = $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
        buildToolsVersion = "$GradleTestProject.DEFAULT_BUILD_TOOL_VERSION"
    }
    android.ndk {
        moduleName = "hello-jni"
        ldFlags.addAll("-llog")
    }
}
"""
    }

    @AfterClass
    static void cleanUp() {
        cProject = null
    }

    @Test
    public void "assemble C project"() {
        cProject.execute("assembleDebug")
        assertThatZip(cProject.getApk("debug")).contains("lib/x86/libhello-jni.so")
    }

    @Test
    public void "assemble C++ project"() {
        cppProject.execute("assembleDebug")
        assertThatZip(cppProject.getApk("debug")).contains("lib/x86/libhello-jni.so")
    }

    @Test
    public void "assemble ld project"() {
        ldProject.execute("assembleDebug")
        assertThatZip(ldProject.getApk("debug")).contains("lib/x86/libhello-jni.so")
    }

    @Test
    @Category(DeviceTests.class)
    public void "connectedCheck C project"() {
        cProject.executeConnectedCheck();
    }

    @Test
    @Category(DeviceTests.class)
    public void "connectedCheck C++ project"() {
        cppProject.executeConnectedCheck();
    }
}
