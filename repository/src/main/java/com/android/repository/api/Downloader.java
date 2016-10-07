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
import com.google.common.annotations.VisibleForTesting;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

/**
 * Implementations provide a general mechanism for downloading files.
 */
public interface Downloader {

    /**
     * Gets a stream associated with the content at the given URL. This could be achieved by
     * downloading the file completely, streaming it directly, or some combination.
     *
     * @param url       The URL to fetch.
     * @param indicator Facility for showing download progress and logging.
     * @return An InputStream corresponding to the specified content, or {@code null} if the
     *         download is cancelled.
     */
    @Nullable
    InputStream downloadAndStream(@NonNull URL url, @NonNull ProgressIndicator indicator)
            throws IOException;

    /**
     * Downloads the content at the given URL to a temporary file and returns a handle to that file.
     *
     * @param url       The URL to fetch.
     * @param indicator Facility for showing download progress and logging.
     * @return The temporary file, or {@code null} if the download is cancelled.
     */
    @Nullable
    File downloadFully(@NonNull URL url, @NonNull ProgressIndicator indicator) throws IOException;

    /**
     * Downloads the content at the given URL to the given file.
     *
     * @param url       The URL to fetch.
     * @param target    The location to download to.
     * @param checksum  If specified, first check {@code target} to see if the given checksum
     *                  matches the existing file. If so, returns immediately.
     * @param indicator Facility for showing download progress and logging.
     */
    void downloadFully(@NonNull URL url, @NonNull File target, @Nullable String checksum,
            @NonNull ProgressIndicator indicator) throws IOException;

    /**
     * Hash the given input stream.
     * @param in The stream to hash. It will be fully consumed but not closed.
     * @param fileSize The expected length of the stream, for progress display purposes.
     * @param progress The indicator will be updated with the expected completion fraction.
     * @return The sha1 hash of the input stream.
     * @throws IOException IF there's a problem reading from the stream.
     */
    @VisibleForTesting
    @NonNull
    static String hash(@NonNull InputStream in, long fileSize, @NonNull ProgressIndicator progress)
            throws IOException {
        progress.setText("Checking existing file...");
        Hasher sha1 = Hashing.sha1().newHasher();
        byte[] buf = new byte[5120];
        long totalRead = 0;
        int bytesRead;
        while ((bytesRead = in.read(buf)) > 0) {
            sha1.putBytes(buf, 0, bytesRead);
            progress.setFraction((double) totalRead / (double) fileSize);
        }
        return sha1.hash().toString();
    }
}
