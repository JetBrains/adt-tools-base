/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle.integration.nativebuild

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.TestSourceFile
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.builder.model.AndroidProject
import com.android.builder.model.NativeAndroidProject
import com.android.builder.model.NativeArtifact
import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.Multimap
import groovy.transform.CompileStatic
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk
/**
 * Assemble tests for ndkJniLib.
 */
@CompileStatic
class NdkBuildJniLibTest {

    private static final TestSourceFile androidMk = new TestSourceFile(
            "lib/src/main/jni", "Android.mk",
            "LOCAL_PATH := \$(call my-dir)\n"
                    +"\n"
                    +"include \$(CLEAR_VARS)\n"
                    +"\n"
                    +"LOCAL_MODULE    := hello-jni\n"
                    +"LOCAL_SRC_FILES := hello-jni.c\n"
                    +"\n"
                    +"include \$(BUILD_SHARED_LIBRARY)");
    @ClassRule
    static public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("ndkJniLib")
            .addFile(androidMk)
            .create();

    @BeforeClass
    static void setUp() {
        new File(project.getTestDir(), "lib/src/main/jni")
                .renameTo(new File(project.getTestDir(), "lib/src/main/cxx"));
        GradleTestProject lib = project.getSubproject("lib")
        lib.buildFile <<
"""
apply plugin: 'com.android.library'
android {
    compileSdkVersion rootProject.latestCompileSdk
    buildToolsVersion = rootProject.buildToolsVersion

    defaultConfig {
        externalNativeBuild {
          ndkBuild {
            path "src/main/cxx/Android.mk"
            cFlags = "-DTEST_C_FLAG"
            cppFlags = "-DTEST_CPP_FLAG"
          }
        }
    }
}
"""
        project.execute("clean", "assembleDebug",
                "generateJsonModelDebug", "generateJsonModelRelease")
    }

    @AfterClass
    static void cleanUp() {
        project = null
    }

    @Test
    void "check version code"() {
        GradleTestProject app = project.getSubproject("app")
        assertThatApk(app.getApk("gingerbread", "universal", "debug")).hasVersionCode(1000123)
        assertThatApk(app.getApk("gingerbread", "armeabi-v7a", "debug")).hasVersionCode(1100123)
        assertThatApk(app.getApk("gingerbread", "mips", "debug")).hasVersionCode(1200123)
        assertThatApk(app.getApk("gingerbread", "x86", "debug")).hasVersionCode(1300123)
        assertThatApk(app.getApk("icecreamSandwich", "universal", "debug")).hasVersionCode(2000123)
        assertThatApk(app.getApk("icecreamSandwich", "armeabi-v7a", "debug")).hasVersionCode(2100123)
        assertThatApk(app.getApk("icecreamSandwich", "mips", "debug")).hasVersionCode(2200123)
        assertThatApk(app.getApk("icecreamSandwich", "x86", "debug")).hasVersionCode(2300123)
    }

    @Test
    void "check apk content"() {
        GradleTestProject app = project.getSubproject("app")

        assertThatApk(app.getApk("gingerbread", "universal", "debug")).contains("lib/armeabi-v7a/libhello-jni.so");
        assertThatApk(app.getApk("icecreamSandwich", "armeabi-v7a", "debug")).contains("lib/armeabi-v7a/libhello-jni.so");
        assertThatApk(app.getApk("icecreamSandwich", "x86", "debug")).doesNotContain("lib/armeabi-v7a/libhello-jni.so");
    }

    @Test
    public void checkModel() {
        // Make sure we can successfully get AndroidProject
        project.model()
                .getMulti(AndroidProject.class).get(":app");
        NativeAndroidProject model = project.model()
                .getMulti(NativeAndroidProject.class).get(":lib");
        assertThat(model).isNotNull();
        assertThat(model.buildFiles).hasSize(1);
        assertThat(model.name).isEqualTo("lib");
        assertThat(model.artifacts).hasSize(14);
        assertThat(model.fileExtensions).hasSize(1);

        for (File file : model.buildFiles) {
            assertThat(file).isFile();
        }

        Multimap<String, NativeArtifact> groupToArtifacts = ArrayListMultimap.create();

        for (NativeArtifact artifact : model.artifacts) {
            List<String> pathElements = TestFileUtils.splitPath(artifact.getOutputFile());
            assertThat(pathElements).contains("obj");
            groupToArtifacts.put(artifact.getGroupName(), artifact);
        }

        assertThat(groupToArtifacts.keySet()).containsExactly("debug", "release");
        assertThat(groupToArtifacts.get("debug")).hasSize(groupToArtifacts.get("release").size());
    }
}
