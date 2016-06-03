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
 * An image resource block has a type (or ID) and an optional
 * name. We'll use the ID to find the blocks we want.
 */
@Chunked
final class ImageResourceBlock {
    @Chunk(byteCount = 4, match = "\"8BIM\"")
    String signature;

    @Chunk(byteCount = 2)
    int id;

    // The name is stored as a Pascal string with even padding
    // The padding takes into account the length byte
    @Chunk(byteCount = 1)
    short nameLength;
    @Chunk(dynamicByteCount = "imageResourceBlock.nameLength")
    String name;
    @SuppressWarnings("unused")
    @Chunk(dynamicByteCount = "Math.max(1, imageResourceBlock.nameLength & 1)")
    Void padding;

    // The actual byte length of the block
    @Chunk(byteCount = 4)
    long length;

    // The length must be padded to make it even if we want to
    // successfully read a block. We could also add a Void pad after.
    @Chunk(dynamicByteCount = "imageResourceBlock.length + (imageResourceBlock.length & 1)",
        switchType = {
            @Chunk.Case(test = "imageResourceBlock.id == 0x0408",
                    type = GuidesResourceBlock.class),
            @Chunk.Case(test = "imageResourceBlock.id == 0x040C",
                    type = ThumbnailResourceBlock.class),
            @Chunk.Case(test = "imageResourceBlock.id == 0x03ED",
                    type = ResolutionInfoBlock.class),
            @Chunk.Case(test = "imageResourceBlock.id == 0x040F",
                    type = ColorProfileBlock.class),
            @Chunk.Case(test = "imageResourceBlock.id == 0x0416",
                    type = UnsignedShortBlock.class),
            @Chunk.Case(test = "imageResourceBlock.id == 0x0417",
                    type = UnsignedShortBlock.class)
        }
    )
    Object data;
}
