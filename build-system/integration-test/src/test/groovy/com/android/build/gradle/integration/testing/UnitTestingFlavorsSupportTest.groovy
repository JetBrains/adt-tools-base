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

package com.android.build.gradle.integration.testing

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.google.common.base.Throwables
import org.gradle.tooling.BuildException
import org.junit.ClassRule
import org.junit.Test

import static com.android.build.gradle.integration.testing.JUnitResults.Outcome.FAILED
import static com.android.build.gradle.integration.testing.JUnitResults.Outcome.PASSED
import static org.junit.Assert.fail

/**
 * Meta-level tests for the app-level unit testing support.
 */
class UnitTestingFlavorsSupportTest {
    @ClassRule
    static public GradleTestProject flavorsProject = GradleTestProject.builder()
            .fromTestProject("unitTestingFlavors")
            .create()

    @Test
    public void 'Tests for a given flavor are only compiled against the flavor'() throws Exception {
        flavorsProject.execute("clean", "testBuildsPassesDebug")

        def results = new JUnitResults(
                flavorsProject.file("build/test-results/buildsPassesDebug/TEST-com.android.tests.PassingTest.xml"))

        assert results.outcome("referenceFlavorSpecificCode") == PASSED

        try {
            flavorsProject.execute("testDoesntBuildPassesDebug")
            fail()
        } catch (BuildException e) {
            assert Throwables.getRootCause(e)
                    .exceptionClassName
                    .endsWith("CompilationFailedException")
        }
    }

    @Test
    public void 'Task for a given flavor only runs the correct tests'() throws Exception {
        flavorsProject.execute("clean", "testBuildsPassesDebug")

        try {
            flavorsProject.execute("testBuildsFailsDebug")
            fail()
        } catch (BuildException e) {
            assert Throwables.getRootCause(e).message.startsWith("There were failing tests.")

            def results = new JUnitResults(
                    flavorsProject.file("build/test-results/buildsFailsDebug/TEST-com.android.tests.FailingTest.xml"))
            assert results.outcome("failingTest") == FAILED
        }
    }
}
