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

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.ModelHelper;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Dependencies;
import com.android.builder.model.JavaLibrary;
import com.android.builder.model.Variant;
import com.google.common.base.Charsets;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;
import com.google.common.truth.Truth;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

/**
 * test for compile jar in app through an aar dependency
 */
public class AppWithCompileIndirectJarTest {

    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("projectWithModules")
            .create();

    @BeforeClass
    public static void setUp() throws IOException {
        Files.write("include 'app', 'library'", project.getSettingsFile(), Charsets.UTF_8);

        appendToFile(project.getBuildFile(),
"\nsubprojects {\n" +
"    apply from: \"$rootDir/../commonLocalRepo.gradle\"\n" +
"}\n");

        appendToFile(project.getSubproject("app").getBuildFile(),
"\ndependencies {\n" +
"    compile project(':library')\n" +
"}\n");

        appendToFile(project.getSubproject("library").getBuildFile(),
"\ndependencies {\n" +
"    compile 'com.google.guava:guava:18.0'\n" +
"}\n");

    }

    @AfterClass
    public static void cleanUp() {
        project = null;
    }

    @Test
    public void checkLevel1Model() {
        Map<String, AndroidProject> models = project.model()
                .level(AndroidProject.MODEL_LEVEL_1_SYNC_ISSUE).getMulti();

        Variant appDebug = ModelHelper.getVariant(models.get(":app").getVariants(), "debug");

        Dependencies deps = appDebug.getMainArtifact().getCompileDependencies();

        Collection<AndroidLibrary> libs = deps.getLibraries();
        assertThat(libs).named("app androidlibrary deps count").hasSize(1);
        AndroidLibrary androidLibrary = Iterables.getOnlyElement(libs);
        assertThat(androidLibrary.getProject()).named("app androidlib deps path").isEqualTo(":library");
        assertThat(androidLibrary.getJavaDependencies()).named("app androidlib java libs count").isEmpty();

        assertThat(deps.getProjects()).named("app module dependency count").isEmpty();

        Collection<JavaLibrary> javaLibraries = deps.getJavaLibraries();
        assertThat(javaLibraries).named("app java dependency count").isEmpty();

        // ---

        Variant libDebug = ModelHelper.getVariant(models.get(":library").getVariants(), "debug");
        Truth.assertThat(libDebug).isNotNull();

        deps = libDebug.getMainArtifact().getCompileDependencies();

        assertThat(deps.getLibraries()).named("lib androidlibrary deps count").isEmpty();

        javaLibraries = deps.getJavaLibraries();
        assertThat(javaLibraries).named("lib java dependency count").hasSize(1);
        JavaLibrary javaLib = Iterables.getOnlyElement(javaLibraries);
        assertThat(javaLib.getResolvedCoordinates())
                .named("lib java lib resolved coordinates")
                .isEqualTo("com.google.guava", "guava", "18.0");
    }

    @Test
    public void checkLevel2Model() {
        Map<String, AndroidProject> models = project.model()
                .level(AndroidProject.MODEL_LEVEL_LATEST).getMulti();

        Variant appDebug = ModelHelper.getVariant(models.get(":app").getVariants(), "debug");

        Dependencies deps = appDebug.getMainArtifact().getCompileDependencies();

        Collection<AndroidLibrary> libs = deps.getLibraries();
        assertThat(libs).named("app androidlibrary deps count").hasSize(1);

        AndroidLibrary androidLibrary = Iterables.getOnlyElement(libs);
        assertThat(androidLibrary.getProject()).named("app androidlib deps path").isEqualTo(":library");

        Collection<? extends JavaLibrary> javaLibraries = androidLibrary.getJavaDependencies();
        assertThat(javaLibraries).named("androidlib java dependency count").hasSize(1);
        JavaLibrary javaLib = Iterables.getOnlyElement(javaLibraries);
        assertThat(javaLib.getResolvedCoordinates())
                .named("androidlib java lib resolved coordinates")
                .isEqualTo("com.google.guava", "guava", "18.0");

        assertThat(deps.getProjects()).named("app module dependency count").isEmpty();
        assertThat(deps.getJavaLibraries()).named("app java dependency count").isEmpty();

        // ---

        Variant libDebug = ModelHelper.getVariant(models.get(":library").getVariants(), "debug");
        Truth.assertThat(libDebug).isNotNull();

        deps = libDebug.getMainArtifact().getCompileDependencies();

        assertThat(deps.getLibraries()).named("lib androidlibrary deps count").isEmpty();

        javaLibraries = deps.getJavaLibraries();
        assertThat(javaLibraries).named("lib java dependency count").hasSize(1);
        javaLib = Iterables.getOnlyElement(javaLibraries);
        assertThat(javaLib.getResolvedCoordinates())
                .named("lib java lib resolved coordinates")
                .isEqualTo("com.google.guava", "guava", "18.0");
    }

}
