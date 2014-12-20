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

package com.android.build.gradle.integration.dependencies
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.builder.model.AndroidProject
import com.android.builder.model.SyncIssue
import groovy.transform.CompileStatic
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertNotNull
/**
 * test for package (apk) local aar in app
 */
@CompileStatic
class AppWithPackageLocalAarTest {

    @ClassRule
    static public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("projectWithLocalDeps")
            .create()
    static AndroidProject model

    @BeforeClass
    static void setUp() {
        project.getBuildFile() << """
apply plugin: 'com.android.application'

android {
    compileSdkVersion $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
    buildToolsVersion "$GradleTestProject.DEFAULT_BUILD_TOOL_VERSION"
}

dependencies {
    apk files('libs/baseLib-1.0.aar')
}
"""

        model = project.getSingleModel()
    }

    @AfterClass
    static void cleanUp() {
        project = null
        model = null
    }

    @Test
    void "check model failed to load"() {
        Collection<SyncIssue> issues = model.getSyncIssues()

        assertNotNull(issues)
        assertEquals(1, issues.size())

        SyncIssue issue = issues.iterator().next()
        assertNotNull(issue)
        assertEquals(SyncIssue.SEVERITY_ERROR, issue.getSeverity())
        assertEquals(SyncIssue.TYPE_NON_JAR_LOCAL_DEP, issue.getType())
        assertEquals("baseLib-1.0.aar", new File(issue.getData()).getName())
    }
}
