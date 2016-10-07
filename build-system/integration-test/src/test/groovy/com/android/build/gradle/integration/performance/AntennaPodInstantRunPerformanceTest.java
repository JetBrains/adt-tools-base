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

package com.android.build.gradle.integration.performance;

import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.runner.FilterableParameterized;
import com.android.build.gradle.integration.common.truth.DexFileSubject;
import com.android.build.gradle.integration.common.utils.DexInProcessHelper;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.integration.instant.InstantRunTestUtils;
import com.android.build.gradle.internal.incremental.ColdswapMode;
import com.android.builder.model.InstantRun;
import com.android.builder.model.OptionalCompilationStep;
import com.android.tools.fd.client.InstantRunArtifact;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.common.truth.Expect;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;

@RunWith(FilterableParameterized.class)
public class AntennaPodInstantRunPerformanceTest {

    @Parameterized.Parameters(name = "{0} {1}")
    public static Collection<Object[]> getParameters() {
        return Sets.cartesianProduct(ImmutableList.of(
                EnumSet.of(/*ColdswapMode.MULTIAPK,*/ ColdswapMode.MULTIDEX),
                EnumSet.of(DexInProcess.DEX_IN_PROCESS, DexInProcess.DEX_OUT_OF_PROCESS)
        )).stream()
                .map(List::toArray)
                .collect(Collectors.toList());
    }

    public AntennaPodInstantRunPerformanceTest(
            @NonNull ColdswapMode coldswapMode,
            @NonNull DexInProcess dexInProcess) {
        this.coldswapMode = coldswapMode;
        this.dexInProcess = dexInProcess;
        benchmarkName = "AntennaPod_" + dexInProcess + "_" + coldswapMode;
    }

    private final ColdswapMode coldswapMode;
    private final DexInProcess dexInProcess;
    private final String benchmarkName;

    @Rule
    public Expect expect = Expect.createAndEnableStackTrace();

    @Rule
    public GradleTestProject mainProject =
            GradleTestProject.builder().fromExternalProject("AntennaPod").create();


    private GradleTestProject project;

    @Before
    public void updateToLatestGradleAndSetOptions() throws IOException {
        project = mainProject.getSubproject("AntennaPod");
        TestFileUtils.searchAndReplace(project.getBuildFile(),
                "classpath \"com.android.tools.build:gradle:\\d+.\\d+.\\d+\"",
                "classpath \"com.android.tools.build:gradle:" +
                        GradleTestProject.ANDROID_GRADLE_PLUGIN_VERSION + '"');
        TestFileUtils.searchAndReplace(project.getBuildFile(),
                "jcenter\\(\\)",
                "maven { url '" + System.getenv("CUSTOM_REPO") +  "'} \n"
                        + "        jcenter()");

        if (dexInProcess == DexInProcess.DEX_OUT_OF_PROCESS) {
            DexInProcessHelper.disableDexInProcess(project.file("app/build.gradle"));
        }
    }

    @Test
    public void runBenchmarks() throws Exception {
        project.execute("clean");
        InstantRun instantRunModel = InstantRunTestUtils
                .getInstantRunModel(project.model().getMulti().get(":app"));

        // Warm the daemon up
        for (int i=0; i<4; i++) {
            project.executor()
                    .withInstantRun(23, coldswapMode, OptionalCompilationStep.RESTART_ONLY)
                    .run(":app:assembleDebug");
            project.executor().run("clean");
        }

        project.executor()
                .recordBenchmark(benchmarkName, BenchmarkMode.INSTANT_RUN_FULL_BUILD)
                .withInstantRun(23, coldswapMode, OptionalCompilationStep.RESTART_ONLY)
                .run(":app:assembleDebug");

        // Test the incremental build
        makeHotSwapChange(50);
        project.executor()
                .recordBenchmark(benchmarkName, BenchmarkMode.INSTANT_RUN_BUILD_INC_JAVA)
                .withInstantRun(23, coldswapMode, OptionalCompilationStep.RESTART_ONLY)
                .run(":app:assembleDebug");

        // The second build with retrolambda in this case has a spurious new inner class.
        // Ignore it for the purposes of this performance test.
        for (int i=0; i<3; i++) {
            makeHotSwapChange(i);
            project.executor()
                    .withInstantRun(23, coldswapMode)
                    .run("assembleDebug");
        }
        makeHotSwapChange(100);

        project.executor()
                .recordBenchmark(benchmarkName, BenchmarkMode.INSTANT_RUN_HOT_SWAP)
                .withInstantRun(23, coldswapMode)
                .run("assembleDebug");

        InstantRunArtifact artifact =
                InstantRunTestUtils.getReloadDexArtifact(instantRunModel);

        expect.about(DexFileSubject.FACTORY)
                .that(artifact.file)
                .hasClass("Lde/danoeh/antennapod/activity/MainActivity$override;")
                .that().hasMethod("onStart");


        // Test cold swap
        for (int i=0; i<2; i++) {
            makeColdSwapChange(i);
            project.executor()
                    .withInstantRun(23, coldswapMode)
                    .run(":app:assembleDebug");
        }
        makeColdSwapChange(100);

        project.executor()
                .recordBenchmark(benchmarkName, BenchmarkMode.INSTANT_RUN_COLD_SWAP)
                .withInstantRun(23, coldswapMode)
                .run(":app:assembleDebug");

        List<InstantRunArtifact> coldSwapArtifact =
                InstantRunTestUtils.getCompiledColdSwapChange(instantRunModel, coldswapMode);
    }

    private void makeHotSwapChange(int i) throws IOException {
        TestFileUtils.searchAndReplace(
                project.file(
                        "app/src/main/java/de/danoeh/antennapod/activity/MainActivity.java"),
                "public void onStart\\(\\) \\{",
                "public void onStart() {\n"
                        + "        Log.d(TAG, \"onStart called " + i + "\");");
    }

    private void makeColdSwapChange(int i) throws IOException {
        String newMethodName = "newMethod" + i;
        File mainActivity =  project.file(
                "app/src/main/java/de/danoeh/antennapod/activity/MainActivity.java");
        TestFileUtils.searchAndReplace(
               mainActivity,
                "public void onStart\\(\\) \\{",
                "public void onStart() {\n"
                        + "        " + newMethodName +  "();");
        TestFileUtils.addMethod(
                mainActivity,
                "private void " + newMethodName + "() {\n"
                        + "        Log.d(TAG, \"" + newMethodName + " called\");\n"
                        + "    }\n");
    }

    private enum DexInProcess {
        DEX_IN_PROCESS,
        DEX_OUT_OF_PROCESS,
    }

}
