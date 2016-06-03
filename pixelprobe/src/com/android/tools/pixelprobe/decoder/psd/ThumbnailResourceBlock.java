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

package com.android.tools.pixelprobe.decoder.psd;

import com.android.tools.chunkio.Chunk;
import com.android.tools.chunkio.Chunked;

/**
 * Thumbnails can be stored as RAW data or JPEGs.
 * We currently only support the JPEG format.
 */
@Chunked
final class ThumbnailResourceBlock {
    static final int ID = 0x040C;

    // RAW=0, JPEG=1, we only want JPEG
    @Chunk(match = "1")
    int format;

    @Chunk(byteCount = 4)
    long width;
    @Chunk(byteCount = 4)
    long height;

    @Chunk(byteCount = 4)
    long rowBytes;
    @Chunk(byteCount = 4)
    long size;
    @Chunk(byteCount = 4)
    long compressedSize;

    // JPEG guarantees 24bpp and 1 plane
    @Chunk(match = "24")
    short bpp;
    @Chunk(match = "1")
    short planes;

    @Chunk(dynamicByteCount = "thumbnailResourceBlock.compressedSize")
    byte[] thumbnail;
}
