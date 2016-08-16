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
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatAar;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.truth.AbstractAndroidSubject;
import com.android.build.gradle.integration.common.utils.ModelHelper;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Dependencies;
import com.android.builder.model.Variant;
import com.android.ide.common.process.ProcessException;
import com.google.common.truth.Truth;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;

/**
 * test for package (publish) local jar in libs
 */
public class LibWithPackageLocalJarTest {

    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("projectWithLocalDeps")
            .create();
    static AndroidProject model;

    @BeforeClass
    public static void setUp() throws IOException {
        TestFileUtils.appendToFile(project.getBuildFile(),
                "\n" +
                "apply plugin: \"com.android.library\"\n" +
                "\n" +
                "android {\n" +
                "    compileSdkVersion " + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION + "\n" +
                "    buildToolsVersion \"" + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION + "\"\n" +
                "}\n" +
                "\n" +
                "dependencies {\n" +
                "    publish files(\"libs/util-1.0.jar\")\n" +
                "}\n");

        model = project.executeAndReturnModel("clean", "assembleDebug");
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
        model = null;
    }

    @Test
    public void checkPackagedLocalJarIsPackaged() throws IOException, ProcessException {
        // search in secondary jars only.
        assertThatAar(project.getAar("debug")).containsClass(
                "Lcom/example/android/multiproject/person/People;",
                AbstractAndroidSubject.ClassFileScope.SECONDARY);
    }

    @Test
    public void checkPackagedLocalJarIsNotInTheCompileModel() {
        Variant variant = ModelHelper.getVariant(model.getVariants(), "debug");

        Dependencies compileDependencies = variant.getMainArtifact().getCompileDependencies();
        assertThat(compileDependencies.getJavaLibraries()).named("java libs").isEmpty();
    }

    @Test
    @Ignore
    public void checkPackagedLocalJarIsNotInThePackageModel() {
        Variant variant = ModelHelper.getVariant(model.getVariants(), "debug");

        Dependencies packageDependencies = variant.getMainArtifact().getPackageDependencies();
        assertThat(packageDependencies.getJavaLibraries()).named("java libs").hasSize(1);
    }
}
