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
import org.junit.Before
import org.junit.Rule
import org.junit.Test

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertTrue
import static org.junit.Assert.fail

/**
 * Tests the handling of test dependencies.
 */
class TestWithMismatchDep {

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("testDependency")
            .captureStdOut(true)
            .captureStdErr(true)
            .create()

    @Before
    public void setUp() {
        project.getBuildFile() << """
dependencies {
    androidTestCompile 'com.google.guava:guava:15.0'
}
"""
    }

    private final static String ERROR_MSG = 'Conflict with dependency \'com.google.guava:guava\'. Resolved versions for app (17.0) and test app (15.0) differ.'

    @Test
    public void "Test mismatch dependency error is in model"() {
        // Query the model to get the mismatch dep sync error.
        AndroidProject model = project.getSingleModelIgnoringSyncIssues()

        assertThat(model).issues().hasSingleIssue(
                SyncIssue.SEVERITY_ERROR,
                SyncIssue.TYPE_MISMATCH_DEP,
                'com.google.guava:guava',
                ERROR_MSG)
    }

    @Test
    public void "Test mismatch dependency breaks test build"() {
        // want to check the log, so can't use Junit's expected exception mechanism.

        try {
            project.execute("assembleAndroidTest")
            fail("build succeeded");
        } catch (Exception e) {
            Throwable t = e
            while (t.getCause() != null) {
                t = t.getCause()
            }

            // looks like we can't actually test the instance t against GradleException
            // due to it coming through the tooling API from a different class loader.
            assertEquals("org.gradle.api.GradleException", t.getClass().canonicalName)
            assertEquals("Dependency Error. See console for details.", t.getMessage())
        }

        // check there is a version of the error, after the task name:
        ByteArrayOutputStream stderr = project.stderr
        String log = stderr.toString()

        assertTrue("stderr contains error", log.contains(ERROR_MSG))
    }

    public void "Test mismatch dependency doesn't break debug build"() {
        project.execute("assembleDebug")

        // check there is a log output
        ByteArrayOutputStream out = project.stdout
        String log = out.toString()

        assertTrue(log.contains(ERROR_MSG))

    }

    @Test
    public void "Test mismatch depenency can run non-build task"() {
        // it's important to be able to run the dependencies task to
        // investigate dependency issues.
        project.execute("dependencies")

        // check there is a log output
        ByteArrayOutputStream out = project.stdout
        String log = out.toString()

        assertTrue("stdout contains warning", log.contains(ERROR_MSG))
    }
}
