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

package com.android.builder.files;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.hash.Hashing;
import com.google.common.io.Files;

import org.apache.commons.codec.binary.Base64;

import java.io.File;
import java.io.IOException;

/**
 * File cache that stored files based on their origin path. The general contract of the
 * {@code FileCacheByPath} is that files are stored and can be later retrieved from their
 * original path. For example:
 *
 * <pre>
 * File cacheDir = ... // some directory.
 * FileCacheByPath cache = new FileCacheByPath(cacheDir);
 *
 * File a = new File(...); // some file in the filesystem.
 * cache.add(a);
 *
 * // Modify file "a".
 * File b = cache.get(a); // "b" will be a different file whose
 *                        // contents are those of "a" before
 *                        // being modified.
 * </pre>
 */
public class FileCacheByPath {

    /**
     * The directory where the cache exists.
     */
    @NonNull
    private final File mDirectory;

    /**
     * Creates a new cache.
     *
     * @param directory the directory where the cache is stored
     */
    public FileCacheByPath(@NonNull File directory) {
        Preconditions.checkArgument(directory.isDirectory(), "!directory.isDirectory()");

        mDirectory = directory;
    }

    /**
     * Adds a file to the cache, replacing any file that had the exact same absolute path.
     *
     * @param f the file to add
     * @throws IOException failed to copy the file into the cache
     */
    public void add(@NonNull File f) throws IOException {
        Preconditions.checkArgument(f.isFile(), "!f.isFile()");

        if (!mDirectory.isDirectory()) {
            FileUtils.mkdirs(mDirectory);
        }

        String k = key(f);
        Files.copy(f, new File(mDirectory, k));
    }

    /**
     * Obtains the cached file corresponding to the file with the given path.
     *
     * @param f the path
     * @return the cached file, {@code null} if there is no file in the cache that corresponds to
     * the given file
     */
    @Nullable
    public File get(@NonNull File f) {
        File file = new File(mDirectory, key(f));
        if (file.isFile()) {
            return file;
        } else {
            return null;
        }
    }

    /**
     * Removes any cached version of the given path.
     *
     * @param f the path
     * @throws IOException failed to remove the file
     */
    public void remove(@NonNull File f) throws IOException {
        File toRemove = new File(mDirectory, key(f));
        if (toRemove.exists()) {
            FileUtils.delete(toRemove);
        }
    }

    /**
     * Computes a unique key identifying the path of the file.
     *
     * @param f the path
     * @return the unique key
     */
    @NonNull
    private static String key(@NonNull File f) {
        String absolutePath = f.getAbsolutePath();
        byte[] sha1Sum = Hashing.sha1().hashString(absolutePath, Charsets.UTF_8).asBytes();
        return new String(Base64.encodeBase64(sha1Sum), Charsets.US_ASCII).replaceAll("/", "_");
    }

    /**
     * Clears the cache.
     *
     * @throws IOException failed to clear the cache
     */
    public void clear() throws IOException {
        File[] files = mDirectory.listFiles();
        if (files == null) {
            return;
        }

        for (File f : files) {
            if (f.isFile()) {
                FileUtils.delete(f);
            }
        }
    }
}
