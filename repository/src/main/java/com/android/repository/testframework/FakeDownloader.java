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

package com.android.repository.testframework;

import com.android.annotations.NonNull;
import com.android.repository.api.Downloader;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.SettingsController;
import com.google.common.collect.Maps;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Map;

/**
 * Fake implementation of {@link Downloader} that returns some specified content for specified
 * URLs.
 */
public class FakeDownloader implements Downloader {

    private Map<String, InputStream> mUrlStreamMap = Maps.newHashMap();

    public void registerUrl(URL url, InputStream stream) {
        mUrlStreamMap.put(url.toExternalForm(), stream);
    }

    @Override
    @NonNull
    public InputStream download(@NonNull URL url, @NonNull SettingsController controller,
            @NonNull ProgressIndicator indicator) throws IOException {
        InputStream toWrap = mUrlStreamMap.get(url.toExternalForm());
        if (toWrap != null) {
            return new ReopeningInputStream(toWrap);
        }
        throw new IOException("Failed to open " + url);
    }

    /**
     * Close all input streams, since they will have been prevented from being closed by {@link
     * ReopeningInputStream}.
     */
    public void dispose() {
        for (InputStream s : mUrlStreamMap.values()) {
            try {
                s.close();
            } catch (IOException e) {
                // ignore
            }
        }
    }

    /**
     * For convenience, so we can download from the same URL more than once with this downloader,
     * reset streams instead of closing them.
     */
    static class ReopeningInputStream extends InputStream {

        private InputStream mWrapped;

        public ReopeningInputStream(InputStream toWrap) {
            toWrap.mark(Integer.MAX_VALUE);
            mWrapped = toWrap;
        }

        @Override
        public int read() throws IOException {
            return mWrapped.read();
        }

        @Override
        public void close() throws IOException {
            mWrapped.reset();
        }

        public void reallyClose() throws IOException {
            mWrapped.close();
        }
    }
}
