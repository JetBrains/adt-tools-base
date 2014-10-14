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

package com.android.test.application

import com.android.test.common.fixture.GradleTestProject
import com.android.test.common.fixture.app.HelloWorldApp
import com.google.common.collect.Sets
import org.junit.Before
import org.junit.Rule
import org.junit.Test

import java.util.regex.Matcher
import java.util.regex.Pattern

import static org.junit.Assert.assertTrue

class BuildToolsTest {

    private static final Pattern UP_TO_DATE_PATTERN = ~/:(\S+)\s+UP-TO-DATE/

    private static final Pattern INPUT_CHANGED_PATTERN =
            ~/Value of input property 'buildToolsVersion' has changed for task ':(\S+)'/

    private static final String[] tasks = [
            "preDexDebug", "dexDebug", "compileDebugAidl", "compileDebugRenderscript",
            "mergeDebugResources", "processDebugResources",
            "preDexRelease", "dexRelease", "compileReleaseAidl", "compileReleaseRenderscript",
            "mergeReleaseResources", "processReleaseResources"
    ]

    @Rule
    public GradleTestProject fixture = new GradleTestProject()

    @Before
    public void setup() {
        new HelloWorldApp().writeSources(fixture.getSourceDir())
        fixture.getBuildFile() << """
apply plugin: 'com.android.application'

android {
    compileSdkVersion $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
    buildToolsVersion "$GradleTestProject.DEFAULT_BUILD_TOOL_VERSION"
}
"""
    }

    @Test
    public void nullBuild() {
        ByteArrayOutputStream output = new ByteArrayOutputStream()

        fixture.execute("assemble")
        fixture.execute(output, "assemble")

        Set<String> skippedTasks = getTasksMatching(UP_TO_DATE_PATTERN, output)
        for (String task : tasks) {
            assertTrue(String.format("Expecting task %s to be UP-TO-DATE" , task),
                    skippedTasks.contains(task))
        }
    }

    @Test
    public void invalidateBuildTools() {
        ByteArrayOutputStream output = new ByteArrayOutputStream()

        fixture.execute("assemble");

        fixture.getBuildFile() << """
apply plugin: 'com.android.application'

android {
    compileSdkVersion $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
    buildToolsVersion "19.1.0"
}
"""

        fixture.execute(output, "assemble");
        Set<String> affectedTasks = getTasksMatching(INPUT_CHANGED_PATTERN, output)
        for (String task : tasks) {
            assertTrue(String.format("Expecting task %s to be invalidated", task),
                    affectedTasks.contains(task))
        }
    }

    private static Set<String> getTasksMatching(Pattern pattern, ByteArrayOutputStream output) {
        Set<String> result = Sets.newHashSet()
        Matcher matcher = (output.toString("UTF-8") =~ pattern)
        while (matcher.find()) {
            result.add(matcher.group(1))
        }
        result
    }
}
