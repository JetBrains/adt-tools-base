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

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.ide.common.repository.GradleCoordinate;
import com.android.ide.common.repository.SdkMavenRepository;
import com.android.repository.Revision;
import com.android.repository.api.Downloader;
import com.android.repository.api.Installer;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoManager;
import com.android.repository.api.SettingsController;
import com.android.repository.api.UpdatablePackage;
import com.android.repository.io.FileOpUtils;
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
import com.google.common.collect.Maps;

import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Singleton-based implementation of SdkLoader for a standard SDK
 */
public class DefaultSdkLoader implements SdkLoader {

    private enum InstallResultType {
        SUCCESS,
        LICENSE_FAIL,
        INSTALL_FAIL,
    }

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
            @NonNull SdkLibData sdkLibData) {
        init(logger);

        // One progress is used for the auto-download feature,
        // the other is used for parsing the repository XMLs and other operations.
        ProgressIndicator progress = new LoggerProgressIndicatorWrapper(logger);
        ProgressIndicator stdOutputProgress = getNewDownloadProgress();
        IAndroidTarget target = mSdkHandler.getAndroidTargetManager(progress)
                .getTargetFromHashString(targetHash, progress);

        BuildToolInfo buildToolInfo =
                mSdkHandler.getBuildToolInfo(buildToolRevision, progress);

        if (sdkLibData.useSdkDownload()) {
            SettingsController settings = sdkLibData.getSettings();
            Downloader downloader = sdkLibData.getDownloader();
            Preconditions.checkNotNull(settings);
            Preconditions.checkNotNull(downloader);

            // Check if Build Tools is preview that the user is requesting the latest revision.
            if (buildToolInfo != null && !buildToolInfo.getRevision().equals(buildToolRevision)) {
                stdOutputProgress.logWarning("Build Tools revision " +
                        buildToolRevision +
                        " requested, but the latest available preview is " +
                        buildToolInfo.getRevision()+ ", which will be used to build.");
            }

            if (target == null || buildToolInfo == null) {
                Map<RemotePackage, InstallResultType> installResults = new HashMap<>();
                RepoManager repoManager = mSdkHandler.getSdkManager(progress);
                repoManager.loadSynchronously(
                        sdkLibData.getCacheExpirationPeriod(), progress, downloader, settings);

                if (buildToolInfo == null) {
                    installResults.putAll(
                            installBuildTools(
                                    buildToolRevision, repoManager, downloader, stdOutputProgress));
                }

                if (target == null) {
                    installResults.putAll(
                            installTarget(targetHash, repoManager, downloader, stdOutputProgress));
                }

                checkResults(installResults);

                repoManager.loadSynchronously(0, progress, null, null);

                buildToolInfo = mSdkHandler.getBuildToolInfo(buildToolRevision, progress);
                target = mSdkHandler.getAndroidTargetManager(progress)
                        .getTargetFromHashString(targetHash, progress);
            }
        }
        if (target == null) {
            throw new IllegalStateException(
                    "Failed to find target with hash string '"
                            + targetHash
                            + "' in: "
                            + mSdkLocation);
        }

        if (buildToolInfo == null) {
            throw new IllegalStateException(
                    "Failed to find Build Tools revision " + buildToolRevision.toString());
        }

        return new TargetInfo(target, buildToolInfo);
    }

    /**
     * Installs a compile target and its dependencies.
     *
     * @param targetHash hash of the target that needs to be installed.
     * @param repoManager used for interacting with repository packages.
     * @param downloader used to download packages.
     * @param progress a logger for messages.
     * @return a {@code Map<RemotePackages, InstallResultType>} of the compile target and its
     *         dependencies and their installation results.
     */
    @NonNull
    private Map<RemotePackage, InstallResultType> installTarget(
            @NonNull String targetHash,
            @NonNull RepoManager repoManager,
            @NonNull Downloader downloader,
            @NonNull ProgressIndicator progress) {
        Map<RemotePackage, InstallResultType> installResults = new HashMap<>();
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
            installResults.putAll(
                    installRemotePackages(
                            ImmutableList.of(platformPkg.getRemote()),
                            repoManager,
                            downloader,
                            progress));
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
                            addonDetails.getAndroidVersion());
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

            installResults.putAll(
                    installRemotePackages(
                            ImmutableList.of(addonPackage), repoManager, downloader, progress));
        }

        return installResults;
    }

    /**
     * Installs a Build Tools revision.
     *
     * @param buildToolRevision the {@code Revision} of the build tools that need installation.
     * @param repoManager used for interacting with repository packages.
     * @param downloader used to download packages.
     * @param progress a logger for messages.
     * @return a {@code Map<RemotePackage, InstallResultType>} between the Build Tools packages and
     * its dependencies and their installation results.
     */
    private Map<RemotePackage, InstallResultType> installBuildTools(
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

        if (!buildToolsPackage.getVersion().equals(buildToolRevision)) {
            progress.logWarning(
                    "Build Tools revision " +
                    buildToolRevision +
                    " requested, but the latest available preview is " +
                    buildToolsPackage.getVersion() + ", which will be installed.");
        }

        return installRemotePackages(
                ImmutableList.of(buildToolsPackage), repoManager, downloader, progress);
    }

    /**
     * Installs a list of {@code RemotePackage} and their dependent packages. Collects the install
     * results for each packages it tries to install.
     *
     * @param requestPackages the packages we want to install.
     *  @param repoManager used for interacting with repository packages.
     * @param downloader used to download packages.
     * @param progress a progress logger for messages.
     * @return a {@code Map} of all the packages we tried to install and the install result.
     */
    private Map<RemotePackage, InstallResultType> installRemotePackages(
            @NonNull List<RemotePackage> requestPackages,
            @NonNull RepoManager repoManager,
            @NonNull Downloader downloader,
            @NonNull ProgressIndicator progress) {

        List<RemotePackage> remotePackages =
                InstallerUtil.computeRequiredPackages(
                        requestPackages, repoManager.getPackages(), progress);

        if (remotePackages == null) {
            return Maps.toMap(requestPackages, p -> InstallResultType.INSTALL_FAIL);
        }

        Map<RemotePackage, InstallResultType> installResults = new HashMap<>();
        for (RemotePackage p : remotePackages) {
            if (p.getLicense() != null
                    && !p.getLicense()
                            .checkAccepted(repoManager.getLocalPath(), mSdkHandler.getFileOp())) {
                progress.setText(
                        "The license for package "
                                + p.getDisplayName()
                                + " was not accepted. "
                                + "Please install this package through Android Studio SDK "
                                + "Manager.");
                installResults.put(p, InstallResultType.LICENSE_FAIL);
            } else {
                Installer installer =
                        SdkInstallerUtil.findBestInstallerFactory(p, mSdkHandler)
                                .createInstaller(
                                        p, repoManager, downloader, mSdkHandler.getFileOp());
                if (installer.prepare(progress) && installer.complete(progress)) {
                    installResults.put(p, InstallResultType.SUCCESS);
                } else {
                    installResults.put(p, InstallResultType.INSTALL_FAIL);
                }
            }
        }
        return installResults;
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
    private ImmutableList<File> computeRepositories() {
        return ImmutableList.of(
                new File(
                        mSdkLocation,
                        FD_EXTRAS + File.separator + "android" + File.separator + FD_M2_REPOSITORY),
                new File(
                        mSdkLocation,
                        FD_EXTRAS + File.separator + "google" + File.separator + FD_M2_REPOSITORY),
                new File(mSdkLocation, FD_EXTRAS + File.separator + FD_M2_REPOSITORY));
    }

    @Override
    @NonNull
    public List<File> updateRepositories(
            @NonNull List<String> repositoryPaths,
            @NonNull SdkLibData sdkLibData,
            @NonNull ILogger logger) {

        ImmutableList.Builder<File> repositoriesBuilder = ImmutableList.builder();
        Map<RemotePackage, InstallResultType> installResults = new HashMap<>();
        ProgressIndicator progress = getNewDownloadProgress();
        RepoManager repoManager = mSdkHandler.getSdkManager(progress);

        repoManager.loadSynchronously(
                sdkLibData.getCacheExpirationPeriod(),
                new LoggerProgressIndicatorWrapper(logger),
                sdkLibData.getDownloader(),
                sdkLibData.getSettings());

        Map<String, RemotePackage> remotePackages = repoManager.getPackages().getRemotePackages();
        List<RemotePackage> artifactPackages =
                repositoryPaths
                        .stream()
                        .map(remotePackages::get)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());

        if (!artifactPackages.isEmpty()) {
            installResults.putAll(installRemotePackages(
                    artifactPackages, repoManager, sdkLibData.getDownloader(), progress));
            repositoriesBuilder.add(new File(
                    mSdkLocation +
                            File.separator +
                            SdkConstants.FD_EXTRAS +
                            File.separator +
                            SdkConstants.FD_M2_REPOSITORY));
        }

        // Check to see if we failed because of some license not being accepted.
        checkResults(installResults);

        // If we can't find some of the remote packages or some install failed
        // we resort to installing/updating the old big repositories.
        if (artifactPackages.size() != repositoryPaths.size()
                || installResults.values().contains(InstallResultType.INSTALL_FAIL)) {
            // Check if there is a Google Repository dependency. If not, we don't install/update
            // the Google repository. If there is one, we update both repositories, since
            // the (maven) packages in the Google repo have dependencies (declared in *.pom files)
            // on packages from the Android repo.
            boolean googleRepositoryRequired = false;

            for (String repoPath : repositoryPaths) {
                GradleCoordinate coordinate = SdkMavenRepository.getCoordinateFromSdkPath(repoPath);
                if (coordinate != null) {
                    String group = coordinate.getGroupId();
                    if (group != null
                            && group.startsWith(SdkConstants.GOOGLE_SUPPORT_ARTIFACT_PREFIX)) {
                        googleRepositoryRequired = true;
                    }
                }
            }

            UpdatablePackage googleRepositoryPackage =
                    repoManager
                            .getPackages()
                            .getConsolidatedPkgs()
                            .get(SdkMavenRepository.GOOGLE.getPackageId());

            UpdatablePackage androidRepositoryPackage =
                    mSdkHandler
                            .getSdkManager(progress)
                            .getPackages()
                            .getConsolidatedPkgs()
                            .get(SdkMavenRepository.ANDROID.getPackageId());

            if (googleRepositoryRequired && (!googleRepositoryPackage.hasLocal()
                    || googleRepositoryPackage.isUpdate())) {
                installResults.putAll(
                        installRemotePackages(
                                ImmutableList.of(googleRepositoryPackage.getRemote()),
                                repoManager,
                                sdkLibData.getDownloader(),
                                progress));

                if (installResults.get(googleRepositoryPackage.getRemote())
                        .equals(InstallResultType.SUCCESS)) {
                    File googleRepo = SdkMavenRepository.GOOGLE
                            .getRepositoryLocation(mSdkLocation, true, FileOpUtils.create());
                    repositoriesBuilder.add(googleRepo);
                }
            }

            if (!androidRepositoryPackage.hasLocal()
                    || androidRepositoryPackage.isUpdate()) {
                installResults.putAll(
                        installRemotePackages(
                                ImmutableList.of(androidRepositoryPackage.getRemote()),
                                repoManager,
                                sdkLibData.getDownloader(),
                                progress));

                if (installResults
                        .get(androidRepositoryPackage.getRemote())
                        .equals(InstallResultType.SUCCESS)) {
                    File androidRepo =
                            SdkMavenRepository.ANDROID.getRepositoryLocation(
                                    mSdkLocation, true, FileOpUtils.create());
                    repositoriesBuilder.add(androidRepo);
                }
            }
            checkResults(installResults);
        }

        return repositoriesBuilder.build();
    }

    /**
     * Checks if any of the installation attempts failed and prints out the appropriate error
     * message.
     *
     * @throws RuntimeException if some packages could not be installed.
     */
    private void checkResults(Map<RemotePackage, InstallResultType> installResults) {
        Function<InstallResultType, List<String>> find =
                resultType ->
                        installResults
                                .entrySet()
                                .stream()
                                .filter(p -> p.getValue() == resultType)
                                .map(p -> p.getKey().getDisplayName())
                                .collect(Collectors.toList());

        List<String> unlicensedPackages = find.apply(InstallResultType.LICENSE_FAIL);
        if (!unlicensedPackages.isEmpty()) {
            throw new RuntimeException(
                    "You have not accepted the license agreements of the following SDK components:\n"
                            + unlicensedPackages.toString()
                            + ".\nBefore building your project, you need to accept the license agreements "
                            + "and complete the installation of the missing components using the Android Studio SDK Manager.\n"
                            + "Alternatively, to learn how to transfer the license agreements from one "
                            + "workstation to another, go to http://d.android.com/r/studio-ui/export-licenses.html");
        }

        List<String> failedPackages = find.apply(InstallResultType.INSTALL_FAIL);
        if (!failedPackages.isEmpty()) {
            String message =
                    String.format(
                            "Failed to install the following SDK components:%n%s%n",
                            failedPackages);

            // Use NIO to check permissions, which seems to work across platform better.
            if (!Files.isWritable(mSdkLocation.toPath())) {
                message +=
                        String.format(
                                "The SDK directory (%s) is not writeable,%n"
                                        + "please update the directory permissions.",
                                mSdkLocation.getAbsolutePath());
            } else {
                message +=
                        "Please install the missing components using the SDK manager in Android Studio.";
            }

            throw new RuntimeException(message);
        }
    }

    private static ProgressIndicator getNewDownloadProgress() {
        return new LoggerProgressIndicatorWrapper(new StdLogger(StdLogger.Level.VERBOSE));
    }
}
