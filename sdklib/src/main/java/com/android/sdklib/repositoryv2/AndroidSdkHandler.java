/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.sdklib.repositoryv2;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.prefs.AndroidLocation;
import com.android.repository.Revision;
import com.android.repository.api.ConstantSourceProvider;
import com.android.repository.api.FallbackRemoteRepoLoader;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RemoteListSourceProvider;
import com.android.repository.api.RepoManager;
import com.android.repository.api.RepoPackage;
import com.android.repository.api.Repository;
import com.android.repository.api.RepositorySource;
import com.android.repository.api.RepositorySourceProvider;
import com.android.repository.api.SchemaModule;
import com.android.repository.impl.installer.BasicInstaller;
import com.android.repository.impl.installer.PackageInstaller;
import com.android.repository.impl.meta.RepositoryPackages;
import com.android.repository.impl.sources.LocalSourceProvider;
import com.android.repository.io.FileOp;
import com.android.repository.io.FileOpUtils;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.repositoryv2.meta.DetailsTypes;
import com.android.sdklib.repositoryv2.meta.SysImgFactory;
import com.android.sdklib.repositoryv2.sources.RemoteSiteType;
import com.android.sdklib.repositoryv2.targets.AndroidTargetManager;
import com.android.sdklib.repositoryv2.targets.SystemImage;
import com.android.sdklib.repositoryv2.targets.SystemImageManager;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;

import java.io.File;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Map;


/**
 * Android SDK interface to {@link RepoManager}. Ensures that the proper android sdk-specific
 * schemas and source providers are registered, and provides android sdk-specific package logic
 * (pending as adoption continues).
 */
public final class AndroidSdkHandler {
    /**
     * Schema module containing the package type information to be used in addon repos.
     */
    private static final SchemaModule ADDON_MODULE = new SchemaModule(
            "com.android.sdklib.repositoryv2.generated.addon.v%d.ObjectFactory",
            "sdk-addon-%02d.xsd", AndroidSdkHandler.class);

    /**
     * Schema module containing the package type information to be used in the primary repo.
     */
    private static final SchemaModule REPOSITORY_MODULE = new SchemaModule(
            "com.android.sdklib.repositoryv2.generated.repository.v%d.ObjectFactory",
            "sdk-repository-%02d.xsd", AndroidSdkHandler.class);

    /**
     * Schema module containing the package type information to be used in system image repos.
     */
    private static final SchemaModule SYS_IMG_MODULE = new SchemaModule(
            "com.android.sdklib.repositoryv2.generated.sysimg.v%d.ObjectFactory",
            "sdk-sys-img-%02d.xsd", AndroidSdkHandler.class);

    /**
     * Common schema module used by the other sdk-specific modules.
     */
    private static final SchemaModule COMMON_MODULE = new SchemaModule(
            "com.android.sdklib.repositoryv2.generated.common.v%d.ObjectFactory",
            "sdk-common-%02d.xsd", AndroidSdkHandler.class);

    /**
     * The URL of the official Google sdk-repository site. The URL ends with a /, allowing easy
     * concatenation.
     */
    public static final String URL_GOOGLE_SDK_SITE = "https://dl.google.com/android/repository/";

    /**
     * The name of the environment variable used to override the url of the primary repository, for
     * testing.
     */
    public static final String SDK_TEST_BASE_URL_ENV_VAR = "SDK_TEST_BASE_URL";

    /**
     * The latest version of legacy remote packages we should expect to receive from a server. If
     * you think you need to change this value you should create a new-style package instead.
     */
    public static final int LATEST_LEGACY_VERSION = 12;

    /**
     * The name of the file containing user-specified remote repositories.
     */
    @VisibleForTesting
    static final String LOCAL_ADDONS_FILENAME = "repositories.cfg";

    /**
     * Pattern for the name of a (remote) file containing a list of urls to check for repositories.
     *
     * @see RemoteListSourceProvider
     */
    private static final String DEFAULT_SITE_LIST_FILENAME_PATTERN = "addons_list-%d.xml";

    /**
     * Lock for synchronizing changes to the our {@link RepoManager}.
     */
    private static final Object MANAGER_LOCK = new Object();

    /**
     * Pattern for the URL pointing to the legacy repository xml (as defined by e.g.
     * sdk-repository-12.xsd).
     */
    private static final String LEGACY_REPO_URL_PATTERN = "%srepository-%d.xml";

    /**
     * Pattern for the URL pointing to the repository xml (as defined by sdk-repository-01.xsd in
     * repositoryv2).
     */
    private static final String REPO_URL_PATTERN = "%srepository2-%d.xml";

    /**
     * The {@link RepoManager} initialized with our {@link SchemaModule}s, {@link
     * RepositorySource}s, and local SDK path.
     */
    private RepoManager mRepoManager;

    /**
     * Finds all {@link SystemImage}s in packages known to {@link #mRepoManager};
     */
    private SystemImageManager mSystemImageManager;

    /**
     * Creates {@link IAndroidTarget}s based on the platforms and addons known to
     * {@link #mRepoManager}.
     */
    private AndroidTargetManager mAndroidTargetManager;

    /**
     * Reference to our latest build tool package.
     */
    private BuildToolInfo mLatestBuildTool = null;

    /**
     * {@link FileOp} to use for local file operations. For normal operation should be
     * {@link FileOpUtils#create()}.
     */
    private final FileOp mFop;

    /**
     * Singleton instance of this class.
     */
    private static Map<File, AndroidSdkHandler> sInstances = Maps.newConcurrentMap();

    /**
     * Location of the local SDK.
     */
    private final File mLocation;

    /**
     * Provider for user-specified {@link RepositorySource}s.
     */
    private LocalSourceProvider mUserSourceProvider;

    /**
     * Loader capable of loading old-style repository xml files, with namespace like
     * http://schemas.android.com/sdk/android/repository/NN or similar.
     *
     * Static, shared among {@link AndroidSdkHandler} instances.
     */
    private static FallbackRemoteRepoLoader sRemoteFallback;

    /**
     * Lazily-initialized class containing our static repository configuration, shared between
     * AndroidSdkHandler instances.
     */
    private static RepoConfig sRepoConfig;

    /**
     * Get a {@code AndroidSdkHandler} instance.
     *
     * @param localPath The path to the local SDK. If {@code null} this handler will only be used
     *                  for remote operations.
     */
    @NonNull
    public static AndroidSdkHandler getInstance(@Nullable File localPath) {
        File key = localPath == null ? new File("") : localPath;
        AndroidSdkHandler instance = sInstances.get(key);
        if (instance == null) {
            instance = new AndroidSdkHandler(localPath, FileOpUtils.create());
            sInstances.put(key, instance);
        }
        return instance;
    }

    /**
     * Don't use this, use {@link #getInstance(File)}, unless you're in a unit test and need to
     * specify a custom {@link FileOp}.
     */
    @VisibleForTesting
    public AndroidSdkHandler(@Nullable File localPath, @NonNull FileOp fop) {
        mFop = fop;
        mLocation = localPath;
    }

    /**
     * Fetches {@link RepoManager} set up to interact with android SDK repositories. It should not
     * cached by callers of this method, since any changes to the fundamental properties of the
     * manager (fallback loaders, local path) will cause a new instance to be created.
     */
    @NonNull
    public RepoManager getSdkManager(@NonNull ProgressIndicator progress) {
        RepoManager result = mRepoManager;
        synchronized (MANAGER_LOCK) {
            if (result == null) {
                mSystemImageManager = null;
                mAndroidTargetManager = null;
                mLatestBuildTool = null;

                result = getRepoConfig(progress)
                        .createRepoManager(progress, mLocation, sRemoteFallback,
                                getUserSourceProvider(progress), mFop);
                // Invalidate system images, targets, the latest build tool, and the legacy local
                // package manager when local packages change
                result.registerLocalChangeListener(new RepoManager.RepoLoadedCallback() {
                    @Override
                    public void doRun(@NonNull RepositoryPackages packages) {
                        mSystemImageManager = null;
                        mAndroidTargetManager = null;
                        mLatestBuildTool = null;
                    }
                });
                mRepoManager = result;
            }
        }
        return mRepoManager;
    }

    /**
     * Gets (and creates if necessary) a {@link SystemImageManager} based on our local sdk packages.
     */
    @NonNull
    public SystemImageManager getSystemImageManager(@NonNull ProgressIndicator progress) {
        if (mSystemImageManager == null) {
            getSdkManager(progress);
            mSystemImageManager = new SystemImageManager(mRepoManager,
              (SysImgFactory) getSysImgModule().createLatestFactory(), mFop);
        }
        return mSystemImageManager;
    }

    /**
     * Gets (and creates if necessary) an {@link AndroidTargetManager} based on our local sdk
     * packages.
     */
    @NonNull
    public AndroidTargetManager getAndroidTargetManager(@NonNull ProgressIndicator progress) {
        if (mAndroidTargetManager == null) {
            getSdkManager(progress);
            mAndroidTargetManager = new AndroidTargetManager(this, mFop);
        }
        return mAndroidTargetManager;
    }

    /**
     * Gets the path of the local SDK, if set.
     */
    @Nullable
    public File getLocation() {
        return mLocation;
    }

    /**
     * Convenience to get a package from the local repo.
     */
    @Nullable
    public LocalPackage getLocalPackage(@NonNull String path, @NonNull ProgressIndicator progress) {
        return getSdkManager(progress).getPackages().getLocalPackages().get(path);
    }

    /**
     * Sets the {@link FallbackRemoteRepoLoader} to be used to parse any old-style remote
     * repositories we might receive.<p> Invalidates the repo manager; it will be recreated when
     * next retrieved.
     */
    public static void setRemoteFallback(@Nullable FallbackRemoteRepoLoader fallbackSdk) {
        synchronized (MANAGER_LOCK) {
            sRemoteFallback = fallbackSdk;
            invalidateAll();
        }
    }

    /**
     * Resets the {@link RepoManager}s of all cached {@link AndroidSdkHandler}s.
     */
    private static void invalidateAll() {
        for (AndroidSdkHandler handler : sInstances.values()) {
            handler.mRepoManager = null;
        }
    }

    /**
     * @return The {@link SchemaModule} containing the common sdk-specific metadata. See
     * sdk-common-XX.xsd.
     */
    @NonNull
    public static SchemaModule getCommonModule() {
        return COMMON_MODULE;
    }

    /**
     * @return The {@link SchemaModule} containing the metadata for addon-type {@link Repository}s.
     * See sdk-addon-XX.xsd.
     */
    @NonNull
    public static SchemaModule getAddonModule() {
        return ADDON_MODULE;
    }

    /**
     * @return The {@link SchemaModule} containing the metadata for the primary android SDK {@link
     * Repository} (containin platforms etc.). See sdk-repository-XX.xsd.
     */
    @NonNull
    public static SchemaModule getRepositoryModule() {
        return REPOSITORY_MODULE;
    }

    /**
     * @return The {@link SchemaModule} containing the metadata for system image-type {@link
     * Repository}s. See sdk-sys-img-XX.xsd.
     */
    @NonNull
    public static SchemaModule getSysImgModule() {
        return SYS_IMG_MODULE;
    }

    @NonNull
    @VisibleForTesting
    RemoteListSourceProvider getRemoteListSourceProvider(@NonNull ProgressIndicator progress) {
        return getRepoConfig(progress).getRemoteListSourceProvider();
    }

    /**
     * Gets the customizable {@link RepositorySourceProvider}. Can be null if there's a problem
     * with the user's environment.
     */
    @Nullable
    public LocalSourceProvider getUserSourceProvider(@NonNull ProgressIndicator progress) {
        if (mUserSourceProvider == null) {
            mUserSourceProvider = RepoConfig.createUserSourceProvider(progress, mFop);
            synchronized (MANAGER_LOCK) {
                if (mRepoManager != null) {
                    // If the repo already exists cause it to be reloaded, so the userSourceProvider
                    // can be added to the config.
                    mRepoManager = null;
                    getSdkManager(progress);
                }
            }
        }
        return mUserSourceProvider;
    }

    @NonNull
    private RepoConfig getRepoConfig(@NonNull ProgressIndicator progress) {
        if (sRepoConfig == null) {
            sRepoConfig = new RepoConfig(progress);
        }
        return sRepoConfig;
    }

    /**
     * Class containing the repository configuration we can (lazily) create statically, as well
     * as a method to create a new {@link RepoManager} based on that configuration.
     *
     * Instances of this class may be shared between {@link AndroidSdkHandler} instances.
     */
    private static class RepoConfig {

        /**
         * Provider for a list of {@link RepositorySource}s fetched from the google.
         */
        private RemoteListSourceProvider mAddonsListSourceProvider;

        /**
         * Provider for the main new-style {@link RepositorySource}
         */
        private ConstantSourceProvider mRepositorySourceProvider;

        /**
         * Provider for the main legacy {@link RepositorySource}
         */
        private ConstantSourceProvider mLegacyRepositorySourceProvider;

        /**
         * Sets up our {@link SchemaModule}s and {@link RepositorySourceProvider}s if they haven't
         * been yet.
         *
         * @param progress Used for error logging.
         */
        public RepoConfig(@NonNull ProgressIndicator progress) {
            // Schema module for the list of update sites we download
            SchemaModule addonListModule = new SchemaModule(
                    "com.android.sdklib.repositoryv2.sources.generated.v%d.ObjectFactory",
                    "sdk-sites-list-%d.xsd", RemoteSiteType.class);

            try {
                // Specify what modules are allowed to be used by what sites.
                Map<Class<? extends RepositorySource>, Collection<SchemaModule>> siteTypes
                        = ImmutableMap.<Class<? extends RepositorySource>, Collection<SchemaModule>>builder()
                        .put(RemoteSiteType.AddonSiteType.class, ImmutableSet.of(ADDON_MODULE))
                        .put(RemoteSiteType.SysImgSiteType.class,
                                ImmutableSet.of(SYS_IMG_MODULE)).build();
                mAddonsListSourceProvider = RemoteListSourceProvider
                        .create(getAddonListUrl(progress), addonListModule, siteTypes);
            } catch (URISyntaxException e) {
                progress.logError("Failed to set up addons source provider", e);
            }

            String url = String.format(REPO_URL_PATTERN, getBaseUrl(progress),
                                       REPOSITORY_MODULE.getNamespaceVersionMap().size());
            mRepositorySourceProvider = new ConstantSourceProvider(url, "Android Repository",
                    ImmutableSet.of(REPOSITORY_MODULE, RepoManager.getGenericModule()));

            url = String.format(LEGACY_REPO_URL_PATTERN, getBaseUrl(progress), LATEST_LEGACY_VERSION);
            mLegacyRepositorySourceProvider = new ConstantSourceProvider(url,
                    "Legacy Android Repository",
                    ImmutableSet.of(REPOSITORY_MODULE, RepoManager.getGenericModule()));
        }

        /**
         * Creates a customizable {@link RepositorySourceProvider}. Can be null if there's a problem
         * with the user's environment.
         */
        @Nullable
        public static LocalSourceProvider createUserSourceProvider(
                @NonNull ProgressIndicator progress,
                @NonNull FileOp fileOp) {
            try {
                return new LocalSourceProvider(
                        new File(AndroidLocation.getFolder(), LOCAL_ADDONS_FILENAME),
                        ImmutableList.of(SYS_IMG_MODULE, ADDON_MODULE), fileOp);
            } catch (AndroidLocation.AndroidLocationException e) {
                progress.logWarning("Couldn't find android folder", e);
            }
            return null;
        }

        @NonNull
        private static String getAddonListUrl(@NonNull ProgressIndicator progress) {
            return getBaseUrl(progress) + DEFAULT_SITE_LIST_FILENAME_PATTERN;
        }

        /**
         * Gets the default url (without the actual filename or specific final part of the path
         * (e.g. sys-img). This will be either the value of {@link #SDK_TEST_BASE_URL_ENV_VAR} or
         * {@link #URL_GOOGLE_SDK_SITE}
         */
        @NonNull
        private static String getBaseUrl(@NonNull ProgressIndicator progress) {
            String baseUrl = System.getenv(SDK_TEST_BASE_URL_ENV_VAR);
            if (baseUrl != null) {
                if (!baseUrl.isEmpty() && baseUrl.endsWith("/")) {
                    return baseUrl;
                } else {
                    progress.logWarning("Ignoring invalid SDK_TEST_BASE_URL: " + baseUrl);
                }
            }
            return URL_GOOGLE_SDK_SITE;
        }

        @NonNull
        public RemoteListSourceProvider getRemoteListSourceProvider() {
            return mAddonsListSourceProvider;
        }

        @NonNull
        public RepoManager createRepoManager(@NonNull ProgressIndicator progress,
                @Nullable File localLocation,
                @Nullable FallbackRemoteRepoLoader remoteFallbackLoader,
                @Nullable LocalSourceProvider userProvider, @NonNull FileOp fop) {
            RepoManager result = RepoManager.create(fop);

            // Create the schema modules etc. if they haven't been already.
            result.registerSchemaModule(ADDON_MODULE);
            result.registerSchemaModule(REPOSITORY_MODULE);
            result.registerSchemaModule(SYS_IMG_MODULE);
            result.registerSchemaModule(COMMON_MODULE);

            result.registerSourceProvider(mRepositorySourceProvider);
            result.registerSourceProvider(mLegacyRepositorySourceProvider);
            result.registerSourceProvider(mAddonsListSourceProvider);
            if (userProvider != null) {
                result.registerSourceProvider(userProvider);
                // The customizable source provider needs a handle on the repo manager, so it can
                // mark the cached packages invalid if the sources change.
                userProvider.setRepoManager(result);
            }


            result.setLocalPath(localLocation);

            if (localLocation != null) {
                // If we have a local sdk path set, set up the old-style loader so we can parse
                // any legacy packages.
                result.setFallbackLocalRepoLoader(new LegacyLocalRepoLoader(localLocation, fop));

                // If a location is set we'll always want at least the local packages loaded, so
                // load them now.
                result.loadSynchronously(0, progress, null, null);
            }

            result.setFallbackRemoteRepoLoader(remoteFallbackLoader);
            return result;
        }
    }

    /**
     * Finds the best {@link PackageInstaller} for the given {@link RepoPackage}.
     */
    @NonNull
    public static PackageInstaller findBestInstaller(@NonNull RepoPackage p) {
        if (p.getTypeDetails() instanceof DetailsTypes.MavenType) {
            return new MavenInstaller();
        }
        return new BasicInstaller();
    }

    /**
     * Gets a {@link BuildToolInfo} corresponding to the newest installed build tool
     * {@link RepoPackage}, or {@code null} if none are installed.
     */
    @Nullable
    public BuildToolInfo getLatestBuildTool(@NonNull ProgressIndicator progress) {
        if (mLatestBuildTool == null) {
            RepoManager manager = getSdkManager(progress);
            BuildToolInfo info = null;
            for (LocalPackage p : manager.getPackages()
                    .getLocalPackagesForPrefix(SdkConstants.FD_BUILD_TOOLS)) {
                if (info == null || info.getRevision().compareTo(p.getVersion()) < 0) {
                     info = new BuildToolInfo(p.getVersion(), p.getLocation());
                }
            }
            mLatestBuildTool = info;
        }
        return mLatestBuildTool;
    }

    /**
     * Creates a the {@link BuildToolInfo} for the specificed build tools revision, if available.
     *
     * @param revision The build tools revision requested
     * @param progress {@link ProgressIndicator} for logging.
     * @return The {@link BuildToolInfo} corresponding to the specified build tools package, or
     * {@code} null if that revision is not installed.
     */
    @Nullable
    public BuildToolInfo getBuildToolInfo(@NonNull Revision revision,
            @NonNull ProgressIndicator progress) {
        RepositoryPackages packages = getSdkManager(progress).getPackages();
        LocalPackage p = packages.getLocalPackages()
                .get(DetailsTypes.getBuildToolsPath(revision));
        if (p == null) {
            return null;
        }
        return new BuildToolInfo(p.getVersion(), p.getLocation());
    }

    /**
     * Gets our {@link FileOp}. Useful so both the sdk handler and file op don't both have to be
     * injected everywhere.
     */
    @NonNull
    public FileOp getFileOp() {
        return mFop;
    }
}
