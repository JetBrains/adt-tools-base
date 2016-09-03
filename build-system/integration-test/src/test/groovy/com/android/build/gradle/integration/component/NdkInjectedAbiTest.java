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

package com.android.build.gradle.integration.component;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk;

import com.android.build.gradle.AndroidGradleOptions;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.builder.model.AndroidProject;
import com.google.common.collect.ImmutableList;

import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.IOException;

/**
 * Test for injected ABI.
 */
public class NdkInjectedAbiTest {

    @ClassRule
    public static GradleTestProject sProject = GradleTestProject.builder()
            .fromTestApp(HelloWorldJniApp.builder().build())
            .useExperimentalGradleVersion(true)
            .create();

    @BeforeClass
    public static void setUp() throws IOException {
        TestFileUtils.appendToFile(
                sProject.getBuildFile(),
                "apply plugin: 'com.android.model.application'\n"
                        + "\n"
                        + "model {\n"
                        + "    android {\n"
                        + "        compileSdkVersion " + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION + "\n"
                        + "        buildToolsVersion \"" + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION + "\"\n"
                        + "        ndk {\n"
                        + "            moduleName \"hello-jni\"\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");
    }

    @Test
    public void checkSingleBuildAbi() throws IOException {
        sProject.executor()
                .withProperty(AndroidGradleOptions.PROPERTY_BUILD_ONLY_TARGET_ABI, "true")
                .withProperty(AndroidProject.PROPERTY_BUILD_ABI, "armeabi")
                .run("clean", "assembleDebug");
        assertThatApk(sProject.getApk("debug")).contains("lib/armeabi/libhello-jni.so");
        assertThatApk(sProject.getApk("debug")).doesNotContain("lib/armeabi-v7a/libhello-jni.so");
        assertThatApk(sProject.getApk("debug")).doesNotContain("lib/x86/libhello-jni.so");
    }

    @Test
    public void checkOnlyTheFirstAbiIsPackaged() throws IOException {
        sProject.executor()
                .withProperty(AndroidGradleOptions.PROPERTY_BUILD_ONLY_TARGET_ABI, "true")
                .withProperty(AndroidProject.PROPERTY_BUILD_ABI, "armeabi,x86")
                .run("clean", "assembleDebug");
        assertThatApk(sProject.getApk("debug")).contains("lib/armeabi/libhello-jni.so");
        assertThatApk(sProject.getApk("debug")).doesNotContain("lib/armeabi-v7a/libhello-jni.so");
        assertThatApk(sProject.getApk("debug")).doesNotContain("lib/x86/libhello-jni.so");
    }

    @Test
    public void checkEmptyListDoNotFilter() throws IOException {
        sProject.executor()
                .withProperty(AndroidGradleOptions.PROPERTY_BUILD_ONLY_TARGET_ABI, "true")
                .withProperty(AndroidProject.PROPERTY_BUILD_ABI, "")
                .run("clean", "assembleDebug");
        assertThatApk(sProject.getApk("debug")).contains("lib/armeabi/libhello-jni.so");
        assertThatApk(sProject.getApk("debug")).contains("lib/armeabi-v7a/libhello-jni.so");
        assertThatApk(sProject.getApk("debug")).contains("lib/x86/libhello-jni.so");
    }

    @Test
    public void checkTargetAbiNeedsToBeEnabled() throws IOException {
        sProject.executor()
                .withProperty(AndroidProject.PROPERTY_BUILD_ABI, "armeabi")
                .run("clean", "assembleDebug");
        assertThatApk(sProject.getApk("debug")).contains("lib/armeabi/libhello-jni.so");
        assertThatApk(sProject.getApk("debug")).contains("lib/armeabi-v7a/libhello-jni.so");
        assertThatApk(sProject.getApk("debug")).contains("lib/x86/libhello-jni.so");
    }
}
