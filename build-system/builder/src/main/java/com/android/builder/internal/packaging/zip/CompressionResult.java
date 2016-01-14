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

package com.android.builder.internal.packaging.zip;

import com.android.annotations.NonNull;
import com.android.builder.internal.packaging.zip.utils.CloseableByteSource;

/**
 * Result of compressing data.
 */
public class CompressionResult {

    /**
     * The compression method used.
     */
    @NonNull
    private final CompressionMethod mCompressionMethod;

    /**
     * The resulting data.
     */
    @NonNull
    private final CloseableByteSource mSource;

    /**
     * Size of the compressed source. Kept because {@code mSource.size()} can throw
     * {@code IOException}.
     */
    private final long mSize;

    /**
     * Creates a new compression result.
     * @param source the data source
     * @param method the compression method
     */
    public CompressionResult(@NonNull CloseableByteSource source, @NonNull CompressionMethod method,
            long size) {
        mCompressionMethod = method;
        mSource = source;
        mSize = size;
    }

    /**
     * Obtains the compression method.
     * @return the compression method
     */
    @NonNull
    public CompressionMethod getCompressionMethod() {
        return mCompressionMethod;
    }

    /**
     * Obtains the compressed data.
     * @return the data, the resulting array should not be modified
     */
    @NonNull
    public CloseableByteSource getSource() {
        return mSource;
    }

    /**
     * Obtains the size of the compression result.
     * @return the size
     */
    public long getSize() {
        return mSize;
    }
}
