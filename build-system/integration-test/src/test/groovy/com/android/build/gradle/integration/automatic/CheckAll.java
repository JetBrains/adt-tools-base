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

package com.android.build.gradle.integration.automatic;

import static com.google.common.base.Preconditions.checkState;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.runner.ParallelParameterized;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;

import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.util.Collection;
import java.util.List;

/**
 * Test case that executes "standard" gradle tasks in all our tests projects.
 *
 * <p>You can run only one test like this:
 * <p>{@code gw b:i:automaticTest --tests *.CheckAll.lint[abiPureSplits]}
 */
@RunWith(ParallelParameterized.class)
public class CheckAll {

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        List<Object[]> parameters = Lists.newArrayList();

        File[] testProjects = GradleTestProject.TEST_PROJECT_DIR.listFiles();
        checkState(testProjects != null);

        for (File testProject : testProjects) {
            if (!isValidProjectDirectory(testProject)) {
                continue;
            }

            parameters.add(new Object[]{testProject.getName()});
        }

        return parameters;
    }

    private static boolean isValidProjectDirectory(File testProject) {
        if (!testProject.isDirectory()) {
            return false;
        }

        File buildGradle = new File(testProject, "build.gradle");
        File settingsGradle = new File(testProject, "settings.gradle");

        return buildGradle.exists() || settingsGradle.exists();
    }

    @Rule
    public GradleTestProject project;

    public String projectName;

    public CheckAll(String projectName) {
        this.projectName = projectName;
        this.project = GradleTestProject.builder().fromTestProject(projectName).create();
    }

    @Test
    public void lint() throws Exception {
        Assume.assumeFalse(BROKEN_LINT.contains(projectName));
        project.execute("lint");
    }

    @Test
    public void assemble() throws Exception {
        Assume.assumeFalse(BROKEN_ASSEMBLE.contains(projectName));
        project.execute("assembleDebug", "assembleAndroidTest");
    }

    // TODO: Investigate and clear these lists.
    private static final ImmutableSet<String> BROKEN_ASSEMBLE = ImmutableSet.of(
            "3rdPartyTests",
            "abiPureSplits",
            "androidTestLibDep",
            "combinedAbiDensityPureSplits",
            "componentModel",
            "customSigning",
            "dependencyCheckerComGoogleAndroidJar",
            "duplicateNameImport",
            "embedded",
            "extractAnnotations",
            "filteredOutVariants",
            "flavorlibWithFailedTests",
            "genFolderApi2",
            "invalidDependencyOnAppProject",
            "jarjarIntegration",
            "jarjarIntegrationLib",
            "localAarTest",
            "ndkJniLib",
            "ndkJniPureSplitLib",
            "ndkPrebuilts",
            "ndkRsHelloCompute",
            "ndkSanAngeles",
            "ndkSanAngeles2",
            "ndkVariants",
            "noPreDex",
            "packagingOptions",
            "privateResources",
            "projectWithLocalDeps",
            "renderscriptNdk",
            "rsSupportMode",
            "simpleManifestMergingTask", // Not an Android project.
            "simpleMicroApp",
            "testWithDep",
            "unitTestingFlavors");

    private static final ImmutableSet<String> BROKEN_LINT = ImmutableSet.of(
            "3rdPartyTests",
            "abiPureSplits",
            "androidTestLibDep",
            "combinedAbiDensityPureSplits",
            "componentModel",
            "customSigning",
            "dependencyCheckerComGoogleAndroidJar",
            "duplicateNameImport",
            "flavorlibWithFailedTests",
            "genFolderApi2",
            "invalidDependencyOnAppProject",
            "jarjarIntegration",
            "jarjarIntegrationLib",
            "ndkJniLib",
            "ndkJniPureSplitLib",
            "ndkPrebuilts",
            "ndkRsHelloCompute",
            "ndkSanAngeles",
            "ndkSanAngeles2",
            "ndkVariants",
            "privateResources",
            "projectWithLocalDeps",
            "projectWithModules",
            "renderscriptNdk",
            "rsSupportMode",
            "simpleManifestMergingTask", // Not an Android project.
            "testWithDep",
            "unitTestingFlavors");
}
