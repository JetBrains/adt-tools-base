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

import static com.android.build.gradle.integration.common.utils.DeviceHelper.DEFAULT_ADB_TIMEOUT_MSEC;
import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.android.annotations.NonNull;
import com.android.build.gradle.OptionalCompilationStep;
import com.android.build.gradle.integration.common.utils.DeviceHelper;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.InstantRun;
import com.android.builder.model.Variant;
import com.android.builder.testing.api.DeviceException;
import com.android.ddmlib.CollectingOutputReceiver;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.IShellOutputReceiver;
import com.android.ddmlib.InstallException;
import com.android.sdklib.AndroidVersion;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

final class InstantRunTestUtils {

    @NonNull
    static InstantRunBuildContext loadContext(@NonNull InstantRun instantRunModel)
            throws Exception {
        InstantRunBuildContext context = new InstantRunBuildContext();
        context.loadFromXmlFile(instantRunModel.getInfoFile());
        return context;
    }

    @NonNull
    static InstantRun getInstantRunModel(@NonNull AndroidProject project) {
        Collection<Variant> variants = project.getVariants();
        for (Variant variant : variants) {
            if ("debug".equals(variant.getName())) {
                return variant.getMainArtifact().getInstantRun();
            }
        }
        throw new AssertionError("Could not find debug variant.");
    }

    @NonNull
    static List<String> getInstantRunArgs(OptionalCompilationStep... flags) {
        return ImmutableList.of(buildOptionalCompilationStepsProperty(flags));
    }

    @NonNull
    static List<String> getInstantRunArgs(int apiLevel,
            @NonNull OptionalCompilationStep... flags) {
        return getInstantRunArgs(new AndroidVersion(apiLevel, null), flags);
    }

    static List<String> getInstantRunArgs(@NonNull IDevice device,
            @NonNull OptionalCompilationStep... flags) {
        return getInstantRunArgs(device.getVersion(), flags);
    }

    @NonNull
    static List<String> getInstantRunArgs(@NonNull AndroidVersion androidVersion,
            @NonNull OptionalCompilationStep... flags) {
        String version =
                String.format("-Pandroid.injected.build.api=%s", androidVersion.getApiString());
        return ImmutableList.of(buildOptionalCompilationStepsProperty(flags), version);
    }

    @NonNull
    private static String buildOptionalCompilationStepsProperty(
            @NonNull OptionalCompilationStep[] optionalCompilationSteps) {
        StringBuilder builder = new StringBuilder();
        builder.append("-P").append(AndroidProject.OPTIONAL_COMPILATION_STEPS).append('=')
                .append(OptionalCompilationStep.INSTANT_DEV);
        for (OptionalCompilationStep step : optionalCompilationSteps) {
            builder.append(',').append(step);
        }
        return builder.toString();
    }

    static void doInstall(
            @NonNull IDevice device,
            @NonNull List<InstantRunBuildContext.Artifact> artifacts) throws DeviceException,
            InstallException {
        List<File> apkFiles = Lists.newArrayList();
        for (InstantRunBuildContext.Artifact artifact : artifacts) {
            if (artifact.getType() == InstantRunBuildContext.FileType.SPLIT) {
                apkFiles.add(artifact.getLocation());
            }
            if (artifact.getType() == InstantRunBuildContext.FileType.MAIN) {
                apkFiles.add(0, artifact.getLocation());
            }
        }

        if (device.getVersion().isGreaterOrEqualThan(21)) {
            device.installPackages(apkFiles, true /*reinstall*/, ImmutableList.<String>of(),
                    DEFAULT_ADB_TIMEOUT_MSEC, MILLISECONDS);

        } else {
            assertThat(apkFiles).hasSize(1);
            device.installPackage(
                    Iterables.getOnlyElement(apkFiles).getAbsolutePath(), true /*reinstall*/);
        }
    }

    static void runApp(IDevice device, String target) throws Exception {
        IShellOutputReceiver receiver = new CollectingOutputReceiver();
        String command = "am start" +
                " -n " + target +
                " -a android.intent.action.MAIN" +
                " -c android.intent.category.LAUNCHER";
        device.executeShellCommand(
                command, receiver, DEFAULT_ADB_TIMEOUT_MSEC, MILLISECONDS);
    }

    static void unlockDevice(@NonNull IDevice device) throws Exception {
        IShellOutputReceiver receiver = new CollectingOutputReceiver();
        device.executeShellCommand(
                "input keyevent KEYCODE_WAKEUP", receiver,
                DEFAULT_ADB_TIMEOUT_MSEC, MILLISECONDS);
        device.executeShellCommand(
                "wm dismiss-keyguard", receiver,
                DEFAULT_ADB_TIMEOUT_MSEC, MILLISECONDS);
    }
}
