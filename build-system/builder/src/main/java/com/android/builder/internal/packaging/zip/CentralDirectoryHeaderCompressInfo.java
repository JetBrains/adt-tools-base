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

/**
 * Information stored in the {@link CentralDirectoryHeader} that is related to compression and may
 * need to be computed lazily.
 */
public class CentralDirectoryHeaderCompressInfo {

    /**
     * Version of zip file that only supports stored files.
     */
    public static final long VERSION_WITH_STORE_FILES_ONLY = 10L;

    /**
     * Version of zip file that only supports directories and deflated files.
     */
    public static final long VERSION_WITH_DIRECTORIES_AND_DEFLATE = 20L;

    /**
     * The compression method.
     */
    @NonNull
    private final CompressionMethod mMethod;

    /**
     * Size of the file compressed. 0 if the file has no data.
     */
    private final long mCompressedSize;

    /**
     * Version needed to extract the zip.
     */
    private final long mVersionExtract;

    /**
     * Creates new compression information for the central directory header.
     *
     * @param method the compression method
     * @param compressedSize the compressed size
     * @param versionToExtract minimum version to extract (typically
     * {@link #VERSION_WITH_STORE_FILES_ONLY} or {@link #VERSION_WITH_DIRECTORIES_AND_DEFLATE})
     */
    public CentralDirectoryHeaderCompressInfo(
            @NonNull CompressionMethod method,
            long compressedSize,
            long versionToExtract) {
        mMethod = method;
        mCompressedSize = compressedSize;
        mVersionExtract = versionToExtract;
    }

    /**
     * Creates new compression information for the central directory header.
     *
     * @param header the header this information relates to
     * @param method the compression method
     * @param compressedSize the compressed size
     */
    public CentralDirectoryHeaderCompressInfo(@NonNull CentralDirectoryHeader header,
            @NonNull CompressionMethod method, long compressedSize) {
        mMethod = method;
        mCompressedSize = compressedSize;

        if (header.getName().endsWith("/") || method == CompressionMethod.DEFLATE) {
            /*
             * Directories and compressed files only in version 2.0.
             */
            mVersionExtract = VERSION_WITH_DIRECTORIES_AND_DEFLATE;
        } else {
            mVersionExtract = VERSION_WITH_STORE_FILES_ONLY;
        }
    }

    /**
     * Obtains the compression data size.
     *
     * @return the compressed data size
     */
    public long getCompressedSize() {
        return mCompressedSize;
    }

    /**
     * Obtains the compression method.
     *
     * @return the compression method
     */
    @NonNull
    public CompressionMethod getMethod() {
        return mMethod;
    }

    /**
     * Obtains the minimum version for extract.
     *
     * @return the minimum version
     */
    public long getVersionExtract() {
        return mVersionExtract;
    }
}
