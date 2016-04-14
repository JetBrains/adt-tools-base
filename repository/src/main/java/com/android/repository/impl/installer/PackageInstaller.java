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

package com.android.repository.impl.installer;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.api.Downloader;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoManager;
import com.android.repository.api.SettingsController;
import com.android.repository.io.FileOp;
import com.android.repository.io.FileOpUtils;

/**
 * A facility for downloading and installing and uninstalling packages.
 */
public interface PackageInstaller {

    /**
     * Uninstall the package.
     *
     * @param p        The {@link LocalPackage} to delete.
     * @param progress A {@link ProgressIndicator} for showing progress and facilitating logging.
     * @param manager  A {@link RepoManager} that knows about this package.
     * @param fop      The {@link FileOp} to use. Should be {@link FileOpUtils#create()} if not in a
     *                 unit test.
     * @return {@code true} if the uninstall was successful, {@code false} otherwise.
     */
    boolean uninstall(@NonNull LocalPackage p, @NonNull ProgressIndicator progress,
            @NonNull RepoManager manager, @NonNull FileOp fop);

    /**
     * Installs the package.
     *
     * @param p          The {@link RemotePackage} to install.
     * @param downloader The {@link Downloader} used to download the archive.
     * @param settings   The {@link SettingsController} to provide any settings needed.
     * @param progress   A {@link ProgressIndicator}, to show install progress and facilitate
     *                   logging.
     * @param manager    A {@link RepoManager} that knows about this package.
     * @param fop        The {@link FileOp} to use. Should be {@link FileOpUtils#create()} if not in
     *                   a unit test.
     * @return {@code true} if the install was successful, {@code false} otherwise.
     */
    boolean install(@NonNull RemotePackage p, @NonNull Downloader downloader,
            @Nullable SettingsController settings, @NonNull ProgressIndicator progress,
            @NonNull RepoManager manager, @NonNull FileOp fop);
}
