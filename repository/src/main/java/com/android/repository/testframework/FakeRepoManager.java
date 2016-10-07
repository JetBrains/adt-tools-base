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
package com.android.repository.testframework;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.api.Downloader;
import com.android.repository.api.FallbackLocalRepoLoader;
import com.android.repository.api.FallbackRemoteRepoLoader;
import com.android.repository.api.PackageOperation;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.ProgressRunner;
import com.android.repository.api.RepoManager;
import com.android.repository.api.RepoPackage;
import com.android.repository.api.RepositorySource;
import com.android.repository.api.RepositorySourceProvider;
import com.android.repository.api.SchemaModule;
import com.android.repository.api.SettingsController;
import com.android.repository.impl.meta.RepositoryPackages;

import org.w3c.dom.ls.LSResourceResolver;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * A fake {@link RepoManager}, for use in unit tests.
 */
public class FakeRepoManager extends RepoManager {
    RepositoryPackages mPackages;

    public FakeRepoManager(RepositoryPackages packages) {
        mPackages = packages;
    }

    @Override
    public void registerSchemaModule(@NonNull SchemaModule module) {

    }

    @NonNull
    @Override
    public Set<SchemaModule> getSchemaModules() {
        return Collections.emptySet();
    }

    @Override
    public void setLocalPath(@Nullable File path) {

    }

    @Nullable
    @Override
    public File getLocalPath() {
        return null;
    }

    @Override
    public void setFallbackLocalRepoLoader(@Nullable FallbackLocalRepoLoader local) {

    }

    @Override
    public void registerSourceProvider(@NonNull RepositorySourceProvider provider) {

    }

    @NonNull
    @Override
    public Set<RepositorySourceProvider> getSourceProviders() {
        return Collections.emptySet();
    }

    @Override
    public Set<RepositorySource> getSources(@Nullable Downloader downloader,
            @NonNull ProgressIndicator progress, boolean forceRefresh) {
        return Collections.emptySet();
    }

    @Override
    public void setFallbackRemoteRepoLoader(@Nullable FallbackRemoteRepoLoader remote) {

    }

    @Override
    public boolean load(long cacheExpirationMs,
            @Nullable List<RepoLoadedCallback> onLocalComplete,
            @Nullable List<RepoLoadedCallback> onSuccess,
            @Nullable List<Runnable> onError, @NonNull ProgressRunner runner,
            @Nullable Downloader downloader, @Nullable SettingsController settings, boolean sync) {
        return false;
    }

    @Override
    public void markInvalid() {

    }

    @Override
    public void markLocalCacheInvalid() {

    }

    @Override
    public boolean reloadLocalIfNeeded(@NonNull ProgressIndicator progress) {
        return false;
    }

    @NonNull
    @Override
    public RepositoryPackages getPackages() {
        return mPackages;
    }

    @Nullable
    @Override
    public LSResourceResolver getResourceResolver(@NonNull ProgressIndicator progress) {
        return null;
    }

    @Override
    public void registerLocalChangeListener(@NonNull RepoLoadedCallback listener) {

    }

    @Override
    public void registerRemoteChangeListener(@NonNull RepoLoadedCallback listener) {

    }

    @Override
    public void installBeginning(@NonNull RepoPackage repoPackage,
            @NonNull PackageOperation installer) {

    }

    @Override
    public void installEnded(@NonNull RepoPackage repoPackage) {

    }

    @Nullable
    @Override
    public PackageOperation getInProgressInstallOperation(@NonNull RepoPackage remotePackage) {
        return null;
    }
}
