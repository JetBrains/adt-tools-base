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

import java.util.Map;

/**
 * The image resources section is a mix-bag of a lot of stuff
 * (thumbnail, guides, printing data, EXIF, XMPP, etc.).
 * This section is divided into typed blocks.
 */
@Chunked
final class ImageResources {
    @Chunk(byteCount = 4)
    long length;

    // Each block has a padded size to make it even
    // Specifying how many bytes we want to read upfront
    // ensures we'll be able to successfully read the rest
    // of the document
    @Chunk(dynamicByteCount = "imageResources.length", key = "imageResourceBlock.id")
    Map<Integer, ImageResourceBlock> blocks;

    @SuppressWarnings("unchecked")
    <T> T get(int id) {
        ImageResourceBlock block = blocks.get(id);
        return block == null ? null : (T) block.data;
    }
}
