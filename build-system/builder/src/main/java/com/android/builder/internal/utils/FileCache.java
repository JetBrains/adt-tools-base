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
import com.google.common.base.Preconditions;
import com.google.common.io.Files;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Cache for already-created files/directories.
 *
 * <p>This class is used to avoid creating the same file/directory multiple times. Files/directories
 * are distinguished via provided keys (the contents of files/directories with the same key should
 * be the same). The main API method {@link #getOrCreateFile(File, String, IOExceptionFunction)}
 * creates a file/directory with a given key by copying it from the cache (or creating and caching
 * it first if it does not already exist).
 */
public class FileCache {

    /** Dummy cache to be used as a convenience when no caching is needed. */
    public static final FileCache NO_CACHE =
            new FileCache() {
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

    @NonNull private final File mCacheDirectory;
    private final boolean mInterProcessLocking;

    private final AtomicInteger mMisses = new AtomicInteger(0);
    private final AtomicInteger mHits = new AtomicInteger(0);

    /** Private constructor to create FileCache.NO_CACHE. */
    private FileCache() {
        mCacheDirectory = new File("");
        mInterProcessLocking = false;
    }

    /**
     * Private constructor to create a {@code FileCache} instance. The cache directory is created if
     * it does not already exist. If inter-process locking mode is set to {@code true}, threads in
     * the same process or threads in different processes cannot access the same file concurrently.
     * If inter-process locking mode is set to {@code false}, threads in the same process cannot
     * access the same file concurrently; however, threads in different processes still can.
     *
     * @param cacheDirectory the directory that will contain the cached files/directories
     * @param interProcessLocking whether inter-process locking is enabled
     */
    private FileCache(@NonNull File cacheDirectory, boolean interProcessLocking) {
        FileUtils.mkdirs(cacheDirectory);

        mCacheDirectory = cacheDirectory;
        mInterProcessLocking = interProcessLocking;
    }

    /**
     * Creates a {@code FileCache} instance with inter-process locking (i.e., threads in the same
     * process or threads in different processes cannot access the same file concurrently). The
     * cache directory is created if it does not already exist.
     *
     * @param cacheDirectory the directory that will contain the cached files/directories
     */
    @NonNull
    public static FileCache withInterProcessLocking(@NonNull File cacheDirectory) {
        return new FileCache(cacheDirectory, true);
    }

    /**
     * Creates a {@code FileCache} instance with single-process locking (i.e., threads in the same
     * process cannot access the same file concurrently; however, threads in different processes
     * still can. The cache directory is created if it does not already exist.
     *
     * @param cacheDirectory the directory that will contain the cached files/directories
     */
    @NonNull
    public static FileCache withSingleProcessLocking(@NonNull File cacheDirectory) {
        return new FileCache(cacheDirectory, false);
    }

    /**
     * Creates a file/directory with a given key by copying it from the cache (or creating it first
     * via a callback function and caching it if the cached file/directory does not yet exist). The
     * key is used to identify the file/directory: Files/directories having the same key are
     * considered the same. The output file/directory is replaced if it already exists.
     *
     * <p>The argument to the callback function is a file/directory to be created (depending on the
     * actual implementation of the cache, it may be an intermediate file/directory), thus the
     * callback should not assume that the passed-back argument is the output file/directory.
     *
     * <p>Note that the callback is not required to always create the file/directory (e.g., we don't
     * create dex files for jars that don't contain class files). In such cases, the file/directory
     * will not be cached.
     *
     * @param outputFile the output file/directory
     * @param fileKey the key of the output file/directory
     * @param fileProducer the callback function to create the output file/directory (or an
     *     intermediate file/directory) in case it has not been cached
     * @return whether the file has been created successfully
     */
    public boolean getOrCreateFile(
            @NonNull File outputFile,
            @NonNull String fileKey,
            @NonNull IOExceptionFunction<File, Void> fileProducer)
            throws IOException {
        // Use the output file's key as the cached file's name
        File cachedFile =
                new File(mCacheDirectory, FileUtils.getValidFileName(fileKey, "", mCacheDirectory));

        // Create the cached file first if it does not already exist. This action should be guarded
        // with inter-process/inter-thread locking since another process/thread might be accessing
        // the same file.
        doLocked(
                cachedFile,
                () -> {
                    if (!cachedFile.exists()) {
                        mMisses.incrementAndGet();
                        // Ask fileProducer to create the cached file. We need to make sure the
                        // file passed to fileProducer has the same file name extension as that of
                        // the final output file; otherwise, fileProducer may not be able to create
                        // the file (e.g., a dx command will fail if the file's extension is
                        // missing). To do that, we first create a temporary file that has the same
                        // extension as the final output file, then rename it to the cached file.
                        // This will have an additional benefit that if fileProducer throws an
                        // Exception, leaving the temporary file in a corrupt state, the cached file
                        // will not be created and will have another chance to be created in the
                        // next run. (The same also applies to directories.)
                        File tmpFile =
                                new File(
                                        mCacheDirectory,
                                        FileUtils.getValidFileName(
                                                cachedFile.getName(),
                                                Files.getFileExtension(outputFile.getName()),
                                                mCacheDirectory));
                        fileProducer.apply(tmpFile);

                        // Before renaming, check whether the temporary file exists since
                        // fileProducer is not required to always create a new file.
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
            doLocked(
                    outputFile,
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
     * Invokes a runnable that accesses a file with inter-process and/or inter-thread locking. That
     * is, processes/threads that access the same file cannot run at the same time, whereas those
     * that access different files can still run concurrently. Note that the file to be accessed may
     * or may not already exist.
     *
     * <p>We design for inter-process and inter-thread locking to take effect within the same cache
     * (i.e., FileCache instances using same cache directory). Processes/threads using different
     * cache directories can still run in parallel even when they are accessing the same file.
     *
     * @param accessedFile the file that a runnable is going to access
     * @param runnable the runnable that will be accessing the file
     * @param interProcessLocking set to {@code true} to enable both inter-process and inter-thread
     *     locking, {@code false} to enable inter-thread locking only
     */
    @VisibleForTesting
    void doLocked(
            @NonNull File accessedFile,
            @NonNull IOExceptionRunnable runnable,
            boolean interProcessLocking)
            throws IOException {
        if (interProcessLocking) {
            doProcessLocked(accessedFile, runnable);
        } else {
            doThreadLocked(accessedFile, runnable);
        }
    }

    /** Invokes a runnable that accesses a file with both inter-process and inter-thread locking. */
    private void doProcessLocked(@NonNull File accessedFile, @NonNull IOExceptionRunnable runnable)
            throws IOException {
        // We use Java's file-locking API to enable inter-process locking. The API permits only one
        // thread per process to wait on a file lock, and it will throw an
        // OverlappingFileLockException if more than one thread in a process attempt to acquire the
        // same file lock. Therefore, we run the file-locking mechanism also with inter-thread
        // locking.
        doThreadLocked(
                accessedFile,
                () -> {
                    // Create a lock file for the runnable. We don't use the file being accessed
                    // (which might not already exist) as the lock file since we don't want it to be
                    // affected by our locking mechanism (specifically, the locking mechanism will
                    // always create the lock file and delete it after the runnable is executed;
                    // however, a runnable may or may not decide to create the file that it is
                    // supposed to access).
                    File lockFile =
                            new File(
                                    mCacheDirectory,
                                    FileUtils.getValidFileName(
                                            accessedFile.getCanonicalPath(), "", mCacheDirectory));
                    FileChannel fileChannel = new RandomAccessFile(lockFile, "rw").getChannel();
                    FileLock fileLock = fileChannel.lock();
                    try {
                        runnable.run();
                    } finally {
                        lockFile.delete(); // Delete the lock file first
                        fileLock.release();
                        fileChannel.close();
                    }
                });
    }

    /**
     * Invokes a runnable that accesses a file with inter-thread locking only and without
     * inter-process locking.
     */
    private void doThreadLocked(@NonNull File accessedFile, @NonNull IOExceptionRunnable runnable)
            throws IOException {
        // Since inter-thread (and inter-process) locking is specific to each cache, we combine
        // both the cache directory and the accessed file's paths as the object that the runnable
        // is synchronized on.
        synchronized (
                (mCacheDirectory.getCanonicalPath() + accessedFile.getCanonicalPath()).intern()) {
            runnable.run();
        }
    }

    /**
     * Copies a file or a directory's contents to another file or directory, which can have a
     * different name. The target file/directory is replaced if it already exists.
     */
    private static void copyFileOrDirectory(@NonNull File from, @NonNull File to)
            throws IOException {
        assert from.exists() : "Source path " + from.getCanonicalPath() + "does not exist.";

        if (!from.getCanonicalPath().equals(to.getCanonicalPath())) {
            if (from.isFile()) {
                Files.createParentDirs(to);
                FileUtils.copyFile(from, to);
            } else if (from.isDirectory()) {
                FileUtils.deletePath(to);
                FileUtils.copyDirectory(from, to);
            }
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
