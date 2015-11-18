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

package com.android.repository.api;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.repository.impl.manager.RepoManagerImpl;
import com.android.repository.impl.meta.RepositoryPackages;
import com.android.repository.io.FileOp;
import com.android.repository.io.FileOpUtils;

import org.w3c.dom.ls.LSResourceResolver;

import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Primary interface for interacting with repository packages.
 *
 * To set up an {@code RepoManager}:
 * <ul>
 *     <li>
 *         Register the {@link SchemaModule}s used to parse the package.xml files and
 *         remote repositories used by this repo using {@link
 *         #registerSchemaModule(SchemaModule)}
 *     </li>
 *     <li>
 *         Set the path where the repo is installed locally using {@link #setLocalPath(File)}.
 *     </li>
 *     <li>
 *         If your local repo might contain packages created by a previous system, set a
 *         {@link FallbackLocalRepoLoader} that can recognize and convert those packages using
 *         {@link #setFallbackLocalRepoLoader(FallbackLocalRepoLoader)}.
 *     </li>
 *     <li>
 *         Add {@link RepositorySourceProvider}s to provide URLs for remotely-available packages.
 *     </li>
 *     <li>
 *         If some sources might be in a format used by a previous system, set a {@link
 *         FallbackRemoteRepoLoader} that can read and convert them.
 *     </li>
 * </ul>
 * <p>
 * To load the local and remote packages, use {@link #load(long, List, List, List, boolean,
 * ProgressRunner, Downloader, SettingsController, boolean)}
 * <br>
 * TODO: it would be nice if this could be redesigned such that load didn't need to be called
 * explicitly, or there was a better way to know if packages were or need to be loaded.
 * <p>
 * To use the loaded packages, get an {@link RepositoryPackages} object from {@link #getPackages()}.
 */
public abstract class RepoManager {

    /**
     * After loading the repository, this is the amount of time that must pass before we consider it
     * to be stale and need to be reloaded.
     */
    public static final long DEFAULT_EXPIRATION_PERIOD_MS = TimeUnit.DAYS.toMillis(1);


    /**
     * @param fop The {@link FileOp} to use for local filesystem operations. Probably
     *            {@link FileOpUtils#create()} unless part of a unit test.
     * @return A new {@code RepoManager}.
     */
    @NonNull
    public static RepoManager create(@NonNull FileOp fop) {
        return new RepoManagerImpl(fop);
    }

    /**
     * Register an {@link SchemaModule} that can be used when parsing XML for this repo.
     */
    public abstract void registerSchemaModule(@NonNull SchemaModule module);

    /**
     * Gets the currently-registered {@link SchemaModule}s. This probably shouldn't be used except
     * by code within the RepoManager or unit tests.
     */
    @NonNull
    public abstract Set<SchemaModule> getSchemaModules();

    /**
     * Gets the core {@link SchemaModule} created by the RepoManager itself. Contains the base
     * definition of repository, package, revision, etc.
     */
    @NonNull
    public abstract SchemaModule getCommonModule();

    /**
     * Sets the path to the local repository root.
     */
    public abstract void setLocalPath(@Nullable File path);

    /**
     * Gets the path to the local repository root. This probably shouldn't be needed except by the
     * repository manager and unit tests.
     */
    @Nullable
    public abstract File getLocalPath();

    /**
     * Sets the {@link FallbackLocalRepoLoader} to use when scanning the local repository for
     * packages.
     */
    public abstract void setFallbackLocalRepoLoader(@Nullable FallbackLocalRepoLoader local);

    /**
     * Adds a {@link RepositorySourceProvider} from which to get {@link RepositorySource}s from
     * which to download lists of available repository packages.
     */
    public abstract void registerSourceProvider(@NonNull RepositorySourceProvider provider);

    /**
     * Gets the currently registered {@link RepositorySourceProvider}s. Should only be needed for
     * testing.
     */
    @NonNull
    @VisibleForTesting
    public abstract Set<RepositorySourceProvider> getSourceProviders();

    /**
     * Gets the actual {@link RepositorySource}s from the registered {@link
     * RepositorySourceProvider}s.
     *
     * Probably should only be needed by a repository UI.
     *
     * @param downloader   The {@link Downloader} to use for downloading source lists, if needed.
     * @param settings     The settings to use when downloading or reading source lists.
     * @param progress     A {@link ProgressIndicator} for source providers to use to show their
     *                     progress and for logging.
     * @param forceRefresh Individual {@link RepositorySourceProvider}s may cache their results. If
     *                     {@code forceRefresh} is true, specifies that they should reload rather
     *                     than returning cached results.
     * @return The {@link RepositorySource}s obtained from the providers.
     */
    public abstract Set<RepositorySource> getSources(@Nullable Downloader downloader,
            @Nullable SettingsController settings, @NonNull ProgressIndicator progress,
            boolean forceRefresh);


    /**
     * Sets the {@link FallbackRemoteRepoLoader} to try when we encounter a remote xml file that the
     * RepoManger can't read.
     */
    public abstract void setFallbackRemoteRepoLoader(@Nullable FallbackRemoteRepoLoader remote);

    /**
     * Load the local and remote repositories.
     *
     * In callbacks, be careful of invoking tasks synchronously on other threads (e.g. the swing ui
     * thread), since they might also be used by the {@link ProgressRunner) passed in.
     *
     * @param cacheExpirationMs How long must have passed since the last load for us to reload even
     *                          if {@code forceRefresh} isn't specified.
     * @param onLocalComplete   When loading, the local repo load happens first, and should be
     *                          relatively fast. When complete, the {@code onLocalComplete} {@link
     *                          RepoLoadedCallback}s are run. Will be called with a {@link
     *                          RepositoryPackages} that contains only the local packages.
     * @param onSuccess         Callbacks that are run when the entire load (local and remote) has
     *                          completed successfully. Called with an {@link RepositoryPackages}
     *                          containing both the local and remote packages.
     * @param onError           Callbacks that are run when there's an error at some point during
     *                          the load.
     * @param forceRefresh      If true, reload the local and remote packages even if {@code
     *                          cacheExpirationMs} has not passed.
     * @param runner            The {@link ProgressRunner} to use for any tasks started during the
     *                          load, including running the callbacks.
     * @param downloader        The {@link Downloader} to use for downloading remote files,
     *                          including any remote list of repo sources and the remote
     *                          repositories themselves.
     * @param settings          The settings to use during the load, including for example proxy
     *                          settings used when fetching remote files.
     * @param sync              If true, load synchronously. If false, load asynchronously (this
     *                          method should return quickly, and the {@code onSuccess} callbacks
     *                          can be used to process the completed results).
     * @return {@code true} if a load was performed. {@code false} if cached results were fresh
     * enough.
     */
    public abstract boolean load(long cacheExpirationMs,
            @NonNull List<RepoLoadedCallback> onLocalComplete,
            @NonNull List<RepoLoadedCallback> onSuccess,
            @NonNull List<Runnable> onError,
            boolean forceRefresh,
            @NonNull ProgressRunner runner,
            @Nullable Downloader downloader,
            @Nullable SettingsController settings,
            boolean sync);

    /**
     * Causes cached results to be considered expired. The next time {@link #load(long, List, List,
     * List, boolean, ProgressRunner, Downloader, SettingsController, boolean)} is called, a
     * complete load will be done.
     */
    public abstract void markInvalid();

    /**
     * Gets the currently-loaded {@link RepositoryPackages}.
     */
    @NonNull
    public abstract RepositoryPackages getPackages();

    /**
     * Gets an {@link LSResourceResolver} that can find the XSDs for all versions of the
     * currently-registered {@link SchemaModule}s by namespace.
     */
    @NonNull
    public abstract LSResourceResolver getResourceResolver(@NonNull ProgressIndicator progress);

    /**
     * Callback for when repository load is completed/partially completed.
     */
    public interface RepoLoadedCallback {

        /**
         * @param packages The packages that have been loaded so far. When this callback is used in
         *                 the {@code onLocalComplete} argument to {@link #load(long, List, List,
         *                 List, boolean, ProgressRunner, Downloader, SettingsController,
         *                 boolean)} {@code packages} will only include local packages.
         */
        void doRun(@NonNull RepositoryPackages packages);
    }
}
