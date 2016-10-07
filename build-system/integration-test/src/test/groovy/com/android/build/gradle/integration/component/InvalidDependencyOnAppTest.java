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

package com.android.build.gradle.integration.component;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.google.common.collect.ImmutableMap;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Checks that an app cannot depend on another app.
 */
public class InvalidDependencyOnAppTest {

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestApp(
                    new MultiModuleTestProject(ImmutableMap.of(
                            ":app", HelloWorldApp.forPlugin("com.android.application"),
                            ":dependency-app", HelloWorldApp.forPlugin("com.android.application"))))
            .create();

    @Before
    public void addBrokenDependency() throws Exception {
        TestFileUtils.appendToFile(
                project.getSubproject(":app").getBuildFile(),
                "dependencies { compile project(':dependency-app') } ");
    }

    @Test
    public void testBuildFails() throws Exception {
        GradleBuildResult result = project.executor().expectFailure().run("assembleDebug");
        assertThat(result.getStdout()).contains("resolves to an APK");
    }
}
