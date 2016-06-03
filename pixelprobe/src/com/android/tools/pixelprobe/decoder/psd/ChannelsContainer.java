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
 * Contains the image data for each channel of a layer.
 * There is one ChannelsContainer per layer.
 */
@Chunked
final class ChannelsContainer {
    @Chunk(
        dynamicSize =
            "$T list = ($T) stack.get(1);\n" +
            "size = list.layers.get(list.channels.size()).channels",
        sizeParams = {
            LayersList.class, LayersList.class
        }
    )
    List<ChannelImageData> imageData;
}
