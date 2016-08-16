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
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk;
import static com.android.build.gradle.integration.common.utils.ModelHelper.getAndroidArtifact;
import static com.android.builder.model.AndroidProject.ARTIFACT_ANDROID_TEST;
import static org.junit.Assert.assertNotNull;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.ModelHelper;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Dependencies;
import com.android.builder.model.JavaLibrary;
import com.android.builder.model.Variant;
import com.android.ide.common.process.ProcessException;
import com.google.common.base.Charsets;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;
import com.google.common.truth.Truth;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

/**
 * test for compile jar in a test app
 */
public class TestWithFlavorsWithCompileDirectJarTest {

    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("projectWithModules")
            .create();
    static Map<String, AndroidProject> models;

    @BeforeClass
    public static void setUp() throws IOException {
        Files.write("include 'app', 'jar'", project.getSettingsFile(), Charsets.UTF_8);

        appendToFile(project.getSubproject("app").getBuildFile(),
                "\n" +
                        "android {\n" +
                        "    productFlavors {\n" +
                        "      pro { }\n" +
                        "      free { }\n" +
                        "    }\n" +
                        "}\n" +
                        "\n" +
                        "dependencies {\n" +
                        "    androidTestCompile project(\":jar\")\n" +
                        "}\n");
        models = project.executeAndReturnMultiModel("clean", ":app:assembleFreeDebugAndroidTest");
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
        models = null;
    }

    @Test
    public void checkCompiledJarIsPackaged() throws IOException, ProcessException {
        assertThatApk(project.getSubproject("app").getTestApk("free", "debug"))
                .containsClass("Lcom/example/android/multiproject/person/People;");
    }

    @Test
    @Ignore
    public void checkCompiledJarIsInTheTestArtifactModel() {
        Variant variant = ModelHelper.getVariant(models.get(":app").getVariants(), "freeDebug");

        Collection<AndroidArtifact> androidArtifacts = variant.getExtraAndroidArtifacts();
        AndroidArtifact testArtifact = getAndroidArtifact(androidArtifacts, ARTIFACT_ANDROID_TEST);
        assertNotNull(testArtifact);

        Dependencies deps = testArtifact.getCompileDependencies();

        Collection<JavaLibrary> javaLibraries = deps.getJavaLibraries();
        assertThat(javaLibraries).hasSize(1);
        JavaLibrary javaLibrary = Iterables.getOnlyElement(javaLibraries);
        assertThat(javaLibrary.getProject()).isEqualTo(":jar");
    }
}
