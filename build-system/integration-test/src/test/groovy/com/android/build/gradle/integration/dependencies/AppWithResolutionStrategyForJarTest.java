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
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Dependencies;
import com.android.builder.model.JavaLibrary;
import com.android.builder.model.Variant;
import com.google.common.collect.Iterables;
import com.google.common.truth.Truth;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
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
        appendToFile(project.getBuildFile(),
                "\n" +
                "subprojects {\n" +
                "    apply from: \"$rootDir/../commonLocalRepo.gradle\"\n" +
                "}\n");
        appendToFile(project.getSubproject("app").getBuildFile(),
                "\n" +
                "\n" +
                "dependencies {\n" +
                "    debugCompile project(\":library\")\n" +
                "    releaseCompile project(\":library\")\n" +
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

        models = project.getAllModels();
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
        models = null;
    }

    @Test
    public void checkModelContainsCorrectDependencies() {
        AndroidProject appProject = models.get(":app");
        Collection<Variant> appVariants = appProject.getVariants();

        checkJarDependency(appVariants, "debug", "com.google.guava:guava:jar:15.0");
        checkJarDependency(appVariants, "release", "com.google.guava:guava:jar:17.0");
    }

    private static void checkJarDependency(
            @NonNull Collection<Variant> appVariants,
            @NonNull String variantName,
            @NonNull String jarCoodinate) {
        Variant appVariant = ModelHelper.getVariant(appVariants, variantName);

        AndroidArtifact appArtifact = appVariant.getMainArtifact();
        Dependencies artifactDependencies = appArtifact.getDependencies();

        Collection<JavaLibrary> javaLibraries = artifactDependencies.getJavaLibraries();
        assertThat(javaLibraries)
                .named("java libs of " + variantName)
                .hasSize(1);

        JavaLibrary javaLibrary = Iterables.getOnlyElement(javaLibraries);
        assertThat(javaLibrary.getResolvedCoordinates().toString())
                .named("single java lib deps of " + variantName)
                .isEqualTo(jarCoodinate);
    }
}
