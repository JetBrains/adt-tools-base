/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.SyncIssue;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;

public class Java8WithoutJackSyncIssueTest {

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestApp(HelloWorldApp.noBuildFile())
            .create();

    @Before
    public void setUp() throws IOException {
        TestFileUtils.appendToFile(project.getBuildFile(), "\n"
                + "apply plugin: 'com.android.application'\n"
                + "android {\n"
                + "    compileSdkVersion " + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION + "\n"
                + "    buildToolsVersion '" + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION + "'\n"
                + "    compileOptions {\n"
                + "        targetCompatibility 1.8\n"
                + "        sourceCompatibility 1.8\n"
                + "    }\n"
                + "}\n");
    }

    @Test
    public void testMismatchDependencyErrorIsInTheModel() {
        // Query the model to get the mismatch dep sync error.
        AndroidProject model = project.model().ignoreSyncIssues().getSingle();

        assertThat(model).hasIssue(
                SyncIssue.SEVERITY_ERROR,
                SyncIssue.TYPE_JACK_REQUIRED_FOR_JAVA_8_LANGUAGE_FEATURES);
    }
}
