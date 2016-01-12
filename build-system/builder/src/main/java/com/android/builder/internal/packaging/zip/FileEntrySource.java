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

package com.android.builder.internal.packaging.zip;

import com.android.annotations.NonNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Entry source that gets data from a file.
 */
public class FileEntrySource implements EntrySource {

    /**
     * File file.
     */
    @NonNull
    private File mFile;

    /**
     * Creates a new entry source.
     *
     * @param file the file where data comes from
     */
    public FileEntrySource(@NonNull File file) {
        mFile = file;
    }

    @NonNull
    @Override
    public InputStream open() throws IOException {
        return new FileInputStream(mFile);
    }

    @Override
    public long size() {
        return mFile.length();
    }

    @Override
    public EntrySource innerCompressed() {
        return null;
    }
}
