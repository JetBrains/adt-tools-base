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

package com.android.tools.pixelprobe.color;

import java.awt.color.ColorSpace;
import java.awt.image.DataBuffer;
import java.awt.image.IndexColorModel;

/**
 * An index color model whose colors can be defined in a non-sRGB color space.
 */
public class CsIndexColorModel extends IndexColorModel {
    private CsIndexColorModel(int bits, int size, int[] cmap, int start, boolean hasalpha,
            int trans, int transferType) {
        super(bits, size, cmap, start, hasalpha, trans, transferType);
    }

    /**
     * Creates a new index color model managed by a color space. The color model
     * is assumed to use 8 bits per pixel. All colors in the color map are assumed
     * to be opaque. Each plane in the color map must have 256 entries.
     *
     * @param size Size of the color map
     * @param map RGB color planes, its length must be <pre>3 * size</pre>
     * @param transparency Index of the transparency pixel
     */
    public static CsIndexColorModel create(int size, byte[] map, int transparency, ColorSpace colorSpace) {
        int[] sRgbMap = new int[size];
        convert(size, map, sRgbMap, colorSpace);
        return new CsIndexColorModel(8, size, sRgbMap, 0, false, transparency, DataBuffer.TYPE_BYTE);
    }

    private static void convert(int size, byte[] srcMap, int[] dstMap, ColorSpace colorSpace) {
        float[] src = new float[3];
        for (int i = 0; i < size; i++) {
            src[0] = srcMap[i      ] / 255.0f;
            src[1] = srcMap[i + 256] / 255.0f;
            src[2] = srcMap[i + 512] / 255.0f;

            float[] dst = colorSpace.toRGB(src);
            dstMap[i] = (((int) (dst[0] * 255.0f) & 0xff) << 16) |
                        (((int) (dst[1] * 255.0f) & 0xff) <<  8) |
                         ((int) (dst[2] * 255.0f) & 0xff);
        }
    }
}
