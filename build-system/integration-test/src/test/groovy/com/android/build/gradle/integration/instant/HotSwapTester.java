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
import com.android.build.gradle.OptionalCompilationStep;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.Logcat;
import com.android.build.gradle.integration.common.utils.DeviceHelper;
import com.android.build.gradle.internal.incremental.ColdswapMode;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.InstantRun;
import com.android.ddmlib.IDevice;
import com.android.ide.common.packaging.PackagingUtils;
import com.android.sdklib.AndroidVersion;
import com.android.tools.fd.client.AppState;
import com.android.tools.fd.client.InstantRunArtifact;
import com.android.tools.fd.client.InstantRunBuildInfo;
import com.android.tools.fd.client.InstantRunClient;
import com.android.tools.fd.client.InstantRunClient.FileTransfer;
import com.android.tools.fd.client.UpdateMode;
import com.android.tools.fd.client.UserFeedback;
import com.android.utils.ILogger;
import com.google.common.collect.ImmutableList;

import org.junit.Assume;
import org.mockito.Mockito;

/**
 * Helper for automating HotSwap testing.
 */
class HotSwapTester {
    private HotSwapTester() {}

    public static void run(
            @NonNull GradleTestProject project,
            @NonNull String packageName,
            @NonNull String activityName,
            @NonNull String logTag,
            @NonNull Logcat logcat,
            @NonNull Steps steps)  throws Exception {
        IDevice device = DeviceHelper.getIDevice();
        // TODO: Generalize apk deployment to any compatible device.
        Assume.assumeTrue(device.getVersion().equals(new AndroidVersion(23, null)));
        try {
            logcat.start(device, logTag);

            // Open project in simulated IDE
            AndroidProject model = project.getSingleModel();
            long token = PackagingUtils.computeApplicationHash(model.getBuildFolder());
            InstantRun instantRunModel = InstantRunTestUtils.getInstantRunModel(model);

            // Run first time on device
            project.execute(
                    InstantRunTestUtils.getInstantRunArgs(
                            device, ColdswapMode.MULTIDEX, OptionalCompilationStep.RESTART_ONLY),
                    "assembleDebug");

            // Deploy to device
            InstantRunBuildInfo info = InstantRunTestUtils.loadContext(instantRunModel);
            InstantRunTestUtils.doInstall(device, info.getArtifacts());

            logcat.clearFiltered();

            // Run app
            InstantRunTestUtils.unlockDevice(device);

            InstantRunTestUtils.runApp(
                    device,
                    String.format("%s/.%s", packageName, activityName));

            UserFeedback userFeedback = Mockito.mock(UserFeedback.class);
            ILogger iLogger = Mockito.mock(ILogger.class);

            //Connect to device
            InstantRunClient client =
                    new InstantRunClient(packageName, userFeedback, iLogger, token, 8125);

            // Give the app a chance to start
            Thread.sleep(2000); // TODO: Is there a way to determine that the app is ready?

            // Check the app is running
            assertThat(client.getAppState(device)).isEqualTo(AppState.FOREGROUND);

            steps.verifyOriginalCode(client, logcat, device);

            steps.makeChange();

            // Now build the hot swap patch.
            project.execute(InstantRunTestUtils.getInstantRunArgs(device, ColdswapMode.MULTIDEX),
                    instantRunModel.getIncrementalAssembleTaskName());

            InstantRunArtifact artifact =
                    InstantRunTestUtils.getCompiledHotSwapCompatibleChange(instantRunModel);

            FileTransfer fileTransfer = FileTransfer.createHotswapPatch(artifact.file);

            logcat.clearFiltered();

            client.pushPatches(
                    device,
                    info.getTimeStamp(),
                    ImmutableList.of(fileTransfer.getPatch()),
                    UpdateMode.HOT_SWAP,
                    false /*restartActivity*/,
                    true /*showToast*/);

            Mockito.verify(userFeedback).notifyEnd(UpdateMode.HOT_SWAP);
            Mockito.verifyNoMoreInteractions(userFeedback);

            assertThat(client.getAppState(device)).isEqualTo(AppState.FOREGROUND);

            steps.verifyNewCode(client, logcat, device);
        } finally {
            try {
                // Clean up
                device.uninstallPackage(packageName);
            } catch (Exception e) {
                // No point hiding the original exception.
            }
        }
    }

    interface Steps {
        void verifyOriginalCode(
                @NonNull InstantRunClient client,
                @NonNull Logcat logcat,
                @NonNull IDevice device) throws Exception;

        void makeChange() throws Exception;

        void verifyNewCode(
                @NonNull InstantRunClient client,
                @NonNull Logcat logcat,
                @NonNull IDevice device) throws Exception;
    }
}
