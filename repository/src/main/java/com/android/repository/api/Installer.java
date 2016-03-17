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

package com.android.repository.api;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;

/**
 * An installer for SDK components.
 *
 * @see Uninstaller
 * @see InstallerFactory
 */
public interface Installer extends PackageOperation {

    /**
     * {@inheritDoc}
     *
     * Installers operate on remote (not yet installed) packages.
     */
    @Override
    @NonNull
    RemotePackage getPackage();

    /**
     * Completes the installation. This should include anything that actually affects the installed
     * SDK or requires user interaction.
     *
     * @param progress A {@link ProgressIndicator}, to show install progress and facilitate
     *                 logging.
     * @return {@code true} if the install was successful, {@code false} otherwise.
     */
    boolean completeInstall(@NonNull ProgressIndicator progress);

    /**
     * Prepares the package for installation. This includes downloading, unzipping, and anything
     * else that can be done without affecting the installed SDK or other state.
     *
     * @param downloader The {@link Downloader} used to download the archive.
     * @param settings   The {@link SettingsController} to provide any settings needed.
     * @param progress   A {@link ProgressIndicator}, to show install progress and facilitate
     *                   logging.
     * @return {@code true} if the operation succeeded, {@code false} otherwise.
     */
    boolean prepareInstall(@NonNull Downloader downloader,
            @Nullable SettingsController settings,
            @NonNull ProgressIndicator progress);
}
