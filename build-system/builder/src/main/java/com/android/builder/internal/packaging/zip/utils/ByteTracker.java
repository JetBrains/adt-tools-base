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

package com.android.builder.internal.packaging.zip.utils;

import com.android.annotations.NonNull;
import com.google.common.io.ByteSource;
import com.google.common.io.ByteStreams;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class ByteTracker {

    /**
     * Number of bytes currently in use.
     */
    private long mBytesUsed;

    /**
     * Maximum number of bytes used.
     */
    private long mMaxBytesUsed;

    /**
     * Creates a new byte source by fully reading an input stream.
     * @param stream the input stream
     * @return a byte source containing the cached data from the given stream
     * @throws IOException failed to read the stream
     */
    public CloseableDelegateByteSource fromStream(@NonNull InputStream stream) throws IOException {
        byte[] data = ByteStreams.toByteArray(stream);
        updateUsage(data.length);
        return new CloseableDelegateByteSource(ByteSource.wrap(data), data.length) {
            @Override
            public synchronized void innerClose() throws IOException {
                super.innerClose();
                updateUsage(-sizeNoException());
            }
        };
    }

    /**
     * Creates a new byte source by snapshotting the provided stream.
     * @param stream the stream with the data
     * @return a byte source containing the cached data from the given stream
     * @throws IOException failed to read the stream
     */
    public CloseableDelegateByteSource fromStream(@NonNull ByteArrayOutputStream stream) throws IOException {
        byte[] data = stream.toByteArray();
        updateUsage(data.length);
        return new CloseableDelegateByteSource(ByteSource.wrap(data), data.length) {
            @Override
            public synchronized void innerClose() throws IOException {
                super.innerClose();
                updateUsage(-sizeNoException());
            }
        };
    }

    /**
     * Creates a new byte source from another byte source.
     * @param source the byte source to copy data from
     * @return the tracked byte source
     * @throws IOException failed to read data from the byte source
     */
    public CloseableDelegateByteSource fromSource(@NonNull ByteSource source) throws IOException {
        return fromStream(source.openStream());
    }

    /**
     * Updates the memory used by this tracker.
     * @param delta the number of bytes to add or remove, if negative
     */
    private synchronized void updateUsage(long delta) {
        mBytesUsed += delta;
        if (mMaxBytesUsed < mBytesUsed) {
            mMaxBytesUsed = mBytesUsed;
        }
    }

    /**
     * Obtains the number of bytes currently used.
     * @return the number of bytes
     */
    public synchronized long getBytesUsed() {
        return mBytesUsed;
    }

    /**
     * Obtains the maximum number of bytes ever used by this tracker.
     * @return the number of bytes
     */
    public synchronized long getMaxBytesUsed() {
        return mMaxBytesUsed;
    }
}
