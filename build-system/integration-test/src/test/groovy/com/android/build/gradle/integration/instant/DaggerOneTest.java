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
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatDex;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.internal.incremental.ColdswapMode;
import com.android.build.gradle.internal.incremental.InstantRunVerifierStatus;
import com.android.builder.model.InstantRun;
import com.android.tools.fd.client.InstantRunArtifact;
import com.android.tools.fd.client.InstantRunArtifactType;
import com.android.tools.fd.client.InstantRunBuildInfo;
import com.google.common.collect.Iterables;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

/**
 * Tests support for Dagger 1 and Instant Run.
 */
public class DaggerOneTest {
    private static final ColdswapMode COLDSWAP_MODE = ColdswapMode.MULTIDEX;

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("daggerOne")
            .create();

    private File mAppModule;

    @Before
    public void setUp() throws IOException {
        Assume.assumeFalse("Disabled until instant run supports Jack", GradleTestProject.USE_JACK);
        mAppModule = project.file("src/main/java/com/android/tests/AppModule.java");
    }

    @Test
    public void normalBuild() throws Exception {
        project.execute("assembleDebug");
    }

    @Test
    public void coldSwap() throws Exception {
        InstantRun instantRunModel = InstantRunTestUtils.doInitialBuild(project, 23, COLDSWAP_MODE);

        InstantRunBuildInfo initialContext = InstantRunTestUtils.loadContext(instantRunModel);
        String startBuildId = initialContext.getTimeStamp();

        makeColdSwapChange();

        project.execute(
                InstantRunTestUtils.getInstantRunArgs(23, COLDSWAP_MODE),
                instantRunModel.getIncrementalAssembleTaskName());

        InstantRunBuildInfo coldSwapContext = InstantRunTestUtils.loadContext(instantRunModel);

        assertThat(coldSwapContext.getVerifierStatus()).named("verifier status")
                .isEqualTo(InstantRunVerifierStatus.METHOD_ADDED.toString());
        assertThat(coldSwapContext.getTimeStamp()).named("build id").isNotEqualTo(startBuildId);

        assertThat(coldSwapContext.getArtifacts()).hasSize(1);
        InstantRunArtifact artifact = Iterables.getOnlyElement(coldSwapContext.getArtifacts());

        assertThat(artifact.type).isEqualTo(InstantRunArtifactType.DEX);
        assertThatDex(artifact.file)
                .hasClass("Lcom/android/tests/AppModule;")
                .that().hasMethod("getMessage");
    }

    @Test
    public void hotSwap() throws Exception {
        InstantRun instantRunModel = InstantRunTestUtils.doInitialBuild(project, 23, COLDSWAP_MODE);

        makeHotSwapChange();

        InstantRunArtifact artifact =
                InstantRunTestUtils.doHotSwapBuild(project, 23, instantRunModel, COLDSWAP_MODE);

        assertThatDex(artifact.file).hasClass("Lcom/android/tests/AppModule$override;");
    }

    private void makeColdSwapChange() throws Exception {
        TestFileUtils.addMethod(
                mAppModule,
                "public String getMessage() { return \"coldswap\"; }");
        TestFileUtils.searchAndReplace(mAppModule, "\"from module\"", "getMessage()");
    }

    private void makeHotSwapChange() throws Exception {
        TestFileUtils.searchAndReplace(mAppModule, "from module", "hotswap");
    }
}
