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

package com.android.build.gradle

import com.android.build.gradle.internal.test.fixture.GradleProjectTestRule
import com.android.build.gradle.internal.test.fixture.app.HelloWorldJniApp
import org.gradle.tooling.BuildException
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

import java.util.zip.ZipFile

import static org.junit.Assert.assertNotNull
import static org.junit.Assert.fail

/**
 * Integration test for STL containers.
 *
 * This unit test is parameterized and will be executed for various values of STL.
 */
@RunWith(Parameterized.class)
public class NdkStlIntegTest {

    @Parameterized.Parameters
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

    NdkStlIntegTest(String stl) {
        this.stl = stl;
    }

    @Rule
    public GradleProjectTestRule fixture = new GradleProjectTestRule();

    @Before
    public void setup() {
        new HelloWorldJniApp().writeSources(fixture.getSourceDir())
        fixture.getBuildFile() << """
apply plugin: 'com.android.model.application'

model {
    android {
        compileSdkVersion $GradleProjectTestRule.DEFAULT_COMPILE_SDK_VERSION
        buildToolsVersion "$GradleProjectTestRule.DEFAULT_BUILD_TOOL_VERSION"
    }
    android.ndk {
        moduleName "hello-jni"
    }
}
"""
    }

    @Test
    public void buildAppWithStl() {
        fixture.getBuildFile() << """
model {
    android.ndk {
        stl "$stl"
    }
}
"""
        if (!stl.equals("invalid")) {
            fixture.execute("assembleDebug");

            ZipFile apk = new ZipFile(
                    fixture.file("build/outputs/apk/${fixture.testDir.name}-debug.apk"));
            assertNotNull(apk.getEntry("lib/x86/libhello-jni.so"));
            assertNotNull(apk.getEntry("lib/mips/libhello-jni.so"));
            assertNotNull(apk.getEntry("lib/armeabi/libhello-jni.so"));
            assertNotNull(apk.getEntry("lib/armeabi-v7a/libhello-jni.so"));
        } else {
            // Fail if it's invalid.
            try {
                fixture.execute("assembleDebug");
                fail();
            } catch (BuildException ignored) {
            }
        }
    }
}

