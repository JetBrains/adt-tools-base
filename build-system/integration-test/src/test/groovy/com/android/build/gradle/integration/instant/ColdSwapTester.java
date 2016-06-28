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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.Packaging;
import com.android.build.gradle.internal.incremental.ColdswapMode;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.incremental.InstantRunVerifierStatus;
import com.android.builder.model.InstantRun;
import com.android.tools.fd.client.InstantRunBuildInfo;

import java.io.File;
import java.util.List;
import java.util.Optional;

/** Helper class for testing cold-swap scenarios. */
class ColdSwapTester {
    private final GradleTestProject mProject;
    private Packaging mPackaging;

    public ColdSwapTester(GradleTestProject project) {
        mProject = project;
        mPackaging = Packaging.DEFAULT;
    }

    ColdSwapTester withPackaging(Packaging packaging) {
        mPackaging = packaging;
        return this;
    }

    void testDalvik(Steps steps) throws Exception {
        doTest(steps, 15, ColdswapMode.AUTO);
    }

    void testMultiDex(Steps steps) throws Exception {
        doTest(steps, 21, ColdswapMode.MULTIDEX);
    }

    private void doTest(Steps steps, int apiLevel, ColdswapMode coldswapMode) throws Exception {
        InstantRun instantRunModel =
                InstantRunTestUtils.doInitialBuild(mProject, apiLevel, coldswapMode);

        steps.checkApk(mProject.getApk("debug"));

        InstantRunBuildInfo initialContext = InstantRunTestUtils.loadContext(instantRunModel);
        String startBuildId = initialContext.getTimeStamp();

        steps.makeChange();

        mProject.executor()
                .withPackaging(mPackaging)
                .withInstantRun(apiLevel, coldswapMode)
                .run("assembleDebug");

        InstantRunBuildContext buildContext =
                InstantRunTestUtils.loadBuildContext(apiLevel, instantRunModel);

        InstantRunBuildContext.Build lastBuild = buildContext.getLastBuild();
        assertNotNull(lastBuild);
        assertThat(lastBuild.getBuildId()).isNotEqualTo(startBuildId);

        Optional<InstantRunVerifierStatus> verifierStatus = lastBuild.getVerifierStatus();
        assertTrue(verifierStatus.isPresent());
        steps.checkVerifierStatus(verifierStatus.get());
        steps.checkArtifacts(lastBuild.getArtifacts());
    }

    void testMultiApk(Steps steps) throws Exception {
        doTest(steps, 24, ColdswapMode.AUTO);
    }

    interface Steps {
        void checkApk(@NonNull File apk) throws Exception;

        void makeChange() throws Exception;

        void checkVerifierStatus(@NonNull InstantRunVerifierStatus status) throws Exception;

        void checkArtifacts(@NonNull List<InstantRunBuildContext.Artifact> artifacts)
                throws Exception;
    }
}
