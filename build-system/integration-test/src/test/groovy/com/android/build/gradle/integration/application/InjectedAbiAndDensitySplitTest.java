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

package com.android.build.gradle.integration.application;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.builder.model.AndroidProject;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.io.Files;

import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

/**
 * Test injected ABI and density reduces the number of splits being built.
 */
public class InjectedAbiAndDensitySplitTest {

    @ClassRule
    public static GradleTestProject sProject = GradleTestProject.builder()
            .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
            .create();

    @BeforeClass
    public static void setUp() throws IOException {
        FileUtils.createFile(sProject.file("src/main/jniLibs/x86/libprebuilt.so"), "");
        FileUtils.createFile(sProject.file("src/main/jniLibs/armeabi-v7a/libprebuilt.so"), "");

        Files.append(
                "android {\n"
                        + "    splits {\n"
                        + "        abi {\n"
                        + "            enable true\n"
                        + "            reset()\n"
                        + "            include 'x86', 'armeabi-v7a'\n"
                        + "            universalApk false\n"
                        + "        }\n"
                        + "        density {\n"
                        + "            enable true\n"
                        + "            reset()\n"
                        + "            include \"ldpi\", \"hdpi\"\n"
                        + "        }\n"
                        + "    }\n"
                        + "}",
                sProject.getBuildFile(),
                Charsets.UTF_8);
    }

    @Test
    public void checkAbi() throws IOException {
        sProject.executor()
                .withProperty(AndroidProject.PROPERTY_BUILD_ABI, "armeabi-v7a")
                .run("clean", "assembleDebug");

        assertThat(sProject.getApk("armeabi-v7a", "debug")).exists();
        assertThatApk(sProject.getApk("armeabi-v7a", "debug")).contains("lib/armeabi-v7a/libprebuilt.so");
        assertThat(sProject.getApk("ldpiArmeabi-v7a", "debug")).doesNotExist();
        assertThat(sProject.getApk("hdpiArmeabi-v7a", "debug")).doesNotExist();
        assertThat(sProject.getApk("x86", "debug")).doesNotExist();
        assertThat(sProject.getApk("ldpiX86", "debug")).doesNotExist();
        assertThat(sProject.getApk("hdpiX86", "debug")).doesNotExist();
    }

    @Test
    public void checkAbiAndDensity() throws IOException {
        sProject.executor()
                .withProperty(AndroidProject.PROPERTY_BUILD_ABI, "armeabi-v7a")
                .withProperty(AndroidProject.PROPERTY_BUILD_DENSITY, "ldpi")
                .run("clean", "assembleDebug");

        File apk;
        // Either ldpi or universal density can match the injected density.
        if (sProject.getApk("armeabi-v7a", "debug").exists()) {
            apk = sProject.getApk("armeabi-v7a", "debug");
            assertThat(sProject.getApk("ldpiArmeabi-v7a", "debug")).doesNotExist();
        } else {
            apk = sProject.getApk("ldpiArmeabi-v7a", "debug");
            assertThat(sProject.getApk("armeabi-v7a", "debug")).doesNotExist();
        }
        assertThat(apk).exists();
        assertThatApk(apk).contains("lib/armeabi-v7a/libprebuilt.so");
        assertThat(sProject.getApk("hdpiArmeabi-v7a", "debug")).doesNotExist();
        assertThat(sProject.getApk("x86", "debug")).doesNotExist();
        assertThat(sProject.getApk("ldpiX86", "debug")).doesNotExist();
        assertThat(sProject.getApk("hdpiX86", "debug")).doesNotExist();
    }

    @Test
    public void checkError() throws IOException {
        sProject.executor()
                .withProperty(AndroidProject.PROPERTY_BUILD_ABI, "mips")
                .expectFailure()
                .run("assembleDebug");

        sProject.executor()
                .withProperty(AndroidProject.PROPERTY_BUILD_DENSITY, "xxxhdpi")
                .expectFailure()
                .run("assembleDebug");
    }
}
