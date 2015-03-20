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
import org.gradle.tooling.BuildException
import org.junit.AfterClass
import org.junit.ClassRule
import org.junit.Test

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import static org.junit.Assert.fail

/**
 * Assemble tests for dependencyChecker.
 */
class DependencyCheckerTest {
    @ClassRule
    static public GradleTestProject httpClientProject = GradleTestProject.builder()
            .fromTestProject("dependencyChecker")
            .captureStdOut(true)
            .create()

    @ClassRule
    static public GradleTestProject minSdkProject = GradleTestProject.builder()
            .fromTestProject("dependencyCheckerComGoogleAndroidJar")
            .captureStdOut(true)
            .captureStdErr(true)
            .create()

    @AfterClass
    static void cleanUp() {
        httpClientProject = null
        minSdkProject = null
    }

    @Test
    public void "org.apache.httpcomponents is ignored"() throws Exception {
        httpClientProject.execute("clean", "assembleDebug")
        assertThat(httpClientProject.stdout.toString())
                .contains("Dependency org.apache.httpcomponents:httpclient:4.1.1 is ignored")
    }

    @Test
    void lint() {
        httpClientProject.execute("lint")
    }

    /**
     * See {@link PrepareDependenciesTask} for the expected output.
     */
    @Test
    public void "com.google.android API version is checked"() throws Exception {
        try {
            minSdkProject.execute("clean", "assemble")
            fail("should throw")
        } catch (BuildException e) {
            // expected.
        }

        String stdOut = minSdkProject.stderr.toString()
        assertThat(stdOut).contains("corresponds to API level 15")
        // Picked up from com.google.android
        assertThat(stdOut).contains("which is 14") // Declared in Gradle.
        assertThat(stdOut).contains("com.google.android")
    }
}
