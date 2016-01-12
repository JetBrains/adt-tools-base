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

package com.android.builder.internal.packaging.zip;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;

/**
 * Data source for an entry in the zip. Data sources allow abstracting from where data is to be
 * stored in a zip. {@code EntrySource}s do not contain the data themselves but rather allow
 * opening a stream that provides the source data.
 */
public interface EntrySource {
    /**
     * Obtains a stream where the source data can be read from. This method can be called multiple
     * times and should return a different stream. It can be seen as a factory method.
     *
     * @return the stream that should return exactly {@link #size()} bytes
     * @throws IOException failed to open the source
     */
    @NonNull
    InputStream open() throws IOException;

    /**
     * Obtains the size of the entry source.
     *
     * @return the number of bytes in the source
     */
    long size();

    /**
     * If this source is based on expansion of an inner, compressed source, obtain the inner
     * source. This may be used to perform optimizations such as avoiding
     * decompression-for-compression.
     *
     * @return the inner compressed source, if any, or {@code null} if this source is already
     * compressed or has no inner source
     */
    @Nullable
    EntrySource innerCompressed();
}
