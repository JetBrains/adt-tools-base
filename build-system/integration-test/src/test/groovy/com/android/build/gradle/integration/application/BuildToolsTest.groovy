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

package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.builder.model.AndroidProject
import com.google.common.collect.ImmutableList
import com.google.common.collect.Sets
import groovy.transform.CompileStatic
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test

import java.util.regex.Matcher
import java.util.regex.Pattern

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import static com.google.common.truth.Truth.assert_
/**
 * Tests to ensure that changing the build tools version in the build.gradle will trigger
 * re-execution of some tasks even if no source file change was detected.
 */
@CompileStatic
class BuildToolsTest {

    private static final Pattern UP_TO_DATE_PATTERN = ~/:(\S+)\s+UP-TO-DATE/

    private static final Pattern INPUT_CHANGED_PATTERN =
            ~/Value of input property '.*' has changed for task ':(\S+)'/

    private static final String[] COMMON_TASKS = [
            "compileDebugAidl", "compileDebugRenderscript",
            "mergeDebugResources", "processDebugResources",
            "compileReleaseAidl", "compileReleaseRenderscript",
            "mergeReleaseResources", "processReleaseResources"
    ]

    private static final List<String> JAVAC_TASKS = ImmutableList.builder().add(COMMON_TASKS)
            .add("transformClassesWithDexForDebug")
            .add("transformClassesWithDexForRelease")
            .build()

    private static final List<String> JACK_TASKS = ImmutableList.builder().add(COMMON_TASKS)
            .add("transformClassesAndResourcesWithPreDexJackRuntimeLibrariesForDebug")
            .add("transformClassesAndResourcesWithPreDexJackPackagedLibrariesForDebug")
            .add("transformJackWithJackForRelease")
            .add("transformClassesAndResourcesWithPreDexJackRuntimeLibrariesForRelease")
            .add("transformClassesAndResourcesWithPreDexJackPackagedLibrariesForRelease")
            .add("transformJackWithJackForDebug")
            .build()

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestApp(HelloWorldApp.noBuildFile())
            .create()

    @Before
    public void setUp() {
        project.getBuildFile() << """
apply plugin: 'com.android.application'

android {
    compileSdkVersion $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
    buildToolsVersion "$GradleTestProject.DEFAULT_BUILD_TOOL_VERSION"
}
"""
    }

    @Test
    public void nullBuild() {
        project.execute("assemble")
        project.execute("assemble")

        Set<String> skippedTasks = getTasksMatching(UP_TO_DATE_PATTERN, project.getStdout())
        assert_().withFailureMessage("Expecting tasks to be UP-TO-DATE").that(skippedTasks)
                .containsAllIn(GradleTestProject.USE_JACK ? JACK_TASKS : JAVAC_TASKS)
    }

    @Test
    public void invalidateBuildTools() {
        // Jack is tied to a build tools version currently, skip this test when testing Jack.
        Assume.assumeFalse(GradleTestProject.USE_JACK);

        project.execute("assemble")

        String oldBuildToolsVersion = "22.0.1"
        // Sanity check:
        assertThat(oldBuildToolsVersion).isNotEqualTo(GradleTestProject.DEFAULT_BUILD_TOOL_VERSION)

        project.getBuildFile() << """
apply plugin: 'com.android.application'

android {
    compileSdkVersion $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
    buildToolsVersion '$oldBuildToolsVersion'
}
"""

        project.execute("assemble")
        Set<String> affectedTasks =
                getTasksMatching(INPUT_CHANGED_PATTERN, project.getStdout())
        assert_().withFailureMessage("Expecting tasks to be invalidated").that(affectedTasks)
                .containsAllIn(GradleTestProject.USE_JACK ? JACK_TASKS : JAVAC_TASKS)
    }

    @Test
    void buildToolsInModel() {
        AndroidProject model = project.getSingleModel()
        assertThat(model.getBuildToolsVersion())
                .named("Build Tools Version")
                .isEqualTo(GradleTestProject.DEFAULT_BUILD_TOOL_VERSION)
    }

    private static Set<String> getTasksMatching(Pattern pattern, String output) {
        Set<String> result = Sets.newHashSet()
        Matcher matcher = (output =~ pattern)
        while (matcher.find()) {
            result.add(matcher.group(1))
        }
        result
    }
}
