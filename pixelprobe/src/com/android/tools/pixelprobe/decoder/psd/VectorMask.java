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
 * A vector mask is a layer property that contains a list of path
 * records, used to descript a path (or vector shape).
 */
@Chunked
final class VectorMask {
    @Chunk
    int version;

    @Chunk
    int flags;

    // LayerProperty.length is rounded to an even byte count
    // Subtract the 8 bytes used for version and flags, and divide
    // by the length of each path record (26 bytes) to know how many
    // path records to read
    @Chunk(dynamicSize = "(int) Math.floor(((($T) stack.get(1)).length - 8) / 26)",
        sizeParams = { LayerProperty.class })
    List<PathRecord> pathRecords;

}
