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

package com.android.builder.internal.packaging.zip;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.android.builder.internal.utils.CachedFileContents;
import com.google.common.base.Charsets;
import com.google.common.base.Predicates;
import com.google.common.hash.Hashing;
import com.google.common.io.ByteStreams;
import com.google.common.io.Closer;
import com.google.common.io.Files;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ZipMergeTest {
    @Rule
    public TemporaryFolder mTemporaryFolder = new TemporaryFolder();

    @Test
    public void mergeZip() throws Exception {
        File aZip = ZipTestUtils.cloneRsrc("simple-zip.zip", mTemporaryFolder, "a.zip");

        CachedFileContents<Object> changeDetector;
        File merged = new File(mTemporaryFolder.getRoot(), "r.zip");
        try (ZFile mergedZf = new ZFile(merged)) {
            mergedZf.mergeFrom(new ZFile(aZip), f -> false);
            mergedZf.close();

            assertEquals(3, mergedZf.entries().size());

            StoredEntry e0 = mergedZf.get("dir/");
            assertNotNull(e0);
            assertSame(StoredEntryType.DIRECTORY, e0.getType());

            StoredEntry e1 = mergedZf.get("dir/inside");
            assertNotNull(e1);
            assertSame(StoredEntryType.FILE, e1.getType());
            ByteArrayOutputStream e1BytesOut = new ByteArrayOutputStream();
            ByteStreams.copy(e1.open(), e1BytesOut);
            byte e1Bytes[] = e1BytesOut.toByteArray();
            String e1Txt = new String(e1Bytes, Charsets.US_ASCII);
            assertEquals("inside", e1Txt);

            StoredEntry e2 = mergedZf.get("file.txt");
            assertNotNull(e2);
            assertSame(StoredEntryType.FILE, e2.getType());
            ByteArrayOutputStream e2BytesOut = new ByteArrayOutputStream();
            ByteStreams.copy(e2.open(), e2BytesOut);
            byte e2Bytes[] = e2BytesOut.toByteArray();
            String e2Txt = new String(e2Bytes, Charsets.US_ASCII);
            assertEquals("file with more text to allow deflating to be useful", e2Txt);

            changeDetector = new CachedFileContents<>(merged);
            changeDetector.closed(null);

            /*
             * Clone aZip into bZip and merge. Should have no effect on the final zip file.
             */
            File bZip = ZipTestUtils.cloneRsrc("simple-zip.zip", mTemporaryFolder, "b.zip");

            mergedZf.mergeFrom(new ZFile(bZip), f -> false);
        }

        assertTrue(changeDetector.isValid());
    }

    @Test
    public void mergeZipWithDeferredCrc() throws Exception {
        File foo = mTemporaryFolder.newFile("foo");

        byte[] wBytes = Files.toByteArray(ZipTestUtils.rsrcFile("text-files/wikipedia.html"));

        try (ZipOutputStream fooOut = new ZipOutputStream(new FileOutputStream(foo))) {
            fooOut.putNextEntry(new ZipEntry("w"));
            fooOut.write(wBytes);
        }

        try (Closer closer = Closer.create()) {
            ZFile fooZf = closer.register(new ZFile(foo));
            StoredEntry wStored = fooZf.get("w");
            assertNotNull(wStored);
            assertTrue(wStored.getCentralDirectoryHeader().getGpBit().isDeferredCrc());
            assertEquals(CompressionMethod.DEFLATE,
                    wStored.getCentralDirectoryHeader().getCompressionInfoWithWait().getMethod());

            ZFile merged = closer.register(new ZFile(new File(mTemporaryFolder.getRoot(), "bar")));
            merged.mergeFrom(fooZf, f -> false);
            merged.update();

            StoredEntry wmStored = merged.get("w");
            assertNotNull(wmStored);
            assertFalse(wmStored.getCentralDirectoryHeader().getGpBit().isDeferredCrc());
            assertEquals(CompressionMethod.DEFLATE,
                    wmStored.getCentralDirectoryHeader().getCompressionInfoWithWait().getMethod());
        }
    }

    @Test
    public void mergeZipKeepsDeflatedAndStored() throws Exception {
        File foo = mTemporaryFolder.newFile("foo");

        byte[] wBytes = Files.toByteArray(ZipTestUtils.rsrcFile("text-files/wikipedia.html"));
        byte[] lBytes = Files.toByteArray(ZipTestUtils.rsrcFile("images/lena.png"));

        try (ZipOutputStream fooOut = new ZipOutputStream(new FileOutputStream(foo))) {
            fooOut.putNextEntry(new ZipEntry("w"));
            fooOut.write(wBytes);
            ZipEntry le = new ZipEntry("l");
            le.setMethod(ZipEntry.STORED);
            le.setSize(lBytes.length);
            le.setCrc(Hashing.crc32().hashBytes(lBytes).padToLong());
            fooOut.putNextEntry(le);
            fooOut.write(lBytes);
        }

        try (Closer closer = Closer.create()) {
            ZFile fooZf = closer.register(new ZFile(foo));
            StoredEntry wStored = fooZf.get("w");
            assertNotNull(wStored);
            assertEquals(CompressionMethod.DEFLATE,
                    wStored.getCentralDirectoryHeader().getCompressionInfoWithWait().getMethod());
            StoredEntry lStored = fooZf.get("l");
            assertNotNull(lStored);
            assertEquals(CompressionMethod.STORE,
                    lStored.getCentralDirectoryHeader().getCompressionInfoWithWait().getMethod());

            ZFile merged = closer.register(new ZFile(new File(mTemporaryFolder.getRoot(), "bar")));
            merged.mergeFrom(fooZf, f -> false);
            merged.update();

            StoredEntry wmStored = merged.get("w");
            assertNotNull(wmStored);
            assertFalse(wmStored.getCentralDirectoryHeader().getGpBit().isDeferredCrc());
            assertEquals(CompressionMethod.DEFLATE,
                    wmStored.getCentralDirectoryHeader().getCompressionInfoWithWait().getMethod());
            assertArrayEquals(wBytes, wmStored.read());

            StoredEntry lmStored = merged.get("l");
            assertNotNull(lmStored);
            assertEquals(CompressionMethod.STORE,
                    lmStored.getCentralDirectoryHeader().getCompressionInfoWithWait().getMethod());
            assertArrayEquals(lBytes, lmStored.read());
        }
    }

    @Test
    public void mergeZipWithSorting() throws Exception {
        File foo = mTemporaryFolder.newFile("foo");

        byte[] wBytes = Files.toByteArray(ZipTestUtils.rsrcFile("text-files/wikipedia.html"));
        byte[] lBytes = Files.toByteArray(ZipTestUtils.rsrcFile("images/lena.png"));

        try (ZipOutputStream fooOut = new ZipOutputStream(new FileOutputStream(foo))) {
            fooOut.putNextEntry(new ZipEntry("w"));
            fooOut.write(wBytes);
            ZipEntry le = new ZipEntry("l");
            le.setMethod(ZipEntry.STORED);
            le.setSize(lBytes.length);
            le.setCrc(Hashing.crc32().hashBytes(lBytes).padToLong());
            fooOut.putNextEntry(le);
            fooOut.write(lBytes);
        }

        try (
                ZFile fooZf = new ZFile(foo);
                ZFile merged = new ZFile(new File(mTemporaryFolder.getRoot(), "bar"))) {
            merged.mergeFrom(fooZf, f -> false);
            merged.sortZipContents();
            merged.update();

            StoredEntry wmStored = merged.get("w");
            assertNotNull(wmStored);
            assertArrayEquals(wBytes, wmStored.read());

            StoredEntry lmStored = merged.get("l");
            assertNotNull(lmStored);
            assertArrayEquals(lBytes, lmStored.read());
        }
    }
}
