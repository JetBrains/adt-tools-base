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
import com.android.repository.api.Downloader;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.SettingsController;
import com.android.repository.io.FileOp;
import com.android.sdklib.internal.repository.CanceledByUserException;
import com.android.sdklib.internal.repository.DownloadCache;
import com.android.utils.Pair;
import com.google.common.io.ByteStreams;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;

/**
 * A {@link Downloader} implementation that uses the old {@link DownloadCache}.
 *
 * TODO: Implement a new, fully-featured downloader.
 */
public class LegacyDownloader implements Downloader {

    private DownloadCache mDownloadCache;

    private FileOp mFileOp;

    public LegacyDownloader(@NonNull FileOp fop) {
        mDownloadCache = new DownloadCache(fop, DownloadCache.Strategy.FRESH_CACHE);
        mFileOp = fop;
    }

    @Override
    @Nullable
    public InputStream downloadAndStream(@NonNull URL url, @Nullable SettingsController controller,
            @NonNull ProgressIndicator indicator) throws IOException {
        try {
            return mDownloadCache.openCachedUrl(url.toString(), new LegacyTaskMonitor(indicator));
        } catch (CanceledByUserException e) {
            indicator.logInfo("The download was cancelled.");
        }
        return null;
    }

    @Nullable
    @Override
    public File downloadFully(@NonNull URL url, @Nullable SettingsController settings,
            @NonNull ProgressIndicator indicator)
            throws IOException {
        File result = File
                .createTempFile("LegacyDownloader", Long.toString(System.currentTimeMillis()));
        OutputStream out = mFileOp.newFileOutputStream(result);
        try {
            Pair<InputStream, Integer> downloadedResult = mDownloadCache
                    .openDirectUrl(url.toString(), new LegacyTaskMonitor(indicator));
            if (downloadedResult.getSecond() == 200) {
                ByteStreams.copy(downloadedResult.getFirst(), out);
                out.close();
                return result;
            }
        } catch (CanceledByUserException e) {
            indicator.logInfo("The download was cancelled.");
        }
        return null;
    }


}
