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

package com.android.builder.internal.utils;

import com.android.annotations.NonNull;
import com.android.annotations.VisibleForTesting;
import com.android.utils.FileUtils;
import com.google.common.base.MoreObjects;
import com.google.common.io.Files;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Cache for already-created files/directories.
 *
 * <p>This class is used to avoid creating the same file/directory multiple times. Files/directories
 * are distinguished via provided keys (the contents of files/directories with the same key should
 * be the same. The main API method {@link #getOrCreateFile(File, String, IOExceptionFunction)}
 * creates a file/directory with a given key by either copying it from the cache or invoking a
 * callback function to create it (and also caches it for later use).
 */
public class FileCache {

    /**
     * Dummy cache to be used as a convenience when no caching is needed.
     */
    public static final FileCache NO_CACHE = new FileCache(new File("")) {
        @Override
        public boolean getOrCreateFile(
                @NonNull File outputFile,
                @NonNull String fileKey,
                @NonNull IOExceptionFunction<File, Void> fileProducer)
                throws IOException {
            fileProducer.apply(outputFile);
            return outputFile.exists();
        }
    };

    @NonNull
    private final File mCacheDirectory;
    private final boolean mInterProcessLocking;

    private final AtomicInteger mMisses = new AtomicInteger(0);
    private final AtomicInteger mHits = new AtomicInteger(0);

    /**
     * Creates a new <code>FileCache</code> instance. If inter-process locking mode is set to
     * <code>true</code>, both inter-process locking and inter-thread locking are enabled (i.e.,
     * processes and threads cannot write to the same file simultaneously). If set to
     * <code>false</code>, only inter-thread locking is enabled.
     *
     * @param cacheDirectory the directory that will contain the cached files/directories
     * @param interProcessLocking whether inter-process locking is enabled
     */
    public FileCache(@NonNull File cacheDirectory, boolean interProcessLocking) {
        mCacheDirectory = cacheDirectory;
        mInterProcessLocking = interProcessLocking;
    }

    /**
     * Creates a new <code>FileCache</code> instance with inter-process locking disabled (and
     * inter-thread locking enabled).
     *
     * @param cacheDirectory the directory that will contain the cached files/directories
     */
    public FileCache(@NonNull File cacheDirectory) {
        this(cacheDirectory, false);
    }

    /**
     * Creates a file/directory with a given key by either copying it from the cache or invoking a
     * callback function to create it (and also caches it for later use). The file/directory is
     * replaced if it already exists.
     *
     * <p>The key is used to represent the uniqueness of the file/directory: Files/directories
     * having the same key are considered the same (i.e., the first call to the cache will invoke
     * the callback function to create the file/directory and cache it, then subsequent calls will
     * use the cached file/directory).
     *
     * <p>The argument to the callback is a file/directory to be created (depending on the actual
     * implementation, it may be an intermediate file/directory), thus the callback should not
     * assume that it is the final output file/directory.
     *
     * <p>Note that the callback is not required to always create the file/directory (e.g., we don't
     * create dex files for jars that don't contain class files). In such cases, the file/directory
     * won't be cached.
     *
     * @param outputFile the output file/directory
     * @param fileKey the key of the output file/directory
     * @param fileProducer the callback function to create the output file/directory (or an
     *                     intermediate file/directory) in case it has not been cached
     * @return whether the file has been created successfully
     */
    public boolean getOrCreateFile(
            @NonNull File outputFile,
            @NonNull String fileKey,
            @NonNull IOExceptionFunction<File, Void> fileProducer)
            throws IOException {
        // Use the output file's key as the cached file's name
        File cachedFile = new File(mCacheDirectory,
                FileUtils.getValidFileName(fileKey, "", mCacheDirectory));

        // Create the cached file first if it does not already exist. This action should be guarded
        // by a process/thread lock since another process/thread might be writing to the same file.
        ProcessLock.doLocked(
                cachedFile.getCanonicalPath(),
                () -> {
                    if (!cachedFile.exists()) {
                        mMisses.incrementAndGet();
                        Files.createParentDirs(cachedFile);

                        // Ask fileProducer to create the cached file. We need to make sure the
                        // file passed to fileProducer has the same file name extension as that of
                        // the final output file; otherwise, fileProducer may not be able to create
                        // the file (e.g., a dx command will fail if the file's extension is
                        // missing). To do that, we first create a temporary file that has the same
                        // extension as the final output file, then rename it to the cached file.
                        // This will have an additional benefit that if fileProducer throws an
                        // Exception, leaving the temporary file in a corrupt state, the cached file
                        // will not be created and will have another chance to be created in the
                        // next run.
                        File tmpFile = new File(mCacheDirectory,
                                FileUtils.getValidFileName(cachedFile.getName(),
                                        Files.getFileExtension(outputFile.getName()),
                                        mCacheDirectory));
                        fileProducer.apply(tmpFile);

                        // Before renaming, check whether tmpFile exists since fileProducer is not
                        // required to always create a new file.
                        if (tmpFile.exists() && !tmpFile.equals(cachedFile)) {
                            Files.move(tmpFile, cachedFile);
                        }
                    } else {
                        mHits.incrementAndGet();
                    }
                },
                mInterProcessLocking);

        // Check whether the cached file exists. This check does not need to be locked because after
        // the previous statement, either the cached file exists and is complete (no other
        // processes/threads are writing to it) or it will never be created at all (it cannot be the
        // case that the current thread has not created the cached file but another thread is now
        // creating it since how the file is created should be deterministic and depend on the
        // output file's key only).
        if (cachedFile.exists()) {
            // If the cached file exists, copy it to the output file. Note that locking only the
            // output file is safe enough. We do not need to lock the cached file for reading
            // because once it's created, it will not be deleted; this will also allow the cached
            // file to be read concurrently by multiple processes/threads.
            ProcessLock.doLocked(
                    outputFile.getCanonicalPath(),
                    () -> {
                        copyFileOrDirectory(cachedFile, outputFile);
                    },
                    mInterProcessLocking);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Copies a file or a directory's contents to another file or directory, which can have a
     * different name. The target file/directory is replaced if it already exists.
     */
    private static void copyFileOrDirectory(@NonNull File from, @NonNull File to)
            throws IOException {
        if (from.isFile()) {
            FileUtils.mkdirs(to.getParentFile());
            FileUtils.copyFile(from, to);
        } else if (from.isDirectory()) {
            FileUtils.deletePath(to);
            FileUtils.copyDirectory(from, to);
        }
    }

    @VisibleForTesting
    int getMisses() {
        return mMisses.get();
    }

    @VisibleForTesting
    int getHits() {
        return mHits.get();
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("cacheDirectory", mCacheDirectory)
                .add("interProcessLocking", mInterProcessLocking)
                .toString();
    }

}