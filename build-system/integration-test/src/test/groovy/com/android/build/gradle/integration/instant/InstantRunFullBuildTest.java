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

import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.internal.incremental.ColdswapMode;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.InstantRun;
import com.android.builder.model.OptionalCompilationStep;
import com.android.tools.fd.client.InstantRunArtifact;
import com.android.tools.fd.client.InstantRunArtifactType;
import com.android.tools.fd.client.InstantRunBuildInfo;
import com.android.utils.Pair;
import com.google.common.truth.Expect;
import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class InstantRunFullBuildTest {

    @Rule
    public GradleTestProject mProject = GradleTestProject.builder()
            .fromTestApp(HelloWorldApp.forPlugin("com.android.application")).create();
    @Rule
    public Expect expect = Expect.createAndEnableStackTrace();

    private InstantRun instantRunModel;

    @Before
    public void getModel() {
        mProject.execute("clean");
        AndroidProject model = mProject.model().getSingle();
        instantRunModel = InstantRunTestUtils.getInstantRunModel(model);
    }

    @Test
    public void testDalvik() throws Exception {
        doTest(19);
    }

    @Test
    public void testMultiDex() throws Exception {
        doTest(23);
    }

    @Test
    public void testMultiApk() throws Exception {
        doTest(24);
    }

    private void doTest(int featureLevel) throws Exception {
        mProject.executor()
                .withInstantRun(
                        featureLevel,
                        ColdswapMode.AUTO,
                        OptionalCompilationStep.FULL_APK)
                .run("assembleDebug");
        InstantRunBuildInfo initialContext = InstantRunTestUtils.loadContext(instantRunModel);

        mProject.executor()
                .withInstantRun(
                        featureLevel,
                        ColdswapMode.AUTO,
                        OptionalCompilationStep.FULL_APK)
                .run("assembleDebug");
        InstantRunBuildInfo secondContext = InstantRunTestUtils.loadContext(instantRunModel);
        assertThat(secondContext.getTimeStamp()).isNotEqualTo(initialContext.getTimeStamp());

        assertThat(toSet(secondContext.getArtifacts()))
                .containsExactlyElementsIn(toSet(initialContext.getArtifacts()));
    }

    @NonNull
    private static Set<Pair<InstantRunArtifactType, File>> toSet(
            @NonNull List<InstantRunArtifact> artifacts) {
        return artifacts.stream()
                .map(artifact -> Pair.of(artifact.type, artifact.file))
                .collect(Collectors.toSet());
    }
}
