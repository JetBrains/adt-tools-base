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
 * The list of layers is actually made of two lists in the PSD
 * file. First, the description and extra data for each layer
 * (name, bounds, etc.). Then an image representation for each
 * layer, as a series of independently encoded channels.
 */
@Chunked
final class LayersList {
    @Chunk(byteCount = 4)
    long length;

    // The count can be negative, which means the first
    // alpha channel contains the transparency data for
    // the flattened image. This means we must ensure
    // we always take the absolute value of the layer
    // count to build lists
    @Chunk
    short count;

    @Chunk(dynamicSize = "Math.abs(layersList.count)")
    List<RawLayer> layers;

    @Chunk(dynamicSize = "Math.abs(layersList.count)")
    List<ChannelsContainer> channels;
}
