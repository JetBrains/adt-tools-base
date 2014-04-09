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
import com.android.annotations.VisibleForTesting;
import com.android.annotations.concurrency.GuardedBy;
import com.android.annotations.concurrency.Immutable;
import com.android.builder.AndroidBuilder;
import com.android.builder.DexOptions;
import com.android.ide.common.internal.CommandLineRunner;
import com.android.ide.common.internal.LoggedErrorException;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.repository.FullRevision;
import com.android.utils.Pair;
import com.google.common.base.Objects;
import com.google.common.collect.Maps;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.google.common.io.Files;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * Pre Dexing cache.
 *
 * Since we cannot yet have a single task for each library that needs to be pre-dexed (because
 * there is no task-level parallelization), this class allows reusing the output of the pre-dexing
 * of a library in a project to write the output of the pre-dexing of the same library in
 * a different project.
 *
 * Because different project could use different build-tools, both the library to pre-dex and the
 * version of the build tools are used as keys in the cache.
 *
 * The API is fairly simple, just call {@link #preDexLibrary(java.io.File, java.io.File, com.android.builder.DexOptions, com.android.sdklib.BuildToolInfo, boolean, com.android.ide.common.internal.CommandLineRunner)}
 *
 * The call will be blocking until the pre-dexing happened, either through actual pre-dexing or
 * through copying the output of a previous pre-dex run.
 *
 * Note that the cache does not yet store data to reuse cache between builds, but this will come
 * later.
 */
public class PreDexCache {

    @Immutable
    private static class Item {
        @NonNull
        private final File mSourceFile;
        @NonNull
        private final File mOutputFile;
        @NonNull
        private final HashCode mSourceHash;
        @NonNull
        private final CountDownLatch mLatch;

        Item(
                @NonNull File sourceFile,
                @NonNull File outputFile,
                @NonNull HashCode sourceHash,
                @NonNull CountDownLatch latch) {
            mSourceFile = sourceFile;
            mOutputFile = outputFile;
            mSourceHash = sourceHash;
            mLatch = latch;
        }

        @NonNull
        private File getSourceFile() {
            return mSourceFile;
        }

        @NonNull
        private File getOutputFile() {
            return mOutputFile;
        }

        @NonNull
        private HashCode getSourceHash() {
            return mSourceHash;
        }

        @NonNull
        private CountDownLatch getLatch() {
            return mLatch;
        }
    }

    @Immutable
    private static class Key {
        @NonNull
        private final File mSourceFile;
        @NonNull
        private final FullRevision mBuildToolsRevision;

        private static Key of(@NonNull File sourceFile, @NonNull FullRevision buildToolsRevision) {
            return new Key(sourceFile, buildToolsRevision);
        }

        private Key(@NonNull File sourceFile, @NonNull FullRevision buildToolsRevision) {
            mSourceFile = sourceFile;
            mBuildToolsRevision = buildToolsRevision;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            Key key = (Key) o;

            if (!mBuildToolsRevision.equals(key.mBuildToolsRevision)) {
                return false;
            }
            if (!mSourceFile.equals(key.mSourceFile)) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(mSourceFile, mBuildToolsRevision);
        }
    }

    private static final PreDexCache sSingleton = new PreDexCache();

    public static PreDexCache getCache() {
        return sSingleton;
    }

    @GuardedBy("this")
    private final Map<Key, Item> mMap = Maps.newHashMap();

    private int mMisses = 0;
    private int mHits = 0;

    /**
     * Pre-dex a given library to a given output with a specific version of the build-tools.
     * @param inputFile the jar to pre-dex
     * @param outFile the output file.
     * @param dexOptions the dex options to run pre-dex
     * @param buildToolInfo the build tools info
     * @param verbose verbose flag
     * @param commandLineRunner the command line runner.
     * @throws IOException
     * @throws LoggedErrorException
     * @throws InterruptedException
     */
    public void preDexLibrary(
            @NonNull File inputFile,
            @NonNull File outFile,
            @NonNull DexOptions dexOptions,
            @NonNull BuildToolInfo buildToolInfo,
            boolean verbose,
            @NonNull CommandLineRunner commandLineRunner)
            throws IOException, LoggedErrorException, InterruptedException {
        Pair<Item, Boolean> item = getItem(inputFile, outFile, buildToolInfo);

        // if this is a new item
        if (item.getSecond()) {
            mMisses++;

            // haven't process this file yet so do it and record it.
            AndroidBuilder.preDexLibrary(inputFile, outFile, dexOptions, buildToolInfo,
                    verbose, commandLineRunner);

            // enable other threads to use the output of this pre-dex
            item.getFirst().getLatch().countDown();
        } else {
            // wait until the file is pre-dexed by the first thread.
            item.getFirst().getLatch().await();

            mHits++;

            // file already pre-dex, just copy the output.
            Files.copy(item.getFirst().getOutputFile(), outFile);
        }
    }

    @VisibleForTesting
    /*package*/ int getMisses() {
        return mMisses;
    }

    @VisibleForTesting
    /*package*/ int getHits() {
        return mHits;
    }

    /**
     * Returns a Pair of {@link Item}, and a boolean which indicates whether the item is new (true)
     * or if it already existed (false).
     *
     * @param inputFile the input file
     * @param outFile the output file
     * @param buildToolInfo the build tools info.
     * @return a pair of item, boolean
     * @throws IOException
     */
    private synchronized Pair<Item, Boolean> getItem(
            @NonNull File inputFile,
            @NonNull File outFile,
            @NonNull BuildToolInfo buildToolInfo) throws IOException {

        Key itemKey = Key.of(inputFile, buildToolInfo.getRevision());

        // get the item
        Item item = mMap.get(itemKey);

        boolean newItem = (item == null);
        if (item == null) {
            item = new Item(inputFile, outFile,
                    Files.hash(inputFile, Hashing.sha1()), new CountDownLatch(1));
            mMap.put(itemKey, item);
        }

        return Pair.of(item, newItem);
    }

    public synchronized void clear() {
        if (!mMap.isEmpty()) {
            System.out.println("PREDEX CACHE HITS:   " + mHits);
            System.out.println("PREDEX CACHE MISSES: " + mMisses);
        }
        mMap.clear();
        mHits = 0;
        mMisses = 0;
    }
}
