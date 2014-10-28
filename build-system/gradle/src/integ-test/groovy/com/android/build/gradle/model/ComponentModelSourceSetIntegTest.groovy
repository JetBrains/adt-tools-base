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

package com.android.build.gradle.model

import com.android.build.gradle.internal.test.fixture.GradleProjectTestRule
import com.android.build.gradle.internal.test.fixture.app.AndroidTestApp
import com.android.build.gradle.internal.test.fixture.app.HelloWorldJniApp
import com.android.build.gradle.internal.test.fixture.app.TestSourceFile
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test

import java.util.zip.ZipFile

import static org.junit.Assert.assertNotNull

/**
 * Integration tests for different configuration of source sets.
 */
class ComponentModelSourceSetIntegTest {
    @ClassRule
    public static GradleProjectTestRule fixture = new GradleProjectTestRule();

    @BeforeClass
    public static void setup() {
        AndroidTestApp app = new HelloWorldJniApp()

        // Remove the main hello-jni.c and place it in different directories for different flavors.
        // Note that *not* all variant can be built.
        TestSourceFile cSource = app.getFile("hello-jni.c");
        app.removeFile(cSource);
        app.addFile(
                new TestSourceFile("release/jni", cSource.name, cSource.content))
        app.addFile(
                new TestSourceFile("flavor1/jni/hello-jni.c", cSource.name, cSource.content))
        app.addFile(new TestSourceFile("flavor2Debug/jni/hello-jni.c", cSource.name,
                cSource.content))
        app.writeSources(fixture.getSourceDir())

        fixture.buildFile << """
apply plugin: "com.android.model.application"

model {
    android {
        compileSdkVersion 19
        buildToolsVersion "19.1.0"
    }
    android.ndk {
        moduleName "hello-jni"
    }
    android.productFlavors {
        flavor1
        flavor2
        flavor3
    }
    sources {
        flavor3 {
            c {
                source {
                    srcDir 'src/flavor1/jni'
                }
            }
        }
    }
}
"""
    }

    @Test
    void defaultBuildTypeSourceDirectory() {
        fixture.execute("assembleFlavor2Release");
        ZipFile apk = new ZipFile(
                fixture.file("build/outputs/apk/${fixture.testDir.getName()}-flavor2-release-unsigned.apk"));
        assertNotNull(apk.getEntry("lib/x86/libhello-jni.so"));
    }

    @Test
    void defaultProductFlavorSourceDirectory() {
        fixture.execute("assembleFlavor1Debug");
        ZipFile apk = new ZipFile(
                fixture.file("build/outputs/apk/${fixture.testDir.getName()}-flavor1-debug.apk"));
        assertNotNull(apk.getEntry("lib/x86/libhello-jni.so"));
    }

    @Test
    void defaultVariantSourceDirectory() {
        fixture.execute("assembleFlavor2Debug");
        ZipFile apk = new ZipFile(
                fixture.file("build/outputs/apk/${fixture.testDir.getName()}-flavor2-debug.apk"));
        assertNotNull(apk.getEntry("lib/x86/libhello-jni.so"));
    }

    @Test
    void nonDefaultSourceDirectory() {
        fixture.execute("assembleFlavor3Debug");
        ZipFile apk = new ZipFile(
                fixture.file("build/outputs/apk/${fixture.testDir.getName()}-flavor3-debug.apk"));
        assertNotNull(apk.getEntry("lib/x86/libhello-jni.so"));
    }
}
