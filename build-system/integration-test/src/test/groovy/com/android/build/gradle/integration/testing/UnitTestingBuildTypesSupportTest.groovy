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

package com.android.build.gradle.integration.testing
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.google.common.base.Throwables
import org.gradle.tooling.BuildException
import org.junit.ClassRule
import org.junit.Test

import static com.android.build.gradle.integration.testing.JUnitResults.Outcome.PASSED
import static org.junit.Assert.fail
/**
 * Meta-level tests for the app-level unit testing support.
 */
class UnitTestingBuildTypesSupportTest {
    @ClassRule
    static public GradleTestProject flavorsProject = GradleTestProject.builder()
            .fromTestProject("unitTestingBuildTypes")
            .create()

    @Test
    public void 'Tests for a given build type are only compiled against the build type'() throws Exception {
        flavorsProject.execute("clean", "testDebug")

        def results = new JUnitResults(
                flavorsProject.file("build/test-results/debug/TEST-com.android.tests.UnitTest.xml"))

        assert results.outcome("referenceProductionCode") == PASSED
        assert results.outcome("resourcesOnClasspath") == PASSED
        assert results.outcome("useDebugOnlyDependency") == PASSED

        flavorsProject.execute("clean", "testBuildTypeWithResource")
        results = new JUnitResults(
                flavorsProject.file("build/test-results/buildTypeWithResource/TEST-com.android.tests.UnitTest.xml"))
        assert results.outcome("javaResourcesOnClasspath") == PASSED
        assert results.outcome("prodJavaResourcesOnClasspath") == PASSED

        try {
            // Tests for release try to compile against a debug-only class.
            flavorsProject.execute("testRelease")
            fail()
        } catch (BuildException e) {
            assert Throwables.getRootCause(e)
                    .exceptionClassName
                    .endsWith("CompilationFailedException")
        }
    }
}
