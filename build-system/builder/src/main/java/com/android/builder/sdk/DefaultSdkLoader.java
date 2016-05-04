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
import com.android.annotations.Nullable;
import com.android.repository.Revision;
import com.android.repository.api.Downloader;
import com.android.repository.api.Installer;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoManager;
import com.android.repository.api.SettingsController;
import com.android.repository.api.UpdatablePackage;
import com.android.repository.util.InstallerUtil;
import com.android.sdklib.AndroidTargetHash;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.LoggerProgressIndicatorWrapper;
import com.android.sdklib.repository.installer.SdkInstallerUtil;
import com.android.sdklib.repository.meta.DetailsTypes;
import com.android.utils.ILogger;
import com.android.utils.StdLogger;
import com.google.common.base.Preconditions;
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
            @NonNull ILogger logger,
            @Nullable SettingsController settings,
            @Nullable Downloader downloader,
            boolean useGradleSdkDownload) {
        init(logger);

        ProgressIndicator progress = new LoggerProgressIndicatorWrapper(
                new StdLogger(StdLogger.Level.VERBOSE)) {
            @Override
            public void setText(@Nullable String s) {
                super.setText(s);
                logInfo(s);
            }
        };
        IAndroidTarget target = mSdkHandler.getAndroidTargetManager(progress)
                .getTargetFromHashString(targetHash, progress);

        BuildToolInfo buildToolInfo = mSdkHandler.getBuildToolInfo(buildToolRevision, progress);

        if (useGradleSdkDownload) {
            Preconditions.checkNotNull(settings);
            Preconditions.checkNotNull(downloader);

            if (target == null || buildToolInfo == null) {
                RepoManager repoManager = mSdkHandler.getSdkManager(progress);
                repoManager.loadSynchronously
                        (RepoManager.DEFAULT_EXPIRATION_PERIOD_MS, progress, downloader, settings);

                if (buildToolInfo == null) {
                    installBuildTools(buildToolRevision, repoManager, downloader, progress);
                }

                if (target == null) {
                    installTarget(targetHash, repoManager, downloader, progress);
                }
                repoManager.markInvalid();
                repoManager.loadSynchronously(0, progress, null, null);

                buildToolInfo = mSdkHandler.getBuildToolInfo(buildToolRevision, progress);
                target = mSdkHandler.getAndroidTargetManager(progress)
                        .getTargetFromHashString(targetHash, progress);
            }
        }
        if (target == null) {
            throw new IllegalStateException(
                    "Failed to find target with hash string '" + targetHash + "' in: "
                            + mSdkLocation);
        }

        if (buildToolInfo == null) {
            throw new IllegalStateException("Failed to find Build Tools revision "
                    + buildToolRevision.toString());
        }

        return new TargetInfo(target, buildToolInfo);
    }

    private void installTarget(
            @NonNull String targetHash,
            @NonNull RepoManager repoManager,
            @NonNull Downloader downloader,
            @NonNull ProgressIndicator progress) {

        AndroidVersion targetVersion = AndroidTargetHash.getVersionFromHash(targetHash);
        String platformPath = DetailsTypes.getPlatformPath(targetVersion);

        UpdatablePackage platformPkg = repoManager.getPackages().getConsolidatedPkgs()
                .get(platformPath);

        // Malformed target hash
        if (platformPkg == null) {
            throw new IllegalStateException(
                    "Failed to find Platform SDK with path: "
                            + platformPath);
        }

        // Install platform sdk if it's not there.
        if (!platformPkg.hasLocal()) {
            if (!installRemotePackage(
                    platformPkg.getRemote(), repoManager, downloader, progress)) {
                throw new IllegalStateException(
                        "Couldn't install Platform SDK with path: "
                                + platformPath);
            }
        }

        // Addon case
        if (!AndroidTargetHash.isPlatform(targetHash)) {
            RemotePackage addonPackage = null;
            for (RemotePackage p : repoManager.getPackages().getRemotePackages()
                    .values()) {
                if (p.getTypeDetails() instanceof DetailsTypes.AddonDetailsType) {
                    DetailsTypes.AddonDetailsType addonDetails
                            = (DetailsTypes.AddonDetailsType) p.getTypeDetails();
                    String addonHash = AndroidTargetHash.getAddonHashString(
                            addonDetails.getVendor().getDisplay(),
                            addonDetails.getTag().getDisplay(),
                            DetailsTypes.getAndroidVersion(addonDetails));
                    if (targetHash.equals(addonHash)) {
                        addonPackage = p;
                        break;
                    }
                }
            }

            // Malformed target hash
            if (addonPackage == null) {
                throw new IllegalStateException(
                        "Failed to find target with hash string " + targetHash);
            }

            if (!installRemotePackage(
                    addonPackage, repoManager, downloader, progress)) {
                throw new IllegalStateException(
                        "Couldn't install target with target "
                                + "hash " + targetHash);
            }

        }
    }

    private void installBuildTools(
            @NonNull Revision buildToolRevision,
            @NonNull RepoManager repoManager,
            @NonNull Downloader downloader,
            @NonNull ProgressIndicator progress) {
        String buildToolsPath = DetailsTypes.getBuildToolsPath(buildToolRevision);
        RemotePackage buildToolsPackage = repoManager
                .getPackages()
                .getRemotePackages()
                .get(buildToolsPath);

        if (buildToolsPackage == null) {
            throw new IllegalStateException("Failed to find Build Tools revision "
                    + buildToolRevision.toString());
        }

        if (!installRemotePackage(
                buildToolsPackage, repoManager, downloader, progress)) {
            throw new IllegalStateException("Couldn't install Build Tools revision "
                    + buildToolRevision);
        }
    }

    /**
     * Installs a {@code RemotePackage} and its dependent packages.
     * @return true if all the packages have been installed properly.
     */
    private boolean installRemotePackage(
            @NonNull RemotePackage requestPackage,
            @NonNull RepoManager repoManager,
            @NonNull Downloader downloader,
            @NonNull ProgressIndicator progress) {

        List<RemotePackage> remotePackages =
                InstallerUtil.computeRequiredPackages(
                        ImmutableList.of(requestPackage), repoManager.getPackages(), progress);

        if (remotePackages == null) {
            return false;
        }

        for (RemotePackage p : remotePackages) {
            if (p.getLicense() != null && !p.getLicense().checkAccepted(
                    repoManager.getLocalPath(), mSdkHandler.getFileOp())) {
                progress.setText(
                        "The license for package " + p.getDisplayName() + " was not accepted. "
                                + "Please install this package through Android Studio SDK "
                                + "Manager.");
                return false;
            }

            Installer installer = SdkInstallerUtil
                    .findBestInstallerFactory(p, mSdkHandler)
                    .createInstaller(p, repoManager, downloader, mSdkHandler.getFileOp());
            boolean result = installer.prepare(progress)
                    && installer.complete(progress);

            if (!result) {
                return false;
            }
        }

        return true;
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
            mSdkHandler = AndroidSdkHandler.getInstance(mSdkLocation);
            ProgressIndicator progress = new LoggerProgressIndicatorWrapper(logger);
            mSdkHandler.getSdkManager(progress).reloadLocalIfNeeded(progress);

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
