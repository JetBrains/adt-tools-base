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
import com.android.tools.pixelprobe.ColorMode;

/**
 * PSD header. A few magic values and important information
 * about the image's dimensions, color depth and mode.
 */
@Chunked
final class Header {
    // Magic marker
    @Chunk(byteCount = 4, match = "\"8BPS\"")
    String signature;

    // Version is always 1
    @Chunk(match = "1")
    short version;

    // 6 reserved bytes that must always be set to 0
    @Chunk(byteCount = 6)
    Void reserved;

    @Chunk(byteCount = 2)
    int channels;

    // Height comes before width here
    @Chunk
    int height;
    @Chunk
    int width;

    @Chunk
    short depth;
    // We only support RGB documents
    @Chunk(byteCount = 2)
    ColorMode colorMode;
}
