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
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp
import com.android.build.gradle.integration.common.fixture.app.TestSourceFile
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatZip

/**
 * Integration tests for different configuration of source sets.
 */
class ComponentSourceSetTest {

    public static AndroidTestApp app = new HelloWorldJniApp()

    static {
        // Remove the main hello-jni.c and place it in different directories for different flavors.
        // Note that *not* all variant can be built.
        TestSourceFile cSource = app.getFile("hello-jni.c");
        app.removeFile(cSource);
        app.addFile(
                new TestSourceFile("src/release/c", cSource.name, cSource.content))
        app.addFile(
                new TestSourceFile("src/flavor1/c/hello-jni.c", cSource.name, cSource.content))
        app.addFile(new TestSourceFile("src/flavor2Debug/c/hello-jni.c", cSource.name,
                cSource.content))
    }

    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder()
            .fromTestApp(app)
            .forExpermimentalPlugin(true)
            .create();

    @BeforeClass
    public static void setUp() {

        project.buildFile << """
apply plugin: "com.android.model.application"

model {
    android.config {
        compileSdkVersion $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
        buildToolsVersion "$GradleTestProject.DEFAULT_BUILD_TOOL_VERSION"
    }
    android.ndk {
        moduleName "hello-jni"
    }
    android.productFlavors {
        flavor1
        flavor2
        flavor3
    }
    android.sources {
        flavor3 {
            c {
                source {
                    srcDir 'src/flavor1/c'
                }
            }
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
    void defaultBuildTypeSourceDirectory() {
        project.execute("assembleFlavor2Release");
        def apk = project.getApk("flavor2", "release", "unsigned")
        assertThatZip(apk).contains("lib/x86/libhello-jni.so");
    }

    @Test
    void defaultProductFlavorSourceDirectory() {
        project.execute("assembleFlavor1Debug");
        def apk = project.getApk("flavor1", "debug")
        assertThatZip(apk).contains("lib/x86/libhello-jni.so");
    }

    @Test
    void defaultVariantSourceDirectory() {
        project.execute("assembleFlavor2Debug");
        def apk = project.getApk("flavor2", "debug")
        assertThatZip(apk).contains("lib/x86/libhello-jni.so");
    }

    @Test
    void nonDefaultSourceDirectory() {
        project.execute("assembleFlavor3Debug");
        def apk = project.getApk("flavor3", "debug")
        assertThatZip(apk).contains("lib/x86/libhello-jni.so");
    }
}
