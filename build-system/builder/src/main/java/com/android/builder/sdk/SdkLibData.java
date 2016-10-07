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

package com.android.builder.sdk;

import com.android.annotations.NonNull;
import com.android.repository.api.Downloader;
import com.android.repository.api.RepoManager;
import com.android.repository.api.SettingsController;
import com.google.common.base.Preconditions;

/**
 * Handles the components needed to download missing SDK components.
 */
public class SdkLibData {
    private Downloader mDownloader;
    private SettingsController mSettings;
    private boolean mUseSdkDownload = false;

    //the expiration period in milliseconds for the cached remote and local repositories.
    private long cacheExpirationPeriod = RepoManager.DEFAULT_EXPIRATION_PERIOD_MS;

    private SdkLibData() {};

    private SdkLibData(Downloader downloader, SettingsController settings) {
        this.mDownloader = downloader;
        this.mSettings = settings;
        this.mUseSdkDownload = true;
    }

    public static SdkLibData dontDownload() {
        return new SdkLibData();
    }

    public static SdkLibData download(
            @NonNull Downloader downloader,
            @NonNull SettingsController settings){
        return new SdkLibData(downloader, settings);
    }

    public boolean useSdkDownload() {
        return mUseSdkDownload;
    }

    /**
     * Returns the downloader used to download the missing SDK components. The downloader should be
     * used only when the mUseSdkDownload flag is set, therefore enabling this feature.
     */
    @NonNull
    public Downloader getDownloader() {
        Preconditions.checkState(mUseSdkDownload, "The downloader should not be used in this build.");
        return mDownloader;
    }

    /**
     * Returns the settings used to download the missing SDK components. The settings should be
     * used only when the mUseSdkDownload flag is set, therefore enabling this feature.
     */
    @NonNull
    public SettingsController getSettings() {
        Preconditions.checkState(mUseSdkDownload, "The settings should not be used in this build.");
        return mSettings;
    }

    public void setCacheExpirationPeriod(long cacheExpirationPeriod) {
        this.cacheExpirationPeriod = cacheExpirationPeriod;
    }

    public long getCacheExpirationPeriod() {
        return cacheExpirationPeriod;
    }
}
