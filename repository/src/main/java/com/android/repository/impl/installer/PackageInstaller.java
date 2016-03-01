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

    enum InstallStatus {
        /** This installer hasn't started yet */
        NOT_STARTED,
        /**
         * This installer is in the process of preparing the component for install. No changes are
         * made to the SDK during this phase.
         */
        PREPARING,
        /** The steps that can be taken without affecting the installed SDK have completed. */
        PREPARED,
        /** The SDK is being modified. */
        INSTALLING,
        /** The installation has completed. */
        COMPLETE
    }

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
     * Completes the installation. This should include anything that actually affects the installed
     * SDK or requires user interaction.
     *
     * @param p          The {@link RemotePackage} to install.
     * @param progress   A {@link ProgressIndicator}, to show install progress and facilitate
     *                   logging.
     * @param manager    A {@link RepoManager} that knows about this package.
     * @param fop        The {@link FileOp} to use. Should be {@link FileOpUtils#create()} if not in
     *                   a unit test.
     * @return {@code true} if the install was successful, {@code false} otherwise.
     */
    boolean completeInstall(@NonNull RemotePackage p, @NonNull ProgressIndicator progress,
            @NonNull RepoManager manager, @NonNull FileOp fop);


    /**
     * Prepares the package for installation. This includes downloading, unzipping, and anything
     * else that can be done without affecting the installed SDK or other state.
     * @param p          The {@link RemotePackage} to install.
     * @param downloader The {@link Downloader} used to download the archive.
     * @param settings   The {@link SettingsController} to provide any settings needed.
     * @param progress   A {@link ProgressIndicator}, to show install progress and facilitate
     *                   logging.
     * @param manager    A {@link RepoManager} that knows about this package.
     * @param fop        The {@link FileOp} to use. Should be {@link FileOpUtils#create()} if not in
     *
     * @return
     */
    boolean prepareInstall(@NonNull RemotePackage p, @NonNull Downloader downloader,
            @Nullable SettingsController settings, @NonNull ProgressIndicator progress,
            @NonNull RepoManager manager, @NonNull FileOp fop);

    /**
     * Gets the current {@link InstallStatus} of this installer.
     */
    @NonNull
    InstallStatus getInstallStatus();

    /**
     * Registers a listener that will be called when the {@link InstallStatus} of this installer
     * changes.
     */
    void registerStateChangeListener(@NonNull StatusChangeListener listener);


    /**
     * A listener that will be called when the {@link #getInstallStatus() status} of this installer
     * changes.
     */
    interface StatusChangeListener {
        void statusChanged(@NonNull PackageInstaller installer);
    }
}

