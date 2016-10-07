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

package com.android.builder.internal.packaging.sign.v2;

/**
 * APK Signature Scheme v2 content digest algorithm.
 */
enum ContentDigestAlgorithm {
    /** SHA2-256 over 1 MB chunks. */
    CHUNKED_SHA256("SHA-256", 256 / 8),

    /** SHA2-512 over 1 MB chunks. */
    CHUNKED_SHA512("SHA-512", 512 / 8);

    private final String mJcaMessageDigestAlgorithmName;
    private final int mChunkDigestOutputSizeBytes;

    private ContentDigestAlgorithm(
            String jcaMessageDigestAlgorithmName, int chunkDigestOutputSizeBytes) {
        mJcaMessageDigestAlgorithmName = jcaMessageDigestAlgorithmName;
        mChunkDigestOutputSizeBytes = chunkDigestOutputSizeBytes;
    }

    /**
     * Returns the {@link java.security.MessageDigest} algorithm used for computing digests of
     * chunks by this content digest algorithm.
     */
    String getJcaMessageDigestAlgorithmName() {
        return mJcaMessageDigestAlgorithmName;
    }

    /**
     * Returns the size (in bytes) of the digest of a chunk of content.
     */
    int getChunkDigestOutputSizeBytes() {
        return mChunkDigestOutputSizeBytes;
    }
}