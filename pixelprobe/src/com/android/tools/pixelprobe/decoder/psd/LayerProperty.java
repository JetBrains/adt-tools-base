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
 * Layer properties encode a lot of interesting attributes but we will
 * only decode a few of them.
 */
@Chunked
final class LayerProperty {
    // The property holds the layer's effects (drop shadows, etc.)
    static final String KEY_EFFECTS = "lfx2";
    // The property holds the layer's stacked effects (multiple
    // drop shadows, etc.) If this property is present, the
    // "lfx2" property above should not be present
    static final String KEY_MULTI_EFFECTS = "lmfx";
    // Indicates that this layer is a section (start or end of a layer group)
    static final String KEY_SECTION = "lsct";
    // The property holds the Unicode name of the layer
    static final String KEY_NAME = "luni";
    // The property holds the layer's ID
    static final String KEY_ID = "lyid";
    // The property holds the solid color adjustment information
    static final String KEY_ADJUSTMENT_SOLID_COLOR = "SoCo";
    // The property holds the text information (styles, text data, etc.)
    static final String KEY_TEXT = "TySh";
    // The property holds the vector data
    static final String KEY_VECTOR_MASK = "vmsk";
    // The layer has a depth of 16 bit per channel
    static final String KEY_LAYER_DEPTH_16 = "Lr16";
    // The layer has a depth of 32 bit per channel
    static final String KEY_LAYER_DEPTH_32 = "Lr32";

    @Chunk(byteCount = 4)
    String signature;

    @Chunk(byteCount = 4)
    String key;

    @Chunk(byteCount = 4)
    long length;

    @Chunk(dynamicByteCount = "layerProperty.length",
        switchType = {
            @Chunk.Case(test = "layerProperty.key.equals(\"lmfx\")", type = LayerEffects.class),
            @Chunk.Case(test = "layerProperty.key.equals(\"lfx2\")", type = LayerEffects.class),
            @Chunk.Case(test = "layerProperty.key.equals(\"lsct\")", type = LayerSection.class),
            @Chunk.Case(test = "layerProperty.key.equals(\"luni\")", type = UnicodeString.class),
            @Chunk.Case(test = "layerProperty.key.equals(\"SoCo\")", type = SolidColorAdjustment.class),
            @Chunk.Case(test = "layerProperty.key.equals(\"TySh\")", type = TypeToolObject.class),
            @Chunk.Case(test = "layerProperty.key.equals(\"vmsk\")", type = VectorMask.class)
        }
    )
    Object data;
}
