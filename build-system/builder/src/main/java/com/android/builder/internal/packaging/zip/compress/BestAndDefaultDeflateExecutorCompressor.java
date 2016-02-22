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
import com.android.builder.internal.packaging.zip.CompressionResult;
import com.android.builder.internal.packaging.zip.utils.ByteTracker;
import com.android.builder.internal.packaging.zip.utils.CloseableByteSource;
import com.google.common.base.Preconditions;

import java.util.concurrent.Executor;
import java.util.zip.Deflater;

/**
 * Compressor that tries both the best and default compression algorithms and picks the default
 * unless the best is at least a given percentage smaller.
 */
public class BestAndDefaultDeflateExecutorCompressor extends ExecutorCompressor {

    /**
     * Deflater using the default compression level.
     */
    @NonNull
    private final DeflateExecutionCompressor mDefaultDeflater;

    /**
     * Deflater using the best compression level.
     */
    @NonNull
    private final DeflateExecutionCompressor mBestDeflater;

    /**
     * Minimum best compression size / default compression size ratio needed to pick the default
     * compression size.
     */
    private final double mMinRatio;

    /**
     * Creates a new compressor.
     *
     * @param executor the executor used to perform compression activities.
     * @param tracker the byte tracker to keep track of allocated bytes
     * @param minRatio the minimum best compression size / default compression size needed to pick
     * the default compression size; if {@code 0.0} then the default compression is always picked,
     * if {@code 1.0} then the best compression is always picked unless it produces the exact same
     * size as the default compression.
     */
    public BestAndDefaultDeflateExecutorCompressor(@NonNull Executor executor,
            @NonNull ByteTracker tracker, double minRatio) {
        super(executor);

        Preconditions.checkArgument(minRatio >= 0.0, "minRatio < 0.0");
        Preconditions.checkArgument(minRatio <= 1.0, "minRatio > 1.0");

        mDefaultDeflater = new DeflateExecutionCompressor(executor, tracker,
                Deflater.DEFAULT_COMPRESSION);
        mBestDeflater = new DeflateExecutionCompressor(executor, tracker,
                Deflater.BEST_COMPRESSION);
        mMinRatio = minRatio;
    }

    @NonNull
    @Override
    protected CompressionResult immediateCompress(@NonNull CloseableByteSource source)
            throws Exception {
        CompressionResult defaultResult = mDefaultDeflater.immediateCompress(source);
        CompressionResult bestResult = mBestDeflater.immediateCompress(source);

        double sizeRatio = bestResult.getSize() / (double) defaultResult.getSize();
        if (sizeRatio >= mMinRatio) {
            return defaultResult;
        } else {
            return bestResult;
        }
    }
}
