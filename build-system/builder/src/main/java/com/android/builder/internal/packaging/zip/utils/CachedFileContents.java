/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.builder.internal.packaging.zip.utils;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;

import java.io.File;

/**
 * A cache for file contents. The cache allows closing a file and saving in memory its contents
 * (or some related information). It can then be used to check if the contents are still valid
 * at some later time. Typical usage flow is:
 * <p>
 * <pre>
 *    Object fileRepresentation = // ...
 *    File toWrite = // ...
 *    // Write file contents and update in memory representation
 *    CachedFileContents<Object> contents = new CachedFileContents<Object>(toWrite);
 *    contents.closed(fileRepresentation);
 *
 *    // Later, when data is needed:
 *    if (contents.isValid()) {
 *        fileRepresentation = contents.getCache();
 *    } else {
 *        // Re-read the file and recreate the file representation
 *    }
 * </pre>
 * @param <T> the type of cached contents
 */
public class CachedFileContents<T> {

    /**
     * The file.
     */
    @NonNull
    private File mFile;

    /**
     * Time when last closed (time when {@link #closed(Object)} was invoked).
     */
    private long mLastClosed;

    /**
     * Cached data associated with the file.
     */
    @Nullable
    private T mCache;

    /**
     * Creates a new contents. When the file is written, {@link #closed(Object)} should be invoked
     * to set the cache.
     *
     * @param file the file
     */
    public CachedFileContents(@NonNull File file) {
        mFile = file;
    }

    /**
     * Should be called when the file's contents are set and the file closed. This will save the
     * cache and register the file's timestamp to later detect if it has been modified.
     * <p>
     * This method can be called as many times as the file has been written.
     *
     * @param cache an optional cache to save
     */
    public void closed(T cache) {
        mCache = cache;
        mLastClosed = mFile.lastModified();
    }

    /**
     * Are the cached contents still valid? If this method determines that the file has been
     * modified since the last time {@link #closed(Object)} was invoked.
     *
     * @return are the cached contents still valid? If this method returns {@code false}, the
     * cache is cleared
     */
    public boolean isValid() {
        if (mFile.exists() && mFile.lastModified() == mLastClosed) {
            return true;
        } else {
            mCache = null;
            return false;
        }
    }

    /**
     * Obtains the cached data set with {@link #closed(Object)} if the file has not been modified
     * since {@link #closed(Object)} was invoked.
     *
     * @return the last cached data or {@code null} if the file has been modified since
     * {@link #closed(Object)} has been invoked
     */
    @Nullable
    public T getCache() {
        return mCache;
    }
}
