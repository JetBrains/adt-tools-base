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

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.build.gradle.integration.common.utils.TestFileUtils.appendToFile;

import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.ModelHelper;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.builder.model.AndroidArtifact;
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
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

/**
 * test for flavored dependency on a different package.
 */
public class AppWithResolutionStrategyForJarTest {

    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("projectWithModules")
            .create();
    static Map<String, AndroidProject> models;

    @BeforeClass
    public static void setUp() throws IOException {
        Files.write("include 'app', 'library'", project.getSettingsFile(), Charsets.UTF_8);

        appendToFile(project.getBuildFile(),
                "\n" +
                "subprojects {\n" +
                "    apply from: \"$rootDir/../commonLocalRepo.gradle\"\n" +
                "}\n");
        appendToFile(project.getSubproject("app").getBuildFile(),
                "\n" +
                "\n" +
                "dependencies {\n" +
                "    compile project(\":library\")\n" +
                "}\n" +
                "\n" +
                "configurations { _debugCompile }\n" +
                "\n" +
                "configurations._debugCompile {\n" +
                "  resolutionStrategy {\n" +
                "    eachDependency { DependencyResolveDetails details ->\n" +
                "      if (details.requested.name == \"guava\") {\n" +
                "        details.useVersion \"15.0\"\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "}\n" +
                "\n");

        TestFileUtils.appendToFile(project.getSubproject("library").getBuildFile(),
                "\n" +
                "dependencies {\n" +
                "    compile \"com.google.guava:guava:17.0\"\n" +
                "}\n");

        models = project.model().getMulti();
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
        models = null;
    }

    @Test
    @Ignore
    public void checkModelContainsCorrectDependencies() {
        AndroidProject appProject = models.get(":app");
        Collection<Variant> appVariants = appProject.getVariants();

        Variant debugVariant = ModelHelper.getVariant(appVariants, "debug");

        AndroidArtifact mainArtifact = debugVariant.getMainArtifact();
        checkJarDependency(mainArtifact.getCompileDependencies(), "15.0", "debug");
        checkJarDependency(mainArtifact.getPackageDependencies(), "17.0", "debug");

        Variant releaseVariant = ModelHelper.getVariant(appVariants, "release");
        Truth.assertThat(releaseVariant).isNotNull();

        mainArtifact = releaseVariant.getMainArtifact();
        checkJarDependency(mainArtifact.getCompileDependencies(), "17.0", "release");
        checkJarDependency(mainArtifact.getPackageDependencies(), "17.0", "release");
    }

    private static void checkJarDependency(Dependencies dependencies,
            @NonNull String jarVersion, @NonNull String variantName) {
        Collection<AndroidLibrary> androidLibraries = dependencies.getLibraries();
        assertThat(androidLibraries).hasSize(1);

        AndroidLibrary androidLibrary = Iterables.getOnlyElement(androidLibraries);
        assertThat(androidLibrary.getProject()).isEqualTo(":library");

        Collection<? extends JavaLibrary> javaLibraries = androidLibrary.getJavaDependencies();
        assertThat(javaLibraries).named("java libs of " + variantName).hasSize(1);

        JavaLibrary javaLibrary = Iterables.getOnlyElement(javaLibraries);
        assertThat(javaLibrary.getResolvedCoordinates())
                .named("single java lib deps of " + variantName)
                .hasVersion(jarVersion);
    }
}
