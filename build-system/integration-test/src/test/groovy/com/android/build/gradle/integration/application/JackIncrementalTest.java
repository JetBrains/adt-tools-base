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

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.utils.AssumeUtil;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.io.Files;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.regex.Pattern;

/**
 * Test for incremental compilation with Jack.
 */
public class JackIncrementalTest {

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
            .withJack(true)
            .create();

    @Before
    public void setUp() throws IOException {
        TestFileUtils.appendToFile(project.getBuildFile(), "\n"
                + "android.buildToolsVersion '" + GradleTestProject.UPCOMING_BUILD_TOOL_VERSION
                + "'\n");
    }

    @Test
    public void assembleDebug() throws IOException {
        project.execute("clean", "assembleDebug");
        File classesDex =
                FileUtils.find(project.file("build/intermediates/transforms/jack"), "classes.dex").get();
        long classesDexTimestamp = classesDex.lastModified();

        // Check pre-dexed library is not updated
        File androidJar =
                FileUtils.find(
                        project.file("build/intermediates/transforms/preJackRuntimeLibraries"),
                        Pattern.compile("android.*")).get(0);
        long androidJarTimestamp = androidJar.lastModified();

        File src = FileUtils.find(project.file("src/main/java"), "HelloWorld.java").get();
        Files.append(" \n", src, Charsets.UTF_8);

        project.execute("assembleDebug");

        assertThat(project.file("build/intermediates/incremental/transformJackWithJackForDebug")).isDirectory();

        assertThat(classesDex).isNewerThan(classesDexTimestamp);
        assertThat(androidJar).wasModifiedAt(androidJarTimestamp);
    }

    @Test
    public void checkDisablingIncrementalCompile() throws IOException {
        TestFileUtils.appendToFile(project.getBuildFile(), "\n"
                + "android.compileOptions.incremental false\n");
        project.execute("clean", "assembleDebug");
        assertThat(project.file("build/intermediates/incremental/transformJackWithJackForDebug")).doesNotExist();

    }
}
