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

import static com.android.SdkConstants.FD_EXTRAS;
import static com.android.SdkConstants.FD_M2_REPOSITORY;
import static com.android.SdkConstants.FD_PLATFORM_TOOLS;
import static com.android.SdkConstants.FD_SUPPORT;
import static com.android.SdkConstants.FD_TOOLS;
import static com.android.SdkConstants.FN_ADB;
import static com.android.SdkConstants.FN_ANNOTATIONS_JAR;

import com.android.annotations.NonNull;
import com.android.repository.Revision;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.io.FileOpUtils;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.repositoryv2.AndroidSdkHandler;
import com.android.sdklib.repositoryv2.LoggerProgressIndicatorWrapper;
import com.android.utils.ILogger;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import java.io.File;
import java.util.List;

/**
 * Singleton-based implementation of SdkLoader for a standard SDK
 */
public class DefaultSdkLoader implements SdkLoader {

    private static DefaultSdkLoader sLoader;

    @NonNull
    private final File mSdkLocation;
    private AndroidSdkHandler mSdkHandler;
    private SdkInfo mSdkInfo;
    private final ImmutableList<File> mRepositories;

    public static synchronized SdkLoader getLoader(
            @NonNull File sdkLocation) {
        if (sLoader == null) {
            sLoader = new DefaultSdkLoader(sdkLocation);
        } else if (!sdkLocation.equals(sLoader.mSdkLocation)) {
            throw new IllegalStateException("Already created an SDK Loader with different SDK Path");
        }

        return sLoader;
    }

    public static synchronized void unload() {
        sLoader = null;
    }

    @Override
    @NonNull
    public TargetInfo getTargetInfo(
            @NonNull String targetHash,
            @NonNull Revision buildToolRevision,
            @NonNull ILogger logger) {
        init(logger);

        ProgressIndicator progress = new LoggerProgressIndicatorWrapper(logger);
        IAndroidTarget target = mSdkHandler.getAndroidTargetManager(progress)
                .getTargetFromHashString(targetHash, progress);
        if (target == null) {
            throw new IllegalStateException("failed to find target with hash string '" + targetHash + "' in: " + mSdkLocation);
        }

        BuildToolInfo buildToolInfo = mSdkHandler.getBuildToolInfo(buildToolRevision, progress);
        if (buildToolInfo == null) {
            throw new IllegalStateException("failed to find Build Tools revision "
                    + buildToolRevision.toString());
        }

        return new TargetInfo(target, buildToolInfo);
    }

    @Override
    @NonNull
    public SdkInfo getSdkInfo(@NonNull ILogger logger) {
        init(logger);
        return mSdkInfo;
    }

    @Override
    @NonNull
    public ImmutableList<File> getRepositories() {
        return mRepositories;
    }

    private DefaultSdkLoader(@NonNull File sdkLocation) {
        mSdkLocation = sdkLocation;
        mRepositories = computeRepositories();
    }

    private synchronized void init(@NonNull ILogger logger) {
        if (mSdkHandler == null) {
            // Intentionally don't use sdk handler caching mechanism
            mSdkHandler = new AndroidSdkHandler(mSdkLocation, FileOpUtils.create());

            File toolsFolder = new File(mSdkLocation, FD_TOOLS);
            File supportToolsFolder = new File(toolsFolder, FD_SUPPORT);
            File platformTools = new File(mSdkLocation, FD_PLATFORM_TOOLS);

            mSdkInfo = new SdkInfo(
                    new File(supportToolsFolder, FN_ANNOTATIONS_JAR),
                    new File(platformTools, FN_ADB));
        }
    }

    @NonNull
    public ImmutableList<File> computeRepositories() {
        List<File> repositories = Lists.newArrayListWithExpectedSize(2);

        File androidRepo = new File(mSdkLocation, FD_EXTRAS + File.separator + "android"
                + File.separator + FD_M2_REPOSITORY);
        if (androidRepo.isDirectory()) {
            repositories.add(androidRepo);
        }

        File googleRepo = new File(mSdkLocation, FD_EXTRAS + File.separator + "google"
                + File.separator + FD_M2_REPOSITORY);
        if (googleRepo.isDirectory()) {
            repositories.add(googleRepo);
        }

        return ImmutableList.copyOf(repositories);
    }
}
