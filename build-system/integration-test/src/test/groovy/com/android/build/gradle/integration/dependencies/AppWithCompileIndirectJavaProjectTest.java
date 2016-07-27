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
import org.junit.Test;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk;
import static com.android.build.gradle.integration.common.utils.TestFileUtils.appendToFile;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * test for compile jar in app through an aar dependency
 */
public class AppWithCompileIndirectJavaProjectTest {

    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("projectWithModules")
            .create();

    @BeforeClass
    public static void setUp() throws IOException {
        Files.write("include 'app', 'library', 'jar'", project.getSettingsFile(), Charsets.UTF_8);

        appendToFile(project.getBuildFile(),
"\nsubprojects {\n" +
"    apply from: \"$rootDir/../commonLocalRepo.gradle\"\n" +
"}\n");

        appendToFile(project.getSubproject("app").getBuildFile(),
"\ndependencies {\n" +
"    compile project(':library')\n" +
"    apk 'com.google.guava:guava:18.0'\n" +
"}\n");

        appendToFile(project.getSubproject("library").getBuildFile(),
"\ndependencies {\n" +
"    compile project(':jar')\n" +
"}\n");

        appendToFile(project.getSubproject("jar").getBuildFile(),
"\ndependencies {\n" +
"    compile 'com.google.guava:guava:17.0'\n" +
"}\n");

    }

    @AfterClass
    public static void cleanUp() {
        project = null;
    }

    @Test
    public void checkPackagedJar() throws IOException, ProcessException {
        project.execute("clean", ":app:assembleDebug");

        File apk = project.getSubproject("app").getApk("debug");

        assertThatApk(apk).containsClass("Lcom/example/android/multiproject/person/People;");
        assertThatApk(apk).containsClass("Lcom/example/android/multiproject/library/PersonView;");
    }

    @Test
    public void checkLevel1Model() {
        Map<String, AndroidProject> models = project.model()
                .level(AndroidProject.MODEL_LEVEL_1_SYNC_ISSUE).getMulti();

        // ---
        // test the dependencies on the :app module.

        Variant appDebug = ModelHelper.getVariant(models.get(":app").getVariants(), "debug");
        Truth.assertThat(appDebug).isNotNull();

        Dependencies appDeps = appDebug.getMainArtifact().getCompileDependencies();

        Collection<AndroidLibrary> appLibDeps = appDeps.getLibraries();
        assertThat(appLibDeps).named("app(androidlibs) count").hasSize(1);
        AndroidLibrary appAndroidLibrary = Iterables.getOnlyElement(appLibDeps);
        assertThat(appAndroidLibrary.getProject()).named("app(androidlibs[0]) project").isEqualTo(":library");

        Collection<String> appProjectDeps = appDeps.getProjects();
        assertThat(appProjectDeps).named("app(modules) count").isEmpty();

        Collection<JavaLibrary> appJavaLibDeps = appDeps.getJavaLibraries();
        assertThat(appJavaLibDeps).named("app(javalibs) count").isEmpty();

        // ---
        // test the dependencies on the :library module.

        Variant libDebug = ModelHelper.getVariant(models.get(":library").getVariants(), "debug");
        Truth.assertThat(libDebug).isNotNull();

        Dependencies libDeps = libDebug.getMainArtifact().getCompileDependencies();

        assertThat(libDeps.getLibraries()).named("lib(androidlibs) count").isEmpty();

        Collection<String> libProjectDeps = libDeps.getProjects();
        assertThat(libProjectDeps).named("lib(modules) count").hasSize(1);
        String libProjectDep = Iterables.getOnlyElement(libProjectDeps);
        assertThat(libProjectDep).named("lib->:jar project").isEqualTo(":jar");

        Collection<JavaLibrary> libJavaLibDeps = appDeps.getJavaLibraries();
        assertThat(libJavaLibDeps).named("lib(javalibs) count").isEmpty();
    }

    @Test
    public void checkLevel2Model() {
        Map<String, AndroidProject> models = project.model()
                .level(AndroidProject.MODEL_LEVEL_LATEST).getMulti();

        // ---
        // test full transitive dependencies from the :app module.

        Variant appDebug = ModelHelper.getVariant(models.get(":app").getVariants(), "debug");
        Truth.assertThat(appDebug).isNotNull();
        {
            Dependencies compileAppDeps = appDebug.getMainArtifact().getCompileDependencies();

            assertThat(compileAppDeps.getProjects()).named("app(modules) count").isEmpty();
            assertThat(compileAppDeps.getJavaLibraries()).named("app(javalibs) count").isEmpty();

            Collection<AndroidLibrary> libs = compileAppDeps.getLibraries();
            assertThat(libs).named("app(androidlibs) count").hasSize(1);

            AndroidLibrary appAndroidLibrary = Iterables.getOnlyElement(libs);
            assertThat(appAndroidLibrary.getProject()).named("app->:lib project").isEqualTo(":library");

            Collection<? extends JavaLibrary> applibJavaLibDeps = appAndroidLibrary
                    .getJavaDependencies();
            assertThat(applibJavaLibDeps).named("app->:lib(javalibs) count").hasSize(1);
            JavaLibrary appLibJavaLibDep = Iterables.getOnlyElement(applibJavaLibDeps);
            assertThat((appLibJavaLibDep.getProject())).named("app->:lib->:jar project")
                    .isEqualTo(":jar");

            List<? extends JavaLibrary> appLibJavaLibLibDeps = appLibJavaLibDep.getDependencies();
            assertThat(appLibJavaLibLibDeps).named("app->:lib->:jar(libs) count").hasSize(1);

            JavaLibrary appLibJavaLibLibDep = Iterables.getOnlyElement(appLibJavaLibLibDeps);
            assertThat(appLibJavaLibLibDep.getProject()).named("app->:lib->:jar->guava project")
                    .isNull();
            assertThat(appLibJavaLibLibDep.getResolvedCoordinates())
                    .named("app->:lib->:jar->guava resolved coordinates")
                    .isEqualTo("com.google.guava", "guava", "17.0");
        }

        // same thing with the package deps.
        {
            Dependencies packageAppDeps = appDebug.getMainArtifact().getPackageDependencies();

            assertThat(packageAppDeps.getProjects()).named("app(modules) count").isEmpty();

            assertThat(packageAppDeps.getJavaLibraries()).named("app(javalibs) count").hasSize(1);
            JavaLibrary topLevelGuava = Iterables.getOnlyElement(packageAppDeps.getJavaLibraries());
            assertThat(topLevelGuava.getProject()).named("app->guava project").isNull();
            assertThat(topLevelGuava.getResolvedCoordinates())
                    .named("app->guava resolved coordinates")
                    .isEqualTo("com.google.guava", "guava", "18.0");

            Collection<AndroidLibrary> libs = packageAppDeps.getLibraries();
            assertThat(libs).named("app(androidlibs) count").hasSize(1);

            AndroidLibrary appAndroidLibrary = Iterables.getOnlyElement(libs);
            assertThat(appAndroidLibrary.getProject()).named("app->:lib project")
                    .isEqualTo(":library");

            Collection<? extends JavaLibrary> applibJavaLibDeps = appAndroidLibrary
                    .getJavaDependencies();
            assertThat(applibJavaLibDeps).named("app->:lib(javalibs) count").hasSize(1);
            JavaLibrary appLibJavaLibDep = Iterables.getOnlyElement(applibJavaLibDeps);
            assertThat((appLibJavaLibDep.getProject())).named("app->:lib->:jar project")
                    .isEqualTo(":jar");

            List<? extends JavaLibrary> appLibJavaLibLibDeps = appLibJavaLibDep.getDependencies();
            assertThat(appLibJavaLibLibDeps).named("app->:lib->:jar(libs) count").hasSize(1);

            JavaLibrary appLibJavaLibLibDep = Iterables.getOnlyElement(appLibJavaLibLibDeps);
            assertThat(appLibJavaLibLibDep.getProject()).named("app->:lib->:jar->guava project")
                    .isNull();
            assertThat(appLibJavaLibLibDep.getResolvedCoordinates())
                    .named("app->:lib->:jar->guava resolved coordinates")
                    .isEqualTo("com.google.guava", "guava", "18.0");

            //assertThat(appLibJavaLibLibDep).isSameAs(topLevelGuava);
        }

        // ---
        // test full transitive dependencies from the :library module.
        {
            Variant libDebug = ModelHelper
                    .getVariant(models.get(":library").getVariants(), "debug");
            Truth.assertThat(libDebug).isNotNull();

            Dependencies libDeps = libDebug.getMainArtifact().getCompileDependencies();

            assertThat(libDeps.getLibraries()).named("lib(androidlibs) count").isEmpty();
            assertThat(libDeps.getProjects()).named("lib(modules) count").isEmpty();

            Collection<JavaLibrary> libJavaLibDeps = libDeps.getJavaLibraries();
            assertThat(libJavaLibDeps).named("lib(javalibs) count").hasSize(1);
            JavaLibrary libJavaLibDep = Iterables.getOnlyElement(libJavaLibDeps);
            assertThat(libJavaLibDep.getProject()).named("lib->:jar project").isEqualTo(":jar");

            List<? extends JavaLibrary> libJavaLibLibDeps = libJavaLibDep.getDependencies();
            assertThat(libJavaLibLibDeps).named("lib->:jar(libs) count").hasSize(1);

            JavaLibrary libJavaLibLibDep = Iterables.getOnlyElement(libJavaLibLibDeps);
            assertThat(libJavaLibLibDep.getProject()).named("lib->:jar->guava project").isNull();
            assertThat(libJavaLibLibDep.getResolvedCoordinates())
                    .named("lib->:jar->guava resolved coordinates")
                    .isEqualTo("com.google.guava", "guava", "17.0");
        }
    }
}