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

import java.awt.*;
import java.awt.color.ColorSpace;
import java.awt.color.ICC_ColorSpace;
import java.awt.color.ICC_Profile;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.io.IOException;
import java.io.InputStream;

/**
 * Various utilities to manipulate and convert colors.
 */
public final class Colors {
    private static final float[] WHITE_POINT_D65 = { 95.0429f, 100.0f, 108.89f };

    private static ICC_Profile CMYK_ICC_Profile;
    private static ICC_ColorSpace CMYK_ICC_ColorSpace;

    private Colors() {
    }

    /**
     * Converts a Lab color to linear RGB.
     */
    public static float[] LABtoRGB(float L, float a, float b) {
        ColorSpace xyz = ColorSpace.getInstance(ColorSpace.CS_CIEXYZ);
        return xyz.toRGB(LABtoXYZ(L, a, b));
    }

    /**
     * Converts a Lab color to the XYZ color space (assuming a D65 white point).
     */
    public static float[] LABtoXYZ(float L, float a, float b) {
        double y = (L + 16.0) / 116.0;
        double y3 = Math.pow(y, 3.0);
        if (y3 > 0.008856) {
            y = y3;
        } else {
            y = (y - (16.0 / 116.0)) / 7.787;
        }

        double x = (a / 500.0) + y;
        double x3 = Math.pow(x, 3.0);
        if (x3 > 0.008856) {
            x = x3;
        } else {
            x = (x - (16.0 / 116.0)) / 7.787;
        }

        double z = y - (b / 200.0);
        double z3 = Math.pow(z, 3.0);
        if (z3 > 0.008856) {
            z = z3;
        } else {
            z = (z - (16.0 / 116.0)) / 7.787;
        }

        return new float[] {
            (float) (x * WHITE_POINT_D65[0]),
            (float) (y * WHITE_POINT_D65[1]),
            (float) (z * WHITE_POINT_D65[2])
        };
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
    public static float[] linearToSRGB(float[] c) {
      float[] v = new float[c.length];
      for (int i = 0; i < c.length; i++) {
          v[i] = linearToSRGB(c[i]);
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
    public static float linearToSRGB(float c) {
        return (c <= 0.0031308f) ? c * 12.92f : ((float) Math.pow(c, 1.0f / 2.4f) * 1.055f) - 0.055f;
    }

    /**
     * Converts the specified CMYK color to linear RGB. The conversion
     * is done using the "U.S. Web Coated v2" ICC color profile.
     */
    public static float[] CMYKtoRGB(float C, float M, float Y, float K) {
        return getCMYKProfile().toRGB(new float[] { C, M, Y, K });
    }

    /**
     * Returns the ICC CMYK color profile used for CMYK to RGB conversions.
     * The color profile is the standard "U.S. Web Coated v2" profile.
     */
    public static ICC_ColorSpace getCMYKProfile() {
        if (CMYK_ICC_Profile == null) {
            try (InputStream in = Colors.class.getResourceAsStream("USWebCoatedSWOP.icc")) {
                CMYK_ICC_Profile = ICC_Profile.getInstance(in);
                CMYK_ICC_ColorSpace = new ICC_ColorSpace(CMYK_ICC_Profile);
            } catch (IOException e) {
                throw new RuntimeException("Cannot find built-in CMYK ICC color profile");
            }
        }
        return CMYK_ICC_ColorSpace;
    }

    /**
     * Converts an image from the specified source color space to the sRGB space.
     * If the color space is null or if the source color space is sRGB, the image
     * is returned directly.
     *
     * @param image The image to convert
     * @param colorSpace The source color space of the image
     *
     * @return A new image, or the source image
     */
    public static BufferedImage convertToSRGB(BufferedImage image, ColorSpace colorSpace) {
        if (image != null && colorSpace != null && !colorSpace.isCS_sRGB()) {
            RenderingHints hints = new RenderingHints(
                    RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
            ColorConvertOp op = new ColorConvertOp(colorSpace,
                    ColorSpace.getInstance(ColorSpace.CS_sRGB), hints);
            image = op.filter(image, null);
        }
        return image;
    }
}
