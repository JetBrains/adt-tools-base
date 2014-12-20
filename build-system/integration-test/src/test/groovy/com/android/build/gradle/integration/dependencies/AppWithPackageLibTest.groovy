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
 * test for package library in app
 */
@CompileStatic
class AppWithPackageLibTest {

    @ClassRule
    static public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("projectWithModules")
            .create()
    static Map<String, AndroidProject> models

    @BeforeClass
    static void setUp() {
        project.getSubproject('app').getBuildFile() << """

dependencies {
    apk project(':library')
}
"""
        models = project.getAllModels()
    }

    @AfterClass
    static void cleanUp() {
        project = null
        models = null
    }

    @Test
    void "check model failed to load"() {
        AndroidProject model = models.get(':app')
        Collection<SyncIssue> issues = model.getSyncIssues()

        assertNotNull(issues)
        assertEquals(1, issues.size())

        SyncIssue issue = issues.iterator().next()
        assertNotNull(issue)
        assertEquals(SyncIssue.SEVERITY_ERROR, issue.getSeverity())
        assertEquals(SyncIssue.TYPE_NON_JAR_PACKAGE_DEP, issue.getType())
        assertEquals("project:library:aar:unspecified", issue.getData())
    }
}
