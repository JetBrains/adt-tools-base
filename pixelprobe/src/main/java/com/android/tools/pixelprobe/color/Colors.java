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
import java.awt.color.ICC_ColorSpace;
import java.awt.color.ICC_Profile;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;

/**
 * Various utilities to manipulate and convert colors.
 *
 * Note: Following java.awt.color.ColorSpace's convention RGB always
 * means an RGB color in the sRGB space. RGB colors not in the sRGB
 * space are noted accordingly ("linearRGB" for instance).
 */
public final class Colors {
    private static final class ColorSpaceHolder {
        static ICC_Profile CMYK_ICC_Profile;
        static ICC_ColorSpace CMYK_ICC_ColorSpace;

        static {
            try (InputStream in = Colors.class.getResourceAsStream("/icc/cmyk/USWebCoatedSWOP.icc")) {
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
     * Cannot be null
     *
     * @return A new array of floats, of the same size as the input array,
     * representing an sRGB color
     */
    public static float[] linearRgbToRgb(float[] c) {
        float[] v = new float[c.length];
        for (int i = 0; i < c.length; i++) {
            v[i] = linearRgbToRgb(c[i]);
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
    public static float linearRgbToRgb(float c) {
        return (c <= 0.0031308f) ? c * 12.92f : ((float) Math.pow(c, 1.0f / 2.4f) * 1.055f) - 0.055f;
    }

    /**
     * Applies a Reinhard tone-mapping curve to the input value
     * and returns the result.
     */
    public static float toneMappingReinhard(float x) {
        return x / (1.0f + x);
    }

    /**
     * Applies an approximated ACES tone-mapping curve to the input value
     * and returns the result.
     */
    public static float toneMappingACES(float x) {
        float a = 2.51f;
        float b = 0.03f;
        float c = 2.43f;
        float d = 0.59f;
        float e = 0.14f;
        return (x * (a * x + b)) / (x * (c * x + d) + e);
    }

    /**
     * Returns a Lab color space.
     */
    public static ColorSpace getLabColorSpace() {
        return CieLabColorSpace.getInstance();
    }

    /**
     * Returns the ICC CMYK color profile used for CMYK to RGB conversions.
     * The color profile is the standard "U.S. Web Coated v2" profile.
     */
    public static ColorSpace getCmykColorSpace() {
        return ColorSpaceHolder.CMYK_ICC_ColorSpace;
    }

    /**
     * Converts the specified HSB color to sRGB. The parameters must
     * be in the range 0..1.
     */
    public static float[] hsbToRgb(float H, float S, float B) {
        if (S == 0.0f) {
            return new float[] { B, B, B };
        }

        double h = H * 6.0;
        if (h >= 6.0) h = 0.0;

        double f = h - Math.floor(h);
        double u = B * (1.0 - S);
        double v = B * (1.0 - S * f);
        double w = B * (1.0 - S * (1.0 - f));

        double r = 0.0;
        double g = 0.0;
        double b = 0.0;

        switch ((int) h) {
            case 0:
                r = B;
                g = w;
                b = u;
                break;
            case 1:
                r = v;
                g = B;
                b = u;
                break;
            case 2:
                r = u;
                g = B;
                b = w;
                break;
            case 3:
                r = u;
                g = v;
                b = B;
                break;
            case 4:
                r = w;
                g = u;
                b = B;
                break;
            case 5:
                r = B;
                g = u;
                b = v;
                break;
        }

        return new float[] { (float) r, (float) g, (float) b };
    }

    /**
     * Returns the description of the ICC profile embedded in the specified color
     * space. If the description cannot be decoded or if the color space is not
     * an ICC color space, this method returns an empty string.
     *
     * @param colorSpace An ICC color space
     *
     * @return The description of the color space's ICC profile, or an empty string
     */
    public static String getIccProfileDescription(ColorSpace colorSpace) {
        if (colorSpace == null) {
            return "";
        }

        if (colorSpace.isCS_sRGB()) {
            return "sRGB IEC61966-2.1";
        }

        if (colorSpace instanceof ICC_ColorSpace) {
            return getIccProfileDescription(((ICC_ColorSpace) colorSpace).getProfile());
        }

        return "";
    }

    /**
     * Returns the description of an ICC profile. If the description cannot be
     * decoded or if the profile is null, this method returns an empty string.
     *
     * @param profile An ICC color profile
     *
     * @return The description of the color space's ICC profile, or an empty string
     */
    public static String getIccProfileDescription(ICC_Profile profile) {
        if (profile == null) return "";

        byte[] data = profile.getData(ICC_Profile.icSigProfileDescriptionTag);
        if (data == null) return "";

        // bytes 0-3  signature
        // bytes 4-7  offset to tag data
        // bytes 8-11 data length
        int length = data[8] << 24 | data[9] << 16 | data[10] << 8 | data[11];

        try {
            // Skip the null terminator
            return new String(data, 12, length - 1, "US-ASCII");
        } catch (UnsupportedEncodingException e) {
            return "";
        }
    }
}
