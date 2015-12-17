/*
 * Copyright (C) 2010 The Android Open Source Project
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
import com.android.builder.signing.SignedJarBuilder.IZipEntryFilter.ZipAbortException;
import com.google.common.collect.ImmutableList;

import java.io.File;
import java.util.List;

/**
 * An exception thrown during packaging of an APK file.
 */
public final class DuplicateFileException extends ZipAbortException {
    private static final long serialVersionUID = 1L;
    @NonNull
    private final String mArchivePath;
    @NonNull
    private final List<File> mSourceFiles;


    public DuplicateFileException(@NonNull String archivePath, @NonNull File... sourceFiles) {
        super();
        mArchivePath = archivePath;
        this.mSourceFiles = ImmutableList.copyOf(sourceFiles);
    }

    public DuplicateFileException(@NonNull String archivePath, @NonNull List<File> sourceFiles) {
        super();
        mArchivePath = archivePath;
        this.mSourceFiles = ImmutableList.copyOf(sourceFiles);
    }

    @NonNull
    public String getArchivePath() {
        return mArchivePath;
    }

    @NonNull
    public List<File> getSourceFiles() {
        return mSourceFiles;
    }

    @Override
    public String getMessage() {
        StringBuilder sb = new StringBuilder();

        sb.append("Duplicate files copied in APK ").append(mArchivePath).append('\n');
        int index = 1;
        for (File file : mSourceFiles) {
            sb.append("\tFile").append(index++).append(": ").append(file).append('\n');
        }

        return sb.toString();
    }
}