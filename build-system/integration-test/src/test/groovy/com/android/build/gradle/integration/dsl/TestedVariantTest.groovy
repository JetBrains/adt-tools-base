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

package com.android.build.gradle.integration.dsl

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Test that the test variant returns what it should.
 */
class TestedVariantTest {

    @Rule
    public GradleTestProject project = GradleTestProject.builder().create()

    @Before
    public void setUp() {
        new HelloWorldApp().write(project.testDir, null)
        project.getBuildFile() << """
apply plugin: 'com.android.application'

android {
  compileSdkVersion $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
  buildToolsVersion "$GradleTestProject.DEFAULT_BUILD_TOOL_VERSION"
}

android.testVariants.all {
  assert it.testedVariant
}
"""
    }

    @Test
    public void testEvaluation() {
        // no need to do a full build, we just want evaluation.
        project.execute(":tasks")
    }
}
