/*
 * Copyright (C) 2015 The Android Open Source Project
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
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatAar;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.truth.TruthHelper;
import com.android.build.gradle.integration.common.utils.ModelHelper;
import com.android.builder.model.AndroidLibrary;
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
 * test for provided jar in library
 */
public class LibWithProvidedDirectJarTest {

    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("projectWithModules")
            .create();
    static Map<String, AndroidProject> models;

    @BeforeClass
    public static void setUp() throws IOException {
        Files.write("include 'app', 'library', 'jar'", project.getSettingsFile(), Charsets.UTF_8);

        appendToFile(project.getSubproject("app").getBuildFile(),
                "\n" +
                "dependencies {\n" +
                "    compile project(\":library\")\n" +
                "}\n");

        appendToFile(project.getSubproject("library").getBuildFile(),
                "\n" +
                "dependencies {\n" +
                "    provided project(\":jar\")\n" +
                "}\n");

        models = project.executeAndReturnMultiModel("clean", ":library:assembleDebug");
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
        models = null;
    }

    @Test
    public void checkProvidedJarIsNotPackaged() throws IOException, ProcessException {
        assertThatAar(project.getSubproject("library").getAar("debug"))
                .doesNotContainClass("Lcom/example/android/multiproject/person/People;");
    }

    @Test
    @Ignore
    public void checkProvidedJarIsIntheLibCompileDeps() {
        Variant variant = ModelHelper.getVariant(models.get(":library").getVariants(), "debug");

        Dependencies deps = variant.getMainArtifact().getCompileDependencies();

        Collection<JavaLibrary> javaLibraries = deps.getJavaLibraries();
        assertThat(javaLibraries).hasSize(1);

        JavaLibrary javaLibrary = Iterables.getOnlyElement(javaLibraries);
        assertThat(javaLibrary.getProject()).isEqualTo(":jar");
        assertThat(javaLibrary.isProvided()).isTrue();
    }

    @Test
    public void checkProvidedJarIsNotIntheLibPackageDeps() {
        Variant variant = ModelHelper.getVariant(models.get(":library").getVariants(), "debug");

        Dependencies deps = variant.getMainArtifact().getPackageDependencies();

        Collection<JavaLibrary> javaLibraries = deps.getJavaLibraries();
        assertThat(javaLibraries).isEmpty();
    }

    @Test
    public void checkProvidedJarIsNotInTheAppDeps() {
        Variant variant = ModelHelper.getVariant(models.get(":app").getVariants(), "debug");

        Dependencies deps = variant.getMainArtifact().getCompileDependencies();
        Collection<AndroidLibrary> libraries = deps.getLibraries();
        assertThat(libraries).hasSize(1);

        AndroidLibrary androidLibrary = Iterables.getOnlyElement(libraries);
        assertThat(androidLibrary.getProject()).isEqualTo(":library");
        assertThat(androidLibrary.getJavaDependencies()).isEmpty();
    }
}
