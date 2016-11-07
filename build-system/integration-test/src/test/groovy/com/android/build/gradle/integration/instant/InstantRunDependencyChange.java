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

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.Packaging;
import com.android.build.gradle.integration.common.fixture.app.AndroidTestApp;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.internal.incremental.ColdswapMode;
import com.android.build.gradle.internal.incremental.InstantRunVerifierStatus;
import com.android.builder.model.InstantRun;
import com.android.builder.model.OptionalCompilationStep;
import com.android.tools.fd.client.InstantRunBuildInfo;
import com.google.common.base.Charsets;
import com.google.common.io.Files;

import org.junit.Rule;
import org.junit.Test;

/**
 * Integration tests for adding/removing dependencies in the InstantRun context.
 */
public class InstantRunDependencyChange {

    private static final AndroidTestApp TEST_APP =
            HelloWorldApp.forPlugin("com.android.application");

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(TEST_APP)
                    .create();

    @Test
    public void addingDependency() throws Exception {

        InstantRun instantRunModel = InstantRunTestUtils.getInstantRunModel(
                project.model().getSingle());

        project.executor()
                .withInstantRun(23, ColdswapMode.AUTO, OptionalCompilationStep.FULL_APK)
                .run("assembleDebug");

        // add a dependency on the project build.
        Files.append("dependencies {\n"
                        + "    compile 'com.google.guava:guava:17.0'\n"
                        + "}",
                project.file("build.gradle"),
                Charsets.UTF_8);

        // now perform an incremental build.
        project.executor()
                .withInstantRun(23, ColdswapMode.AUTO)
                .run("assembleDebug");

        // check that adding a new dependency triggered a coldswap build.
        InstantRunBuildInfo context = InstantRunTestUtils.loadContext(instantRunModel);
        assertThat(context.getVerifierStatus()).isEqualTo(
                InstantRunVerifierStatus.DEPENDENCY_CHANGED.toString());
    }

    @Test
    public void changingDependency() throws Exception {

        InstantRun instantRunModel = InstantRunTestUtils.getInstantRunModel(
                project.model().getSingle());

        String originalBuildFile =
                Files.asCharSource(project.file("build.gradle"), Charsets.UTF_8).read();

        // add the dependency and do a clean build.
        Files.write(originalBuildFile + "dependencies {\n"
                        + "    compile 'com.google.guava:guava:17.0'\n"
                        + "}",
                project.file("build.gradle"),
                Charsets.UTF_8);

        project.execute("clean");
        project.executor()
                .withInstantRun(23, ColdswapMode.AUTO, OptionalCompilationStep.FULL_APK)
                .run("assembleDebug");

        // change the dependency version on the project build.
        Files.write(originalBuildFile + "dependencies {\n"
                        + "    compile 'com.google.guava:guava:18.0'\n"
                        + "}",
                project.file("build.gradle"),
                Charsets.UTF_8);

        // now perform an incremental build.
        project.executor()
                .withInstantRun(23, ColdswapMode.AUTO)
                .run("assembleDebug");

        // check that changing a dependency triggered a coldswap build.
        InstantRunBuildInfo context = InstantRunTestUtils.loadContext(instantRunModel);
        assertThat(context.getVerifierStatus()).isEqualTo(
                InstantRunVerifierStatus.DEPENDENCY_CHANGED.toString());
    }
}
