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
import com.android.annotations.concurrency.Immutable;
import com.android.utils.FileUtils;
import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.collect.Maps;
import com.google.common.hash.Hashing;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Cache for already-created files/directories.
 *
 * <p>This class is used to avoid creating the same file/directory multiple times. The main API
 * method {@link #getOrCreateFile(File, Inputs, IOExceptionFunction)} creates a file/directory by
 * copying it from the cache, or creating and caching it first if it does not already exist.
 */
@Immutable
public class FileCache {

    /** Dummy cache to be used as a convenience when no caching is needed. */
    public static final FileCache NO_CACHE =
            new FileCache() {
                @Override
                public boolean getOrCreateFile(
                        @NonNull File outputFile,
                        @NonNull Inputs inputs,
                        @NonNull IOExceptionFunction<File, Void> fileProducer)
                        throws IOException {
                    FileUtils.deletePath(outputFile);
                    Files.createParentDirs(outputFile);

                    fileProducer.apply(outputFile);

                    return outputFile.exists();
                }
            };

    @NonNull private final File mCacheDirectory;
    private final boolean mInterProcessLocking;

    @NonNull private final AtomicInteger mMisses = new AtomicInteger(0);
    @NonNull private final AtomicInteger mHits = new AtomicInteger(0);

    /** Private constructor to create {@linkplain #NO_CACHE FileCache.NO_CACHE}. */
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
     * still can). The cache directory is created if it does not already exist.
     *
     * @param cacheDirectory the directory that will contain the cached files/directories
     */
    @NonNull
    public static FileCache withSingleProcessLocking(@NonNull File cacheDirectory) {
        return new FileCache(cacheDirectory, false);
    }

    /**
     * Creates a file/directory by copying it from the cache, or creating it first via a callback
     * function and caching it if the cached file/directory does not already exist.
     *
     * <p>To determine whether to reuse a cached file/directory or create a new file/directory, the
     * client needs to provide all the inputs that affect the creation of the output file/directory,
     * including input files/directories and other input parameters. If some inputs are missing
     * (e.g., {@code encoding=utf-8}), the client may reuse a cached file/directory that is
     * incorrect. On the other hand, if some irrelevant inputs are included (e.g., {@code
     * verbose=true}), the cache may create a new cached file/directory even though the same one
     * already exists. In other words, missing inputs affect correctness, and irrelevant inputs
     * affect performance. Thus, the client needs to consider carefully what to include and exclude
     * in these inputs. For example, if the client uses different commands or different versions of
     * the same command to create the output, then the commands or their versions also need to be
     * specified as part of the inputs. As another example, if the content of an input file has
     * changed, then in addition to the file path, the file's timestamp or a hash code of the file's
     * content also needs to be included in the inputs.
     *
     * <p>These input parameters are wrapped in the {@link Inputs} object. They are ordered
     * according to the order in which they are added to the {@code Inputs} object. If this cache is
     * invoked multiple times on the same list of inputs, the first call will cache the output
     * file/directory and subsequent calls will reuse the cached file/directory.
     *
     * <p>The argument that this cache passed to the callback function is a file/directory to be
     * created (depending on the actual implementation of the cache, it may be an intermediate
     * file/directory and not the final output file/directory). Thus, the callback should not assume
     * that the passed-back argument is the output file/directory. Before the callback is invoked,
     * this cache deletes the passed-back file/directory if it already exists and creates its parent
     * directory if it does not exist.
     *
     * <p>The callback is not required to always create the file/directory (e.g., we don't create
     * dex files for jars that don't contain class files). In such cases, the file/directory will
     * not be cached, and subsequent calls on the same list of inputs will produce no output.
     *
     * <p>Finally, the output file/directory is replaced if it already exists.
     *
     * @param outputFile the output file/directory
     * @param inputs all the inputs the affect the creation of the output file/directory
     * @param fileProducer the callback function to create the output file/directory
     * @return whether the output file/directory has been created successfully
     */
    public boolean getOrCreateFile(
            @NonNull File outputFile,
            @NonNull Inputs inputs,
            @NonNull IOExceptionFunction<File, Void> fileProducer)
            throws IOException {
        // For each unique list of inputs, we compute a unique key and use it as the name of the
        // cached file container. The cached file container is a directory that contains the actual
        // cached file and another file describing the inputs.
        File cachedFileContainer = new File(mCacheDirectory, inputs.getKey());
        File cachedFile = new File(cachedFileContainer, "output");
        File inputsFile = new File(cachedFileContainer, "inputs");

        // Create the cached file first if it does not already exist. This action should be guarded
        // with inter-process/inter-thread locking since another process/thread might be accessing
        // the same file.
        doLocked(
                cachedFileContainer,
                () -> {
                    if (!cachedFileContainer.exists()) {
                        mMisses.incrementAndGet();

                        // Ask fileProducer to create the cached file. The name of the cached file
                        // should be independent of the name of the output file since the cache may
                        // be used to create different output files with the same set of inputs
                        // (and therefore sharing the same cached file). However, we also need to
                        // make sure that the file passed to fileProducer has the same file name
                        // extension as that of the final output file; otherwise, fileProducer may
                        // not be able to create the file (e.g., a dx command will fail if the
                        // file's extension is missing). To do that, we first create a temporary
                        // file that has the same name as the final output file, then rename it to
                        // the cached file.
                        FileUtils.mkdirs(cachedFileContainer);
                        File tmpFile = new File(cachedFileContainer, outputFile.getName());
                        boolean success = false;
                        try {
                            fileProducer.apply(tmpFile);
                            success = true;
                        } finally {
                            // If fileProducer throws any Exception, we need to clean up the cached
                            // file container directory
                            if (!success) {
                                FileUtils.deletePath(cachedFileContainer);
                            }
                        }

                        // Before renaming, check whether the temporary file exists since
                        // fileProducer is not required to always create a new file.
                        if (tmpFile.exists() && !tmpFile.equals(cachedFile)) {
                            Files.move(tmpFile, cachedFile);
                        }

                        // Write the inputs to the inputs file for diagnostic purposes
                        Files.write(inputs.toString(), inputsFile, StandardCharsets.UTF_8);
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
        // inputs' key only).
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
     * Invokes a task that accesses a file/directory with inter-process and/or inter-thread locking.
     * That is, processes/threads that access the same file/directory cannot run at the same time,
     * whereas those that access different files/directories can still run concurrently.
     *
     * <p>We design for inter-process and inter-thread locking to take effect within the same cache
     * (i.e., @{code FileCache} instances using same cache directory). Processes/threads using
     * different cache directories can still run in parallel even when they are accessing the same
     * file/directory.
     *
     * <p>Note that the file/directory to be accessed may or may not already exist.
     *
     * @param accessedFile the file/directory that a task is going to access
     * @param task the task that will be accessing the file/directory
     * @param interProcessLocking set to {@code true} to enable both inter-process and inter-thread
     *     locking, {@code false} to enable inter-thread locking only
     */
    @VisibleForTesting
    void doLocked(
            @NonNull File accessedFile,
            @NonNull IOExceptionRunnable task,
            boolean interProcessLocking)
            throws IOException {
        if (interProcessLocking) {
            doProcessLocked(accessedFile, task);
        } else {
            doThreadLocked(accessedFile, task);
        }
    }

    /**
     * Invokes a task that accesses a file/directory with both inter-process and inter-thread
     * locking.
     */
    private void doProcessLocked(@NonNull File accessedFile, @NonNull IOExceptionRunnable task)
            throws IOException {
        // We use Java's file-locking API to enable inter-process locking. The API permits only one
        // thread per process to wait on a file lock, and it will throw an
        // OverlappingFileLockException if more than one thread in a process attempt to acquire the
        // same file lock. Therefore, we run the file-locking mechanism also with inter-thread
        // locking.
        doThreadLocked(
                accessedFile,
                () -> {
                    // Create a lock file for the task. We don't use the file being accessed
                    // (which might not already exist) as the lock file since we don't want it to be
                    // affected by our locking mechanism (specifically, the locking mechanism will
                    // always create the lock file and delete it after the task is executed;
                    // however, a task may or may not create the file that it is supposed to
                    // access).
                    String lockFileName =
                            Hashing.sha1()
                                    .hashString(
                                            accessedFile.getCanonicalPath(), StandardCharsets.UTF_8)
                                    .toString();
                    File lockFile = new File(mCacheDirectory, lockFileName);
                    FileChannel fileChannel = new RandomAccessFile(lockFile, "rw").getChannel();
                    FileLock fileLock = fileChannel.lock();
                    try {
                        task.run();
                    } finally {
                        // Delete the lock file first; if we delete the lock file after the file
                        // lock is released, another process might have grabbed the file lock and
                        // we will be deleting the file while the other process is using it.
                        lockFile.delete();
                        fileLock.release();
                        fileChannel.close();
                    }
                });
    }

    /**
     * Invokes a task that accesses a file/directory with inter-thread locking only and without
     * inter-process locking.
     */
    private void doThreadLocked(@NonNull File accessedFile, @NonNull IOExceptionRunnable task)
            throws IOException {
        // Since inter-thread (and inter-process) locking is specific to each cache, we combine
        // both the cache directory and the accessed file's paths as the object that the task is
        // synchronized on.
        synchronized (
                (mCacheDirectory.getCanonicalPath() + accessedFile.getCanonicalPath()).intern()) {
            task.run();
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

    /**
     * List of input parameters to be provided by the client when calling method
     * {@link FileCache#getOrCreateFile(File, Inputs, IOExceptionFunction)}.
     */
    public static class Inputs {

        @NonNull private final LinkedHashMap<String, String> mParameters;

        /** Builder of {@link FileCache.Inputs}. */
        public static class Builder {

            @NonNull
            private final LinkedHashMap<String, String> mParameters = Maps.newLinkedHashMap();

            /**
             * Adds an input file/directory. If a parameter with the same name exists, the
             * parameter's value is overwritten.
             */
            public Builder put(@NonNull String name, @NonNull File value) {
                mParameters.put(name, value.getPath());
                return this;
            }

            /**
             * Adds an input parameter with a String value. If a parameter with the same name
             * exists, the parameter's value is overwritten.
             */
            public Builder put(@NonNull String name, @NonNull String value) {
                mParameters.put(name, value);
                return this;
            }

            /**
             * Adds an input parameter with a Boolean value. If a parameter with the same name
             * exists, the parameter's value is overwritten.
             */
            public Builder put(@NonNull String name, @NonNull boolean value) {
                mParameters.put(name, String.valueOf(value));
                return this;
            }

            /**
             * Builds an {@code Inputs} instance.
             *
             * @throws IllegalStateException if the inputs are empty
             */
            public Inputs build() {
                if (mParameters.isEmpty()) {
                    throw new IllegalStateException("Inputs must not be empty.");
                }
                return new Inputs(this);
            }
        }

        private Inputs(@NonNull Builder builder) {
            mParameters = Maps.newLinkedHashMap(builder.mParameters);
        }

        @Override
        @NonNull
        public String toString() {
            return Joiner.on(System.lineSeparator()).withKeyValueSeparator("=").join(mParameters);
        }

        /**
         * Returns a key representing this list of input parameters. They input parameters are
         * ordered according to the order in which they were added when building this {@code Inputs}
         * object. Two lists of input parameters are considered different if the input parameters
         * are different in size, order, or values. This method guarantees to return different keys
         * for different lists of inputs.
         */
        @NonNull
        public String getKey() {
            return Hashing.sha1().hashString(toString(), StandardCharsets.UTF_8).toString();
        }
    }
}
