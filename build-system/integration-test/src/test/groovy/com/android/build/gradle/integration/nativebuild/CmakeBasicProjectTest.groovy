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
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.tasks.NativeBuildSystem
import com.android.builder.model.NativeAndroidProject
import com.android.builder.model.NativeArtifact
import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.Multimap
import groovy.transform.CompileStatic
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk
/**
 * Assemble tests for Cmake.
 */
@CompileStatic
@RunWith(Parameterized.class)
class CmakeBasicProjectTest {
    @Parameterized.Parameters(name = "model = {0}")
    public static Collection<Object[]> data() {
        return [
                [false].toArray(),
                [true].toArray()
        ];
    }

    private boolean isModel;

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestApp(HelloWorldJniApp.builder()
                .withNativeDir("cxx")
                .build())
            .addFile(HelloWorldJniApp.cmakeLists("."))
            .useExperimentalGradleVersion(isModel)
            .create();

    CmakeBasicProjectTest(boolean isModel) {
        this.isModel = isModel;
    }

    @Before
    void setUp() {
        String plugin = isModel ? "apply plugin: 'com.android.model.application'"
                : "apply plugin: 'com.android.application'";
        String modelBefore = isModel ? "model { " : ""
        String modelAfter = isModel ? " }" : ""
        project.buildFile <<
                """
$plugin
$modelBefore
    android {
        compileSdkVersion $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
        buildToolsVersion "$GradleTestProject.DEFAULT_BUILD_TOOL_VERSION"
        defaultConfig {
          cmake {
            cFlags.addAll("-DTEST_C_FLAG", "-DTEST_C_FLAG_2")
            cppFlags.addAll("-DTEST_CPP_FLAG")
            abiFilters.addAll("armeabi-v7a", "armeabi", "armeabi-v7a with NEON",
                "armeabi-v7a with VFPV3", "armeabi-v6 with VFP")
            targets.addAll("hello-jni")
          }
        }
        externalNativeBuild {
          cmake {
            path "CMakeLists.txt"
          }
        }
    }
$modelAfter
""";
        if (!isModel) {
            project.buildFile << """
android {
    applicationVariants.all { variant ->
        assert !variant.getExternalNativeBuildTasks().isEmpty()
        for (def task : variant.getExternalNativeBuildTasks()) {
            assert task.getName() == "externalNativeBuild" + variant.getName().capitalize()
        }
    }
}
"""
        }
        project.execute("clean", "assembleDebug")
    }

    @Test
    void "check apk content"() {
        assertThatApk(project.getApk("debug")).hasVersionCode(1)
        assertThatApk(project.getApk("debug")).contains("lib/armeabi-v7a/libhello-jni.so");
        assertThatApk(project.getApk("debug")).contains("lib/armeabi/libhello-jni.so");
        assertThatApk(project.getApk("debug")).contains("lib/armeabi-v7a with NEON/libhello-jni.so");
        assertThatApk(project.getApk("debug")).contains("lib/armeabi-v7a with VFPV3/libhello-jni.so");
        assertThatApk(project.getApk("debug")).contains("lib/armeabi-v6 with VFP/libhello-jni.so");
    }

    @Test
    public void checkModel() {
        project.model().getSingle(); // Make sure we can successfully get AndroidProject
        NativeAndroidProject model = project.model().getSingle(NativeAndroidProject.class);
        assertThat(model).isNotNull();
        assertThat(model.getBuildSystems()).containsExactly(NativeBuildSystem.CMAKE.getName());
        assertThat(model.buildFiles).hasSize(1);
        assertThat(model.name).isEqualTo("project");
        int abiCount = 5;
        assertThat(model.artifacts).hasSize(abiCount * /* variantCount */ 2);
        assertThat(model.fileExtensions).hasSize(1);

        for (File file : model.buildFiles) {
            assertThat(file).isFile();
        }

        Multimap<String, NativeArtifact> groupToArtifacts = ArrayListMultimap.create();

        for (NativeArtifact artifact : model.artifacts) {
            List<String> pathElements = TestFileUtils.splitPath(artifact.getOutputFile());
            assertThat(pathElements).contains("obj");
            assertThat(pathElements).doesNotContain("lib");
            groupToArtifacts.put(artifact.getGroupName(), artifact);
        }

        assertThat(model).hasArtifactGroupsNamed("debug", "release");
        assertThat(model).hasArtifactGroupsOfSize(abiCount);
    }
}
