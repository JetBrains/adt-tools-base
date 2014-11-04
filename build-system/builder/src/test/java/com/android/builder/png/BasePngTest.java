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

import com.android.annotations.NonNull;
import com.android.testutils.TestUtils;
import com.google.common.collect.Maps;
import com.google.common.io.Files;

import junit.framework.TestCase;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Map;

import javax.imageio.ImageIO;

/**
 */
public abstract class BasePngTest extends TestCase {

    /**
     * Signature of a PNG file.
     */
    public static final byte[] SIGNATURE = new byte[] {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A };


    protected static void compareImageContent(@NonNull File originalFile, @NonNull File createdFile,
            boolean is9Patch)
            throws IOException {
        BufferedImage originalImage = ImageIO.read(originalFile);
        BufferedImage createdImage = ImageIO.read(createdFile);

        int originalWidth = originalImage.getWidth();
        int originalHeight = originalImage.getHeight();

        int createdWidth = createdImage.getWidth();
        int createdHeight = createdImage.getHeight();

        // compare sizes taking into account if the image is a 9-patch
        // in which case the original is bigger by 2 since it has the patch area still.
        assertEquals(originalWidth, createdWidth + (is9Patch ? 2 : 0));
        assertEquals(originalHeight, createdHeight + (is9Patch ? 2 : 0));

        // get the file content
        // always use the created Size. And for the original image, if 9-patch, just take
        // the image minus the 1-pixel border all around.
        int[] originalContent = new int[createdWidth * createdHeight];
        if (is9Patch) {
            originalImage.getRGB(1, 1, createdWidth, createdHeight, originalContent, 0, createdWidth);
        } else {
            originalImage.getRGB(0, 0, createdWidth, createdHeight, originalContent, 0, createdWidth);
        }

        int[] createdContent = new int[createdWidth * createdHeight];
        createdImage.getRGB(0, 0, createdWidth, createdHeight, createdContent, 0, createdWidth);

        for (int y = 0 ; y < createdHeight ; y++) {
            for (int x = 0 ; x < createdWidth ; x++) {
                int originalRGBA = originalContent[y * createdWidth + x];
                int createdRGBA = createdContent[y * createdWidth + x];
                assertEquals(
                        String.format("%dx%d: 0x%08x : 0x%08x", x, y, originalRGBA, createdRGBA),
                        originalRGBA,
                        createdRGBA);
            }
        }
    }

    @NonNull
    protected static Map<String, Chunk> readChunks(@NonNull File file) throws IOException {
        Map<String, Chunk> chunks = Maps.newHashMap();

        byte[] fileBuffer = Files.toByteArray(file);
        ByteBuffer buffer = ByteBuffer.wrap(fileBuffer);

        byte[] sig = new byte[8];
        buffer.get(sig);

        assertTrue(Arrays.equals(sig, SIGNATURE));

        byte[] data, type;
        int len;
        int crc32;

        while (buffer.hasRemaining()) {
            len = buffer.getInt();

            type = new byte[4];
            buffer.get(type);

            data = new byte[len];
            buffer.get(data);

            // crc
            crc32 = buffer.getInt();

            Chunk chunk = new Chunk(type, data, crc32);
            chunks.put(chunk.getTypeAsString(), chunk);
        }

        return chunks;
    }

    /**
     * Returns the SDK folder as built from the Android source tree.
     * @return the SDK
     */
    @NonNull
    protected File getSdkDir() {
        String androidHome = System.getenv("ANDROID_HOME");
        if (androidHome != null) {
            File f = new File(androidHome);
            if (f.isDirectory()) {
                return f;
            }
        }

        throw new IllegalStateException("SDK not defined with ANDROID_HOME");
    }


    @NonNull
    protected static File getFile(@NonNull String name) {
        return new File(getPngFolder(), name);
    }

    @NonNull
    protected static File getPngFolder() {
        File folder = TestUtils.getRoot("png");
        assertTrue(folder.isDirectory());
        return folder;
    }
}
