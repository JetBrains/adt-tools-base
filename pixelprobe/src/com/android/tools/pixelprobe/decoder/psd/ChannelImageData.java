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
 * The image data for a layer's channel. The compression method can
 * in theory vary per layer. The overall length of the data comes from
 * the ChannelInformation section seen earlier.
 */
@Chunked
final class ChannelImageData {
    @Chunk(byteCount = 2)
    CompressionMethod compression;

    // Subtract 2 bytes because the channel info data length takes the
    // compression method into account
    @Chunk(
        dynamicByteCount =
            "$T list = ($T) stack.get(2);\n" +
            "$T layer = list.layers.get(list.channels.size());\n" +
            "$T container = ($T) stack.get(1);\n" +
            "$T info = layer.channelsInfo.get(container.imageData.size());\n" +
            "byteCount = info.dataLength - 2",
        byteCountParams = {
            LayersList.class, LayersList.class,
            RawLayer.class,
            ChannelsContainer.class, ChannelsContainer.class,
            ChannelInformation.class
        }
    )
    byte[] data;
}
