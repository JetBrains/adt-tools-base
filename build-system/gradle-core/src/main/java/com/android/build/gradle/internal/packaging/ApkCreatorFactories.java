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

package com.android.build.gradle.internal.packaging;

import com.android.annotations.NonNull;
import com.android.build.gradle.AndroidGradleOptions;
import com.android.builder.internal.packaging.zfile.ApkZFileCreatorFactory;
import com.android.builder.internal.packaging.zip.ZFileOptions;
import com.android.builder.internal.packaging.zip.compress.BestAndDefaultDeflateExecutorCompressor;
import com.android.builder.internal.packaging.zip.compress.DeflateExecutionCompressor;
import com.android.builder.packaging.ApkCreatorFactory;
import com.android.builder.signing.SignedJarApkCreatorFactory;

import org.gradle.api.Project;

import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.zip.Deflater;

/**
 * Constructs a {@link ApkCreatorFactory} based on gradle options.
 */
public final class ApkCreatorFactories {

    /**
     * Time after which background compression threads should be discarded.
     */
    private static final long BACKGROUND_THREAD_DISCARD_TIME_MS = 100;

    /**
     * Maximum number of compression threads.
     */
    private static final int MAXIMUM_COMPRESSION_THREADS = 2;

    /**
     * Utility class: no constructor.
     */
    private ApkCreatorFactories() {
        /*
         * Nothing to do.
         */
    }

    /**
     * Creates an {@link ApkCreatorFactory} based on the definitions in the project.
     *
     * @param project the project whose properties will be checked
     * @param debuggableBuild whether the {@link ApkCreatorFactory} will be used to create a
     *                        debuggable archive
     * @return the factory
     */
    @NonNull
    public static ApkCreatorFactory fromProjectProperties(
            @NonNull Project project,
            boolean debuggableBuild) {
        boolean useOldPackaging = AndroidGradleOptions.useOldPackaging(project);
        if (useOldPackaging) {
            return new SignedJarApkCreatorFactory();
        } else {
            boolean keepTimestamps = AndroidGradleOptions.keepTimestampsInApk(project);

            ZFileOptions options = new ZFileOptions();
            options.setNoTimestamps(!keepTimestamps);
            options.setUseExtraFieldForAlignment(false);

            ThreadPoolExecutor compressionExecutor =
                    new ThreadPoolExecutor(
                            0, /* Number of always alive threads */
                            MAXIMUM_COMPRESSION_THREADS,
                            BACKGROUND_THREAD_DISCARD_TIME_MS,
                            TimeUnit.MILLISECONDS,
                            new LinkedBlockingDeque<>());

            if (debuggableBuild) {
                options.setCompressor(
                        new DeflateExecutionCompressor(
                                compressionExecutor,
                                options.getTracker(),
                                Deflater.BEST_SPEED));
            } else {
                options.setCompressor(
                        new BestAndDefaultDeflateExecutorCompressor(
                                compressionExecutor,
                                options.getTracker(),
                                1.0));
                options.setAutoSortFiles(true);
            }

            return new ApkZFileCreatorFactory(options);
        }
    }
}
