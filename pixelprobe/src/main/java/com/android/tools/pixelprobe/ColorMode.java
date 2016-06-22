/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.tools.pixelprobe;

/**
 * This enum contains the list of color modes for an Image.
 */
@SuppressWarnings("unused")
public enum ColorMode {
    /**
     * Each pixel is either white or black.
     */
    BITMAP,
    /**
     * Each pixel is a grayscale value.
     */
    GRAYSCALE,
    /**
     * Each pixel is an index in a color palette.
     */
    INDEXED,
    /**
     * Each pixel is stored either as an RGB or
     * ARGB (RGB + alpha) value. This does not
     * define the image's color space.
     */
    RGB,
    /**
     * Each pixel is stored as a CMYK value.
     */
    CMYK,
    /**
     * Unknown color mode.
     */
    UNKNOWN,
    /**
     * Unknown color mode.
     */
    NONE,
    /**
     * Each pixel is stored over more than 4 channels.
     */
    MULTI_CHANNEL,
    /**
     * Each pixel is represented as the superimposition of
     * two halftone colors.
     */
    DUOTONE,
    /**
     * Each is pixel is stored as a L*a*b* value (3 channels).
     */
    LAB
}
