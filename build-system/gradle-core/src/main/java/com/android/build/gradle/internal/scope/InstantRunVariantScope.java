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

package com.android.build.gradle.internal.scope;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.pipeline.TransformTask;
import com.android.build.gradle.tasks.PackageApplication;
import com.google.common.collect.ImmutableList;

import org.gradle.api.DefaultTask;

import java.io.File;
import java.util.List;

/**
 * Scope for all variant scoped information related to supporting the Instant Run features.
 */
public interface InstantRunVariantScope {

    @NonNull
    String getFullVariantName();

    @NonNull
    TransformVariantScope getTransformVariantScope();

    @NonNull
    TransformGlobalScope getGlobalScope();

    @NonNull
    File getReloadDexOutputFolder();

    @NonNull
    File getRestartDexOutputFolder();

    @NonNull
    File getInstantRunSupportDir();

    @NonNull
    File getIncrementalVerifierDir();

    @NonNull
    InstantRunBuildContext getInstantRunBuildContext();

    @NonNull
    File getInstantRunPastIterationsFolder();

    @NonNull
    File getInstantRunSliceSupportDir();

    @NonNull
    File getIncrementalRuntimeSupportJar();

    @NonNull
    File getIncrementalApplicationSupportDir();

    /** The {@code *.ap_} with added assets, used for hot and cold swaps. */
    @NonNull
    File getInstantRunResourcesFile();

    /**
     * Returns the boot class path which matches the target device API level.
     */
    @NonNull
    ImmutableList<File> getInstantRunBootClasspath();

    AndroidTask<TransformTask> getInstantRunVerifierTask();
    void setInstantRunVerifierTask(AndroidTask<TransformTask> verifierTask);

    AndroidTask<TransformTask> getInstantRunSlicerTask();
    void setInstantRunSlicerTask(AndroidTask<TransformTask> slicerTask);

    List<AndroidTask<? extends DefaultTask>> getColdSwapBuildTasks();
    void addColdSwapBuildTask(@NonNull AndroidTask<? extends DefaultTask> task);

    AndroidTask<PackageApplication> getPackageApplicationTask();
    void setPackageApplicationTask(AndroidTask<PackageApplication> packageApplicationTask);
}
