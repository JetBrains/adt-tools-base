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
 * A layer section is a property that marks layer groups.
 */
@Chunked
final class LayerSection {
    /**
     * The section type.
     */
    enum Type {
        /**
         * Who knows.
         */
        OTHER,
        /**
         * Open group of layers.
         */
        GROUP_OPENED,
        /**
         * Closed group of layers.
         */
        GROUP_CLOSED,
        /**
         * End of a group (invisible in the UI).
         */
        BOUNDING
    }

    @Chunk(byteCount = 4)
    Type type;

    @Chunk(byteCount = 4,
        readIf = "(($T) stack.get(1)).length >= 12",
        readIfParams = { LayerProperty.class }
    )
    String signature;

    @Chunk(byteCount = 4,
        readIf = "(($T) stack.get(1)).length >= 12",
        readIfParams = { LayerProperty.class }
    )
    String blendMode;

    // Apparently only used for the animation timeline
    @Chunk(byteCount = 4,
        readIf = "(($T) stack.get(1)).length >= 16",
        readIfParams = { LayerProperty.class }
    )
    int subType;
}
