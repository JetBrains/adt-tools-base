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

package com.android.tools.pixelprobe.util;

import java.awt.color.ColorSpace;
import java.awt.color.ICC_ColorSpace;
import java.awt.color.ICC_Profile;
import java.io.IOException;
import java.io.InputStream;

/**
 * Various utilities to manipulate and convert colors.
 */
public final class Colors {
    private static final class ColorSpaceHolder {
        static ICC_Profile CMYK_ICC_Profile;
        static ICC_ColorSpace CMYK_ICC_ColorSpace;

        static {
            try (InputStream in = Colors.class.getResourceAsStream("USWebCoatedSWOP.icc")) {
                CMYK_ICC_Profile = ICC_Profile.getInstance(in);
                CMYK_ICC_ColorSpace = new ICC_ColorSpace(CMYK_ICC_Profile);
            } catch (IOException e) {
                throw new RuntimeException("Cannot find built-in CMYK ICC color profile");
            }
        }
    }

    private Colors() {
    }

    /**
     * Converts the specified linear RGB color, represented as a float array,
     * to an sRGB color. The input values must be between 0.0 and 1.0. The
     * output values are in the same range.
     *
     * @param c An array of floats representing a linear RGB color.
     *          Cannot be null
     *
     * @return A new array of floats, of the same size as the input array,
     *         representing an sRGB color
     */
    public static float[] linearTosRGB(float[] c) {
      float[] v = new float[c.length];
      for (int i = 0; i < c.length; i++) {
          v[i] = linearTosRGB(c[i]);
      }
      return v;
    }

    /**
     * Converts the specified linear RGB component to an sRGB component.
     * The input value must be between 0.0 and 1.0. The output value is
     * in the same range.
     *
     * @param c A linear RGB component between 0.0 and 1.0
     *
     * @return An sRGB component between 0.0 and 1.0
     */
    public static float linearTosRGB(float c) {
        return (c <= 0.0031308f) ? c * 12.92f : ((float) Math.pow(c, 1.0f / 2.4f) * 1.055f) - 0.055f;
    }

    /**
     * Returns a Lab color space.
     */
    public static ColorSpace getLabColorSpace() {
        return CieLab.getInstance();
    }

    /**
     * Converts the specified Lab color to linear RGB. The conversion
     * is done using a D50 illuminant.
     */
    public static float[] LABtoRGB(float L, float a, float b) {
        return getLabColorSpace().toRGB(new float[] { L, a, b });
    }

    /**
     * Converts the specified CMYK color to linear RGB. The conversion
     * is done using the "U.S. Web Coated v2" ICC color profile.
     */
    public static float[] CMYKtoRGB(float C, float M, float Y, float K) {
        return getCmykColorSpace().toRGB(new float[] { C, M, Y, K });
    }

    /**
     * Returns the ICC CMYK color profile used for CMYK to RGB conversions.
     * The color profile is the standard "U.S. Web Coated v2" profile.
     */
    public static ColorSpace getCmykColorSpace() {
        return ColorSpaceHolder.CMYK_ICC_ColorSpace;
    }
}
