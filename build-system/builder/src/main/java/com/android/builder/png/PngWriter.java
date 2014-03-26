/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.builder.png;

import com.android.annotations.NonNull;
import com.google.common.collect.Lists;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * PNG Writer.
 *
 * A PNG file is simply a signature followed by a number of {@link Chunk}.
 *
 * PNG specification reference: http://tools.ietf.org/html/rfc2083
 */
public class PngWriter {

    /** Chunk type for the chunk that ends the PNG file. */
    private static final Chunk sIend = new Chunk(new byte[] { 'I', 'E', 'N', 'D' });

    /**
     * Signature of a PNG file.
     */
    public static final byte[] SIGNATURE = new byte[] {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A };


    /** Chunk type for the Image-Data chunk. */
    public static final byte[] IDAT = new byte[] { 'I', 'D', 'A', 'T' };
    /** Chunk type for the Image-Header chunk. */
    public static final byte[] IHDR = new byte[] { 'I', 'H', 'D', 'R' };
    /** Chunk type for the palette chunk. */
    public static final byte[] PLTE = new byte[] { 'P', 'L', 'T', 'E' };
    /** Chunk type for the transparency data chunk. */
    public static final byte[] TRNS = new byte[] { 't', 'R', 'N', 'S' };


    @NonNull
    private final File mToFile;

    private Chunk mIhdr;
    private final List<Chunk> mChunks = Lists.newArrayList();

    public PngWriter(@NonNull File toFile) {
        mToFile = toFile;
    }

    public PngWriter setIhdr(@NonNull Chunk chunk) {
        mIhdr = chunk;
        return this;
    }

    public PngWriter setChunk(@NonNull Chunk chunk) {
        mChunks.add(chunk);
        return this;
    }

    public PngWriter setChunks(@NonNull List<Chunk> chunks) {
        mChunks.addAll(chunks);
        return this;
    }

    public void write() throws IOException {
        FileOutputStream fos = new FileOutputStream(mToFile);
        try {
            // copy the sig
            fos.write(SIGNATURE);

            mIhdr.write(fos);
            for (Chunk chunk : mChunks) {
                chunk.write(fos);
            }

            sIend.write(fos);
        } finally {
            fos.close();
        }
    }
}
