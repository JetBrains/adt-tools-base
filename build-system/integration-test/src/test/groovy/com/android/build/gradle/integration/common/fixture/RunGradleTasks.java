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

package com.android.build.gradle.integration.common.fixture;


import static com.google.common.truth.Truth.assertThat;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.AndroidGradleOptions;
import com.android.build.gradle.internal.incremental.ColdswapMode;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.OptionalCompilationStep;
import com.android.builder.tasks.BooleanLatch;
import com.android.ddmlib.IDevice;
import com.android.resources.Density;
import com.android.sdklib.AndroidVersion;
import com.google.common.base.Joiner;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import org.apache.commons.io.output.TeeOutputStream;
import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.ResultHandler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * A Gradle tooling api build builder.
 */
public class RunGradleTasks extends BaseGradleExecutor<RunGradleTasks> {

    private final boolean isUseJack;
    private final boolean isMinifyEnabled;

    private boolean mExpectingFailure = false;

    private Packaging mPackaging;

    RunGradleTasks(@NonNull GradleTestProject gradleTestProject,
            @NonNull ProjectConnection projectConnection) {
        super(projectConnection, gradleTestProject.getBuildFile(), gradleTestProject.getHeapSize());
        isUseJack = gradleTestProject.isUseJack();
        isMinifyEnabled = gradleTestProject.isMinifyEnabled();
    }

    /**
     * Assert that the task called fails.
     *
     * The resulting exception is stored in the {@link GradleBuildResult}.
     */
    public RunGradleTasks expectFailure() {
        mExpectingFailure = true;
        return this;
    }

    /**
     * Inject the instant run arguments.
     *
     * @param apiLevel The device api level.
     * @param coldswapMode The cold swap strategy to use.
     * @param flags additional instant run flags, {@see OptionalCompilationStep}.
     */
    public RunGradleTasks withInstantRun(int apiLevel,
            @NonNull ColdswapMode coldswapMode,
            @NonNull OptionalCompilationStep... flags) {
        setInstantRunArgs(
                new AndroidVersion(apiLevel, null), null /* density */, coldswapMode, flags);
        return this;
    }

    /**
     * Inject the instant run arguments.
     *
     * @param device The connected device.
     * @param coldswapMode The cold swap strategy to use.
     * @param flags additional instant run flags, {@see OptionalCompilationStep}.
     */
    public RunGradleTasks withInstantRun(
            @NonNull IDevice device,
            @NonNull ColdswapMode coldswapMode,
            @NonNull OptionalCompilationStep... flags) {
        setInstantRunArgs(device.getVersion(),
                Density.getEnum(device.getDensity()), coldswapMode, flags);
        return this;
    }

    /**
     * Sets the desired packaging implementation.
     */
    public RunGradleTasks withPackaging(@NonNull Packaging packaging) {
        mPackaging = packaging;
        return this;
    }

    /**
     * Call connected check.
     *
     * Uses deviceCheck in the background to support the device pool.
     */
    public GradleBuildResult executeConnectedCheck() {
        return run("deviceCheck");
    }

    /** Execute the specified tasks */
    public GradleBuildResult run(@NonNull String... tasks) {
        return run(ImmutableList.copyOf(tasks));
    }

    public GradleBuildResult run(@NonNull List<String> tasksList) {
        assertThat(tasksList).named("tasks list").isNotEmpty();

        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        syncFileSystem();
        List<String> args = Lists.newArrayList();

        if (enableInfoLogging) {
            args.add("-i"); // -i, --info Set log level to info.
        }
        args.add("-u"); // -u, --no-search-upward  Don't search in parent folders for a
                        // settings.gradle file.
        args.add("-Pcom.android.build.gradle.integratonTest.useJack="
                + Boolean.toString(isUseJack));
        args.add("-Pcom.android.build.gradle.integratonTest.minifyEnabled="
                + Boolean.toString(isMinifyEnabled));

        if (mPackaging != null) {
            args.add(
                    String.format(
                            "-P%s=%s",
                            AndroidGradleOptions.PROPERTY_USE_OLD_PACKAGING,
                            mPackaging.mFlagValue));
        }

        args.addAll(mArguments);

        System.out.println("[GradleTestProject] Executing tasks: gradle "
                + Joiner.on(' ').join(args) + " " + Joiner.on(' ').join(tasksList));

        BuildLauncher launcher = mProjectConnection.newBuild()
                .forTasks(Iterables.toArray(tasksList, String.class))
                .withArguments(Iterables.toArray(args, String.class));

        setJvmArguments(launcher);

        launcher.setStandardOutput(new TeeOutputStream(stdout, System.out));
        launcher.setStandardError(new TeeOutputStream(stderr, System.err));

        WaitingResultHandler handler = new WaitingResultHandler();
        launcher.run(handler);

        GradleConnectionException failure = handler.waitForResult();
        if (mExpectingFailure && failure == null) {
            throw new AssertionError("Expecting build to fail");
        } else if (!mExpectingFailure && failure != null) {
            throw failure;
        }
        return new GradleBuildResult(stdout, stderr, failure);
    }


    private static class WaitingResultHandler implements ResultHandler<Void> {

        private final BooleanLatch latch = new BooleanLatch();
        private GradleConnectionException failure;

        @Override
        public void onComplete(Void aVoid) {
            latch.signal();
        }

        @Override
        public void onFailure(GradleConnectionException e) {
            failure = e;
            latch.signal();
        }

        /**
         * Waits for the build to complete.
         *
         * @return null if the build passed, the GradleConnectionException if the build failed.
         */
        @Nullable
        private GradleConnectionException waitForResult() {
            try {
                latch.await();
            } catch (InterruptedException e) {
                throw new AssertionError(e);
            }
            return failure;
        }
    }

    private static void syncFileSystem() {
        try {
            if (System.getProperty("os.name").contains("Linux")) {
                if (Runtime.getRuntime().exec("/bin/sync").waitFor() != 0) {
                    throw new IOException("Failed to sync file system.");
                }
            }
        } catch (IOException | InterruptedException e) {
            System.err.println(Throwables.getStackTraceAsString(e));
        }
    }


    private void setInstantRunArgs(
            @Nullable AndroidVersion androidVersion,
            @Nullable Density density,
            @NonNull ColdswapMode coldswapMode,
            @NonNull OptionalCompilationStep[] flags) {
        if (androidVersion != null) {
            withProperty(AndroidProject.PROPERTY_BUILD_API, androidVersion.getFeatureLevel());
        }

        if (density != null) {
            withProperty(AndroidProject.PROPERTY_BUILD_DENSITY, density.getResourceValue());
        }

        withProperty(AndroidProject.PROPERTY_SIGNING_COLDSWAP_MODE, coldswapMode.name());
        withProperty(AndroidProject.PROPERTY_VERSION_CODE, AndroidProject.INSTANT_RUN_VERSION_CODE);
        withProperty(AndroidProject.PROPERTY_VERSION_NAME, AndroidProject.INSTANT_RUN_VERSION_NAME);

        StringBuilder optionalSteps = new StringBuilder()
                .append("-P").append("android.optional.compilation").append('=')
                .append("INSTANT_DEV");
        for (OptionalCompilationStep step : flags) {
            optionalSteps.append(',').append(step);
        }
        mArguments.add(optionalSteps.toString());
    }

}
