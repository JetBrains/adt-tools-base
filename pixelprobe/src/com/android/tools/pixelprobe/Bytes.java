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
 * Various utilities to manipulate bytes.
 */
final class Bytes {
    /**
     * Converts the specified fixed-point integer into a float.
     *
     * @param fixed A 8.24 fixed-point integer
     *
     * @return The float representation of the fixed-point integer
     */
    static float fixed8_24ToFloat(int fixed) {
        return fixed / (float) 0x01000000;
    }

    /**
     * Converts the specified fixed-point integer into a float.
     *
     * @param fixed A 16.16 fixed-point integer
     *
     * @return The float representation of the fixed-point integer
     */
    static float fixed16_16ToFloat(int fixed) {
        return fixed / (float) 0x00010000;
    }

    /**
     * Converts a string containing an hexadecimal sequence ("ffaafe")
     * for instance and converts it into a byte array. Invalid characters
     * are treated as zeroes.
     *
     * @param hex An hex string whose length must be a multiple of 2
     *
     * @return A byte array
     */
    static byte[] fromHexString(String hex) {
        if (hex.length() % 2 != 0) {
            throw new IllegalArgumentException("Unexpected string length: " + hex);
        }

        byte[] data = new byte[hex.length() / 2];
        for (int i = 0; i < data.length; i++) {
            int d1 = hexToDigit(hex.charAt(i * 2)) << 4;
            int d2 = hexToDigit(hex.charAt(i * 2 + 1));
            data[i] = (byte) (d1 + d2);
        }

        return data;
    }

    private static int hexToDigit(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= 'a' && c <= 'f') {
            return c - 'a' + 10;
        }
        if (c >= 'A' && c <= 'F') {
            return c - 'A' + 10;
        }
        return 0;
    }
}
