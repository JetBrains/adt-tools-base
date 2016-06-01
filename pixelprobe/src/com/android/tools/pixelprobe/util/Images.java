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

import com.android.tools.pixelprobe.ColorMode;

import java.awt.*;
import java.awt.color.ColorSpace;
import java.awt.image.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Various utilities to create and decode images.
 */
public final class Images {
    enum Type {
        INT_RGB,
        INT_ALPHA_RGB,
        BYTE_GRAY,
        BYTE_ALPHA_GRAY,
        BYTE_CMYK,
        BYTE_ALPHA_CMYK,
        BYTE_LAB,
        BYTE_ALPHA_LAB,
        FLOAT_RGB,
        FLOAT_ALPHA_RGB,
        FLOAT_GRAY,
        FLOAT_ALPHA_GRAY,
    }

    private static final Map<LutKey, BufferedImageOp> lookupTables = new HashMap<>();
    private static final ReentrantLock lookupTablesLock = new ReentrantLock();

    private Images() {
    }

    /**
     * Creates a new BufferedImage with the specified width and height.
     * The type of the BufferedImage depends on the number of channels.
     *
     * @param width The bitmap's width
     * @param height The bitmap's height
     * @param colorMode The bitmap's source color mode
     * @param channels The number of channels
     * @param colorSpace The bitmap's color space, can be null
     * @param depth The bitmaps' component depth
     *
     * @return A BufferedImage instance
     */
    public static BufferedImage create(int width, int height, ColorMode colorMode,
            int channels, ColorSpace colorSpace, int depth) {

        Type type = getImageType(channels, colorMode, depth);
        ColorModel colorModel;
        WritableRaster raster;

        switch (type) {
            case INT_RGB:
                if (colorSpace == null) colorSpace = ColorSpace.getInstance(ColorSpace.CS_sRGB);
                colorModel = new DirectColorModel(colorSpace, 24,
                        0x00ff0000, 0x0000ff00, 0x000000ff, 0x0, false, getTransferType(24));
                raster = colorModel.createCompatibleWritableRaster(width, height);
                break;
            case INT_ALPHA_RGB:
                if (colorSpace == null) colorSpace = ColorSpace.getInstance(ColorSpace.CS_sRGB);
                colorModel = new DirectColorModel(colorSpace, 32,
                        0x00ff0000, 0x0000ff00, 0x000000ff, 0xff000000, false, getTransferType(32));
                raster = colorModel.createCompatibleWritableRaster(width, height);
                break;
            case BYTE_GRAY:
                if (colorSpace == null) colorSpace = ColorSpace.getInstance(ColorSpace.CS_GRAY);
                colorModel = new ComponentColorModel(colorSpace, new int[] { 8 }, false, false,
                        Transparency.OPAQUE, DataBuffer.TYPE_BYTE);
                raster = colorModel.createCompatibleWritableRaster(width, height);
                break;
            case BYTE_ALPHA_GRAY:
                if (colorSpace == null) colorSpace = ColorSpace.getInstance(ColorSpace.CS_GRAY);
                colorModel = new ComponentColorModel(colorSpace, new int[] { 8, 8 }, true, false,
                        Transparency.TRANSLUCENT, DataBuffer.TYPE_BYTE);
                raster = colorModel.createCompatibleWritableRaster(width, height);
                break;
            case BYTE_CMYK:
                if (colorSpace == null) colorSpace = Colors.getCmykColorSpace();
                colorModel = new ComponentColorModel(colorSpace, new int[] { 8, 8, 8, 8 },
                        false, false, Transparency.OPAQUE, DataBuffer.TYPE_BYTE);
                raster = Raster.createInterleavedRaster(DataBuffer.TYPE_BYTE,
                        width, height, width * 4, 4, new int[] { 0, 1, 2, 3 }, null);
                break;
            case BYTE_ALPHA_CMYK:
                if (colorSpace == null) colorSpace = Colors.getCmykColorSpace();
                colorModel = new ComponentColorModel(colorSpace, new int[] { 8, 8, 8, 8, 8 },
                        true, false, Transparency.TRANSLUCENT, DataBuffer.TYPE_BYTE);
                raster = Raster.createInterleavedRaster(DataBuffer.TYPE_BYTE,
                        width, height, width * 5, 5, new int[] { 1, 2, 3, 4, 0 }, null);
                break;
            case BYTE_LAB:
                if (colorSpace == null || colorSpace.getType() != ColorSpace.TYPE_Lab) {
                    colorSpace = Colors.getLabColorSpace();
                }
                colorModel = new ComponentColorModel(colorSpace, new int[] { 8, 8, 8 },
                        false, false, Transparency.OPAQUE, DataBuffer.TYPE_BYTE);
                raster = Raster.createInterleavedRaster(DataBuffer.TYPE_BYTE,
                        width, height, width * 3, 3, new int[] { 0, 1, 2 }, null);
                break;
            case BYTE_ALPHA_LAB:
                if (colorSpace == null || colorSpace.getType() != ColorSpace.TYPE_Lab) {
                    colorSpace = Colors.getLabColorSpace();
                }
                colorModel = new ComponentColorModel(colorSpace, new int[] { 8, 8, 8, 8 },
                        true, false, Transparency.TRANSLUCENT, DataBuffer.TYPE_BYTE);
                raster = Raster.createInterleavedRaster(DataBuffer.TYPE_BYTE,
                        width, height, width * 4, 4, new int[] { 0, 1, 2, 3 }, null);
                break;
            case FLOAT_RGB:
                if (colorSpace == null) colorSpace = ColorSpace.getInstance(ColorSpace.CS_sRGB);
                colorModel = new ComponentColorModel(colorSpace, new int[] { 32, 32, 32 },
                        false, false, Transparency.OPAQUE, DataBuffer.TYPE_FLOAT);
                raster = colorModel.createCompatibleWritableRaster(width, height);
                break;
            case FLOAT_ALPHA_RGB:
                if (colorSpace == null) colorSpace = ColorSpace.getInstance(ColorSpace.CS_sRGB);
                colorModel = new ComponentColorModel(colorSpace, new int[] { 32, 32, 32, 32 },
                        true, false, Transparency.TRANSLUCENT, DataBuffer.TYPE_FLOAT);
                raster = colorModel.createCompatibleWritableRaster(width, height);
                break;
            case FLOAT_GRAY:
                if (colorSpace == null) colorSpace = ColorSpace.getInstance(ColorSpace.CS_GRAY);
                colorModel = new ComponentColorModel(colorSpace, new int[] { 32 }, false, false,
                        Transparency.OPAQUE, DataBuffer.TYPE_FLOAT);
                raster = colorModel.createCompatibleWritableRaster(width, height);
                break;
            case FLOAT_ALPHA_GRAY:
                if (colorSpace == null) colorSpace = ColorSpace.getInstance(ColorSpace.CS_GRAY);
                colorModel = new ComponentColorModel(colorSpace, new int[] { 32, 32 }, true, false,
                        Transparency.TRANSLUCENT, DataBuffer.TYPE_FLOAT);
                raster = colorModel.createCompatibleWritableRaster(width, height);
                break;
            default:
                throw new RuntimeException("Unknown image type, color mode = " +
                        colorMode + ", channels = " + channels);
        }

        //noinspection UndesirableClassUsage
        return new BufferedImage(colorModel, raster, colorModel.isAlphaPremultiplied(), null);
    }

    private static int getTransferType(int bits) {
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
     * Inverts the specified image (ignores the alpha channel if any).
     */
    public static BufferedImage invert(BufferedImage image) {
        BufferedImageOp imageOp;
        ColorModel colorModel = image.getColorModel();
        LutKey key = new LutKey(colorModel.getNumComponents(), colorModel.hasAlpha());

        lookupTablesLock.lock();
        try {
            imageOp = lookupTables.get(key);
            if (imageOp == null) {
                int numColorComponents = colorModel.getNumColorComponents();
                byte[][] lut = new byte[key.componentCount][256];

                for (int j = 0; j < numColorComponents; j++) {
                    for (int i = 0; i < 256; i++) {
                        lut[j][i] = (byte)(255 - i);
                    }
                }

                if (key.hasAlpha) {
                    int a = key.componentCount - 1;
                    for (int i = 0; i < 256; i++) {
                        lut[a][i] = (byte)i;
                    }
                }

                imageOp = new LookupOp(new ByteLookupTable(0, lut), null);
                lookupTables.put(key, imageOp);
            }
        } finally {
            lookupTablesLock.unlock();
        }

        return imageOp.filter(image, null);
    }

    /**
     * Decodes the supplied byte array as an RLE encoded channel and stores
     * the decoded result in the specified BufferedImage.
     *
     * You must specify the channel's index to indicate where to store
     * the decoded data. The index must be set to one of the following values:
     *
     * <ul>
     * <li>0, red/cyan channel</li>
     * <li>1, green/magenta channel</li>
     * <li>2, blue/yellow channel</li>
     * <li>3, alpha/black channel</li>
     * <li>4, alpha/black channel</li>
     * <li>-1, alpha channel</li>
     * </ul>
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
     * @param colorMode The image's source color mode
     * @param channels Number of channels to decode
     * @param colorSpace The image's source color space
     * @param depth The image's component depth
     *
     * @return A BufferedImage instance
     */
    public static BufferedImage decodeRLE(byte[] data, int offset, int width, int height,
            ColorMode colorMode, int channels, ColorSpace colorSpace, int depth) {

        BufferedImage image = create(width, height, colorMode, channels, colorSpace, depth);
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
     * <ul>
     * <li>0, red/cyan channel</li>
     * <li>1, green/magenta channel</li>
     * <li>2, blue/yellow channel</li>
     * <li>3, alpha/black channel</li>
     * <li>4, alpha/black channel</li>
     * <li>-1, alpha channel</li>
     * </ul>
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
                if (shouldCompress(image.getColorModel().getColorSpace())) {
                    for (int y = 0; y < height; y++) {
                        for (int x = 0; x < width; x++) {
                            int d = (data[pos++] & 0xff) << 8 | (data[pos++] & 0xff);
                            raster.setSample(x, y, band, ((int) (d / 65535.0f * 255.0f) & 0xff));
                        }
                    }
                } else {
                    for (int y = 0; y < height; y++) {
                        for (int x = 0; x < width; x++) {
                            int d = (data[pos++] & 0xff) << 8 | (data[pos++] & 0xff);
                            raster.setSample(x, y, band, d / 65535.0f);
                        }
                    }
                }
                break;
            }
            case 32: {
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        int d = ((data[pos++] & 0xff) << 24) |
                                ((data[pos++] & 0xff) << 16) |
                                ((data[pos++] & 0xff) << 8) |
                                ((data[pos++] & 0xff));
                        // TODO: apply the proper tone mapping curve
                        raster.setSample(x, y, band, Colors.toneMappingACES(Float.intBitsToFloat(d)));
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
     * @param colorMode The image's source color mode
     * @param channels Number of channels to decode
     * @param colorSpace The image's source color space
     * @param depth Bit-depth of each channel, must be 8, 16 or 32
     *
     * @return A BufferedImage instance, never null
     */
    public static BufferedImage decodeRaw(byte[] data, int offset, int width, int height,
            ColorMode colorMode, int channels, ColorSpace colorSpace, int depth) {

        int pos = offset;

        BufferedImage image = create(width, height, colorMode, channels, colorSpace, depth);
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
                if (shouldCompress(image.getColorModel().getColorSpace())) {
                    for (int c = 0; c < channels; c++) {
                        for (int y = 0; y < height; y++) {
                            for (int x = 0; x < width; x++) {
                                int d = (data[pos++] & 0xff) << 8 | (data[pos++] & 0xff);
                                raster.setSample(x, y, c, ((int) (d / 65535.0f * 255.0f) & 0xff));
                            }
                        }
                    }
                } else {
                    for (int c = 0; c < channels; c++) {
                        for (int y = 0; y < height; y++) {
                            for (int x = 0; x < width; x++) {
                                int d = (data[pos++] & 0xff) << 8 | (data[pos++] & 0xff);
                                raster.setSample(x, y, c, d / 65535.0f);
                            }
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
                                    ((data[pos++] & 0xff) << 8) |
                                    ((data[pos++] & 0xff));
                            // TODO: apply the proper tone mapping curve
                            raster.setSample(x, y, c, Colors.toneMappingACES(Float.intBitsToFloat(d)));
                        }
                    }
                }
                break;
            }
        }

        return image;
    }

    private static boolean shouldCompress(ColorSpace colorSpace) {
        // Couldn't get 16 bit CMYK images to work so let's turn them into 8 bit images
        // We also want to compress Lab images to keep the CIELAB ColorSpace implementation
        // working in the regular Lab ranges instead of 0..1
        int type = colorSpace.getType();
        return type == ColorSpace.TYPE_CMYK || type == ColorSpace.TYPE_Lab;
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
            case  0: return 0; // R, C
            case  1: return 1; // G, M
            case  2: return 2; // B, Y
            case  3: return 3; // A, K
            case  4: return 4; // A
        }
        throw new IllegalArgumentException("The channel index must be <= 3, not " + channel);
    }

    /**
     * Returns a BufferedImage type for a given number of channels.
     *
     * @param channels The number of channels, must be <= 5
     * @param colorMode The color mode
     * @param depth The depth the components
     */
    private static Type getImageType(int channels, ColorMode colorMode, int depth) {
        switch (colorMode) {
            case BITMAP:
                break;
            case GRAYSCALE:
                switch (channels) {
                    case 1: return depth > 8 ? Type.FLOAT_GRAY : Type.BYTE_GRAY;
                    case 2: return depth > 8 ? Type.FLOAT_ALPHA_GRAY : Type.BYTE_ALPHA_GRAY;
                }
                throw new IllegalArgumentException("The Grayscale channels count must be 1 or 2");
            case INDEXED:
                break;
            case RGB:
                switch (channels) {
                    case 3: return depth > 8 ? Type.FLOAT_RGB : Type.INT_RGB;
                    case 4: return depth > 8 ? Type.FLOAT_ALPHA_RGB : Type.INT_ALPHA_RGB;
                }
                throw new IllegalArgumentException("The RGB channels count must be 3 or 4");
            case CMYK:
                switch (channels) {
                    case 4: return Type.BYTE_CMYK;
                    case 5: return Type.BYTE_ALPHA_CMYK;
                }
                throw new IllegalArgumentException("The CMYK channels count must be 4 or 5");
            case UNKNOWN:
            case NONE:
            case MULTI_CHANNEL:
                break;
            case DUOTONE:
                break;
            case LAB:
                switch (channels) {
                    case 3: return Type.BYTE_LAB;
                    case 4: return Type.BYTE_ALPHA_LAB;
                }
                throw new IllegalArgumentException("The LAB channels count must be 3 or 4");
        }

        throw new IllegalArgumentException("Unsupported color mode/channels count: " + colorMode);
    }

    private static final class LutKey {
        int componentCount;
        boolean hasAlpha;

        LutKey(int componentCount, boolean hasAlpha) {
            this.componentCount = componentCount;
            this.hasAlpha = hasAlpha;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            LutKey lutKey = (LutKey) o;

            if (componentCount != lutKey.componentCount) return false;
            if (hasAlpha != lutKey.hasAlpha) return false;

            return true;
        }

        @Override
        public int hashCode() {
            int result = componentCount;
            result = 31 * result + (hasAlpha ? 1 : 0);
            return result;
        }
    }
}
