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
 * The TypeToolObject layer property contains all the data needed to
 * render a text layer.
 */
@Chunked
final class TypeToolObject {
    // Descriptor that holds the actual text
    static final String KEY_TEXT = "Txt ";
    // Descriptor that holds structured text data used for styling, see TextEngine
    static final String KEY_ENGINE_DATA = "EngineData";
    // Descriptor that holds the text's bounding box, required to apply alignment
    static final String KEY_BOUNDING_BOX = "boundingBox";

    @Chunk
    short version;

    // The text's transform (translation, scale and shear)
    @Chunk
    double xx;
    @Chunk
    double xy;
    @Chunk
    double yx;
    @Chunk
    double yy;
    @Chunk
    double tx;
    @Chunk
    double ty;

    @Chunk
    short textVersion;

    @Chunk
    int testDescriptorVersion;

    // The descriptor is a horrifyingly generic object
    // that happens to hold important data (the actual
    // text, styling info, etc.)
    @Chunk
    Descriptor text;

    @Chunk
    short warpVersion;

    @Chunk
    int warpDescriptorVersion;

    @Chunk
    Descriptor warp;

    // These always seem to be set to 0
    @Chunk
    int left;
    @Chunk
    int top;
    @Chunk
    int right;
    @Chunk
    int bottom;
}
