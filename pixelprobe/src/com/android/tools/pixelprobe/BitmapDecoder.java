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

import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;

/**
 * Internal API used to decode bitmaps from data sources encoded
 * in various ways. The API currently supports decoding bitmaps
 * in RAW and RLE formats.
 *
 * This class assumes an 8-bits depth.
 */
final class BitmapDecoder {
    private BitmapDecoder() {
    }

    /**
     * Creates a new BufferedImage with the specified width and height.
     * The type of the BufferedImage depends on the number of channels.
     *
     * @param width The bitmap's width
     * @param height The bitmap's height
     * @param channels The number of channels, must be 3 or 4
     *
     * @return A BufferedImage instance
     */
    static BufferedImage createBitmap(int width, int height, int channels) {
        //noinspection UndesirableClassUsage
        return new BufferedImage(width, height, getImageType(channels));
    }

    /**
     * Decodes the supplied byte array as an RLE encoded channel and stores
     * the decoded result in the specified BufferedImage.
     *
     * You must specify the channel's index to indicate where to store
     * the decoded data. The index must be set to one of the following values:
     *
     *      0, red channel
     *      1, green channel
     *      2, blue channel
     *      3, alpha channel
     *     -1, alpha channel
     *
     * @param data The source data
     * @param offset Start offset of the data to decode in the byte array
     * @param channel The channel index
     * @param image The destination image
     */
    static void decodeChannelRLE(byte[] data, int offset, int channel, BufferedImage image) {
        WritableRaster raster = image.getRaster();

        int width = image.getWidth();
        int height = image.getHeight();

        int band = getBand(channel);

        int pos = offset;
        for (int y = 0; y < height; y++) {
            int x = 0;
            while (x < width) {
                byte packetInfo = data[pos++];
                if (packetInfo < 0) {
                    // When packet info is negative, the following byte
                    // must be repeated (-packetInfo + 1) times
                    int runCount = -packetInfo + 1;
                    int value = data[pos++] & 0xff;
                    for (int i = 0; i < runCount; i++, x++) {
                        raster.setSample(x, y, band, value);
                    }
                } else {
                    // The packet info is positive, we need to read
                    // the next packetInfo+1 bytes individually
                    int runCount = packetInfo + 1;
                    for (int i = 0; i < runCount; i++, x++) {
                        raster.setSample(x, y, band, data[pos++] & 0xff);
                    }
                }
            }
        }
    }

    /**
     * Decodes the supplied byte array as a planar, RLE encoded bitmap.
     *
     * @param data The source data
     * @param offset Start offset of the data to decode in the byte array
     * @param width Width of the image to decode
     * @param height Height of the image to decode
     * @param channels Number of channels to decode, must be 3 or 4
     *
     * @return A BufferedImage instance
     */
    static BufferedImage decodeRLE(byte[] data, int offset, int width, int height, int channels) {
        int[] pixels = new int[width * height];

        int pos = offset;
        for (int c = 0; c < channels; c++) {
            int bitShift = getBitShift(c);
            for (int y = 0; y < height; y++) {
                int x = 0;
                while (x < width) {
                    byte packetInfo = data[pos++];
                    if (packetInfo < 0) {
                        int runCount = -packetInfo + 1;
                        int value = data[pos++] & 0xff;
                        for (int i = 0; i < runCount; i++, x++) {
                            pixels[y * width + x] |= value << bitShift;
                        }
                    } else {
                        int runCount = packetInfo + 1;
                        for (int i = 0; i < runCount; i++, x++) {
                            pixels[y * width + x] |= (data[pos++] & 0xff) << bitShift;
                        }
                    }
                }
            }
        }

        BufferedImage image = createBitmap(width, height, channels);
        image.setRGB(0, 0, width, height, pixels, 0, width);
        return image;
    }

    /**
     * Decodes the supplied byte array as a RAW channel and stores the decoded result
     * in the specified BufferedImage.
     *
     * You must specify the channel's index to indicate where to store
     * the decoded data. The index must be set to one of the following values:
     *
     *      0, red channel
     *      1, green channel
     *      2, blue channel
     *      3, alpha channel
     *     -1, alpha channel
     * @param data The source data
     * @param offset Start offset of the data to decode in the byte array
     * @param channel The channel index
     * @param image The destination image
     * @param depth The number of bits per channel, must be 8, 16 or 32
     */
    static void decodeChannelRaw(byte[] data, int offset, int channel, BufferedImage image, int depth) {
        WritableRaster raster = image.getRaster();

        int width = image.getWidth();
        int height = image.getHeight();

        int band = getBand(channel);
        int pos = offset;

        switch (depth) {
            case 8: {
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        raster.setSample(x, y, band, data[pos++] & 0xff);
                    }
                }
                break;
            }
            case 16: {
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        int d = (data[pos++] & 0xff) << 8 | (data[pos++] & 0xff);
                        raster.setSample(x, y, band, (int) (d / 65535.0f * 255.0f) & 0xff);
                    }
                }
                break;
            }
            case 32: {
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        int d =  ((data[pos++] & 0xff) << 24) |
                                 ((data[pos++] & 0xff) << 16) |
                                 ((data[pos++] & 0xff) <<  8) |
                                 ((data[pos++] & 0xff)      );
                        raster.setSample(x, y, band, (int) (Float.intBitsToFloat(d) * 255.0f) & 0xff);
                    }
                }
                break;
            }
        }
    }

    /**
     * Decodes the supplied byte array as a planar, RAW encoded bitmap.
     *
     * @param data The source data
     * @param offset Start offset of the data to decode in the byte array
     * @param width Width of the image to decode
     * @param height Height of the image to decode
     * @param channels Number of channels to decode, must be 3 or 4
     * @param depth Bit-depth of each channel, must be 8, 16 or 32
     *
     * @return A BufferedImage instance, never null
     */
    static BufferedImage decodeRaw(byte[] data, int offset, int width, int height, int channels, int depth) {
        int pos = offset;
        int[] pixels = new int[width * height];

        switch (depth) {
            case 8: {
                for (int c = 0; c < channels; c++) {
                    int bitShift = getBitShift(c);
                    for (int y = 0; y < height; y++) {
                        for (int x = 0; x < width; x++) {
                            pixels[y * width + x] |= (data[pos++] & 0xff) << bitShift;
                        }
                    }
                }
                break;
            }
            case 16: {
                for (int c = 0; c < channels; c++) {
                    int bitShift = getBitShift(c);
                    for (int y = 0; y < height; y++) {
                        for (int x = 0; x < width; x++) {
                            int d = (data[pos++] & 0xff) << 8 | (data[pos++] & 0xff);
                            pixels[y * width + x] |= ((int) (d / 65535.0f * 255.0f) & 0xff) << bitShift;
                        }
                    }
                }
                break;
            }
            case 32: {
                for (int c = 0; c < channels; c++) {
                    int bitShift = getBitShift(c);
                    for (int y = 0; y < height; y++) {
                        for (int x = 0; x < width; x++) {
                            int d =  ((data[pos++] & 0xff) << 24) |
                                     ((data[pos++] & 0xff) << 16) |
                                     ((data[pos++] & 0xff) <<  8) |
                                     ((data[pos++] & 0xff)      );
                            pixels[y * width + x] |=
                                    ((int) (Float.intBitsToFloat(d) * 255.0f) & 0xff) << bitShift;
                        }
                    }
                }
                break;
            }
        }

        BufferedImage image = createBitmap(width, height, channels);
        image.setRGB(0, 0, width, height, pixels, 0, width);
        return image;
    }

    /**
     * Returns the number of bits to shift a channel value by to store it in
     * a packed ARGB pixel.
     *
     * For instance, getBitShift(0) will return 16 (the red channel must be
     * shifted by 16 bits to be stored in a packed ARGB pixel).
     *
     * @param channel The channel index, must be -1, 0, 1, 2 or 3
     */
    private static int getBitShift(int channel) {
        // RGBA to ARGB
        switch (channel) {
            case -1: return 24; // A
            case  0: return 16; // R
            case  1: return 8;  // G
            case  2: return 0;  // B
            case  3: return 24; // A
        }
        throw new IllegalArgumentException("The channel index must be <= 3");
    }

    /**
     * Returns the BufferedImage band that corresponds to a given channel
     * index.
     *
     * @param channel The channel index, must be -1, 0, 1, 2 or 3
     */
    private static int getBand(int channel) {
        switch (channel) {
            case -1: return 3; // A
            case  0: return 0; // R
            case  1: return 1; // G
            case  2: return 2; // B
            case  3: return 3; // A
        }
        throw new IllegalArgumentException("The channel index must be <= 3");
    }

    /**
     * Returns a BufferedImage type for a given number of channels.
     *
     * @param channels The number of channels, must be 3 or 4
     */
    private static int getImageType(int channels) {
        switch (channels) {
            case 3:
                return BufferedImage.TYPE_INT_RGB;
            case 4:
            case 5:
                return BufferedImage.TYPE_INT_ARGB;
        }
        throw new IllegalArgumentException("The channels count must be 3, 4 or 5");
    }
}
