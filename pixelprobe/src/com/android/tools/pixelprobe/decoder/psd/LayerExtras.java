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
import java.util.Map;

/**
 * The layer's extras contains important values such as
 * the layer's name and a series of named properties.
 */
@Chunked
final class LayerExtras {
    @Chunk
    MaskAdjustment maskAdjustment;

    @Chunk(byteCount = 4)
    long blendRangesLength;

    // The first blend range is always composite gray
    @Chunk(dynamicByteCount = "layerExtras.blendRangesLength")
    List<BlendRange> layerBlendRanges;

    // The layer's name is stored as a Pascal string,
    // padded to a multiple of 4 bytes
    @Chunk(byteCount = 1)
    short nameLength;
    @Chunk(dynamicByteCount = "layerExtras.nameLength")
    String name;
    @Chunk(dynamicByteCount =
            "((layerExtras.nameLength + 4) & ~3) - (layerExtras.nameLength + 1)")
    Void namePadding;

    @Chunk(key = "layerProperty.key")
    Map<String, LayerProperty> properties;
}
