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
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.internal.incremental.ColdswapMode;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.InstantRun;
import com.android.builder.model.OptionalCompilationStep;
import com.android.tools.fd.client.InstantRunArtifact;
import com.android.tools.fd.client.InstantRunArtifactType;
import com.android.tools.fd.client.InstantRunBuildInfo;
import com.android.utils.Pair;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Test instant run with split.
 */
public class InstantRunFullSplitApkTest {

    @Rule
    public GradleTestProject mProject = GradleTestProject.builder()
            .fromTestApp(HelloWorldApp.forPlugin("com.android.application")).create();

    private InstantRun instantRunModel;

    @Before
    public void getModel() throws IOException {
        TestFileUtils.appendToFile(mProject.getBuildFile(),
                "android {\n"
                        + "    splits {\n"
                        + "        abi {\n"
                        + "            enable true\n"
                        + "            reset()\n"
                        + "            include 'x86', 'armeabi-v7a'\n"
                        + "            universalApk false\n"
                        + "        }\n"
                        + "    }\n"
                        + "}");


        mProject.execute("clean");
        AndroidProject model = mProject.model().getSingle();
        instantRunModel = InstantRunTestUtils.getInstantRunModel(model);
    }

    @Test
    public void testSplit() throws Exception {
        mProject.executor()
                .withInstantRun(
                        24,
                        ColdswapMode.AUTO,
                        OptionalCompilationStep.FULL_APK)
                .withProperty(AndroidProject.PROPERTY_BUILD_ABI, "armeabi-v7a")
                .run("assembleDebug");
        InstantRunBuildInfo initialContext = InstantRunTestUtils.loadContext(instantRunModel);
        List<InstantRunArtifact> artifacts = initialContext.getArtifacts();

        InstantRunArtifact main = artifacts.stream()
                .filter(artifact -> artifact.type == InstantRunArtifactType.SPLIT_MAIN)
                .findFirst().orElseThrow(() -> new AssertionError("Main artifact not found"));

        assertThat(main.file).hasName("project-armeabi-v7a-debug.apk");
    }
}
