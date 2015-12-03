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

package com.android.build.gradle.integration.dependencies
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.builder.model.AndroidProject
import com.android.builder.model.SyncIssue
import groovy.transform.CompileStatic
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import static org.junit.Assert.assertTrue
/**
 * test for flavored dependency on a different package.
 */
@CompileStatic
class AppWithNonExistentResolutionStrategyForAarTest {

    @ClassRule
    static public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("projectWithModules")
            .create()

    static Map<String, AndroidProject> models

    @BeforeClass
    static void setUp() {
        project.getBuildFile() <<
                "\n" +
                "subprojects {\n" +
                "    apply from: \"\$rootDir/../commonLocalRepo.gradle\"\n" +
                "}\n"
        project.getSubproject('app').getBuildFile() <<
                "\n" +
                "\n" +
                "dependencies {\n" +
                "    debugCompile project(':library')\n" +
                "    releaseCompile project(':library')\n" +
                "}\n" +
                "\n" +
                "configurations { _debugCompile }\n" +
                "\n" +
                "configurations._debugCompile {\n" +
                "  resolutionStrategy {\n" +
                "    eachDependency { DependencyResolveDetails details ->\n" +
                "      if (details.requested.name == 'jdeferred-android-aar') {\n" +
                "        details.useVersion '-1.-1.-1'\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "}\n" +
                "\n"

        project.getSubproject('library').getBuildFile() << """

dependencies {
    compile 'org.jdeferred:jdeferred-android-aar:1.2.3'
}
"""

        models = project.getAllModelsIgnoringSyncIssues()
    }

    @AfterClass
    static void cleanUp() {
        project = null
        models = null
    }

    @Test
    void "check we received a sync issue"() {
        SyncIssue issue = assertThat(models.get(":app")).hasSingleIssue(
                SyncIssue.SEVERITY_ERROR,
                SyncIssue.TYPE_UNRESOLVED_DEPENDENCY)
        assertTrue(issue.message.contains("org.jdeferred:jdeferred-android-aar:-1.-1.-1"));
    }
}
