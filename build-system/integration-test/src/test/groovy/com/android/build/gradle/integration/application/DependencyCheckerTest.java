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

package com.android.build.gradle.integration.application;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static org.junit.Assert.fail;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;

import org.gradle.tooling.BuildException;
import org.junit.Rule;
import org.junit.Test;

/**
 * Assemble tests for dependencyChecker.
 */
public class DependencyCheckerTest {

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
            .create();

    @Test
    public void httpComponents() throws Exception {
        TestFileUtils.appendToFile(project.getBuildFile(), "\n"
                + "dependencies.compile 'org.apache.httpcomponents:httpclient:4.1.1'\n");

        project.execute("clean", "assembleDebug");
        assertThat(project.getStdout())
                .contains("Dependency org.apache.httpcomponents:httpclient:4.1.1 is ignored");
    }


    /**
     * See {@link com.android.build.gradle.internal.tasks.PrepareDependenciesTask} for the expected
     * output.
     */
    @Test
    public void comGoogleAndroid() throws Exception {
        TestFileUtils.appendToFile(project.getBuildFile(), "\n"
                + "// Lower than com.google.android:android:4.1.14\n"
                + "android.defaultConfig.minSdkVersion 14\n"
                + "\n"
                + "repositories {\n"
                + "    mavenCentral()\n"
                + "}\n"
                + "\n"
                + "dependencies {\n"
                + "    compile 'com.google.android:android:4.1.1.4'\n"
                + "}");

        try {
            project.execute("clean", "assemble");
            fail("should throw");
        } catch (BuildException e) {
            // expected.
        }

        String stderr = project.getStderr();
        assertThat(stderr).contains("corresponds to API level 15");
        // Picked up from com.google.android
        assertThat(stderr).contains("which is 14"); // Declared in Gradle.
        assertThat(stderr).contains("com.google.android");
    }
}
