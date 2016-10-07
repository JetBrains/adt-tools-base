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

package com.android.builder.internal.packaging.zip.compress;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.builder.internal.packaging.zip.CentralDirectoryHeaderCompressInfo;
import com.android.builder.internal.packaging.zip.CompressionMethod;
import com.android.builder.internal.packaging.zip.StoredEntry;
import com.android.builder.internal.packaging.zip.ZFile;
import com.android.builder.internal.packaging.zip.ZFileOptions;
import com.android.testutils.TestUtils;
import com.google.common.io.Files;
import com.google.common.util.concurrent.MoreExecutors;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.zip.Deflater;

public class MultiCompressorTest {
    @Rule
    public TemporaryFolder mTemporaryFolder = new TemporaryFolder();

    private byte[] getCompressibleData() throws Exception {
        File packagingRoot = TestUtils.getRoot("packaging");
        File textFiles = new File(packagingRoot, "text-files");
        assertTrue(textFiles.isDirectory());
        File wikipediaFile = new File(textFiles, "wikipedia.html");
        assertTrue(wikipediaFile.isFile());
        return Files.asByteSource(wikipediaFile).read();
    }

    private byte[] compress(byte[] data, int level) throws Exception {
        Deflater deflater = new Deflater(level);
        deflater.setInput(data);
        deflater.finish();

        byte[] resultAll = new byte[data.length * 2];
        int resultAllCount = deflater.deflate(resultAll);

        byte[] result = new byte[resultAllCount];
        System.arraycopy(resultAll, 0, result, 0, resultAllCount);
        return result;
    }

    @Test
    public void storeIsBest() throws Exception {
        File zip = new File(mTemporaryFolder.getRoot(), "test.zip");

        try (ZFile zf = new ZFile(zip)) {
            zf.add("file", new ByteArrayInputStream(new byte[0]));
            StoredEntry entry = zf.get("file");
            assertNotNull(entry);

            CentralDirectoryHeaderCompressInfo ci =
                    entry.getCentralDirectoryHeader().getCompressionInfoWithWait();

            assertEquals(0, ci.getCompressedSize());
            assertEquals(CompressionMethod.STORE, ci.getMethod());
        }
    }

    @Test
    public void sameCompressionResultButBetterThanStore() throws Exception {
        File zip = new File(mTemporaryFolder.getRoot(), "test.zip");

        byte[] data = new byte[] { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };

        try (ZFile zf = new ZFile(zip)) {
            zf.add("file", new ByteArrayInputStream(data));
            StoredEntry entry = zf.get("file");
            assertNotNull(entry);

            CentralDirectoryHeaderCompressInfo ci =
                    entry.getCentralDirectoryHeader().getCompressionInfoWithWait();

            assertEquals(CompressionMethod.DEFLATE, ci.getMethod());
            assertTrue(ci.getCompressedSize() < data.length);
        }
    }

    @Test
    public void bestBetterThanDefault() throws Exception {
        byte[] data = getCompressibleData();
        int bestSize = compress(data, Deflater.BEST_COMPRESSION).length;
        int defaultSize = compress(data, Deflater.DEFAULT_COMPRESSION).length;

        double ratio = bestSize / (double) defaultSize;
        assertTrue(ratio < 1.0);

        File defaultFile = new File(mTemporaryFolder.getRoot(), "default.zip");
        File resultFile = new File(mTemporaryFolder.getRoot(), "result.zip");

        ZFileOptions resultOptions = new ZFileOptions();
        resultOptions.setCompressor(new BestAndDefaultDeflateExecutorCompressor(
                MoreExecutors.sameThreadExecutor(), resultOptions.getTracker(), ratio + 0.001));

        try (
                ZFile defaultZFile = new ZFile(defaultFile);
                ZFile resultZFile = new ZFile(resultFile, resultOptions)) {
            defaultZFile.add("wikipedia.html", new ByteArrayInputStream(data));
            resultZFile.add("wikipedia.html", new ByteArrayInputStream(data));
        }

        long defaultFileSize = defaultFile.length();
        long resultFileSize = resultFile.length();

        assertTrue(resultFileSize < defaultFileSize);
    }

    @Test
    public void bestBetterThanDefaultButNotEnough() throws Exception {
        byte[] data = getCompressibleData();
        int bestSize = compress(data, Deflater.BEST_COMPRESSION).length;
        int defaultSize = compress(data, Deflater.DEFAULT_COMPRESSION).length;

        double ratio = bestSize / (double) defaultSize;
        assertTrue(ratio < 1.0);

        File defaultFile = new File(mTemporaryFolder.getRoot(), "default.zip");
        File resultFile = new File(mTemporaryFolder.getRoot(), "result.zip");

        ZFileOptions resultOptions = new ZFileOptions();
        resultOptions.setCompressor(new BestAndDefaultDeflateExecutorCompressor(
                MoreExecutors.sameThreadExecutor(), resultOptions.getTracker(), ratio - 0.001));

        try (
                ZFile defaultZFile = new ZFile(defaultFile);
                ZFile resultZFile = new ZFile(resultFile, resultOptions)) {
            defaultZFile.add("wikipedia.html", new ByteArrayInputStream(data));
            resultZFile.add("wikipedia.html", new ByteArrayInputStream(data));
        }

        long defaultFileSize = defaultFile.length();
        long resultFileSize = resultFile.length();

        assertTrue(resultFileSize == defaultFileSize);
    }
}
