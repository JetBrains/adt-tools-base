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
import com.android.build.gradle.integration.common.utils.ZipHelper
import com.android.build.gradle.tasks.NativeBuildSystem
import com.android.builder.model.AndroidProject
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
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatNativeLib
/**
 * Assemble tests for ndk-build.
 */
@CompileStatic
@RunWith(Parameterized.class)
class NdkBuildBasicProjectTest {
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
                .build())
            .addFile(HelloWorldJniApp.androidMkC("src/main/jni"))
            .useExperimentalGradleVersion(isModel)
            .create();

    NdkBuildBasicProjectTest(boolean isModel) {
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
        externalNativeBuild {
          ndkBuild {
            path "src/main/jni/Android.mk"
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
    }

    @Test
    void "check apk content"() {
        project.execute("clean", "assembleDebug")
        File apk = project.getApk("debug");
        assertThatApk(apk).hasVersionCode(1)
        assertThatApk(apk).contains("lib/armeabi-v7a/libhello-jni.so");
        assertThatApk(apk).contains("lib/armeabi/libhello-jni.so");
        assertThatApk(apk).contains("lib/x86/libhello-jni.so");
        assertThatApk(apk).contains("lib/x86_64/libhello-jni.so");

        File lib = ZipHelper.extractFile(apk, "lib/armeabi-v7a/libhello-jni.so");
        assertThatNativeLib(lib).isStripped();

        lib = ZipHelper.extractFile(apk, "lib/armeabi/libhello-jni.so");
        assertThatNativeLib(lib).isStripped();
    }

    @Test
    void "check apk content with injected ABI"() {
        // Pass invalid-abi, x86 and armeabi. The first (invalid-abi) should be ignored because
        // it is not valid for the build . The second (x86) should be the one chosen to build.
        // Finally, armeabi is valid but it will be ignored because x86 is "preferred".
        project.executor()
                .withProperty(AndroidProject.PROPERTY_BUILD_ABI, "invalid-abi,x86,armeabi")
                .run("clean", "assembleDebug")
        File apk = project.getApk("debug");
        assertThatApk(apk).doesNotContain("lib/armeabi-v7a/libhello-jni.so");
        assertThatApk(apk).doesNotContain("lib/armeabi/libhello-jni.so");
        assertThatApk(apk).contains("lib/x86/libhello-jni.so");
        assertThatApk(apk).doesNotContain("lib/x86_64/libhello-jni.so");

        File lib = ZipHelper.extractFile(apk, "lib/x86/libhello-jni.so");
        assertThatNativeLib(lib).isStripped();
    }

    @Test
    public void checkModel() {
        project.model().getSingle(); // Make sure we can successfully get AndroidProject
        NativeAndroidProject model = project.model().getSingle(NativeAndroidProject.class);
        assertThat(model.getBuildSystems()).containsExactly(NativeBuildSystem.NDK_BUILD.getName());
        assertThat(model.buildFiles).hasSize(1);
        assertThat(model.name).isEqualTo("project");
        assertThat(model.artifacts).hasSize(14);
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
        assertThat(model).hasArtifactGroupsOfSize(7);
    }

    @Test
    public void checkClean() {
        project.execute("clean", "assembleDebug", "assembleRelease")
        NativeAndroidProject model = project.model().getSingle(NativeAndroidProject.class);
        assertThat(model).hasBuildOutputCountEqualTo(14);
        assertThat(model).allBuildOutputsExist();
        assertThat(model).hasExactObjectFiles("hello-jni.o");
        assertThat(model).hasExactSharedObjectFiles("libhello-jni.so");
        project.execute("clean");
        assertThat(model).noBuildOutputsExist();
        assertThat(model).hasExactObjectFiles();
        assertThat(model).hasExactSharedObjectFiles();
    }
}
