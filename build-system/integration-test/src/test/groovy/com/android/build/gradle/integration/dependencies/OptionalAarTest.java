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
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.ModelHelper;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Dependencies;
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

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;

/**
 * test for optional aar (using the provided scope)
 */
public class OptionalAarTest {

    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("projectWithModules")
            .create();
    static Map<String, AndroidProject> models;

    @BeforeClass
    public static void setUp() throws IOException {
        Files.write("include 'app', 'library', 'library2'", project.getSettingsFile(), Charsets.UTF_8);

        appendToFile(project.getSubproject("app").getBuildFile(),
                "\n" +
                "\n" +
                "dependencies {\n" +
                "    compile project(\":library\")\n" +
                "}\n");
        appendToFile(project.getSubproject("library").getBuildFile(),
                "\n" +
                "\n" +
                "dependencies {\n" +
                "    provided project(\":library2\")\n" +
                "}\n");
        models = project.executeAndReturnMultiModel("clean", ":app:assembleDebug");
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
        models = null;
    }

    @Test
    public void checkAppDoesNotContainProvidedLibsLayout() throws IOException, ProcessException {
        File apk = project.getSubproject("app").getApk("debug");

        assertThatApk(apk).doesNotContainResource("layout/lib2layout.xml");
    }

    @Test
    public void checkAppDoesNotContainProvidedLibsCode() throws IOException, ProcessException {
        File apk = project.getSubproject("app").getApk("debug");

        assertThatApk(apk).doesNotContainClass("Lcom/example/android/multiproject/library2/PersonView2;");
    }

    @Test
    public void checkLIbDoesNotContainProvidedLibsLayout() throws IOException, ProcessException {
        File aar = project.getSubproject("library").getAar("release");

        assertThatAar(aar).doesNotContainResource("layout/lib2layout.xml");
        assertThatAar(aar).textSymbolFile().contains("int layout liblayout");
        assertThatAar(aar).textSymbolFile().doesNotContain("int layout lib2layout");
    }

    @Test
    public void checkAppModelDoesNotIncludeOptionalLibrary() {
        Collection<Variant> variants = models.get(":app").getVariants();

        // get the main artifact of the debug artifact and its dependencies
        Variant variant = ModelHelper.getVariant(variants, "debug");

        AndroidArtifact artifact = variant.getMainArtifact();
        Dependencies dependencies = artifact.getCompileDependencies();
        Collection<AndroidLibrary> libs = dependencies.getLibraries();

        assertThat(libs).hasSize(1);

        AndroidLibrary library = Iterables.getOnlyElement(libs);
        assertThat(library.getProject()).isEqualTo(":library");
        assertThat(library.isProvided()).isFalse();

        assertThat(library.getLibraryDependencies()).isEmpty();
    }

    @Test
    @Ignore
    public void checkLibraryModelIncludesOptionalLibrary() {
        Collection<Variant> variants = models.get(":library").getVariants();

        // get the main artifact of the debug artifact and its dependencies
        Variant variant = ModelHelper.getVariant(variants, "debug");

        AndroidArtifact artifact = variant.getMainArtifact();
        Dependencies compileDependencies = artifact.getCompileDependencies();
        Collection<AndroidLibrary> libs = compileDependencies.getLibraries();

        assertThat(libs).hasSize(1);

        AndroidLibrary library = Iterables.getOnlyElement(libs);
        assertThat(library.getProject()).isEqualTo(":library2");
        assertThat(library.isProvided()).isTrue();

        Dependencies packageDependencies = artifact.getPackageDependencies();
        assertThat(packageDependencies.getLibraries()).isEmpty();
    }
}
