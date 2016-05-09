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

import java.io.IOException;
import java.security.MessageDigest;

import com.android.annotations.NonNull;

/**
 * Source of data which is fed into {@link MessageDigest} instances.
 *
 * <p>This abstraction serves two purposes:
 * <ul>
 * <li>Transparent digesting of different types of sources, such as {@code byte[]},
 *     {@code ZFile}, {@link java.nio.ByteBuffer} and/or memory-mapped file.</li>
 * <li>Support sources larger than 2 GB. If all sources were smaller than 2 GB, {@code ByteBuffer}
 *     would have worked as the unifying abstraction.</li>
 * </ul>
 */
public interface DigestSource {
    /**
     * Returns the amount of data (in bytes) contained in this data source.
     */
    long size();

    /**
     * Feeds the specified chunk from this data source into each of the provided
     * {@link MessageDigest} instances. Each {@code MessageDigest} instance receives the specified
     * chunk of data in full.
     *
     * @param offset index (in bytes) at which the chunk starts relative to the start of this data
     *        source.
     * @param size size (in bytes) of the chunk.
     */
    void feedDigests(long offset, int size, @NonNull MessageDigest[] digests) throws IOException;
}
