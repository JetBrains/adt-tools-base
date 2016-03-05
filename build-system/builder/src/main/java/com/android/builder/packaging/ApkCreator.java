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

package com.android.builder.packaging;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;

/**
 * Creates APKs based on provided entries.
 */
public interface ApkCreator extends Closeable {

    /**
     * Copies the content of a Jar/Zip archive into the receiver archive.
     * <p>
     * An optional {@link ZipEntryFilter} allows to selectively choose which files
     * to copy over.
     * @param zip the zip to copy data from
     * @param filter the filter or  {@code null}
     * @throws IOException I/O error
     * @throws ZipAbortException if the {@link ZipEntryFilter} filter indicated that the write
     * must be aborted.
     */
    void writeZip(@NonNull File zip, @Nullable ZipEntryFilter filter) throws IOException,
            ZipAbortException;

    /**
     * Writes a new {@link File} into the archive.
     * @param inputFile the {@link File} to write.
     * @param jarPath the filepath inside the archive.
     * @throws IOException I/O error
     */
    void writeFile(@NonNull File inputFile, @NonNull String jarPath) throws IOException;
}
