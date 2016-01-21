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
import groovy.transform.CompileStatic
import org.gradle.tooling.BuildException
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.runners.Enclosed
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import static org.junit.Assert.fail
/**
 * Assemble tests for dependencyChecker.
 */
@CompileStatic
@RunWith(Enclosed)
class DependencyCheckerTest {

    @RunWith(JUnit4)
    @CompileStatic
    public static class HttpClient {
        @Rule
        public GradleTestProject httpClientProject = GradleTestProject.builder()
                .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
                .create()

        @Test
        public void httpComponents() throws Exception {
            httpClientProject.buildFile <<
                    "dependencies.compile 'org.apache.httpcomponents:httpclient:4.1.1'"

            httpClientProject.execute("clean", "assembleDebug")
            assertThat(httpClientProject.getStdout())
                    .contains("Dependency org.apache.httpcomponents:httpclient:4.1.1 is ignored")
        }
    }

    @RunWith(JUnit4)
    @CompileStatic
    public static class ComGoogleAndroid {
        @Rule
        public GradleTestProject minSdkProject = GradleTestProject.builder()
                .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
                .create()

        /**
         * See {@link com.android.build.gradle.internal.tasks.PrepareDependenciesTask} for the
         * expected output.
         */
        @Test
        public void comGoogleAndroid() throws Exception {
            minSdkProject.buildFile << """
                // Lower than com.google.android:android:4.1.14
                android.defaultConfig.minSdkVersion 14

                repositories {
                    mavenCentral()
                }

                dependencies {
                    compile 'com.google.android:android:4.1.1.4'
                }
                """

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
}
