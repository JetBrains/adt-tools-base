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
import java.awt.image.*;

/**
 * Various utilities to create and decode images.
 */
public final class Images {
    private static final int TYPE_BYTE_ALPHA_GRAY = 42;

    private Images() {
    }

    /**
     * Creates a new BufferedImage with the specified width and height.
     * The type of the BufferedImage depends on the number of channels.
     *
     * @param width The bitmap's width
     * @param height The bitmap's height
     * @param channels The number of channels, must be 3 or 4
     * @param colorSpace The bitmap's color space, can be null
     *
     * @return A BufferedImage instance
     */
    public static BufferedImage create(int width, int height, int channels, ColorSpace colorSpace) {
        int type = getImageType(channels);
        ColorModel colorModel;
        WritableRaster raster;

        switch (type) {
            case BufferedImage.TYPE_BYTE_GRAY:
                if (colorSpace == null) colorSpace = ColorSpace.getInstance(ColorSpace.CS_GRAY);
                colorModel = new ComponentColorModel(colorSpace, new int[] { 8 }, false, false,
                        Transparency.OPAQUE, DataBuffer.TYPE_BYTE);
                raster = colorModel.createCompatibleWritableRaster(width, height);
                break;
            case TYPE_BYTE_ALPHA_GRAY:
                if (colorSpace == null) colorSpace = ColorSpace.getInstance(ColorSpace.CS_GRAY);
                colorModel = new ComponentColorModel(colorSpace, new int[] { 8, 8 }, true, false,
                        Transparency.TRANSLUCENT, DataBuffer.TYPE_BYTE);
                raster = colorModel.createCompatibleWritableRaster(width, height);
                break;
            case BufferedImage.TYPE_INT_RGB:
                if (colorSpace == null) colorSpace = ColorSpace.getInstance(ColorSpace.CS_sRGB);
                colorModel = new DirectColorModel(colorSpace, 24,
                        0x00ff0000, 0x0000ff00, 0x000000ff, 0x0, false, getDefaultTransferType(24));
                raster = colorModel.createCompatibleWritableRaster(width, height);
                break;
            case BufferedImage.TYPE_INT_ARGB:
                if (colorSpace == null) colorSpace = ColorSpace.getInstance(ColorSpace.CS_sRGB);
                colorModel = new DirectColorModel(colorSpace, 32,
                        0x00ff0000, 0x0000ff00, 0x000000ff, 0xff000000, false, getDefaultTransferType(32));
                raster = colorModel.createCompatibleWritableRaster(width, height);
                break;
            default:
                throw new RuntimeException("Unknown image type " + type);
        }

        //noinspection UndesirableClassUsage
        return new BufferedImage(colorModel, raster, false, null);
    }

    private static int getDefaultTransferType(int bits) {
        if (bits <= 8) {
            return DataBuffer.TYPE_BYTE;
        } else if (bits <= 16) {
            return DataBuffer.TYPE_USHORT;
        } else if (bits <= 32) {
            return DataBuffer.TYPE_INT;
        } else {
            return DataBuffer.TYPE_UNDEFINED;
        }
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
    public static void decodeChannelRLE(byte[] data, int offset, int channel, BufferedImage image) {
        WritableRaster raster = image.getRaster();

        int width = image.getWidth();
        int height = image.getHeight();

        int band = getBand(channel, image.getColorModel());

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
     * @param colorSpace
     * @return A BufferedImage instance
     */
    public static BufferedImage decodeRLE(byte[] data, int offset, int width, int height,
            int channels, ColorSpace colorSpace) {

        BufferedImage image = create(width, height, channels, colorSpace);
        WritableRaster raster = image.getRaster();

        int pos = offset;
        for (int c = 0; c < channels; c++) {
            for (int y = 0; y < height; y++) {
                int x = 0;
                while (x < width) {
                    byte packetInfo = data[pos++];
                    if (packetInfo < 0) {
                        int runCount = -packetInfo + 1;
                        int value = data[pos++] & 0xff;
                        for (int i = 0; i < runCount; i++, x++) {
                            raster.setSample(x, y, c, value);
                        }
                    } else {
                        int runCount = packetInfo + 1;
                        for (int i = 0; i < runCount; i++, x++) {
                            raster.setSample(x, y, c, data[pos++] & 0xff);
                        }
                    }
                }
            }
        }

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
     *
     * @param data The source data
     * @param offset Start offset of the data to decode in the byte array
     * @param channel The channel index
     * @param image The destination image
     * @param depth The number of bits per channel, must be 8, 16 or 32
     */
    public static void decodeChannelRaw(byte[] data, int offset, int channel,
            BufferedImage image, int depth) {

        WritableRaster raster = image.getRaster();

        int width = image.getWidth();
        int height = image.getHeight();

        int band = getBand(channel, image.getColorModel());
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
                        int d = ((data[pos++] & 0xff) << 24) |
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
     * @param colorSpace
     * @return A BufferedImage instance, never null
     */
    public static BufferedImage decodeRaw(byte[] data, int offset, int width, int height,
            int channels, int depth, ColorSpace colorSpace) {

        int pos = offset;

        BufferedImage image = create(width, height, channels, colorSpace);
        WritableRaster raster = image.getRaster();

        switch (depth) {
            case 8: {
                for (int c = 0; c < channels; c++) {
                    for (int y = 0; y < height; y++) {
                        for (int x = 0; x < width; x++) {
                            raster.setSample(x, y, c, data[pos++] & 0xff);
                        }
                    }
                }
                break;
            }
            case 16: {
                for (int c = 0; c < channels; c++) {
                    for (int y = 0; y < height; y++) {
                        for (int x = 0; x < width; x++) {
                            int d = (data[pos++] & 0xff) << 8 | (data[pos++] & 0xff);
                            raster.setSample(x, y, c, ((int) (d / 65535.0f * 255.0f) & 0xff));
                        }
                    }
                }
                break;
            }
            case 32: {
                for (int c = 0; c < channels; c++) {
                    for (int y = 0; y < height; y++) {
                        for (int x = 0; x < width; x++) {
                            int d = ((data[pos++] & 0xff) << 24) |
                                    ((data[pos++] & 0xff) << 16) |
                                    ((data[pos++] & 0xff) <<  8) |
                                    ((data[pos++] & 0xff)      );
                            raster.setSample(x, y, c, (int) (Float.intBitsToFloat(d) * 255.0f) & 0xff);
                        }
                    }
                }
                break;
            }
        }

        return image;
    }

    /**
     * Returns the BufferedImage band that corresponds to a given channel index.
     * @param channel The channel index, must be -1, 0, 1, 2 or 3
     * @param colorModel The color model the channel belongs to
     */
    private static int getBand(int channel, ColorModel colorModel) {
        switch (channel) {
            // handle the special alpha case
            case -1: return colorModel.getNumComponents() - 1;
            case  0: return 0; // R
            case  1: return 1; // G
            case  2: return 2; // B
            case  3: return 3; // A
        }
        throw new IllegalArgumentException("The channel index must be <= 3, not " + channel);
    }

    /**
     * Returns a BufferedImage type for a given number of channels.
     *
     * @param channels The number of channels, must be <= 5
     */
    private static int getImageType(int channels) {
        switch (channels) {
            case 1:
                return BufferedImage.TYPE_BYTE_GRAY;
            case 2:
                return TYPE_BYTE_ALPHA_GRAY;
            case 3:
                return BufferedImage.TYPE_INT_RGB;
            case 4:
            case 5:
                return BufferedImage.TYPE_INT_ARGB;
        }
        throw new IllegalArgumentException("The channels count must be <= 5");
    }

}
