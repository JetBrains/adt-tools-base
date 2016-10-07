/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.builder.internal.compiler;

import com.android.annotations.NonNull;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.JackProcessOptions;
import com.android.ide.common.process.ProcessException;
import com.android.jack.api.ConfigNotSupportedException;
import com.android.jack.api.v01.CompilationException;
import com.android.jack.api.v01.ConfigurationException;
import com.android.jack.api.v01.UnrecoverableException;
import com.android.utils.FileUtils;
import com.android.utils.Pair;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.io.Files;

import java.io.File;
import java.io.IOException;

/**
 * Cache for jar -> jack conversion, using the Jack --import tool.
 *
 * Since we cannot yet have a single task for each library that needs to be run through Jack
 * (because there is no task-level parallelization), this class allows reusing the output of
 * the Jack process for a library in a project in other projects.
 *
 * Because different project could use different build-tools, both the library to be converted
 * and the version of the build tools are used as keys in the cache.
 *
 * The API is fairly simple, just call {@link #convertLibrary(AndroidBuilder, File, File, JackProcessOptions, boolean)}
 *
 * The call will be blocking until the conversion happened, either through actually running Jack or
 * through copying the output of a previous Jack run.
 *
 * After a build a call to {@link #clear(java.io.File, com.android.utils.ILogger)} with a file
 * will allow saving the known converted libraries for future reuse.
 */
public class JackConversionCache extends PreProcessCache<JackDexKey> {

    private static final JackConversionCache sSingleton = new JackConversionCache();

    public static JackConversionCache getCache() {
        return sSingleton;
    }

    @NonNull
    @Override
    protected KeyFactory<JackDexKey> getKeyFactory() {
        return JackDexKey.FACTORY;
    }

    /**
     * Converts a given library to a given output with Jack, using a specific version of the
     * build-tools.
     *
     * @throws ProcessException
     */
    public void convertLibrary(
            @NonNull AndroidBuilder androidBuilder,
            @NonNull File inputFile,
            @NonNull File outFile,
            @NonNull JackProcessOptions options,
            boolean isJackInProcess)
            throws ConfigNotSupportedException, ClassNotFoundException, ConfigurationException,
            CompilationException, UnrecoverableException, ProcessException, InterruptedException,
            IOException {
        Preconditions.checkNotNull(androidBuilder.getTargetInfo());
        JackDexKey itemKey = JackDexKey.of(
                inputFile,
                androidBuilder.getTargetInfo().getBuildTools().getRevision(),
                options.getJumboMode(),
                options.getDexOptimize(),
                options.getAdditionalParameters());

        Pair<PreProcessCache.Item, Boolean> pair = getItem(androidBuilder.getLogger(), itemKey);
        Item item = pair.getFirst();

        // if this is a new item
        if (pair.getSecond()) {
            try {
                // haven't process this file yet so do it and record it.

                androidBuilder.convertByteCodeUsingJack(options, isJackInProcess);
                item.getOutputFiles().add(outFile);

                incrementMisses();
            } catch (Exception exception) {
                // in case of error, delete (now obsolete) output file
                //noinspection ResultOfMethodCallIgnored - we are throwing an error anyway.
                outFile.delete();
                // and rethrow the error
                throw exception;
            } finally {
                // enable other threads to use the output of this pre-dex.
                // if something was thrown they'll handle the missing output file.
                item.getLatch().countDown();
            }
        } else {
            // wait until the file is pre-dexed by the first thread.
            item.getLatch().await();

            // check that the generated file actually exists
            // while the api allow for 2+ files, there's only ever one in this case.
            File fromFile = item.getOutputFiles().get(0);

            if (!fromFile.getCanonicalPath().equals(outFile.getCanonicalPath())
                    && fromFile.isFile()) {
                // file already pre-dex, just copy the output.
                // while the api allow for 2+ files, there's only ever one in this case.
                FileUtils.mkdirs(outFile.getParentFile());
                Files.copy(fromFile, outFile);
                incrementHits();
            }
        }
    }
}
