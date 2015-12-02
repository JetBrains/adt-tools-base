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

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.ModelHelper;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Dependencies;
import com.android.builder.model.Variant;
import com.android.ide.common.process.ProcessException;
import com.google.common.collect.Iterables;
import com.google.common.truth.Truth;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk;
import static com.android.build.gradle.integration.common.utils.TestFileUtils.appendToFile;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;

/**
 * test for compile jar in app through an aar dependency
 */
public class AppWithCompileIndirectJavaProjectTest {

    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("projectWithModules")
            .create();
    static Map<String, AndroidProject> models;

    @BeforeClass
    public static void setUp() throws IOException {
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
"    compile project(':jar')\n" +
"}\n");

        models = project.executeAndReturnMultiModel("clean", ":app:assembleDebug");
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
        models = null;
    }

    @Test
    public void checkPackagedJar() throws IOException, ProcessException {
        File apk = project.getSubproject("app").getApk("debug");

        assertThatApk(apk).containsClass("Lcom/example/android/multiproject/person/People;");
        assertThatApk(apk).containsClass("Lcom/example/android/multiproject/library/PersonView;");
    }

    @Test
    public void checkTheModel() {
        Variant appDebug = ModelHelper.getVariant(models.get(":app").getVariants(), "debug");
        Truth.assertThat(appDebug).isNotNull();

        Dependencies deps = appDebug.getMainArtifact().getDependencies();

        Collection<AndroidLibrary> libs = deps.getLibraries();
        assertThat(libs).named("app androidlibrary deps count").hasSize(1);
        AndroidLibrary androidLibrary = Iterables.getOnlyElement(libs);
        assertThat(androidLibrary.getProject()).named("app androidlib deps path").isEqualTo(":library");

        Collection<String> projectDeps = deps.getProjects();
        assertThat(projectDeps).named("app module dependency count").hasSize(1);
        String projectDep = Iterables.getOnlyElement(projectDeps);
        assertThat(projectDep).named("app dependency path").isEqualTo(":jar");

        assertThat(deps.getJavaLibraries()).named("app java dependency count").isEmpty();

        // ---

        Variant libDebug = ModelHelper.getVariant(models.get(":library").getVariants(), "debug");
        Truth.assertThat(libDebug).isNotNull();

        deps = libDebug.getMainArtifact().getDependencies();

        assertThat(deps.getLibraries()).named("lib androidlibrary deps count").isEmpty();

        projectDeps = deps.getProjects();
        assertThat(projectDeps).named("lib module dependency count").hasSize(1);
        projectDep = Iterables.getOnlyElement(projectDeps);
        assertThat(projectDep).named("lib dependency path").isEqualTo(":jar");

        assertThat(deps.getJavaLibraries()).named("lib java dependency count").isEmpty();
    }
}