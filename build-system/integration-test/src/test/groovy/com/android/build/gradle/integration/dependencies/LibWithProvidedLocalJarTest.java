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

package com.android.build.gradle.integration.dependencies;

import static com.android.build.gradle.integration.common.utils.TestFileUtils.appendToFile;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatZip;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.ModelHelper;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Dependencies;
import com.android.builder.model.Variant;
import com.google.common.truth.Truth;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.IOException;
/**
 * test for provided local jar in libs
 */
public class LibWithProvidedLocalJarTest {

    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("projectWithLocalDeps")
            .create();
    static AndroidProject model;

    @BeforeClass
    public static void setUp() throws IOException {
        appendToFile(project.getBuildFile(),
                "\n" +
                "apply plugin: \"com.android.library\"\n" +
                "\n" +
                "android {\n" +
                "    compileSdkVersion " + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION + "\n" +
                "    buildToolsVersion \"" + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION + "\"\n" +
                "}\n" +
                "\n" +
                "dependencies {\n" +
                "    provided files(\"libs/util-1.0.jar\")\n" +
                "}\n");

        model = project.executeAndReturnModel("clean", "assembleDebug");
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
        model = null;
    }

    @Test
    public void checkProvidedLocalJarIsNotPackaged() throws IOException {
        assertThatZip(project.getAar("debug")).doesNotContain("libs/util-1.0.jar");
    }

    @Test
    public void checkProvidedLocalJarIsInTheMainArtifactDependency() {
        Variant variant = ModelHelper.getVariant(model.getVariants(), "debug");

        Dependencies deps = variant.getMainArtifact().getDependencies();
        assertThat(deps.getJavaLibraries()).hasSize(1);
    }

    @Test
    public void checkProvidedLocalJarIsInTheAndroidTestDeps() {
        // TODO
    }

    @Test
    public void checkProvidedLocalJarIsInTheUnitTestDeps() {
        // TODO
    }
}
