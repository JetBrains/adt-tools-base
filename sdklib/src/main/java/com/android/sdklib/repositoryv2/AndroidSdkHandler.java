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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.prefs.AndroidLocation;
import com.android.repository.api.ConstantSourceProvider;
import com.android.repository.api.FallbackRemoteRepoLoader;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RemoteListSourceProvider;
import com.android.repository.api.RepoManager;
import com.android.repository.api.Repository;
import com.android.repository.api.RepositorySource;
import com.android.repository.api.RepositorySourceProvider;
import com.android.repository.api.SchemaModule;
import com.android.repository.impl.sources.LocalSourceProvider;
import com.android.repository.io.FileOp;
import com.android.repository.io.FileOpUtils;
import com.android.sdklib.repositoryv2.sources.RemoteSiteType;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

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
     * The {@link RepoManager} initialized with our {@link SchemaModule}s, {@link
     * RepositorySource}s, and local SDK path.
     */
    private RepoManager mRepoManager;

    /**
     * {@link FileOp} to use for local file operations. For normal operation should be
     * {@link FileOpUtils#create()}.
     */
    private final FileOp mFop;

    /**
     * Singleton instance of this class.
     */
    private static AndroidSdkHandler sInstance;

    /**
     * Location of the local SDK.
     */
    private File mLocation;

    /**
     * Loader capable of loading old-style repository xml files, with namespace like
     * http://schemas.android.com/sdk/android/repository/NN or similar.
     */
    private FallbackRemoteRepoLoader mRemoteFallback;

    /**
     * Lazily-initialized class containing our static repository configuration.
     */
    private RepoConfig mRepoConfig;

    /**
     * Get a {@code AndroidSdkHandler} instance.
     */
    @NonNull
    public static AndroidSdkHandler getInstance() {
        if (sInstance == null) {
            sInstance = new AndroidSdkHandler(FileOpUtils.create());
        }
        return sInstance;
    }

    /**
     * Don't use this, use {@link #getInstance()}, unless you're in a unit test and need to specify
     * a custom {@link FileOp}.
     */
    @VisibleForTesting
    AndroidSdkHandler(@NonNull FileOp fop) {
        mFop = fop;
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
                mRepoManager = getRepoConfig(progress).createRepoManager();
                result = mRepoManager;
            }
        }
        return result;
    }

    /**
     * Sets the path the the local SDK.<p> Invalidates the repo manager; it will be recreated when
     * next retrieved.
     */
    public void setLocation(@Nullable File location) {
        synchronized (MANAGER_LOCK) {
            mLocation = location;
            mRepoManager = null;
        }
    }

    /**
     * Sets the {@link FallbackRemoteRepoLoader} to be used to parse any old-style remote
     * repositories we might receive.<p> Invalidates the repo manager; it will be recreated when
     * next retrieved.
     */
    public void setRemoteFallback(@Nullable FallbackRemoteRepoLoader fallbackSdk) {
        synchronized (MANAGER_LOCK) {
            mRemoteFallback = fallbackSdk;
            mRepoManager = null;
        }
    }

    /**
     * @return The {@link SchemaModule} containing the common sdk-specific metadata. See
     * sdk-common-XX.xsd.
     */
    @NonNull
    public SchemaModule getCommonModule(@NonNull ProgressIndicator progress) {
        return getRepoConfig(progress).getCommonModule();
    }

    /**
     * @return The {@link SchemaModule} containing the metadata for addon-type {@link Repository}s.
     * See sdk-addon-XX.xsd.
     */
    @NonNull
    public SchemaModule getAddonModule(@NonNull ProgressIndicator progress) {
        return getRepoConfig(progress).getAddonModule();
    }

    /**
     * @return The {@link SchemaModule} containing the metadata for the primary android SDK {@link
     * Repository} (containin platforms etc.). See sdk-repository-XX.xsd.
     */
    @NonNull
    public SchemaModule getRepositoryModule(@NonNull ProgressIndicator progress) {
        return getRepoConfig(progress).getRepositoryModule();
    }

    /**
     * @return The {@link SchemaModule} containing the metadata for system image-type {@link
     * Repository}s. See sdk-sys-img-XX.xsd.
     */
    @NonNull
    public SchemaModule getSysImgModule(@NonNull ProgressIndicator progress) {
        return getRepoConfig(progress).getSysImgModule();
    }

    @NonNull
    @VisibleForTesting
    RemoteListSourceProvider getRemoteListSourceProvider(@NonNull ProgressIndicator progress) {
        return getRepoConfig(progress).getRemoteListSourceProvider();
    }

    @NonNull
    public RepositorySourceProvider getUserSourceProvider(@NonNull ProgressIndicator progress) {
        return getRepoConfig(progress).getUserSourceProvider();
    }

    @NonNull
    private RepoConfig getRepoConfig(@NonNull ProgressIndicator progress) {
        if (mRepoConfig == null) {
            mRepoConfig = new RepoConfig(progress);
        }
        return mRepoConfig;
    }

    /**
     * Class containing the repository configuration we can (lazily) create statically. as well
     * as a method to create a new {@link RepoManager} based on that configuration.
     */
    private class RepoConfig {

        /**
         * Schema module containing the package type information to be used in addon repos.
         */
        private SchemaModule mAddonModule;

        /**
         * Schema module containing the package type information to be used in the primary repo.
         */
        private SchemaModule mRepositoryModule;

        /**
         * Schema module containing the package type information to be used in system image repos.
         */
        private SchemaModule mSysImgModule;

        /**
         * Common schema module used by the other sdk-specific modules.
         */
        private SchemaModule mCommonModule;

        /**
         * Provider for a list of {@link RepositorySource}s fetched from the google.
         */
        private RemoteListSourceProvider mAddonsListSourceProvider;

        /**
         * Provider for user-specified {@link RepositorySource}s.
         */
        private LocalSourceProvider mUserSourceProvider;

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
            try {
                mAddonModule = new SchemaModule(
                        "com.android.sdklib.repositoryv2.generated.addon.v%d.ObjectFactory",
                        "sdk-addon-%02d.xsd", AndroidSdkHandler.class);
                mRepositoryModule = new SchemaModule(
                        "com.android.sdklib.repositoryv2.generated.repository.v%d.ObjectFactory",
                        "sdk-repository-%02d.xsd", AndroidSdkHandler.class);
                mSysImgModule = new SchemaModule(
                        "com.android.sdklib.repositoryv2.generated.sysimg.v%d.ObjectFactory",
                        "sdk-sys-img-%02d.xsd", AndroidSdkHandler.class);
                mCommonModule = new SchemaModule(
                        "com.android.sdklib.repositoryv2.generated.common.v%d.ObjectFactory",
                        "sdk-common-%02d.xsd", AndroidSdkHandler.class);

                // Schema module for the list of update sites we download
                SchemaModule addonListModule = new SchemaModule(
                        "com.android.sdklib.repositoryv2.sources.generated.v%d.ObjectFactory",
                        "sdk-sites-list-%d.xsd", RemoteSiteType.class);

                try {
                    // Specify what modules are allowed to be used by what sites.
                    Map<Class<? extends RepositorySource>, Collection<SchemaModule>> siteTypes =
                            ImmutableMap
                                    .<Class<? extends RepositorySource>, Collection<SchemaModule>>builder()
                                    .put(RemoteSiteType.AddonSiteType.class,
                                            ImmutableSet.of(mAddonModule))
                                    .put(RemoteSiteType.SysImgSiteType.class,
                                            ImmutableSet.of(mSysImgModule)).build();
                    mAddonsListSourceProvider = RemoteListSourceProvider
                            .create(getAddonListUrl(progress), addonListModule, siteTypes);
                } catch (URISyntaxException e) {
                    progress.logError("Failed to set up addons source provider", e);
                }

            } catch (InstantiationException e) {
                progress.logError("Error setting up schema modules", e);
            } catch (URISyntaxException e) {
                progress.logError("Error setting up schema modules", e);
            }

            String url = System.getenv(SDK_TEST_BASE_URL_ENV_VAR);
            if (url == null || url.length() <= 0 || !url.endsWith("/")) {
                url = String.format("%srepository2-%d.xml", URL_GOOGLE_SDK_SITE,
                        mRepositoryModule.getNamespaceVersionMap().size());
            }

            mRepositorySourceProvider = new ConstantSourceProvider(url, "Android Repository",
                    ImmutableSet.of(mRepositoryModule));

            url = System.getenv("SDK_TEST_BASE_URL");
            if (url == null || url.length() <= 0 || !url.endsWith("/")) {
                url = String.format("%srepository-%d.xml", URL_GOOGLE_SDK_SITE,
                        LATEST_LEGACY_VERSION);
            }

            mLegacyRepositorySourceProvider = new ConstantSourceProvider(url,
                    "Legacy Android Repository", ImmutableSet.of(
                    mRepositoryModule));
            try {
                mUserSourceProvider = new LocalSourceProvider(new File(
                        AndroidLocation.getFolder(), LOCAL_ADDONS_FILENAME), ImmutableList
                        .of(mSysImgModule, mAddonModule), mFop);
            } catch (AndroidLocation.AndroidLocationException e) {
                progress.logWarning("Couldn't find android folder", e);
            }
        }

        @NonNull
        private String getAddonListUrl(@NonNull ProgressIndicator progress) {
            String url = URL_GOOGLE_SDK_SITE + DEFAULT_SITE_LIST_FILENAME_PATTERN;
            String baseUrl = System.getenv("SDK_TEST_BASE_URL");
            if (baseUrl != null) {
                if (!baseUrl.isEmpty() && baseUrl.endsWith("/")) {
                    if (url.startsWith(URL_GOOGLE_SDK_SITE)) {
                        url = baseUrl + url.substring(URL_GOOGLE_SDK_SITE.length());
                    }
                } else {
                    progress.logWarning("Ignoring invalid SDK_TEST_BASE_URL: " + baseUrl);
                }
            }

            return url;
        }

        @NonNull
        public SchemaModule getCommonModule() {
            return mCommonModule;
        }

        @NonNull
        public SchemaModule getAddonModule() {
            return mAddonModule;
        }

        @NonNull
        public SchemaModule getRepositoryModule() {
            return mRepositoryModule;
        }

        @NonNull
        public SchemaModule getSysImgModule() {
            return mSysImgModule;
        }

        @NonNull
        public RemoteListSourceProvider getRemoteListSourceProvider() {
            return mAddonsListSourceProvider;
        }

        @NonNull
        public LocalSourceProvider getUserSourceProvider() {
            return mUserSourceProvider;
        }

        @NonNull
        public RepoManager createRepoManager() {
            RepoManager result = RepoManager.create(mFop);

            // Create the schema modules etc. if they haven't been already.
            result.registerSchemaModule(mAddonModule);
            result.registerSchemaModule(mRepositoryModule);
            result.registerSchemaModule(mSysImgModule);
            result.registerSchemaModule(mCommonModule);
            result.registerSourceProvider(mRepositorySourceProvider);
            result.registerSourceProvider(mLegacyRepositorySourceProvider);
            result.registerSourceProvider(mAddonsListSourceProvider);
            result.registerSourceProvider(mUserSourceProvider);

            // The customizable source provider needs a handle on the repo manager, so it can
            // mark the cached packages invalid if the sources change.
            mUserSourceProvider.setRepoManager(result);

            result.setLocalPath(mLocation);

            // If we have a local sdk path set, set up the old-style loader so we can parse
            // any legacy packages.
            if (mLocation != null) {
                result.setFallbackLocalRepoLoader(
                        new LegacyLocalRepoLoader(mLocation, mFop, result));
            }

            result.setFallbackRemoteRepoLoader(mRemoteFallback);
            mUserSourceProvider.setRepoManager(result);
            return result;
        }
    }
}
