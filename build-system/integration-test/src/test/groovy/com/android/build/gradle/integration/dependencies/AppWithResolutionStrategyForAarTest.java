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

import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.ModelHelper;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Dependencies;
import com.android.builder.model.Variant;
import com.google.common.base.Charsets;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * test for flavored dependency on a different package.
 */
public class AppWithResolutionStrategyForAarTest {

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
                "    debugCompile project(\":library\")\n" +
                "    releaseCompile project(\":library\")\n" +
                "}\n" +
                "\n" +
                "configurations {\n" +
                "  _debugCompile\n" +
                "  _debugApk\n" +
                "}\n" +
                "\n" +
                "configurations._debugCompile {\n" +
                "  resolutionStrategy {\n" +
                "    eachDependency { DependencyResolveDetails details ->\n" +
                "      if (details.requested.name == \"jdeferred-android-aar\") {\n" +
                "        details.useVersion \"1.2.2\"\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "}\n" +
                "configurations._debugApk {\n" +
                "  resolutionStrategy {\n" +
                "    eachDependency { DependencyResolveDetails details ->\n" +
                "      if (details.requested.name == \"jdeferred-android-aar\") {\n" +
                "        details.useVersion \"1.2.2\"\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "}\n" +
                "\n");

        TestFileUtils.appendToFile(project.getSubproject("library").getBuildFile(),
                "\n" +
                "dependencies {\n" +
                "    compile \"org.jdeferred:jdeferred-android-aar:1.2.3\"\n" +
                "}\n");

        models = project.model().getMulti();
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

        checkJarDependency(appVariants, "debug", "org.jdeferred:jdeferred-android-aar:aar:1.2.2");
        checkJarDependency(appVariants, "release", "org.jdeferred:jdeferred-android-aar:aar:1.2.3");
    }

    private static void checkJarDependency(
            @NonNull Collection<Variant> appVariants,
            @NonNull String variantName,
            @NonNull String aarCoodinate) {
        Variant appVariant = ModelHelper.getVariant(appVariants, variantName);

        AndroidArtifact appArtifact = appVariant.getMainArtifact();
        Dependencies artifactDependencies = appArtifact.getCompileDependencies();

        Collection<AndroidLibrary> directLibraries = artifactDependencies.getLibraries();
        assertThat(directLibraries)
                .named("direct libs of " + variantName)
                .hasSize(1);
        AndroidLibrary directLibrary = Iterables.getOnlyElement(directLibraries);
        assertThat(directLibrary.getProject())
                .named("Single lib name of " + variantName)
                .isEqualTo(":library");

        List<? extends AndroidLibrary> transitiveLibraries = directLibrary.getLibraryDependencies();
        assertThat(transitiveLibraries)
                .named("transitive libs of single direct lib of " + variantName)
                .hasSize(1);

        AndroidLibrary transitiveLibrary = Iterables.getOnlyElement(transitiveLibraries);
        assertThat(transitiveLibrary.getResolvedCoordinates().toString())
                .named("coord of single transitive deps of " + variantName)
                .isEqualTo(aarCoodinate);
    }
}
