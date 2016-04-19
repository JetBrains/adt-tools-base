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

package com.android.build.gradle.integration.instant;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.internal.incremental.ColdswapMode;
import com.android.builder.model.OptionalCompilationStep;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Checks that building with resource shrinking works as expected.
 */
public class InstantRunResourceShrinkerTest {

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
                    .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
                    .create();

    @Before
    public void enableResourceShrinking() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\nandroid.buildTypes.debug.shrinkResources = true\n"
                        + "\nandroid.buildTypes.debug.minifyEnabled = true\n");
    }

    @Test
    public void checkPackaging() throws Exception {
        project.execute("clean");
        project.execute(
                InstantRunTestUtils.getInstantRunArgs(
                        23,
                        ColdswapMode.MULTIDEX,
                        OptionalCompilationStep.RESTART_ONLY),
                "assembleDebug");
    }
}
