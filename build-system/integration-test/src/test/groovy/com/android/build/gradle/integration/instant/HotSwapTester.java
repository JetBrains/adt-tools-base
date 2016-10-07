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

import static com.android.build.gradle.integration.common.utils.AndroidVersionMatcher.thatUsesArt;
import static org.junit.Assert.assertEquals;

import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.UninstallOnClose;
import com.android.build.gradle.integration.common.fixture.Adb;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.Logcat;
import com.android.build.gradle.integration.common.fixture.Packaging;
import com.android.build.gradle.internal.incremental.ColdswapMode;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.InstantRun;
import com.android.builder.packaging.PackagingUtils;
import com.android.ddmlib.IDevice;
import com.android.tools.fd.client.InstantRunArtifact;
import com.android.tools.fd.client.InstantRunBuildInfo;
import com.android.tools.fd.client.InstantRunClient;
import com.android.tools.fd.client.InstantRunClient.FileTransfer;
import com.android.tools.fd.client.UpdateMode;
import com.android.utils.ILogger;
import com.google.common.collect.ImmutableList;

import org.mockito.Mockito;

import java.io.Closeable;

/**
 * Helper for automating HotSwap testing.
 */
public class HotSwapTester {
    private HotSwapTester() {}

    public static void run(
            @NonNull GradleTestProject project,
            @NonNull Packaging packaging,
            @NonNull String packageName,
            @NonNull String activityName,
            @NonNull String logTag,
            @NonNull Adb adb,
            @NonNull Logcat logcat,
            @NonNull Steps steps)  throws Exception {
        IDevice device = adb.getDevice(thatUsesArt());
        run(project, packaging, packageName, activityName, logTag, device, logcat, steps);
    }

    public static void run(
            @NonNull GradleTestProject project,
            @NonNull Packaging packaging,
            @NonNull String packageName,
            @NonNull String activityName,
            @NonNull String logTag,
            @NonNull IDevice device,
            @NonNull Logcat logcat,
            @NonNull Steps steps)  throws Exception {
        try (Closeable ignored = new UninstallOnClose(device, packageName)) {
            logcat.start(device, logTag);

            // Open project in simulated IDE
            AndroidProject model = project.model().getSingle();
            long token = PackagingUtils.computeApplicationHash(model.getBuildFolder());
            InstantRun instantRunModel = InstantRunTestUtils.getInstantRunModel(model);

            // Run first time on device
            InstantRunTestUtils.doInitialBuild(
                    project, packaging, device.getVersion().getApiLevel(), ColdswapMode.MULTIDEX);

            // Deploy to device
            InstantRunBuildInfo info = InstantRunTestUtils.loadContext(instantRunModel);
            InstantRunTestUtils.doInstall(device, info.getArtifacts());

            logcat.clearFiltered();

            // Run app
            InstantRunTestUtils.unlockDevice(device);

            InstantRunTestUtils.runApp(
                    device,
                    String.format("%s/.%s", packageName, activityName));

            ILogger iLogger = Mockito.mock(ILogger.class);

            //Connect to device
            InstantRunClient client =
                    new InstantRunClient(packageName, iLogger, token, 8125);

            // Give the app a chance to start
            InstantRunTestUtils.waitForAppStart(client, device);

            steps.verifyOriginalCode(client, logcat, device);

            steps.makeChange();

            // Now build the hot swap patch.
            project.executor()
                    .withPackaging(packaging)
                    .withInstantRun(device.getVersion().getApiLevel(), ColdswapMode.MULTIDEX)
                    .run("assembleDebug");

            InstantRunArtifact artifact =
                    InstantRunTestUtils.getReloadDexArtifact(instantRunModel);

            FileTransfer fileTransfer = FileTransfer.createHotswapPatch(artifact.file);

            logcat.clearFiltered();

            UpdateMode updateMode = client.pushPatches(
                    device,
                    info.getTimeStamp(),
                    ImmutableList.of(fileTransfer.getPatch()),
                    UpdateMode.HOT_SWAP,
                    false /*restartActivity*/,
                    true /*showToast*/);

            assertEquals(UpdateMode.HOT_SWAP, updateMode);

            InstantRunTestUtils.waitForAppStart(client, device);

            steps.verifyNewCode(client, logcat, device);
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
