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

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;

import com.android.build.gradle.OptionalCompilationStep;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.internal.incremental.ColdswapMode;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.incremental.InstantRunVerifierStatus;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.InstantRun;
import com.android.tools.fd.client.InstantRunBuildInfo;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.io.Files;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

/**
 * Instant run test for changing Java resources.
 */
public class JavaResourcesTest {
    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
            .create();

    private File resource;

    @Before
    public void setUp() throws IOException {
        resource = project.file("src/main/resources/foo.txt");
        FileUtils.createFile(resource, "foo");
    }

    @Test
    public void testChangingJavaResources() throws Exception {
        project.execute(
                InstantRunTestUtils.getInstantRunArgs(
                        21,
                        ColdswapMode.DEFAULT,
                        OptionalCompilationStep.RESTART_ONLY),
                "assembleDebug");
        AndroidProject model = project.getSingleModel();

        assertThat(project.getApk("debug")).exists();
        Files.write("bar", resource, Charsets.UTF_8);

        InstantRun instantRunModel = InstantRunTestUtils.getInstantRunModel(model);
        project.execute(
                InstantRunTestUtils.getInstantRunArgs(21, ColdswapMode.DEFAULT),
                instantRunModel.getIncrementalAssembleTaskName());
        InstantRunBuildInfo context = InstantRunTestUtils.loadContext(instantRunModel);
        assertThat(context.getVerifierStatus()).isEqualTo(
                InstantRunVerifierStatus.JAVA_RESOURCES_CHANGED.toString());
        assertThat(context.getArtifacts()).hasSize(0);
    }

    @Test
    public void testChangingJavaSources() throws Exception {
        project.execute(
                InstantRunTestUtils.getInstantRunArgs(
                        21,
                        ColdswapMode.DEFAULT,
                        OptionalCompilationStep.RESTART_ONLY),
                "assembleDebug");
        AndroidProject model = project.getSingleModel();
        assertThat(project.getApk("debug")).exists();

        Files.write("bar", resource, Charsets.UTF_8);

        InstantRun instantRunModel = InstantRunTestUtils.getInstantRunModel(model);
        project.execute(
                InstantRunTestUtils.getInstantRunArgs(21, ColdswapMode.DEFAULT),
                instantRunModel.getIncrementalAssembleTaskName());
        InstantRunBuildInfo context = InstantRunTestUtils.loadContext(instantRunModel);
        assertThat(context.getVerifierStatus()).isEqualTo(
                InstantRunVerifierStatus.JAVA_RESOURCES_CHANGED.toString());
        assertThat(context.getArtifacts()).hasSize(0);
    }
}
