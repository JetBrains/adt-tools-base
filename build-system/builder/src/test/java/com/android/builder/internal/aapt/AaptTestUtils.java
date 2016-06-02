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

package com.android.builder.internal.aapt;

import static org.junit.Assert.assertTrue;

import com.android.annotations.NonNull;
import com.android.testutils.TestUtils;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.io.Files;

import org.junit.rules.TemporaryFolder;

import java.io.File;

/**
 * Utility files for Aapt tests.
 */
public class AaptTestUtils {
    /**
     * Obtains a PNG for testing.
     *
     * @param temporaryFolder the temporary folder where temporary files should be placed
     * @return a file with a PNG in a {@code drawables} folder
     * @throws Exception failed to create the PNG
     */
    @NonNull
    public static File getTestPng(@NonNull TemporaryFolder temporaryFolder) throws Exception {
        File drawables = new File(temporaryFolder.getRoot(), "drawable");
        FileUtils.mkdirs(drawables);
        File png = new File(TestUtils.getRoot("aapt"), "lena.png");
        assertTrue(png.isFile());
        File dstPng = new File(drawables, "lena.png");
        Files.copy(png, dstPng);
        return dstPng;
    }

    /**
     * Obtains a text file for testing.
     *
     * @param temporaryFolder the temporary folder where temporary files should be placed
     * @return a test file in a {@code raw} folder
     */
    @NonNull
    public static File getTestTxt(@NonNull TemporaryFolder temporaryFolder) throws Exception {
        File raw = new File(temporaryFolder.getRoot(), "raw");
        FileUtils.mkdirs(raw);
        File txt = new File(raw, "abc.txt");
        Files.write("text.txt", txt, Charsets.US_ASCII);
        return txt;
    }

    /**
     * Obtains a PNG that cannot be crunched for testing.
     *
     * @return a PNG
     */
    @NonNull
    public static File getNonCrunchableTestPng() {
        File png = new File(TestUtils.getRoot("aapt"), "png-that-is-bigger-if-crunched.png");
        assertTrue(png.isFile());
        return png;
    }

    /**
     * Obtains a PNG that can be crunched for testing.
     *
     * @return a PNG
     */
    @NonNull
    public static File getCrunchableTestPng() {
        File png = new File(TestUtils.getRoot("aapt"), "lorem-lena.png");
        assertTrue(png.isFile());
        return png;
    }

    /**
     * Obtains a PNG that cannot be crunched for testing.
     *
     * @return a PNG
     */
    @NonNull
    public static File getNinePatchTestPng() {
        File png = new File(TestUtils.getRoot("aapt"), "9patch.9.png");
        assertTrue(png.isFile());
        return png;
    }

    /**
     * Obtains the temporary directory where output files should be placed.
     *
     * @param temporaryFolder the temporary folder where temporary files should be placed
     * @return the output directory
     * @throws Exception failed to create the output directory
     */
    @NonNull
    public static File getOutputDir(@NonNull TemporaryFolder temporaryFolder) throws Exception {
        File outputDir = new File(temporaryFolder.getRoot(), "compile-output");
        if (!outputDir.isDirectory()) {
            assertTrue(outputDir.mkdirs());
        }

        return outputDir;
    }
}
