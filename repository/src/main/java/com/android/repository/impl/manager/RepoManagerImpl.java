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

package com.android.repository.impl.manager;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.repository.api.Downloader;
import com.android.repository.api.FallbackLocalRepoLoader;
import com.android.repository.api.FallbackRemoteRepoLoader;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.ProgressRunner;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoManager;
import com.android.repository.api.RepoPackage;
import com.android.repository.api.RepositorySource;
import com.android.repository.api.RepositorySourceProvider;
import com.android.repository.api.SchemaModule;
import com.android.repository.api.SettingsController;
import com.android.repository.impl.meta.RepositoryPackages;
import com.android.repository.impl.meta.SchemaModuleUtil;
import com.android.repository.io.FileOp;
import com.android.repository.io.impl.FileOpImpl;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

import org.w3c.dom.ls.LSResourceResolver;

import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Semaphore;

/**
 * Main implementation of {@link RepoManager}. Loads local and remote {@link RepoPackage}s
 * synchronously and asynchronously into a {@link RepositoryPackages} instance from the given local
 * path and from the registered {@link RepositorySourceProvider}s, using the registered {@link
 * SchemaModule}s.
 */
public class RepoManagerImpl extends RepoManager {

    /**
     * Pattern for fully-qualified name of the {@code ObjectFactory} used in {@link
     * #mCommonModule}.
     */
    private static final String COMMON_OBJECT_FACTORY_PATTERN
            = "com.android.repository.impl.generated.v%d.ObjectFactory";

    /**
     * Pattern for name of the xsd file used in {@link #mCommonModule}.
     */
    public static final String COMMON_XSD_PATTERN = "repo-common-%02d.xsd";

    /**
     * The registered {@link SchemaModule}s.
     */
    private final Set<SchemaModule> mModules = Sets.newHashSet();

    /**
     * The {@link FallbackLocalRepoLoader} to use when loading local packages.
     */
    @Nullable
    private FallbackLocalRepoLoader mFallbackLocalRepoLoader;

    /**
     * The path under which to look for installed packages.
     */
    @Nullable
    private File mLocalPath;

    /**
     * The {@link FallbackRemoteRepoLoader} to use if the normal {@link RemoteRepoLoader} can't
     * understand a downloaded repository xml file.
     */
    @Nullable
    private FallbackRemoteRepoLoader mFallbackRemoteRepoLoader;

    /**
     * The {@link RepositorySourceProvider}s from which to get {@link RepositorySource}s to load
     * from.
     */
    private Set<RepositorySourceProvider> mSourceProviders = Sets.newHashSet();

    /**
     * The loaded packages.
     */
    private RepositoryPackages mPackages = new RepositoryPackages();

    /**
     * When a load last happened.
     */
    private long mLastRefreshMs;

    /**
     * The task used to load packages. If non-null, a load is currently in progress.
     */
    private LoadTask mTask;

    /**
     * Lock used when setting {@link #mTask}.
     */
    private final Object mTaskLock = new Object();

    /**
     * The base {@link SchemaModule} that is created by {@code RepoManagerImpl} itself.
     */
    private final SchemaModule mCommonModule;

    /**
     * {@link FileOp} to be used for local file operations. Should be {@link FileOpImpl} for normal
     * operation.
     */
    private final FileOp mFop;

    /**
     * Create a new {@code RepoManagerImpl}. Before anything can be loaded, at least a local path
     * and/or at least one {@link RepositorySourceProvider} must be set.
     *
     * @param fop {@link FileOp} to use for local file operations. Should only be null if you're
     *            never planning to load a local repo using this {@code RepoManagerImpl}.
     */
    public RepoManagerImpl(@Nullable FileOp fop) {
        mFop = fop;
        SchemaModule m = null;
        try {
            m = new SchemaModule(COMMON_OBJECT_FACTORY_PATTERN, COMMON_XSD_PATTERN,
                    RepoManager.class);
        } catch (Exception e) {
            // This should never happen unless there's something wrong with the common repo schema.
            assert false : "Failed to create RepoManager: " + e;
        }
        mCommonModule = m;
        registerSchemaModule(mCommonModule);
    }

    @Nullable
    @Override
    public File getLocalPath() {
        return mLocalPath;
    }

    /**
     * {@inheritDoc} This calls {@link  #markInvalid()}, so a complete load will occur the next time
     * {@link #load(long, List, List, List, boolean, ProgressRunner, Downloader,
     * SettingsController, boolean)} is called.
     */
    @Override
    public void setFallbackLocalRepoLoader(@Nullable FallbackLocalRepoLoader fallback) {
        mFallbackLocalRepoLoader = fallback;
        markInvalid();
    }

    /**
     * {@inheritDoc} This calls {@link  #markInvalid()}, so a complete load will occur the next time
     * {@link #load(long, List, List, List, boolean, ProgressRunner, Downloader,
     * SettingsController, boolean)} is called.
     */
    @Override
    public void setFallbackRemoteRepoLoader(@Nullable FallbackRemoteRepoLoader remote) {
        mFallbackRemoteRepoLoader = remote;
        markInvalid();
    }

    /**
     * {@inheritDoc} This calls {@link  #markInvalid()}, so a complete load will occur the next time
     * {@link #load(long, List, List, List, boolean, ProgressRunner, Downloader,
     * SettingsController, boolean)} is called.
     */
    @Override
    public void setLocalPath(@Nullable File path) {
        mLocalPath = path;
        markInvalid();
    }

    @Override
    @NonNull
    public SchemaModule getCommonModule() {
        return mCommonModule;
    }

    /**
     * {@inheritDoc} This calls {@link  #markInvalid()}, so a complete load will occur the next time
     * {@link #load(long, List, List, List, boolean, ProgressRunner, Downloader,
     * SettingsController, boolean)} is called.
     */
    @Override
    public void registerSourceProvider(@NonNull RepositorySourceProvider provider) {
        mSourceProviders.add(provider);
        markInvalid();
    }

    @VisibleForTesting
    @Override
    @NonNull
    public Set<RepositorySourceProvider> getSourceProviders() {
        return mSourceProviders;
    }

    @Override
    @NonNull
    public Set<RepositorySource> getSources(@Nullable Downloader downloader,
            @Nullable SettingsController settings, @Nullable ProgressIndicator progress,
            boolean forceRefresh) {
        Set<RepositorySource> result = Sets.newHashSet();
        for (RepositorySourceProvider provider : mSourceProviders) {
            result.addAll(provider.getSources(downloader, settings, progress, forceRefresh));
        }
        return result;
    }

    @Override
    @NonNull
    public Set<SchemaModule> getSchemaModules() {
        return mModules;
    }

    /**
     * {@inheritDoc} This calls {@link  #markInvalid()}, so a complete load will occur the next time
     * {@link #load(long, List, List, List, boolean, ProgressRunner, Downloader,
     * SettingsController, boolean)} is called.
     */
    @Override
    public void registerSchemaModule(@NonNull SchemaModule module) {
        mModules.add(module);
        markInvalid();
    }

    @Override
    public void markInvalid() {
        mLastRefreshMs = 0;
    }

    @Override
    @Nullable
    public LSResourceResolver getResourceResolver(@NonNull  ProgressIndicator progress) {
        List<SchemaModule> allModules = ImmutableList.<SchemaModule>builder().addAll(
                getSchemaModules()).add(
                getCommonModule()).build();
        return SchemaModuleUtil.createResourceResolver(allModules, progress);
    }

    @Override
    @NonNull
    public RepositoryPackages getPackages() {
        return mPackages;
    }

    // TODO: fix up invalidation. It's annoying that you have to manually reload.
    // TODO: Maybe: when you load, instead of load as now, you get back a loader, which knows how
    // TODO: to reload with same settings,
    // TODO: and contains current valid or invalid packages as they are cached here.
    @Override
    public boolean load(long cacheExpirationMs,
            @NonNull List<RepoLoadedCallback> onLocalComplete,
            @NonNull List<RepoLoadedCallback> onSuccess,
            @NonNull List<Runnable> onError,
            boolean forceRefresh,
            @NonNull ProgressRunner runner,
            @Nullable Downloader downloader,
            @Nullable SettingsController settings,
            boolean sync) {
        // If we're not going to refresh, just run the callbacks.
        if (!forceRefresh && System.currentTimeMillis() - mLastRefreshMs < cacheExpirationMs) {
            for (RepoLoadedCallback localComplete : onLocalComplete) {
                runner.runSyncWithoutProgress(new CallbackRunnable(localComplete, mPackages));
            }
            for (RepoLoadedCallback success : onSuccess) {
                runner.runSyncWithoutProgress(new CallbackRunnable(success, mPackages));
            }
            // false: we didn't actually reload.
            return false;
        }

        final Semaphore completed = new Semaphore(1);
        try {
            completed.acquire();
        } catch (InterruptedException e) {
            // shouldn't happen.
        }
        if (sync) {
            // If we're running synchronously, release the semaphore after run complete.
            onSuccess = Lists.newArrayList(onSuccess);
            onSuccess.add(new RepoLoadedCallback() {
                @Override
                public void doRun(@NonNull RepositoryPackages packages) {
                    completed.release();
                }
            });
            onError = Lists.newArrayList(onError);
            onError.add(new Runnable() {
                @Override
                public void run() {
                    completed.release();
                }
            });
        }

        // If we created the currently running task, we need to clean it up at the end.
        boolean createdTask = false;

        try {
            synchronized (mTaskLock) {
                if (mTask != null) {
                    // If there's a task running already, just add our callbacks to it.
                    mTask.addCallbacks(onLocalComplete, onSuccess, onError);
                } else {
                    // Otherwise, create a new task.
                    mTask = new LoadTask(onLocalComplete, onSuccess, onError,
                            downloader, settings);
                    createdTask = true;
                }
            }

            if (createdTask) {
                // If we created a task, run it.
                if (sync) {
                    runner.runSyncWithProgress(mTask);
                } else {
                    runner.runAsyncWithProgress(mTask);
                }
            } else if (sync) {
                // Otherwise wait for the semaphore to be released by the callback if we're
                // running synchronously.
                try {
                    completed.acquire();
                } catch (InterruptedException e) {
                    // shouldn't happen
                }
            }
        } finally {
            if (createdTask) {
                // If we created a task, clean it up.
                mTask = null;
            }
        }

        return true;
    }

    /**
     * A task to load the local and remote repos.
     */
    private class LoadTask implements ProgressRunner.ProgressRunnable {

        private final List<RepoLoadedCallback> mOnSuccesses = Lists.newArrayList();

        private final List<Runnable> mOnErrors = Lists.newArrayList();

        private final List<RepoLoadedCallback> mOnLocalCompletes = Lists.newArrayList();

        private final Downloader mDownloader;

        private final SettingsController mSettings;

        public LoadTask(@NonNull List<RepoLoadedCallback> onLocalComplete,
                @NonNull List<RepoLoadedCallback> onSuccess,
                @NonNull List<Runnable> onError,
                @Nullable Downloader downloader,
                @Nullable SettingsController settings) {
            addCallbacks(onLocalComplete, onSuccess, onError);
            mDownloader = downloader;
            mSettings = settings;
        }

        /**
         * Add callbacks to this task (if e.g. {@link #load(long, List, List, List,
         * boolean, ProgressRunner, Downloader, SettingsController, boolean)} is called again while
         * a task is already running.
         */
        public void addCallbacks(@NonNull List<RepoLoadedCallback> onLocalComplete,
                @NonNull List<RepoLoadedCallback> onSuccess,
                @NonNull List<Runnable> onError) {
            mOnLocalCompletes.addAll(onLocalComplete);
            mOnSuccesses.addAll(onSuccess);
            mOnErrors.addAll(onError);
        }

        /**
         * Do the actual load.
         *
         * @param indicator {@link ProgressIndicator} for logging and showing actual progress
         * @param runner    {@link ProgressRunner} for running asynchronous tasks and callbacks.
         */
        @Override
        public void run(@NonNull ProgressIndicator indicator, @NonNull ProgressRunner runner) {
            boolean success = false;
            try {
                RepositoryPackages packages = new RepositoryPackages();

                if (mLocalPath != null) {
                    LocalRepoLoader local = new LocalRepoLoader(mLocalPath, RepoManagerImpl.this,
                            mFallbackLocalRepoLoader, mFop);
                    indicator.setText("Loading local repository...");
                    packages.setLocalPkgInfos(local.getPackages(indicator));
                    indicator.setFraction(0.25);
                }
                if (indicator.isCanceled()) {
                    return;
                }
                synchronized (mTaskLock) {
                    for (RepoLoadedCallback onLocalComplete : mOnLocalCompletes) {
                        runner.runSyncWithoutProgress(
                                new CallbackRunnable(onLocalComplete, packages));
                    }
                    mOnLocalCompletes.clear();
                }
                indicator.setText("Fetch remote repository...");
                indicator.setSecondaryText("");

                if (!mSourceProviders.isEmpty() && mDownloader != null) {
                    RemoteRepoLoader remoteLoader = new RemoteRepoLoader(mSourceProviders,
                            getResourceResolver(indicator), mFallbackRemoteRepoLoader);
                    Multimap<String, RemotePackage> remotes = remoteLoader
                            .fetchPackages(indicator, mDownloader, mSettings);
                    indicator.setText("Computing updates...");
                    indicator.setFraction(0.75);
                    packages.setRemotePkgInfos(remotes);
                }

                mPackages = packages;
                if (indicator.isCanceled()) {
                    return;
                }
                indicator.setSecondaryText("");
                indicator.setFraction(1.0);

                if (indicator.isCanceled()) {
                    return;
                }
                success = true;
                mLastRefreshMs = System.currentTimeMillis();
            } finally {
                mLastRefreshMs = System.currentTimeMillis();
                synchronized (mTaskLock) {
                    // The processing of the task is now complete.
                    // To ensure that no more callbacks are added, and to allow another task to be
                    // kicked off when needed, set mTask to null.
                    mTask = null;
                    if (success) {
                        for (final RepoLoadedCallback onLocalComplete : mOnLocalCompletes) {
                            // in case some were added by another call in the interim.
                            runner.runSyncWithoutProgress(
                                    new CallbackRunnable(onLocalComplete, mPackages));
                        }
                        for (final RepoLoadedCallback onSuccess : mOnSuccesses) {
                            runner.runSyncWithoutProgress(
                                    new CallbackRunnable(onSuccess, mPackages));
                        }
                    } else {
                        for (final Runnable onError : mOnErrors) {
                            onError.run();
                        }
                    }
                }
            }
        }
    }

    /**
     * A {@link Runnable} that wraps a {@link RepoLoadedCallback} and calls it with the
     * appropriate args.
     */
    private static class CallbackRunnable implements Runnable {

        RepoLoadedCallback mCallback;

        RepositoryPackages mPackages;

        public CallbackRunnable(@NonNull RepoLoadedCallback callback,
                @NonNull RepositoryPackages packages) {
            mCallback = callback;
            mPackages = packages;
        }

        @Override
        public void run() {
            mCallback.doRun(mPackages);
        }
    }
}
