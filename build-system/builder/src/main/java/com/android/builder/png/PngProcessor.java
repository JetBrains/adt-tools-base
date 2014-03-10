/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.builder.png;

import static com.google.common.base.Preconditions.checkNotNull;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.google.common.collect.Lists;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.Deflater;

import javax.imageio.ImageIO;

/**
 * a PngProcessor.
 *
 * It reads a png file and write another png file that's been optimized
 * and processed in case of a 9-patch.
 */
public class PngProcessor {

    private static final int COLOR_WHITE              = 0xFFFFFFFF;
    private static final int COLOR_TICK               = 0xFF000000;
    private static final int COLOR_LAYOUT_BOUNDS_TICK = 0xFFFF0000;

    private static final int PNG_9PATCH_NO_COLOR          = 0x00000001;
    private static final int PNG_9PATCH_TRANSPARENT_COLOR = 0x00000000;


    @NonNull
    private final File mFile;

    private Chunk mIhdr;
    private Chunk mIdat;
    private List<Chunk> mOtherChunks = Lists.newArrayList();

    /**
     * Processes a given png and writes the resulting png file.
     * @param from the input file
     * @param to the destination file
     * @throws IOException
     * @throws NinePatchException
     */
    public static void process(@NonNull File from, @NonNull File to)
            throws IOException, NinePatchException {
        PngProcessor processor = new PngProcessor(from);
        processor.read();

        PngWriter writer = new PngWriter(to);
        writer.setIhdr(processor.getIhdr())
                .setChunks(processor.getOtherChunks())
                .setChunk(processor.getIdat());

        writer.write();
    }

    public static void clearCache() {
        ByteUtils.Cache.getCache().clear();
    }

    @VisibleForTesting
    PngProcessor(@NonNull File file) {
        checkNotNull(file);

        mFile = file;
    }

    @VisibleForTesting
    @NonNull
    Chunk getIhdr() {
        return mIhdr;
    }

    @VisibleForTesting
    @NonNull
    List<Chunk> getOtherChunks() {
        return mOtherChunks;
    }

    @VisibleForTesting
    @NonNull
    Chunk getIdat() {
        return mIdat;
    }

    @VisibleForTesting
    void read() throws IOException, NinePatchException {
        BufferedImage image = ImageIO.read(mFile);

        processImageContent(image);
    }

    private void addChunk(@NonNull Chunk chunk) {
        mOtherChunks.add(chunk);
    }

    private void processImageContent(@NonNull BufferedImage image) throws NinePatchException,
            IOException {
        int width = image.getWidth();
        int height = image.getHeight();

        int[] content = new int[width * height];

        image.getRGB(0, 0, width, height, content, 0, width);

        int startX = 0;
        int startY = 0;
        int endX = width;
        int endY = height;

        if (is9Patch()) {
            startX = 1;
            startY = 1;
            endX--;
            endY--;

            processBorder(content, width, height);
        }

        ColorType colorType = createImage(content, width, startX, endX, startY, endY, is9Patch());

        mIhdr = computeIhdr(endX - startX, endY - startY, (byte) 8, colorType);
    }

    private void writeIDat(byte[] data) throws IOException {
        // create a growing buffer for the result.
        ByteArrayOutputStream bos = new ByteArrayOutputStream(data.length);

        Deflater deflater = new Deflater(Deflater.DEFLATED);
        deflater.setInput(data);
        deflater.finish();

        // temp buffer for compressed data.
        byte[] tmpBuffer = new byte[1024];
        while (!deflater.finished()) {
            int compressedLen = deflater.deflate(tmpBuffer);
            bos.write(tmpBuffer, 0, compressedLen);
        }

        bos.close();

        byte[] compressedData = bos.toByteArray();

        mIdat = new Chunk(PngWriter.IDAT, compressedData);
    }

    /**
     * Creates the PNG image buffer to be encoded in IDAT.
     *
     * This figures out the smallest way to encode it, and creates the IDAT chunk (and other needed
     * chunks like PLTE).
     *
     * @param content the image array in ARGB.
     * @param scanline the scanline value for the image buffer
     * @param startX the startX of the image we want to create from the buffer
     * @param endX the endX of the image we want to create from the buffer
     * @param startY the startY of the image we want to create from the buffer
     * @param endY the endY of the image we want to create from the buffer
     * @param is9Patch whether the image is a nine-patch
     */
    private ColorType createImage(@NonNull int[] content, int scanline,
            int startX, int endX, int startY, int endY, boolean is9Patch) throws IOException {
        int[] paletteColors = new int[256];
        int paletteColorCount = 0;

        int grayscaleTolerance = -1;
        int maxGrayDeviation = 0;

        boolean isOpaque = true;
        boolean isPalette = true;
        boolean isGrayscale = true;

        int width = endX - startX;
        int height = endY - startY;

        // RGBA buffer, in case it's the best one.
        int rgbaLen = (1 + width * 4) * height;
        ByteBuffer rgbaBufer = ByteBuffer.allocate(rgbaLen);

        // Store palette data optimistically in case we use palette mode.
        // Better than regoing through content after.
        int indexedLen = (1 + width) * height;
        byte[] indexedContent = new byte[indexedLen];
        int indexedContentIndex = 0;

        // Scan the entire image and determine if:
        // 1. Every pixel has R == G == B (grayscale)
        // 2. Every pixel has A == 255 (opaque)
        // 3. There are no more than 256 distinct RGBA colors
        for (int y = startY ; y < endY ; y++) {
            rgbaBufer.put((byte) 0);
            indexedContent[indexedContentIndex++] = 0;

            for (int x = startX ; x < endX ; x++) {
                int argb = content[(scanline * y) + x];

                int aa =  argb >>> 24;
                int rr = (argb >>  16) & 0x000000FF;
                int gg = (argb >>   8) & 0x000000FF;
                int bb =  argb & 0x000000FF;

                int odev = maxGrayDeviation;
                maxGrayDeviation = Math.max(Math.abs(rr - gg), maxGrayDeviation);
                maxGrayDeviation = Math.max(Math.abs(gg - bb), maxGrayDeviation);
                maxGrayDeviation = Math.max(Math.abs(bb - rr), maxGrayDeviation);

                // Check if image is really grayscale
                if (isGrayscale && (rr != gg || rr != bb)) {
                    isGrayscale = false;
                }

                // Check if image is really opaque
                if (isOpaque && aa != 0xff) {
                    isOpaque = false;
                }

                // Check if image is really <= 256 colors
                if (isPalette) {
                    int rgba = (argb << 8) | aa;

                    boolean match = false;
                    int idx;
                    for (idx = 0; idx < paletteColorCount; idx++) {
                        if (paletteColors[idx] == rgba) {
                            match = true;
                            break;
                        }
                    }

                    // Write the palette index for the pixel to outRows optimistically
                    // We might overwrite it later if we decide to encode as gray or
                    // gray + alpha
                    indexedContent[indexedContentIndex++] = (byte)idx;
                    if (!match) {
                        if (paletteColorCount == 256) {
                            isPalette = false;
                        } else {
                            paletteColors[paletteColorCount++] = rgba;
                        }
                    }
                }

                // write rgba optimistically
                rgbaBufer.putInt((argb << 8) | aa);
            }
        }

        boolean hasTransparency = !isOpaque;

        int bpp = isOpaque ? 3 : 4;
        int paletteSize = width * height + (isOpaque ? 3 : 4) * paletteColorCount;

        ColorType colorType;

        // Choose the best color type for the image.
        // 1. Opaque gray - use COLOR_TYPE_GRAY at 1 byte/pixel
        // 2. Gray + alpha - use COLOR_TYPE_PALETTE if the number of distinct combinations
        //     is sufficiently small, otherwise use COLOR_TYPE_GRAY_ALPHA
        // 3. RGB(A) - use COLOR_TYPE_PALETTE if the number of distinct colors is sufficiently
        //     small, otherwise use COLOR_TYPE_RGB{_ALPHA}
        if (isGrayscale) {
            if (isOpaque) {
                colorType = ColorType.GRAY_SCALE; // 1 byte/pixel
            } else {
                // Use a simple heuristic to determine whether using a palette will
                // save space versus using gray + alpha for each pixel.
                // This doesn't take into account chunk overhead, filtering, LZ
                // compression, etc.
                if (isPalette && paletteSize < 2 * width * height) {
                    colorType = ColorType.PLTE; // 1 byte/pixel + 4 bytes/color
                } else {
                    colorType = ColorType.GRAY_SCALE_ALPHA; // 2 bytes per pixel
                }
            }
        } else if (isPalette && paletteSize < bpp * width * height) {
            colorType = ColorType.PLTE; // 1 byte/pixel + 4 bytes/color
        } else {
            if (maxGrayDeviation <= grayscaleTolerance) {
                colorType = isOpaque ? ColorType.GRAY_SCALE : ColorType.GRAY_SCALE_ALPHA;
            } else {
                colorType = isOpaque ? ColorType.RGB : ColorType.RGBA;
            }
        }

        // If the image is a 9-patch, we need to preserve it as a ARGB file to make
        // sure the pixels will not be pre-dithered/clamped until we decide they are
        if (is9Patch && (colorType == ColorType.RGB ||
                colorType == ColorType.GRAY_SCALE || colorType == ColorType.PLTE)) {
            colorType = ColorType.RGBA;
        }

        // Perform postprocessing of the image or palette data based on the final
        // color type chosen
        if (colorType == ColorType.PLTE) {
            byte[] rgbPalette = new byte[paletteColorCount * 3];
            byte[] alphaPalette = null;
            if (hasTransparency) {
                alphaPalette = new byte[paletteColorCount];
            }

            // Create the RGB and alpha palettes
            for (int idx = 0; idx < paletteColorCount; idx++) {
                int color = paletteColors[idx];
                rgbPalette[idx * 3]     = (byte) ( color >>> 24);
                rgbPalette[idx * 3 + 1] = (byte) ((color >>  16) & 0xFF);
                rgbPalette[idx * 3 + 2] = (byte) ((color >>   8) & 0xFF);
                if (hasTransparency) {
                    alphaPalette[idx]   = (byte)  (color         & 0xFF);
                }
            }

            // create chunks.
            addChunk(new Chunk(PngWriter.PLTE, rgbPalette));

            if (hasTransparency) {
                addChunk(new Chunk(PngWriter.TRNS, alphaPalette));
            }

            // create image data chunk
            writeIDat(indexedContent);

        } else if (colorType == ColorType.GRAY_SCALE || colorType == ColorType.GRAY_SCALE_ALPHA) {
            int grayLen = (1 + width * (1 + (hasTransparency ? 1 : 0))) * height;
            byte[] grayContent = new byte[grayLen];
            int grayContentIndex = 0;

            for (int y = startY ; y < endY ; y++) {
                grayContent[grayContentIndex++] = 0;

                for (int x = startX ; x < endX ; x++) {
                    int argb = content[(scanline * y) + x];

                    int rr = (argb >> 16) & 0x000000FF;

                    if (isGrayscale) {
                        grayContent[grayContentIndex++] = (byte) rr;
                    } else {
                        int gg = (argb >>  8) & 0x000000FF;
                        int bb =  argb & 0x000000FF;

                        // convert RGB to Grayscale.
                        // Ref: http://en.wikipedia.org/wiki/Luma_(video)
                        grayContent[grayContentIndex++] = (byte) (rr * 0.2126f + gg * 0.7152f + bb * 0.0722f);
                    }

                    if (hasTransparency) {
                        int aa = argb >>> 24;
                        grayContent[grayContentIndex++] = (byte) aa;
                    }
                }
            }

            // create image data chunk
            writeIDat(grayContent);

        } else if (colorType == ColorType.RGBA) {
            writeIDat(rgbaBufer.array());
        } else {
            //RGB mode
            int rgbLen = (1 + width * 3) * height;
            byte[] rgbContent = new byte[rgbLen];
            int rgbContentIndex = 0;

            for (int y = startY ; y < endY ; y++) {
                rgbContent[rgbContentIndex++] = 0;

                for (int x = startX ; x < endX ; x++) {
                    int argb = content[(scanline * y) + x];

                    rgbContent[rgbContentIndex++] = (byte) ((argb >> 16) & 0x000000FF);
                    rgbContent[rgbContentIndex++] = (byte) ((argb >>  8) & 0x000000FF);
                    rgbContent[rgbContentIndex++] = (byte) ( argb        & 0x000000FF);
                }
            }

            // create image data chunk
            writeIDat(rgbContent);
        }

        return colorType;
    }

    /**
     * process the border of the image to find 9-patch info
     * @param content the content of ARGB
     * @param width the width
     * @param height the height
     * @throws NinePatchException
     */
    private void processBorder(int[] content, int width, int height)
            throws NinePatchException {
        // Validate size...
        if (width < 3 || height < 3) {
            throw new NinePatchException(mFile, "Image must be at least 3x3 (1x1 without frame) pixels");
        }

        int i, j;

        int[] xDivs = new int[width];
        int[] yDivs = new int[height];
        int[] colors;
        Arrays.fill(xDivs, -1);
        Arrays.fill(yDivs, -1);

        int numXDivs;
        int numYDivs;
        byte numColors;
        int numRows, numCols;
        int top, left, right, bottom;

        int paddingLeft, paddingTop, paddingRight, paddingBottom;

        boolean transparent = (content[0] & 0xFF000000) == 0;

        int colorIndex = 0;

        // Validate frame...
        if (!transparent && content[0] != 0xFFFFFFFF) {
            throw new NinePatchException(mFile,
                    "Must have one-pixel frame that is either transparent or white");
        }

        // Find left and right of sizing areas...
        AtomicInteger outInt = new AtomicInteger(0);
        try {
            getHorizontalTicks(
                    content, 0, width,
                    transparent, true /*required*/,
                    xDivs, 0, 1, outInt,
                    true /*multipleAllowed*/);
            numXDivs = outInt.get();
        } catch (TickException e) {
            throw new NinePatchException(mFile, e, "top");
        }

        // Find top and bottom of sizing areas...
        outInt.set(0);
        try {
            getVerticalTicks(content, 0, width, height,
                    transparent, true /*required*/,
                    yDivs, 0, 1, outInt,
                    true /*multipleAllowed*/);
            numYDivs = outInt.get();
        } catch (TickException e) {
            throw new NinePatchException(mFile, e, "left");
        }

        // Find left and right of padding area...
        int[] values = new int[2];
        try {
            getHorizontalTicks(
                    content, width * (height - 1), width,
                    transparent, false /*required*/,
                    values, 0, 1, null,
                    false /*multipleAllowed*/);
            paddingLeft = values[0];
            paddingRight = values[1];
            values[0] = values[1] = 0;
        } catch (TickException e) {
            throw new NinePatchException(mFile, e, "bottom");
        }

        // Find top and bottom of padding area...
        try {
            getVerticalTicks(
                    content, width - 1, width, height,
                    transparent, false /*required*/,
                    values, 0, 1, null,
                    false /*multipleAllowed*/);
            paddingTop = values[0];
            paddingBottom = values[1];
        } catch (TickException e) {
            throw new NinePatchException(mFile, e, "right");
        }

        try {
            // Find left and right of layout padding...
            getHorizontalLayoutBoundsTicks(content, width * (height - 1), width,
                    transparent, false, values);
        } catch (TickException e) {
            throw new NinePatchException(mFile, e, "bottom");
        }

        int[] values2 = new int[2];
        try {
            getVerticalLayoutBoundsTicks(content, width - 1, width, height,
                    transparent, false, values2);
        } catch (TickException e) {
            throw new NinePatchException(mFile, e, "right");
        }

        LayoutBoundChunkBuilder layoutBoundChunkBuilder = null;
        if (values[0] != 0 || values[1] != 0 || values2[0] != 0 || values2[1] != 0) {
            layoutBoundChunkBuilder = new LayoutBoundChunkBuilder(
                    values[0], values2[0], values[1], values2[1]);
        }

        // If padding is not yet specified, take values from size.
        if (paddingLeft < 0) {
            paddingLeft = xDivs[0];
            paddingRight = width - 2 - xDivs[1];
        } else {
            // Adjust value to be correct!
            paddingRight = width - 2 - paddingRight;
        }
        if (paddingTop < 0) {
            paddingTop = yDivs[0];
            paddingBottom = height - 2 - yDivs[1];
        } else {
            // Adjust value to be correct!
            paddingBottom = height - 2 - paddingBottom;
        }

        // Remove frame from image.
        width -= 2;
        height -= 2;

        // Figure out the number of rows and columns in the N-patch
        numCols = numXDivs + 1;
        if (xDivs[0] == 0) {  // Column 1 is strechable
            numCols--;
        }
        if (xDivs[numXDivs - 1] == width) {
            numCols--;
        }
        numRows = numYDivs + 1;
        if (yDivs[0] == 0) {  // Row 1 is strechable
            numRows--;
        }
        if (yDivs[numYDivs - 1] == height) {
            numRows--;
        }

        // Make sure the amount of rows and columns will fit in the number of
        // colors we can use in the 9-patch format.
        if (numRows * numCols > 0x7F) {
            throw new NinePatchException(mFile, "Too many rows and columns in 9-patch perimeter");
        }

        numColors = (byte) (numRows * numCols);
        colors = new int[numColors];

        // Fill in color information for each patch.

        int c;
        top = 0;

        // The first row always starts with the top being at y=0 and the bottom
        // being either yDivs[1] (if yDivs[0]=0) of yDivs[0].  In the former case
        // the first row is stretchable along the Y axis, otherwise it is fixed.
        // The last row always ends with the bottom being bitmap.height and the top
        // being either yDivs[numYDivs-2] (if yDivs[numYDivs-1]=bitmap.height) or
        // yDivs[numYDivs-1]. In the former case the last row is stretchable along
        // the Y axis, otherwise it is fixed.
        //
        // The first and last columns are similarly treated with respect to the X
        // axis.
        //
        // The above is to help explain some of the special casing that goes on the
        // code below.

        // The initial yDiv and whether the first row is considered stretchable or
        // not depends on whether yDiv[0] was zero or not.
        for (j = (yDivs[0] == 0 ? 1 : 0);
                j <= numYDivs && top < height;
                j++) {
            if (j == numYDivs) {
                bottom = height;
            } else {
                bottom = yDivs[j];
            }
            left = 0;
            // The initial xDiv and whether the first column is considered
            // stretchable or not depends on whether xDiv[0] was zero or not.
            for (i = xDivs[0] == 0 ? 1 : 0;
                    i <= numXDivs && left < width;
                    i++) {
                if (i == numXDivs) {
                    right = width;
                } else {
                    right = xDivs[i];
                }
                c = getColor(content, width + 2, left, top, right - 1, bottom - 1);
                colors[colorIndex++] = c;
                left = right;
            }
            top = bottom;
        }

        // Create the chunks.
        NinePatchChunkBuilder ninePatchChunkBuilder = new NinePatchChunkBuilder(
                xDivs, numXDivs, yDivs, numYDivs, colors,
                paddingLeft, paddingRight, paddingTop, paddingBottom);

        addChunk(ninePatchChunkBuilder.getChunk());
        if (layoutBoundChunkBuilder != null) {
            addChunk(layoutBoundChunkBuilder.getChunk());
        }
    }

    /**
     * returns a color. the top/left/right/bottom coordinate are in a subframe of content, starting
     * in (1,1).
     * @param content the image buffer
     * @param width the width of the image buffer
     * @param left left coordinate.
     * @param top top coordinate.
     * @param right right coordinate.
     * @param bottom bottom coordinate.
     * @return a color.
     */
    static int getColor(@NonNull int[] content, int width,
            int left, int top, int right, int bottom) {
        int color = content[(top + 1) * width + left + 1];
        int alpha = color & 0xFF000000;

        if (left > right || top > bottom) {
            return PNG_9PATCH_TRANSPARENT_COLOR;
        }

        while (top <= bottom) {
            for (int i = left; i <= right; i++) {
                int c = content[(top + 1) * width + i + 1];
                if (alpha == 0) {
                    if ((c & 0xFF000000) != 0) {
                        return PNG_9PATCH_NO_COLOR;
                    }
                } else if (c != color) {
                    return PNG_9PATCH_NO_COLOR;
                }
            }
            top++;
        }

        if (alpha == 0) {
            return PNG_9PATCH_TRANSPARENT_COLOR;
        }

        return color;
    }


    private static enum TickType {
        NONE, TICK, LAYOUT_BOUNDS, BOTH
    }

    @NonNull
    private static TickType getTickType(int color, boolean transparent) throws TickException {

        int alpha = color >>> 24;

        if (transparent) {
            if (alpha == 0) {
                return TickType.NONE;
            }
            if (color == COLOR_LAYOUT_BOUNDS_TICK) {
                return TickType.LAYOUT_BOUNDS;
            }
            if (color == COLOR_TICK) {
                return TickType.TICK;
            }

            // Error cases
            if (alpha != 0xFF) {
                throw TickException.createWithColor(
                        "Frame pixels must be either solid or transparent (not intermediate alphas)",
                        color);
            }
            if ((color & 0x00FFFFFF) != 0) {
                throw TickException.createWithColor("Ticks in transparent frame must be black or red",
                        color);
            }
            return TickType.TICK;
        }

        if (alpha != 0xFF) {
            throw TickException.createWithColor("White frame must be a solid color (no alpha)",
                    color);
        }
        if (color == COLOR_WHITE) {
            return TickType.NONE;
        }
        if (color == COLOR_TICK) {
            return TickType.TICK;
        }
        if (color == COLOR_LAYOUT_BOUNDS_TICK) {
            return TickType.LAYOUT_BOUNDS;
        }

        if ((color & 0x00FFFFFF) != 0) {
            throw TickException.createWithColor("Ticks in transparent frame must be black or red",
                    color);
        }

        return TickType.TICK;
    }


    private static enum Tick {
        START, INSIDE_1, OUTSIDE_1
    }

    private static void getHorizontalTicks(
            @NonNull int[] content, int offset, int width,
            boolean transparent, boolean required,
            @NonNull int[] divs, int left, int right,
            @Nullable AtomicInteger outDivs, boolean multipleAllowed) throws TickException {
        int i;
        divs[left] = divs[right] = -1;
        Tick state = Tick.START;
        boolean found = false;

        for (i = 1; i < width - 1; i++) {
            TickType tickType;
            try {
                tickType = getTickType(content[offset + i], transparent);
            } catch (TickException e) {
                throw new TickException(e, i);
            }

            if (TickType.TICK == tickType) {
                if (state == Tick.START ||
                        (state == Tick.OUTSIDE_1 && multipleAllowed)) {
                    divs[left] = i - 1;
                    divs[right] = width - 2;
                    found = true;
                    if (outDivs != null) {
                        outDivs.addAndGet(2);
                    }
                    state = Tick.INSIDE_1;
                } else if (state == Tick.OUTSIDE_1) {
                    throw new TickException("Can't have more than one marked region along edge");
                }
            } else {
                if (state == Tick.INSIDE_1) {
                    // We're done with this div.  Move on to the next.
                    divs[right] = i - 1;
                    right += 2;
                    left += 2;
                    state = Tick.OUTSIDE_1;
                }
            }

        }

        if (required && !found) {
            throw new TickException("No marked region found along edge");
        }
    }

    private static void getVerticalTicks(
            @NonNull int[] content, int offset, int width, int height,
            boolean transparent, boolean required,
            @NonNull int[] divs, int top, int bottom,
            @Nullable AtomicInteger outDivs, boolean multipleAllowed) throws TickException {

        int i;
        divs[top] = divs[bottom] = -1;
        Tick state = Tick.START;
        boolean found = false;

        for (i = 1; i < height - 1; i++) {
            TickType tickType;
            try {
                tickType = getTickType(content[offset + width * i], transparent);
            } catch (TickException e) {
                throw new TickException(e, i);
            }

            if (TickType.TICK == tickType) {
                if (state == Tick.START ||
                        (state == Tick.OUTSIDE_1 && multipleAllowed)) {
                    divs[top] = i - 1;
                    divs[bottom] = height - 2;
                    found = true;
                    if (outDivs != null) {
                        outDivs.addAndGet(2);
                    }
                    state = Tick.INSIDE_1;
                } else if (state == Tick.OUTSIDE_1) {
                    throw new TickException("Can't have more than one marked region along edge");
                }
            } else {
                if (state == Tick.INSIDE_1) {
                    // We're done with this div.  Move on to the next.
                    divs[bottom] = i - 1;
                    top += 2;
                    bottom += 2;
                    state = Tick.OUTSIDE_1;
                }
            }
        }

        if (required && !found) {
            throw new TickException("No marked region found along edge");
        }
    }

    private static void getHorizontalLayoutBoundsTicks(
            @NonNull int[] content, int offset, int width, boolean transparent, boolean required,
            @NonNull int[] outValues) throws TickException {

        int i;
        outValues[0] = outValues[1] = 0;

        // Look for left tick
        if (TickType.LAYOUT_BOUNDS == getTickType(content[offset + 1], transparent)) {
            // Starting with a layout padding tick
            i = 1;
            while (i < width - 1) {
                outValues[0]++;
                i++;
                TickType tick = getTickType(content[offset + i], transparent);
                if (tick != TickType.LAYOUT_BOUNDS) {
                    break;
                }
            }
        }

        // Look for right tick
        if (TickType.LAYOUT_BOUNDS == getTickType(content[offset + (width - 2)], transparent)) {
            // Ending with a layout padding tick
            i = width - 2;
            while (i > 1) {
                outValues[1]++;
                i--;
                TickType tick = getTickType(content[offset + i], transparent);
                if (tick != TickType.LAYOUT_BOUNDS) {
                    break;
                }
            }
        }
    }

    private static void getVerticalLayoutBoundsTicks(
            @NonNull int[] content, int offset, int width, int height,
            boolean transparent, boolean required,
            @NonNull int[] outValues) throws TickException {
        int i;
        outValues[0] = outValues[1] = 0;

        // Look for top tick
        if (TickType.LAYOUT_BOUNDS == getTickType(content[offset + width], transparent)) {
            // Starting with a layout padding tick
            i = 1;
            while (i < height - 1) {
                outValues[0]++;
                i++;
                TickType tick = getTickType(content[offset + width * i], transparent);
                if (tick != TickType.LAYOUT_BOUNDS) {
                    break;
                }
            }
        }

        // Look for bottom tick
        if (TickType.LAYOUT_BOUNDS == getTickType(content[offset + width * (height - 2)],
                transparent)) {
            // Ending with a layout padding tick
            i = height - 2;
            while (i > 1) {
                outValues[1]++;
                i--;
                TickType tick = getTickType(content[offset + width * i], transparent);
                if (tick != TickType.LAYOUT_BOUNDS) {
                    break;
                }
            }
        }
    }

    @VisibleForTesting
    Chunk computeIhdr(int width, int height, byte bitDepth, @NonNull ColorType colorType) {
        byte[] buffer = new byte[13];

        ByteUtils utils = ByteUtils.Cache.get();

        System.arraycopy(utils.getIntAsArray(width), 0, buffer, 0, 4);
        System.arraycopy(utils.getIntAsArray(height), 0, buffer, 4, 4);
        buffer[8] = bitDepth;
        buffer[9] = colorType.getFlag();
        buffer[10] = 0; // compressionMethod
        buffer[11] = 0; // filterMethod;
        buffer[12] = 0; // interlaceMethod

        return new Chunk(PngWriter.IHDR, buffer);
    }

    private boolean is9Patch() {
        return mFile.getPath().endsWith(SdkConstants.DOT_9PNG);
    }
}
