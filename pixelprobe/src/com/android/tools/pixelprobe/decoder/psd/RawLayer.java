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

import java.util.List;

/**
 * A layer contains a few static properties and a list of
 * keyed "extras", The extras are crucial for non-image
 * layers. They also contain layer effects (drop shadows,
 * inner glow, etc.).
 */
@Chunked
final class RawLayer {
    // Mask for the flags field, indicating whether the layer is visible or not
    static final int INVISIBLE = 0x2;

    // Firs we have the layer's bounds, in pixels,
    // in absolute image coordinates
    @Chunk
    int top;
    @Chunk
    int left;
    @Chunk
    int bottom;
    @Chunk
    int right;

    // The channels count, 3 or 4 in our case since we
    // only support RGB files
    @Chunk(byteCount = 2)
    short channels;
    // Important stuff in there, read on
    @Chunk(dynamicSize = "rawLayer.channels")
    List<ChannelInformation> channelsInfo;

    @Chunk(byteCount = 4, match = "\"8BIM\"")
    String signature;
    @Chunk(byteCount = 4)
    String blendMode;

    // The opacity is stored as an unsigned byte,
    // from 0 (transparent) to 255 (opaque)
    @Chunk(byteCount = 1)
    short opacity;
    @Chunk
    byte clipping;
    @Chunk
    byte flags;

    // Padding gunk
    @Chunk(byteCount = 1)
    Void filler;

    // The number of bytes taken by all the extras
    @Chunk(byteCount = 4)
    long extraLength;

    @Chunk(dynamicByteCount = "rawLayer.extraLength")
    LayerExtras extras;
}
