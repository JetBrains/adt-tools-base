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
 * Specific to masks. This section has to be read carefully from the
 * file has its length varies depending on a set of flags.
 */
@Chunked
final class MaskAdjustment {
    @Chunk(byteCount = 4, stopIf = "maskAdjustment.length == 0")
    long length;

    @Chunk(byteCount = 4)
    long top;
    @Chunk(byteCount = 4)
    long left;
    @Chunk(byteCount = 4)
    long bottom;
    @Chunk(byteCount = 4)
    long right;

    @Chunk(byteCount = 1)
    short defaultColor;

    @Chunk
    byte flags;

    @Chunk(readIf = "(maskAdjustment.flags & 0x10) != 0")
    byte maskParameters;

    @Chunk(byteCount = 1, readIf = "(maskAdjustment.maskParameters & 0x1) != 0")
    short userMaskDensity;
    @Chunk(readIf = "(maskAdjustment.maskParameters & 0x2) != 0")
    double userMaskFeather;
    @Chunk(byteCount = 1, readIf = "(maskAdjustment.maskParameters & 0x4) != 0")
    short vectorMaskDensity;
    @Chunk(readIf = "(maskAdjustment.maskParameters & 0x8) != 0")
    double vectorMaskFeather;

    @Chunk(readIf = "maskAdjustment.length == 20", stopIf = "maskAdjustment.length == 20")
    short padding;

    @Chunk
    byte realFlags;

    @Chunk(byteCount = 1)
    short userMaskBackground;

    @Chunk(byteCount = 4)
    long realTop;
    @Chunk(byteCount = 4)
    long realLeft;
    @Chunk(byteCount = 4)
    long realBottom;
    @Chunk(byteCount = 4)
    long realRight;
}
