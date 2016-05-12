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
import com.android.utils.FileUtils;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;

/**
 * Provides synchronization for different processes or threads.
 *
 * <p>Each piece of code to be run ("runnable") is tied to a key: Runnables with the same key can be
 * executed by only one single process or thread at a time, whereas runnables with different keys
 * can be executed concurrently.
 */
public class ProcessLock {

    private static final File LOCK_FOLDER = FileUtils.join(
            new File(System.getProperty("user.home")), ".android-studio", "process-lock");

    /**
     * Invokes a runnable with inter-process and/or inter-thread locking. That is, processes/threads
     * with the same provided key cannot run concurrently, whereas those with different keys can
     * still run.
     *
     * @param key the key of a process/thread
     * @param runnable the runnable
     * @param interProcessLocking <code>true</code> to enable both inter-process and inter-thread
     *                            locking, <code>false</code> to enable inter-thread locking only
     */
    public static void doLocked(
            @NonNull String key,
            @NonNull IOExceptionRunnable runnable,
            boolean interProcessLocking)
            throws IOException {
        if (interProcessLocking) {
            doProcessLocked(key, runnable);
        } else {
            doThreadLocked(key, runnable);
        }
    }

    private static void doProcessLocked(@NonNull String key, @NonNull IOExceptionRunnable runnable)
            throws IOException {
        // We use Java's file-locking API to enable inter-process locking. Note that the API only
        // permits one thread per process to wait on the file lock, and it will throw an
        // OverlappingFileLockException if more than one thread in the current Java virtual machine
        // attempt to acquire the same file lock. Therefore, we run the file-locking mechanism
        // also with inter-thread locking.
        doThreadLocked(key, () -> {
            // Use key as the lock file's name
            String lockFileName = FileUtils.getValidFileName(key, "", LOCK_FOLDER);
            FileUtils.mkdirs(LOCK_FOLDER);

            File lockFile = new File(LOCK_FOLDER, lockFileName);
            FileChannel fileChannel = new RandomAccessFile(lockFile, "rw").getChannel();
            FileLock fileLock = fileChannel.lock();
            try {
                runnable.run();
            } finally {
                lockFile.delete(); // Delete the file first
                fileLock.release();
                fileChannel.close();
            }
        });
    }

    private static void doThreadLocked(@NonNull String key, @NonNull IOExceptionRunnable runnable)
            throws IOException {
        synchronized (key.intern()) {
            runnable.run();
        }
    }

}