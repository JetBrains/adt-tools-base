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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Entry source that gets its data from a byte array. The most important use is to keep deflated
 * files in memory before storing them in the zip file.
 */
public class ByteArrayEntrySource implements EntrySource {
    /**
     * The byte data.
     */
    @NonNull
    private byte[] mData;

    /**
     * Creates a new source.
     *
     * @param data the data to use as source
     */
    public ByteArrayEntrySource(@NonNull byte[] data) {
        mData = data;
    }

    @NonNull
    @Override
    public InputStream open() throws IOException {
        return new ByteArrayInputStream(mData);
    }

    @Override
    public long size() {
        return mData.length;
    }

    @Override
    public EntrySource innerCompressed() {
        return null;
    }
}
