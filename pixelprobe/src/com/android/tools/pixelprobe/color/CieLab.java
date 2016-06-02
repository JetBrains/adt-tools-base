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
 * Implementation of the CIELAB D50 color space.
 */
public class CieLab extends ColorSpace {
    private static final double[] WHITE_POINT_D50 = { 96.4212, 100.0, 82.5188 };
    private static final ColorSpace sRGB = ColorSpace.getInstance(ColorSpace.CS_sRGB);

    private static final class InstanceHolder {
        static final CieLab CIELab = new CieLab();
    }

    private CieLab() {
        super(ColorSpace.TYPE_Lab, 3);
    }

    public static CieLab getInstance() {
        return InstanceHolder.CIELab;
    }

    @Override
    public float getMinValue(int component) {
        return component == 0 ? 0.0f : -128.0f;
    }

    @Override
    public float getMaxValue(int component) {
        return component == 0 ? 100.0f : 127.0f;
    }

    @Override
    public float[] toRGB(float[] lab) {
        float[] xyz = toCIEXYZ(lab);

        // Bradford-adapted D50 XYZ to sRGB matrix
        double r =  3.1338561 * xyz[0] + -1.6168667 * xyz[1] + -0.4906146 * xyz[2];
        double g = -0.9787684 * xyz[0] +  1.9161415 * xyz[1] +  0.0334540 * xyz[2];
        double b =  0.0719453 * xyz[0] + -0.2289914 * xyz[1] +  1.4052427 * xyz[2];

        xyz[0] = clamp(Colors.linearTosRGB((float) r));
        xyz[1] = clamp(Colors.linearTosRGB((float) g));
        xyz[2] = clamp(Colors.linearTosRGB((float) b));

        return xyz;
    }

    private static float clamp(float v) {
        return Math.max(0.0f, Math.min(v, 1.0f));
    }

    @Override
    public float[] fromRGB(float[] rgb) {
        return fromCIEXYZ(sRGB.toCIEXYZ(rgb));
    }

    @Override
    public float[] toCIEXYZ(float[] lab) {
        double fy = (lab[0] + 16.0) / 116.0;
        double fx = fy + (lab[1] / 500.0);
        double fz = fy - (lab[2] / 200.0);

        double X = fx > (6.0 / 29.0) ? fx * fx * fx : (108.0 / 841.0) * (fx - (4.0 / 29.0));
        double Y = fy > (6.0 / 29.0) ? fy * fy * fy : (108.0 / 841.0) * (fy - (4.0 / 29.0));
        double Z = fz > (6.0 / 29.0) ? fz * fz * fz : (108.0 / 841.0) * (fz - (4.0 / 29.0));

        return new float[] {
            (float) (X * WHITE_POINT_D50[0] * 0.01),
            (float) (Y * WHITE_POINT_D50[1] * 0.01),
            (float) (Z * WHITE_POINT_D50[2] * 0.01)
        };
    }

    @Override
    public float[] fromCIEXYZ(float[] xyz) {
        double X = xyz[0] * (100.0 / WHITE_POINT_D50[0]);
        double Y = xyz[1] * (100.0 / WHITE_POINT_D50[1]);
        double Z = xyz[2] * (100.0 / WHITE_POINT_D50[2]);

        double fx = X > (216.0 / 24389.0) ? Math.cbrt(X) : (841.0 / 108.0) * X + (4.0 / 29.0);
        double fy = Y > (216.0 / 24389.0) ? Math.cbrt(Y) : (841.0 / 108.0) * Y + (4.0 / 29.0);
        double fz = Z > (216.0 / 24389.0) ? Math.cbrt(Z) : (841.0 / 108.0) * Z + (4.0 / 29.0);

        double L = 116.0 * fy - 16.0;
        double a = 500.0 * (fx - fy);
        double b = 200.0 * (fy - fz);

        return new float[] { (float) L, (float) a, (float) b };
    }
}
