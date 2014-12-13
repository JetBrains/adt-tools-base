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
import com.android.builder.model.SyncIssue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertNotNull
/**
 * General Model tests
 */
class ModelTest {

    @Rule
    public GradleTestProject project = GradleTestProject.builder().create();

    @Before
    public void setUp() {
        new HelloWorldApp().writeSources(project.testDir)
        project.getBuildFile() << """
apply plugin: 'com.android.application'

android {
    compileSdkVersion $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
    buildToolsVersion "$GradleTestProject.DEFAULT_BUILD_TOOL_VERSION"
}
"""
    }

    @Test
    public void unresolvedDependencies() {
        project.getBuildFile() << """
dependencies {
    compile 'foo:bar:1.2.3'
}
"""
        AndroidProject model = project.getSingleModel()

        Collection<SyncIssue> issues = model.getSyncIssues()
        assertNotNull(issues)

        assertEquals(1, issues.size())

        SyncIssue issue = issues.iterator().next()
        assertNotNull(issue)

        assertEquals(SyncIssue.SEVERITY_ERROR, issue.severity)
        assertEquals(SyncIssue.TYPE_UNRESOLVED_DEPENDENCY, issue.type)
        assertEquals('foo:bar:1.2.3', issue.data)
    }
}
