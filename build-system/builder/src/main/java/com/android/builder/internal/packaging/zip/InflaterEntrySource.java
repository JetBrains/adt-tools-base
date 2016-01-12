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
import com.google.common.base.Preconditions;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

/**
 * Entry source that inflates an inner source. The inner source must contain delfated data.
 */
class InflaterEntrySource implements EntrySource {
    /**
     * The inner source of deflated data.
     */
    @NonNull
    private EntrySource mDeflatedSource;

    /**
     * Size of uncompressed data.
     */
    private long mUncompressedSize;

    /**
     * Creates a new source.
     *
     * @param deflatedSource the source of deflated data
     * @param uncompressedSize the size of deflated data after inflation
     */
    InflaterEntrySource(@NonNull EntrySource deflatedSource, long uncompressedSize) {
        Preconditions.checkArgument(uncompressedSize >= 0, "uncompressedSize (%s) < 0",
                uncompressedSize);

        mDeflatedSource = deflatedSource;
        mUncompressedSize = uncompressedSize;
    }

    @NonNull
    @Override
    public InputStream open() throws IOException {
        InputStream rawStream = mDeflatedSource.open();

        /*
         * The extra byte is a dummy byte required by the inflater. Weirdo.
         * (see the java.util.Inflater documentation). Looks like a hack...
         * "Oh, I need an extra dummy byte to allow for some... err... optimizations..."
         */
        ByteArrayInputStream hackByte = new ByteArrayInputStream(new byte[] { 0 });
        return new InflaterInputStream(new SequenceInputStream(rawStream, hackByte),
                new Inflater(true));
    }

    @Override
    public long size() {
        return mUncompressedSize;
    }

    @Override
    public EntrySource innerCompressed() {
        return mDeflatedSource;
    }
}
