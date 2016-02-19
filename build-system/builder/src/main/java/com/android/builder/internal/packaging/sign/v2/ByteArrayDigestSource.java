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

import java.security.MessageDigest;

import com.android.annotations.NonNull;
import com.google.common.base.Preconditions;

/**
 * {@code byte[]} which is fed into {@link MessageDigest} instances.
 */
public class ByteArrayDigestSource implements DigestSource {
    private final byte[] mBuf;

    /**
     * Constructs a new {@code ByteArrayDigestSource} instance which obtains its data from the
     * provided byte array. Changes to the byte array's contents are reflected visible in this
     * source.
     */
    public ByteArrayDigestSource(@NonNull byte[] buf) {
        mBuf = buf;
    }

    @Override
    public long size() {
        return mBuf.length;
    }

    @Override
    public void feedDigests(long offset, int size, @NonNull MessageDigest[] digests) {
        Preconditions.checkArgument(offset >= 0, "offset: %s", offset);
        Preconditions.checkArgument(size >= 0, "size: %s", size);
        Preconditions.checkArgument(offset <= mBuf.length, "offset too large: %s", offset);
        int offsetInBuf = (int) offset;
        Preconditions.checkPositionIndex(offsetInBuf, mBuf.length, "offset out of range");
        int availableSize = mBuf.length - offsetInBuf;
        Preconditions.checkArgument(
                size <= availableSize,
                "offset (%s) + size (%s) > array length (%s)", offset, size, mBuf.length);

        for (MessageDigest md : digests) {
            md.update(mBuf, offsetInBuf, size);
        }
    }
}
