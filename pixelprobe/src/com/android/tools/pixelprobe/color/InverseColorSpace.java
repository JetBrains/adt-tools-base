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

/**
 * The {@link InverseColorSpace} wraps another {@link ColorSpace}.
 * This class inverses the values passed to {@link #toRGB(float[])} and
 * {@link #toCIEXYZ(float[])}. It also inverses the values returned by
 * {@link #fromRGB(float[])} and {@link #fromCIEXYZ(float[])}.
 */
public class InverseColorSpace extends ColorSpace {
    private final ColorSpace colorSpace;

    /**
     * Create a new inverted color space.
     *
     * @param colorSpace The color space to wrap, cannot be null
     */
    public InverseColorSpace(ColorSpace colorSpace) {
        super(colorSpace.getType(), colorSpace.getNumComponents());
        this.colorSpace = colorSpace;
    }

    @Override
    public float[] toRGB(float[] value) {
        for (int i = 0; i < value.length; i++) {
            value[i] = 1.0f - value[i];
        }
        return colorSpace.toRGB(value);
    }

    @Override
    public float[] fromRGB(float[] rgb) {
        float[] value = colorSpace.fromRGB(rgb);
        for (int i = 0; i < value.length; i++) {
            value[i] = 1.0f - value[i];
        }
        return value;
    }

    @Override
    public float[] toCIEXYZ(float[] value) {
        for (int i = 0; i < value.length; i++) {
            value[i] = 1.0f - value[i];
        }
        return colorSpace.toCIEXYZ(value);
    }

    @Override
    public float[] fromCIEXYZ(float[] xyz) {
        float[] value  = colorSpace.fromCIEXYZ(xyz);
        for (int i = 0; i < value.length; i++) {
            value[i] = 1.0f - value[i];
        }
        return value;
    }
}
