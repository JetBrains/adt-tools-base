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

import static junit.framework.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.ide.common.internal.PngCruncher;
import com.android.ide.common.internal.PngException;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.SdkManager;
import com.android.sdklib.repository.FullRevision;
import com.android.testutils.TestUtils;
import com.android.utils.ILogger;
import com.android.utils.StdLogger;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.io.Files;

import junit.framework.Assert;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.DataFormatException;

import javax.imageio.ImageIO;

/**
 * Utilities common to tests for both the synchronous and the asynchronous Aapt processor.
 */
public class NinePatchAaptProcessorTestUtils {

    /**
     * Signature of a PNG file.
     */
    public static final byte[] SIGNATURE = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    static File getAapt(FullRevision fullRevision) {
        ILogger logger = new StdLogger(StdLogger.Level.VERBOSE);
        SdkManager sdkManager = SdkManager.createManager(getSdkDir().getAbsolutePath(), logger);
        assert sdkManager != null;
        BuildToolInfo buildToolInfo = sdkManager.getBuildTool(fullRevision);
        if (buildToolInfo == null) {
            throw new RuntimeException("Test requires build-tools " + fullRevision.toShortString());
        }
        return new File(buildToolInfo.getPath(BuildToolInfo.PathId.AAPT));
    }


    public static void tearDownAndCheck(Map<File, File> sourceAndCrunchedFiles,
            PngCruncher cruncher, AtomicLong classStartTime)
            throws IOException, DataFormatException {
        long startTime = System.currentTimeMillis();
        try {
            cruncher.end();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println(
                "waiting for requests completion : " + (System.currentTimeMillis() - startTime));
        System.out.println("total time : " + (System.currentTimeMillis() - classStartTime.get()));
        System.out.println("Comparing crunched files");
        long comparisonStartTime = System.currentTimeMillis();
        for (Map.Entry<File, File> sourceAndCrunched : sourceAndCrunchedFiles.entrySet()) {
            System.out.println(sourceAndCrunched.getKey().getName());
            File crunched = new File(sourceAndCrunched.getKey().getParent(),
                    sourceAndCrunched.getKey().getName() + getControlFileSuffix());

            //copyFile(sourceAndCrunched.getValue(), crunched);
            Map<String, Chunk> testedChunks = compareChunks(crunched, sourceAndCrunched.getValue());

            try {
                compareImageContent(crunched, sourceAndCrunched.getValue(), false);
            } catch (Throwable e) {
                throw new RuntimeException("Failed with " + testedChunks.get("IHDR"), e);
            }
        }
        System.out.println("Done comparing crunched files " + (System.currentTimeMillis()
                - comparisonStartTime));
    }

    protected static String getControlFileSuffix() {
        return ".crunched.aapt";
    }

    private static void copyFile(File source, File dest)
            throws IOException {
        FileChannel inputChannel = null;
        FileChannel outputChannel = null;
        try {
            inputChannel = new FileInputStream(source).getChannel();
            outputChannel = new FileOutputStream(dest).getChannel();
            outputChannel.transferFrom(inputChannel, 0, inputChannel.size());
        } finally {
            inputChannel.close();
            outputChannel.close();
        }
    }


    @NonNull
    static File crunchFile(@NonNull File file, PngCruncher aaptCruncher)
            throws PngException, IOException {
        File outFile = File.createTempFile("pngWriterTest", ".png");
        outFile.deleteOnExit();
        try {
            aaptCruncher.crunchPng(file, outFile);
        } catch (PngException e) {
            e.printStackTrace();
            throw e;
        }
        System.out.println("crunch " + file.getPath());
        return outFile;
    }


    private static Map<String, Chunk> compareChunks(@NonNull File original, @NonNull File tested)
            throws
            IOException, DataFormatException {
        Map<String, Chunk> originalChunks = readChunks(original);
        Map<String, Chunk> testedChunks = readChunks(tested);

        compareChunk(originalChunks, testedChunks, "IHDR");
        compareChunk(originalChunks, testedChunks, "npLb");
        compareChunk(originalChunks, testedChunks, "npTc");

        return testedChunks;
    }

    private static void compareChunk(
            @NonNull Map<String, Chunk> originalChunks,
            @NonNull Map<String, Chunk> testedChunks,
            @NonNull String chunkType) {
        assertEquals(originalChunks.get(chunkType), testedChunks.get(chunkType));
    }

    public static Collection<Object[]> getNinePatches() {
        File pngFolder = getPngFolder();
        File ninePatchFolder = new File(pngFolder, "ninepatch");

        File[] files = ninePatchFolder.listFiles(new FileFilter() {
            @Override
            public boolean accept(File file) {
                return file.getPath().endsWith(SdkConstants.DOT_9PNG);
            }
        });
        if (files != null) {
            ImmutableList.Builder<Object[]> params = ImmutableList.builder();
            for (File file : files) {
                params.add(new Object[]{file, file.getName()});
            }
            return params.build();
        }

        return ImmutableList.of();
    }

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
        Assert.assertEquals(originalWidth, createdWidth + (is9Patch ? 2 : 0));
        Assert.assertEquals(originalHeight, createdHeight + (is9Patch ? 2 : 0));

        // get the file content
        // always use the created Size. And for the original image, if 9-patch, just take
        // the image minus the 1-pixel border all around.
        int[] originalContent = new int[createdWidth * createdHeight];
        if (is9Patch) {
            originalImage
                    .getRGB(1, 1, createdWidth, createdHeight, originalContent, 0, createdWidth);
        } else {
            originalImage
                    .getRGB(0, 0, createdWidth, createdHeight, originalContent, 0, createdWidth);
        }

        int[] createdContent = new int[createdWidth * createdHeight];
        createdImage.getRGB(0, 0, createdWidth, createdHeight, createdContent, 0, createdWidth);

        for (int y = 0; y < createdHeight; y++) {
            for (int x = 0; x < createdWidth; x++) {
                int originalRGBA = originalContent[y * createdWidth + x];
                int createdRGBA = createdContent[y * createdWidth + x];
                Assert.assertEquals(
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
     *
     * @return the SDK
     */
    @NonNull
    protected static File getSdkDir() {
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
