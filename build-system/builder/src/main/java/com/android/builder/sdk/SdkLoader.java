/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.builder.sdk;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.Revision;
import com.android.repository.api.Downloader;
import com.android.repository.api.SettingsController;
import com.android.utils.ILogger;
import com.google.common.collect.ImmutableList;

import java.io.File;
import java.util.List;

/**
 * A loader for the SDK. It's able to provide general SDK information
 * ({@link #getSdkInfo(com.android.utils.ILogger)}, or {@link #getRepositories()}), or
 * target-specific information
 * ({@link #getTargetInfo(String, Revision, com.android.utils.ILogger, SdkLibData)}).
 */
public interface SdkLoader {

    /**
     * Returns information about a build target.
     * Potentially downloads SDK components if {@code sdkLibData.useSdlDownload()} is true.
     * This requires loading/parsing the SDK.
     *
     * @param targetHash the compilation target hash string.
     * @param buildToolRevision the build tools revision.
     * @param logger a logger to output messages.
     * @param sdkLibData a wrapper containing all the components for downloading.
     * @return the target info.
     */
    @NonNull
    TargetInfo getTargetInfo(
            @NonNull String targetHash,
            @NonNull Revision buildToolRevision,
            @NonNull ILogger logger,
            @NonNull SdkLibData sdkLibData);

    /**
     * Returns generic SDK information.
     *
     * This requires loading/parsing the SDK.
     *
     * @param logger a logger to output messages.
     * @return the sdk info.
     */
    @NonNull
    SdkInfo getSdkInfo(@NonNull ILogger logger);

    /**
     * Returns the location of artifact repositories built-in the SDK.
     * @return a non null list of repository folders.
     */
    @NonNull
    ImmutableList<File> getRepositories();

    /**
     * Tries to update (or install) all local maven repositories and returns a list of directories
     * that were modified.
     * @param repositoryPaths a list of all install paths as described in {@code RepoPackage}
     *                        of the remote packages for the maven repositories.
     *                        Eg.: extras;google;m2repository
     * @param sdkLibData contains all the components for downloading.
     * @param logger a logger for messages.
     * @return a {@code List} of locations to the directories that contain the maven repositories.
     */
    @NonNull
    List<File> updateRepositories(
            @NonNull List<String> repositoryPaths,
            @NonNull SdkLibData sdkLibData,
            @NonNull ILogger logger);
}
