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
import com.android.builder.internal.packaging.zip.ZFile;
import com.google.common.base.Preconditions;

/**
 * Contiguous section of {@link ZFile} which is fed into {@link MessageDigest} instances.
 */
public class ZFileDigestSource implements DigestSource {
    private final ZFile mFile;
    private final long mOffset;
    private final long mSize;

    /**
     * Constructs a new {@code ZFileDigestSource} representing the section of the file starting
     * at the provided {@code offset} and extending for the provided {@code size} number of bytes.
     */
    public ZFileDigestSource(@NonNull ZFile file, long offset, long size) {
        Preconditions.checkArgument(offset >= 0, "offset: %s", offset);
        Preconditions.checkArgument(size >= 0, "size: %s", size);
        mFile = file;
        mOffset = offset;
        mSize = size;
    }


    @Override
    public long size() {
        return mSize;
    }

    @Override
    public void feedDigests(long offset, int size, @NonNull MessageDigest[] digests)
            throws IOException {
        Preconditions.checkArgument(offset >= 0, "offset: %s", offset);
        Preconditions.checkArgument(size >= 0, "size: %s", size);
        Preconditions.checkArgument(offset <= mSize, "offset: %s, file size: %s", offset, mSize);
        long chunkStartOffset = mOffset + offset;
        long availableSize = mSize - offset;
        Preconditions.checkArgument(
                size <= availableSize, "offset: %s, size: %s, file size: %s", offset, size, mSize);

        byte[] chunk = new byte[size];
        mFile.directFullyRead(chunkStartOffset, chunk);
        for (MessageDigest md : digests) {
            md.update(chunk);
        }
    }
}
