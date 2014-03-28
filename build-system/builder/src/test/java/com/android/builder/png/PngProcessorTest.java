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
import com.google.common.io.Files;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

public class PngProcessorTest extends BasePngTest {

    public void testSimplePng() throws IOException, NinePatchException {
        File fromFile = getFile("icon.png");
        File outFile = crunch(fromFile);

        compareImageContent(fromFile, outFile, false /*is9Patch*/);
    }

    public void testGrayscalePng() throws IOException, NinePatchException, DataFormatException {
        File fromFile = getFile("grayscale.png");
        File outFile = crunch(fromFile);

        // compare to the aapt-crunched file since ImageIO applies some color correction
        // when loading grayscale images.
        File aaptFile = new File(fromFile.getParent(), fromFile.getName() + ".crunched");

        compareImageContent(aaptFile, outFile, false /*is9Patch*/);
    }

    public void testBroken9PatchBlue() {
        File fromFile = getFile("blue_patch.9.png");
        try {
            crunch(fromFile);
            assertFalse("Crunching didn't fail", true);
        } catch (NinePatchException e) {
            TickException tickException = e.getTickException();
            assertNotNull(tickException);

            assertEquals(1, tickException.getPixelLocation());
            assertNotNull(tickException.getPixelColor());
            assertEquals(0xFF0000FF, tickException.getPixelColor().intValue());
            assertEquals("left", e.getEdge());

        } catch (IOException e) {
            assertFalse("Got IOException instead of NinePatchException", true);
        }
    }

    public void testMissingPatch() {
        File fromFile = getFile("missing_patch.9.png");
        try {
            crunch(fromFile);
            assertFalse("Crunching didn't fail", true);
        } catch (NinePatchException e) {
            TickException tickException = e.getTickException();
            assertNotNull(tickException);

            assertEquals(-1, tickException.getPixelLocation());
            assertNull(tickException.getPixelColor());
            assertEquals("top", e.getEdge());

        } catch (IOException e) {
            assertFalse("Got IOException instead of NinePatchException", true);
        }
    }

    public void testAlreadyCrunchedFile() throws IOException, NinePatchException {
        File fromFile = getFile("crunched.png");
        File outFile = crunch(fromFile);

        // check the files are the same.
        assertEquals(fromFile.length(), outFile.length());

        byte[] fromArray = Files.toByteArray(fromFile);
        byte[] toArray = Files.toByteArray(outFile);
        assertTrue(Arrays.equals(fromArray, toArray));
    }

    byte[] getRawImageData(@NonNull File file) throws DataFormatException, IOException {
        Map<String, Chunk> chunks = readChunks(file);

        Chunk idat = chunks.get("IDAT");

        assertNotNull(idat);
        assertNotNull(idat.getData());

        // create a growing buffer for the result.
        ByteArrayOutputStream bos = new ByteArrayOutputStream(idat.getData().length);

        Inflater inflater = new Inflater();
        inflater.setInput(idat.getData());

        // temp buffer for compressed data.
        byte[] tmpBuffer = new byte[1024];
        while (!inflater.finished()) {
            int compressedLen = inflater.inflate(tmpBuffer);
            bos.write(tmpBuffer, 0, compressedLen);
        }

        bos.close();

        return bos.toByteArray();
    }
}
