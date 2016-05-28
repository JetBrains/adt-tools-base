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

package com.android.tools.pixelprobe;

import java.awt.color.ColorSpace;
import java.awt.color.ICC_ColorSpace;
import java.awt.color.ICC_Profile;
import java.io.IOException;
import java.io.InputStream;

class ColorUtilities {
    private static final float[] WHITE_POINT_D65 = { 95.0429f, 100.0f, 108.89f };

    private static ICC_Profile CMYK_ICC_Profile;
    private static ICC_ColorSpace CMYK_ICC_ColorSpace;

    private ColorUtilities() {
    }

    static float[] LABtoRGB(float L, float a, float b) {
        ColorSpace xyz = ColorSpace.getInstance(ColorSpace.CS_CIEXYZ);
        return xyz.toRGB(LABtoXYZ(L, a, b));
    }

    static float[] LABtoXYZ(float L, float a, float b) {
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

    static float[] linearToSRGB(float[] x) {
      float[] v = new float[x.length];
      for (int i = 0; i < x.length; i++) {
          v[i] = linearToSRGB(x[i]);
      }
      return v;
    }

    static float linearToSRGB(float x) {
        return (x <= 0.0031308f) ? x * 12.92f : ((float) Math.pow(x, 1.0f / 2.4f) * 1.055f) - 0.055f;
    }

    static float[] CMYKtoRGB(float C, float M, float Y, float K) {
        if (CMYK_ICC_Profile == null) {
            try (InputStream in = PsdDecoder.class.getResourceAsStream("USWebCoatedSWOP.icc")) {
                CMYK_ICC_Profile = ICC_Profile.getInstance(in);
                CMYK_ICC_ColorSpace = new ICC_ColorSpace(CMYK_ICC_Profile);
            } catch (IOException e) {
                throw new RuntimeException("Cannot find built-in CMYK ICC color profile");
            }
        }
        return CMYK_ICC_ColorSpace.toRGB(new float[] { C, M, Y, K });
    }
}
