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
     * @param settings  Settings (e.g. proxy configuration) for the connection.
     * @param indicator Facility for showing download progress and logging.
     * @return An InputStream corresponding to the specified content, or {@code null} if the
     *         download is cancelled.
     */
    @Nullable
    InputStream downloadAndStream(@NonNull URL url, @Nullable SettingsController settings,
            @NonNull ProgressIndicator indicator) throws IOException;

    /**
     * Downloads the content at the given URL to a temporary file and returns a handle to that file.
     *
     * @param url       The URL to fetch.
     * @param settings  Settings (e.g. proxy configuration) for the connection.
     * @param indicator Facility for showing download progress and logging.
     * @return The temporary file, or {@code null} if the download is cancelled.
     */
    @Nullable
    File downloadFully(@NonNull URL url, @Nullable SettingsController settings,
            @NonNull ProgressIndicator indicator) throws IOException;

    /**
     * Downloads the content at the given URL to the given file.
     *
     * @param url       The URL to fetch.
     * @param settings  Settings (e.g. proxy configuration) for the connection.
     * @param target    The location to download to.
     * @param indicator Facility for showing download progress and logging.
     */
    void downloadFully(@NonNull URL url, @Nullable SettingsController settings,
            @NonNull File target, @NonNull ProgressIndicator indicator) throws IOException;
}
