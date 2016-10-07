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
package com.android.sdklib.repository.legacy;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.api.Downloader;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.io.FileOp;
import com.android.sdklib.repository.legacy.remote.internal.DownloadCache;
import com.android.utils.Pair;
import com.google.common.io.ByteStreams;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;

/**
 * A {@link Downloader} implementation that uses the old {@link DownloadCache}.
 *
 * TODO: Implement a new, fully-featured downloader, then mark this as deprecated.
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
    public InputStream downloadAndStream(@NonNull URL url, @NonNull ProgressIndicator indicator)
            throws IOException {
        return mDownloadCache.openCachedUrl(url.toString(), new LegacyTaskMonitor(indicator));
    }

    @Nullable
    @Override
    public File downloadFully(@NonNull URL url, @NonNull ProgressIndicator indicator)
            throws IOException {
        File target = File.createTempFile("LegacyDownloader", null);
        downloadFully(url, target, null, indicator);
        return target;
    }

    @Override
    public void downloadFully(@NonNull URL url, @NonNull File target, @Nullable String checksum,
            @NonNull ProgressIndicator indicator) throws IOException {
        if (mFileOp.exists(target) && checksum != null) {
            try (InputStream in = new BufferedInputStream(mFileOp.newFileInputStream(target))) {
                if (checksum.equals(Downloader.hash(in, mFileOp.length(target), indicator))) {
                    return;
                }
            }
        }
        mFileOp.mkdirs(target.getParentFile());
        OutputStream out = mFileOp.newFileOutputStream(target);
        Pair<InputStream, Integer> downloadedResult = mDownloadCache
                .openDirectUrl(url.toString(), new LegacyTaskMonitor(indicator));
        if (downloadedResult.getSecond() == 200) {
            ByteStreams.copy(downloadedResult.getFirst(), out);
            out.close();
        }
    }


}
