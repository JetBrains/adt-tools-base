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

package com.android.builder.internal.packaging.zip.compress;

import com.android.annotations.NonNull;
import com.android.builder.internal.packaging.zip.CompressionMethod;
import com.android.builder.internal.packaging.zip.CompressionResult;
import com.android.builder.internal.packaging.zip.utils.ByteTracker;
import com.android.builder.internal.packaging.zip.utils.CloseableByteSource;
import com.google.common.io.Closer;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.Executor;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/**
 * Compressor that uses deflate with an executor.
 */
public class DeflateExecutionCompressor extends ExecutorCompressor {


    /**
     * Deflate compression level.
     */
    private final int mLevel;

    /**
     * Byte tracker to use to create byte sources.
     */
    @NonNull
    private final ByteTracker mTracker;

    /**
     * Creates a new compressor.
     *
     * @param executor the executor to run deflation tasks
     * @param tracker the byte tracker to use to keep track of memory usage
     * @param level the compression level
     */
    public DeflateExecutionCompressor(@NonNull Executor executor, @NonNull ByteTracker tracker,
            int level) {
        super(executor);

        mLevel = level;
        mTracker = tracker;
    }

    @NonNull
    @Override
    protected CompressionResult immediateCompress(@NonNull CloseableByteSource source)
            throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Deflater deflater = new Deflater(mLevel, true);

        try (DeflaterOutputStream dos = new DeflaterOutputStream(output, deflater)) {
            dos.write(source.read());
        }

        CloseableByteSource result = mTracker.fromStream(output);
        if (result.size() >= source.size()) {
            return new CompressionResult(source, CompressionMethod.STORE, source.size());
        } else {
            return new CompressionResult(result, CompressionMethod.DEFLATE, result.size());
        }
    }
}
