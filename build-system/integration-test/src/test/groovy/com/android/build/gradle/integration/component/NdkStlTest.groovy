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
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp
import com.android.build.gradle.internal.ndk.NdkHandler
import com.android.repository.Revision
import com.android.utils.FileUtils
import groovy.transform.CompileStatic
import org.gradle.tooling.BuildException
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatZip
import static com.google.common.truth.TruthJUnit.assume
import static org.junit.Assert.fail

/**
 * Integration test for STL containers.
 *
 * This unit test is parameterized and will be executed for various values of STL.
 */
@RunWith(Parameterized.class)
@CompileStatic
public class NdkStlTest {

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        return [
                ["system"].toArray(),
                ["stlport_static"].toArray(),
                ["stlport_shared"].toArray(),
                ["gnustl_static"].toArray(),
                ["gnustl_shared"].toArray(),
                ["gabi++_static"].toArray(),
                ["gabi++_shared"].toArray(),
                ["c++_static"].toArray(),
                ["c++_shared"].toArray(),
                ["invalid"].toArray(),
        ];
    }

    private String stl;

    NdkStlTest(String stl) {
        this.stl = stl;
    }

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestApp(HelloWorldJniApp.builder().useCppSource().build())
            .useExperimentalGradleVersion(true)
            .create()

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
            stl "$stl"
        }
    }
}
"""
    }

    @Test
    public void buildAppWithStl() {
        // ndk r11 does noes support gabi++
        Revision ndkRevision = NdkHandler.findRevision(project.getNdkDir())
        boolean notGabiSupported =
                stl.startsWith("gabi++") && ndkRevision != null && ndkRevision.major >= 11

        if (stl.equals("invalid") || notGabiSupported) {
            // Fail if it's invalid.
            try {
                project.execute("assembleDebug");
                fail();
            } catch (BuildException ignored) {
            }
        } else {
            project.execute("assembleDebug");

            File apk = project.getApk("debug");
            assertThatZip(apk).contains("lib/x86/libhello-jni.so");
            assertThatZip(apk).contains("lib/mips/libhello-jni.so");
            assertThatZip(apk).contains("lib/armeabi/libhello-jni.so");
            assertThatZip(apk).contains("lib/armeabi-v7a/libhello-jni.so");

            if (stl.endsWith("shared")) {
                assertThatZip(apk).contains("lib/x86/lib" + stl + ".so");
                assertThatZip(apk).contains("lib/mips/lib" + stl + ".so");
                assertThatZip(apk).contains("lib/armeabi/lib" + stl + ".so");
                assertThatZip(apk).contains("lib/armeabi-v7a/lib" + stl + ".so");
            }
        }
    }

    @Test
    public void "check with code that uses the STL"() {
        assume().that(stl).isNotEqualTo("invalid")
        assume().that(stl).isNotEqualTo("system")
        assume().that(stl).isNotEqualTo("gabi++_shared")
        assume().that(stl).isNotEqualTo("gabi++_static")

        File src = FileUtils.find(project.testDir, "hello-jni.cpp").get()
        src.delete()
        src << """
#include <jni.h>
#include <string>

extern "C"
jstring
Java_com_example_hellojni_HelloJni_stringFromJNI(JNIEnv* env, jobject thiz) {
    std::string greeting = "hello world!";
    return env->NewStringUTF(greeting.c_str());
}

"""
        project.execute("assembleDebug");
        File apk = project.getApk("debug");
        assertThatZip(apk).contains("lib/x86/libhello-jni.so");
        assertThatZip(apk).contains("lib/mips/libhello-jni.so");
        assertThatZip(apk).contains("lib/armeabi/libhello-jni.so");
        assertThatZip(apk).contains("lib/armeabi-v7a/libhello-jni.so");

        if (stl.endsWith("shared")) {
            assertThatZip(apk).contains("lib/x86/lib" + stl + ".so");
            assertThatZip(apk).contains("lib/mips/lib" + stl + ".so");
            assertThatZip(apk).contains("lib/armeabi/lib" + stl + ".so");
            assertThatZip(apk).contains("lib/armeabi-v7a/lib" + stl + ".so");
        }

    }
}

