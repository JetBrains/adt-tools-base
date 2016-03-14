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

package com.android.builder.internal.packaging.zip;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.android.builder.internal.packaging.zip.utils.CachedFileContents;
import com.android.testutils.TestUtils;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Strings;
import com.google.common.collect.Sets;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Random;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Ignore("Broken on java 7/8. Already fixed upstream.")
public class ZFileTest {
    @Rule
    public TemporaryFolder mTemporaryFolder = new TemporaryFolder();

    private File cloneRsrc(String rsrcName, String fname) throws Exception {
        File packagingRoot = TestUtils.getRoot("packaging");
        String rsrcPath = packagingRoot.getAbsolutePath() + "/" + rsrcName;
        File rsrcFile = new File(rsrcPath);
        File result = mTemporaryFolder.newFile(fname);
        Files.copy(rsrcFile, result);
        return result;
    }

    @Test
    public void readNonExistingFile() throws Exception {
        File temporaryDir = Files.createTempDir();
        File zf = new File(temporaryDir, "a");
        ZFile azf = new ZFile(zf);
        azf.touch();
        azf.close();
        assertTrue(zf.exists());
    }

    @Test(expected = IOException.class)
    public void readExistingEmptyFile() throws Exception {
        File temporaryDir = Files.createTempDir();
        File zf = new File(temporaryDir, "a");
        Files.write(new byte[0], zf);
        @SuppressWarnings("unused")
        ZFile azf = new ZFile(zf);
    }

    @Test
    public void readAlmostEmptyZip() throws Exception {
        File zf = cloneRsrc("empty-zip.zip", "a");

        ZFile azf = new ZFile(zf);
        assertEquals(1, azf.entries().size());

        StoredEntry z = azf.get("z/");
        assertNotNull(z);
        assertSame(StoredEntryType.DIRECTORY, z.getType());
    }

    @Test
    public void readZipWithTwoFilesOneDirectory() throws Exception {
        File zf = cloneRsrc("simple-zip.zip", "a");
        ZFile azf = new ZFile(zf);
        assertEquals(3, azf.entries().size());

        StoredEntry e0 = azf.get("dir/");
        assertNotNull(e0);
        assertSame(StoredEntryType.DIRECTORY, e0.getType());

        StoredEntry e1 = azf.get("dir/inside");
        assertNotNull(e1);
        assertSame(StoredEntryType.FILE, e1.getType());
        ByteArrayOutputStream e1BytesOut = new ByteArrayOutputStream();
        ByteStreams.copy(e1.open(), e1BytesOut);
        byte e1Bytes[] = e1BytesOut.toByteArray();
        String e1Txt = new String(e1Bytes, Charsets.US_ASCII);
        assertEquals("inside", e1Txt);

        StoredEntry e2 = azf.get("file.txt");
        assertNotNull(e2);
        assertSame(StoredEntryType.FILE, e2.getType());
        ByteArrayOutputStream e2BytesOut = new ByteArrayOutputStream();
        ByteStreams.copy(e2.open(), e2BytesOut);
        byte e2Bytes[] = e2BytesOut.toByteArray();
        String e2Txt = new String(e2Bytes, Charsets.US_ASCII);
        assertEquals("file with more text to allow deflating to be useful", e2Txt);
    }

    @Test
    public void readOnlyZipSupport() throws Exception {
        File testZip = cloneRsrc("empty-zip.zip", "tz");

        assertTrue(testZip.setWritable(false));

        ZFile zf = new ZFile(testZip);
        assertEquals(1, zf.entries().size());
    }

    @Test
    public void removeFileFromZip() throws Exception {
        File zipFile = mTemporaryFolder.newFile("test.zip");

        ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile));
        try {
            ZipEntry entry = new ZipEntry("foo/");
            entry.setMethod(ZipEntry.STORED);
            entry.setSize(0);
            entry.setCompressedSize(0);
            entry.setCrc(0);
            zos.putNextEntry(entry);
            zos.putNextEntry(new ZipEntry("foo/bar"));
            zos.write(new byte[]{1, 2, 3, 4});
            zos.closeEntry();
        } finally {
            zos.close();
        }

        ZFile zf = new ZFile(zipFile);
        assertEquals(2, zf.entries().size());
        for (StoredEntry e : zf.entries()) {
            if (e.getType() == StoredEntryType.FILE) {
                e.delete();
            }
        }

        zf.update();

        ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile));
        try {
            ZipEntry e1 = zis.getNextEntry();
            assertNotNull(e1);

            assertEquals("foo/", e1.getName());

            ZipEntry e2 = zis.getNextEntry();
            assertNull(e2);
        } finally {
            zis.close();
        }
    }

    @Test
    public void addFileToZip() throws Exception {
        File zipFile = mTemporaryFolder.newFile("test.zip");

        ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile));
        try {
            ZipEntry fooDir = new ZipEntry("foo/");
            fooDir.setCrc(0);
            fooDir.setCompressedSize(0);
            fooDir.setSize(0);
            fooDir.setMethod(ZipEntry.STORED);
            zos.putNextEntry(fooDir);
            zos.closeEntry();
        } finally {
            zos.close();
        }

        ZFile zf = new ZFile(zipFile);
        assertEquals(1, zf.entries().size());


        zf.update();

        ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile));
        try {
            ZipEntry e1 = zis.getNextEntry();
            assertNotNull(e1);

            assertEquals("foo/", e1.getName());

            ZipEntry e2 = zis.getNextEntry();
            assertNull(e2);
        } finally {
            zis.close();
        }
    }

    @Test
    public void createNewZip() throws Exception {
        File zipFile = new File(mTemporaryFolder.getRoot(), "test.zip");

        ZFile zf = new ZFile(zipFile);
        zf.add("foo", new ByteArrayEntrySource(new byte[] { 0, 1 }), CompressionMethod.DEFLATE);
        zf.close();

        ZipFile jzf = new ZipFile(zipFile);
        try {
            assertEquals(1, jzf.size());

            ZipEntry fooEntry = jzf.getEntry("foo");
            assertNotNull(fooEntry);
            assertEquals("foo", fooEntry.getName());
            assertEquals(2, fooEntry.getSize());

            InputStream is = jzf.getInputStream(fooEntry);
            assertEquals(0, is.read());
            assertEquals(1, is.read());
            assertEquals(-1, is.read());

            is.close();
        } finally {
            jzf.close();
        }
    }

    @Test
    public void mergeZip() throws Exception {
        File aZip = cloneRsrc("simple-zip.zip", "a.zip");

        File merged = new File(mTemporaryFolder.getRoot(), "r.zip");
        ZFile mergedZf = new ZFile(merged);
        mergedZf.mergeFrom(new ZFile(aZip), Sets.<Pattern>newHashSet());
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

        CachedFileContents<Object> changeDetector = new CachedFileContents<Object>(merged);
        changeDetector.closed(null);

        File bZip = cloneRsrc("simple-zip.zip", "b.zip");

        mergedZf.mergeFrom(new ZFile(bZip), Sets.<Pattern>newHashSet());
        mergedZf.close();

        assertTrue(changeDetector.isValid());
    }

    @Test
    public void replaceFileWithSmallerInMiddle() throws Exception {
        File zipFile = new File(mTemporaryFolder.getRoot(), "test.zip");

        ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile));
        try {
            zos.putNextEntry(new ZipEntry("file1"));
            zos.write(new byte[]{1, 2, 3, 4, 5});
            zos.putNextEntry(new ZipEntry("file2"));
            zos.write(new byte[]{6, 7, 8});
            zos.putNextEntry(new ZipEntry("file3"));
            zos.write(new byte[]{9, 0, 1, 2, 3, 4});
        } finally {
            zos.close();
        }

        int totalSize = (int) zipFile.length();

        ZFile zf = new ZFile(zipFile);
        assertEquals(3, zf.entries().size());

        StoredEntry file2 = zf.get("file2");
        assertNotNull(file2);
        assertEquals(3, file2.getCentralDirectoryHeader().getUncompressedSize());

        assertArrayEquals(new byte[] { 6, 7, 8 }, file2.read());

        zf.add("file2", new ByteArrayEntrySource(new byte[] { 11, 12 }), CompressionMethod.DEFLATE);
        zf.close();

        int newTotalSize = (int) zipFile.length();
        assertTrue(newTotalSize + " == " + totalSize, newTotalSize == totalSize);

        file2 = zf.get("file2");
        assertNotNull(file2);
        assertArrayEquals(new byte[] { 11, 12, }, file2.read());

        ZFile zf2 = new ZFile(zipFile);
        file2 = zf2.get("file2");
        assertNotNull(file2);
        assertArrayEquals(new byte[] { 11, 12, }, file2.read());
    }

    @Test
    public void replaceFileWithSmallerAtEnd() throws Exception {
        File zipFile = new File(mTemporaryFolder.getRoot(), "test.zip");

        ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile));
        try {
            zos.putNextEntry(new ZipEntry("file1"));
            zos.write(new byte[]{1, 2, 3, 4, 5});
            zos.putNextEntry(new ZipEntry("file2"));
            zos.write(new byte[]{6, 7, 8});
            zos.putNextEntry(new ZipEntry("file3"));
            zos.write(new byte[]{9, 0, 1, 2, 3, 4});
        } finally {
            zos.close();
        }

        int totalSize = (int) zipFile.length();

        ZFile zf = new ZFile(zipFile);
        assertEquals(3, zf.entries().size());

        StoredEntry file3 = zf.get("file3");
        assertNotNull(file3);
        assertEquals(6, file3.getCentralDirectoryHeader().getUncompressedSize());

        assertArrayEquals(new byte[] { 9, 0, 1, 2, 3, 4 }, file3.read());

        zf.add("file3", new ByteArrayEntrySource(new byte[] { 11, 12 }), CompressionMethod.DEFLATE);
        zf.close();

        int newTotalSize = (int) zipFile.length();
        assertTrue(newTotalSize + " < " + totalSize, newTotalSize < totalSize);

        file3 = zf.get("file3");
        assertNotNull(file3);
        assertArrayEquals(new byte[] { 11, 12, }, file3.read());

        ZFile zf2 = new ZFile(zipFile);
        file3 = zf2.get("file3");
        assertNotNull(file3);
        assertArrayEquals(new byte[] { 11, 12, }, file3.read());
    }

    @Test
    public void replaceFileWithBiggerAtBegin() throws Exception {
        File zipFile = new File(mTemporaryFolder.getRoot(), "test.zip");

        ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile));
        try {
            zos.putNextEntry(new ZipEntry("file1"));
            zos.write(new byte[]{1, 2, 3, 4, 5});
            zos.putNextEntry(new ZipEntry("file2"));
            zos.write(new byte[]{6, 7, 8});
            zos.putNextEntry(new ZipEntry("file3"));
            zos.write(new byte[]{9, 0, 1, 2, 3, 4});
        } finally {
            zos.close();
        }

        int totalSize = (int) zipFile.length();

        ZFile zf = new ZFile(zipFile);
        assertEquals(3, zf.entries().size());

        StoredEntry file1 = zf.get("file1");
        assertNotNull(file1);
        assertEquals(5, file1.getCentralDirectoryHeader().getUncompressedSize());

        assertArrayEquals(new byte[] { 1, 2, 3, 4, 5 }, file1.read());

        /*
         * Need some data because java zip API uses data descriptors which we don't and makes the
         * entries bigger (meaning just adding a couple of bytes would still fit in the same
         * place).
         */
        byte[] newData = new byte[100];
        Random r = new Random();
        r.nextBytes(newData);

        zf.add("file1", new ByteArrayEntrySource(newData), CompressionMethod.DEFLATE);
        zf.close();

        int newTotalSize = (int) zipFile.length();
        assertTrue(newTotalSize + " > " + totalSize, newTotalSize > totalSize);

        file1 = zf.get("file1");
        assertNotNull(file1);
        assertArrayEquals(newData, file1.read());

        ZFile zf2 = new ZFile(zipFile);
        file1 = zf2.get("file1");
        assertNotNull(file1);
        assertArrayEquals(newData, file1.read());
    }

    @Test
    public void replaceFileWithBiggerAtEnd() throws Exception {
        File zipFile = new File(mTemporaryFolder.getRoot(), "test.zip");

        ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile));
        try {
            zos.putNextEntry(new ZipEntry("file1"));
            zos.write(new byte[]{1, 2, 3, 4, 5});
            zos.putNextEntry(new ZipEntry("file2"));
            zos.write(new byte[]{6, 7, 8});
            zos.putNextEntry(new ZipEntry("file3"));
            zos.write(new byte[]{9, 0, 1, 2, 3, 4});
        } finally {
            zos.close();
        }

        int totalSize = (int) zipFile.length();

        ZFile zf = new ZFile(zipFile);
        assertEquals(3, zf.entries().size());

        StoredEntry file3 = zf.get("file3");
        assertNotNull(file3);
        assertEquals(6, file3.getCentralDirectoryHeader().getUncompressedSize());

        assertArrayEquals(new byte[] { 9, 0, 1, 2, 3, 4 }, file3.read());

        /*
         * Need some data because java zip API uses data descriptors which we don't and makes the
         * entries bigger (meaning just adding a couple of bytes would still fit in the same
         * place).
         */
        byte[] newData = new byte[100];
        Random r = new Random();
        r.nextBytes(newData);

        zf.add("file3", new ByteArrayEntrySource(newData), CompressionMethod.DEFLATE);
        zf.close();

        int newTotalSize = (int) zipFile.length();
        assertTrue(newTotalSize + " > " + totalSize, newTotalSize > totalSize);

        file3 = zf.get("file3");
        assertNotNull(file3);
        assertArrayEquals(newData, file3.read());

        ZFile zf2 = new ZFile(zipFile);
        file3 = zf2.get("file3");
        assertNotNull(file3);
        assertArrayEquals(newData, file3.read());
    }

    @Test
    public void ignoredFilesDuringMerge() throws Exception {
        File zip1 = mTemporaryFolder.newFile("t1.zip");
        ZipOutputStream zos1 = new ZipOutputStream(new FileOutputStream(zip1));
        try {
            zos1.putNextEntry(new ZipEntry("only_in_1"));
            zos1.write(new byte[] { 1, 2 });
            zos1.putNextEntry(new ZipEntry("overridden_by_2"));
            zos1.write(new byte[] { 2, 3 });
            zos1.putNextEntry(new ZipEntry("not_overridden_by_2"));
            zos1.write(new byte[] { 3, 4 });
        } finally {
            zos1.close();
        }

        File zip2 = mTemporaryFolder.newFile("t2.zip");
        ZipOutputStream zos2 = new ZipOutputStream(new FileOutputStream(zip2));
        try {
            zos2.putNextEntry(new ZipEntry("only_in_2"));
            zos2.write(new byte[] { 4, 5 });
            zos2.putNextEntry(new ZipEntry("overridden_by_2"));
            zos2.write(new byte[] { 5, 6 });
            zos2.putNextEntry(new ZipEntry("not_overridden_by_2"));
            zos2.write(new byte[] { 6, 7 });
            zos2.putNextEntry(new ZipEntry("ignored_in_2"));
            zos2.write(new byte[] { 7, 8 });
        } finally {
            zos2.close();
        }

        Set<Pattern> ignoreFiles = Sets.newHashSet();
        ignoreFiles.add(Pattern.compile("not.*"));
        ignoreFiles.add(Pattern.compile(".*gnored.*"));

        ZFile zf1 = new ZFile(zip1);
        ZFile zf2 = new ZFile(zip2);
        zf1.mergeFrom(zf2, ignoreFiles);

        StoredEntry only_in_1 = zf1.get("only_in_1");
        assertNotNull(only_in_1);
        assertArrayEquals(new byte[] { 1, 2 }, only_in_1.read());

        StoredEntry overridden_by_2 = zf1.get("overridden_by_2");
        assertNotNull(overridden_by_2);
        assertArrayEquals(new byte[] { 5, 6 }, overridden_by_2.read());

        StoredEntry not_overridden_by_2 = zf1.get("not_overridden_by_2");
        assertNotNull(not_overridden_by_2);
        assertArrayEquals(new byte[] { 3, 4 }, not_overridden_by_2.read());

        StoredEntry only_in_2 = zf1.get("only_in_2");
        assertNotNull(only_in_2);
        assertArrayEquals(new byte[] { 4, 5 }, only_in_2.read());

        StoredEntry ignored_in_2 = zf1.get("ignored_in_2");
        assertNull(ignored_in_2);
    }

    @Test
    public void addingFileDoesNotAddDirectoriesAutomatically() throws Exception {
        File zip = new File(mTemporaryFolder.getRoot(), "z.zip");
        ZFile zf = new ZFile(zip);
        zf.add("a/b/c", new ByteArrayEntrySource(new byte[] { 1, 2, 3 }),
                CompressionMethod.DEFLATE);
        zf.update();
        assertEquals(1, zf.entries().size());

        StoredEntry c = zf.get("a/b/c");
        assertNotNull(c);
        assertEquals(3, c.read().length);

        zf.close();
    }

    @Test
    public void zipFileWithEocdSignatureInComment() throws Exception {
        File zip = mTemporaryFolder.newFile("f.zip");
        ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zip));
        try {
            zos.putNextEntry(new ZipEntry("a"));
            zos.write(new byte[] { 1, 2, 3 });
            zos.setComment("Random comment with XXXX weird characters. There must be enough "
                    + "characters to survive skipping back the EOCD size.");
        } finally {
            zos.close();
        }

        byte zipBytes[] = Files.toByteArray(zip);
        boolean didX4 = false;
        for (int i = 0; i < zipBytes.length - 3; i++) {
            boolean x4 = true;
            for (int j = 0; j < 4; j++) {
                if (zipBytes[i + j] != 'X') {
                    x4 = false;
                    break;
                }
            }

            if (x4) {
                zipBytes[i] = (byte) 0x50;
                zipBytes[i + 1] = (byte) 0x4b;
                zipBytes[i + 2] = (byte) 0x05;
                zipBytes[i + 3] = (byte) 0x06;
                didX4 = true;
                break;
            }
        }

        assertTrue(didX4);

        Files.write(zipBytes, zip);

        ZFile zf = new ZFile(zip);
        assertEquals(1, zf.entries().size());
        StoredEntry a = zf.get("a");
        assertNotNull(a);
        assertArrayEquals(new byte[] { 1, 2, 3 }, a.read());

    }

    @Test
    public void addFileRecursively() throws Exception {
        File tdir = mTemporaryFolder.newFolder();
        File tfile = new File(tdir, "blah-blah");
        Files.write("blah", tfile, Charsets.US_ASCII);

        File zip = new File(tdir, "f.zip");
        ZFile zf = new ZFile(zip);
        zf.addAllRecursively(tfile, new Function<File, CompressionMethod>() {
            @Override
            public CompressionMethod apply(File input) {
                return CompressionMethod.DEFLATE;
            }
        });

        StoredEntry blahEntry = zf.get("blah-blah");
        assertNotNull(blahEntry);
        String contents = new String(blahEntry.read(), Charsets.US_ASCII);
        assertEquals("blah", contents);
        zf.close();
    }

    @Test
    public void addDirectoryRecursively() throws Exception {
        File tdir = mTemporaryFolder.newFolder();

        String boom = Strings.repeat("BOOM!", 100);
        String kaboom = Strings.repeat("KABOOM!", 100);

        Files.write(boom, new File(tdir, "danger"), Charsets.US_ASCII);
        Files.write(kaboom, new File(tdir, "do not touch"), Charsets.US_ASCII);
        File safeDir = new File(tdir, "safe");
        assertTrue(safeDir.mkdir());

        String iLoveChocolate = Strings.repeat("I love chocolate! ", 200);
        String iLoveOrange = Strings.repeat("I love orange! ", 50);
        String loremIpsum = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Aenean vitae "
                + "turpis quis justo scelerisque vulputate in et magna. Suspendisse eleifend "
                + "ultricies nisi, placerat consequat risus accumsan et. Pellentesque habitant "
                + "morbi tristique senectus et netus et malesuada fames ac turpis egestas. "
                + "Integer vitae leo purus. Nulla facilisi. Duis ligula libero, lacinia a "
                + "malesuada a, viverra tempor sapien. Donec eget consequat sapien, ultrices"
                + "interdum diam. Maecenas ipsum erat, suscipit at iaculis a, mollis nec risus. "
                + "Quisque tristique ac velit sed auctor. Nulla lacus diam, tristique id sem non, "
                + "pellentesque commodo mauris.";

        Files.write(iLoveChocolate, new File(safeDir, "eat.sweet"), Charsets.US_ASCII);
        Files.write(iLoveOrange, new File(safeDir, "eat.fruit"), Charsets.US_ASCII);
        Files.write(loremIpsum, new File(safeDir, "bedtime.reading.txt"), Charsets.US_ASCII);

        File zip = new File(tdir, "f.zip");
        ZFile zf = new ZFile(zip);
        zf.addAllRecursively(tdir, new Function<File, CompressionMethod>() {
            @Override
            public CompressionMethod apply(File input) {
                if (input.getName().startsWith("eat.")) {
                    return CompressionMethod.STORE;
                } else {
                    return CompressionMethod.DEFLATE;
                }
            }
        });

        assertEquals(6, zf.entries().size());

        StoredEntry boomEntry = zf.get("danger");
        assertNotNull(boomEntry);
        assertEquals(CompressionMethod.DEFLATE, boomEntry.getCentralDirectoryHeader().getMethod());
        assertEquals(boom, new String(boomEntry.read(), Charsets.US_ASCII));

        StoredEntry kaboomEntry = zf.get("do not touch");
        assertNotNull(kaboomEntry);
        assertEquals(CompressionMethod.DEFLATE,
                kaboomEntry.getCentralDirectoryHeader().getMethod());
        assertEquals(kaboom, new String(kaboomEntry.read(), Charsets.US_ASCII));

        StoredEntry safeEntry = zf.get("safe/");
        assertNotNull(safeEntry);
        assertEquals(0, safeEntry.read().length);

        StoredEntry chocolateEntry = zf.get("safe/eat.sweet");
        assertNotNull(chocolateEntry);
        assertEquals(CompressionMethod.STORE,
                chocolateEntry.getCentralDirectoryHeader().getMethod());
        assertEquals(iLoveChocolate, new String(chocolateEntry.read(), Charsets.US_ASCII));

        StoredEntry orangeEntry = zf.get("safe/eat.fruit");
        assertNotNull(orangeEntry);
        assertEquals(CompressionMethod.STORE,
                orangeEntry.getCentralDirectoryHeader().getMethod());
        assertEquals(iLoveOrange, new String(orangeEntry.read(), Charsets.US_ASCII));

        StoredEntry loremIpsumEntry = zf.get("safe/bedtime.reading.txt");
        assertNotNull(loremIpsumEntry);
        assertEquals(CompressionMethod.DEFLATE,
                loremIpsumEntry.getCentralDirectoryHeader().getMethod());
        assertEquals(loremIpsum, new String(loremIpsumEntry.read(), Charsets.US_ASCII));

        zf.close();
    }

    @Test
    public void extraDirectoryOffsetEmptyFile() throws Exception {
        File zipNoOffsetFile = new File(mTemporaryFolder.getRoot(), "a.zip");
        File zipWithOffsetFile = new File(mTemporaryFolder.getRoot(), "b.zip");

        ZFile zipNoOffset = new ZFile(zipNoOffsetFile);
        ZFile zipWithOffset = new ZFile(zipWithOffsetFile);
        zipWithOffset.setExtraDirectoryOffset(31);

        zipNoOffset.close();
        zipWithOffset.close();

        long zipNoOffsetSize = zipNoOffsetFile.length();
        long zipWithOffsetSize = zipWithOffsetFile.length();

        assertEquals(zipNoOffsetSize + 31, zipWithOffsetSize);

        /*
         * EOCD with no comment has 22 bytes.
         */
        assertEquals(0, zipNoOffset.getCentralDirectoryOffset());
        assertEquals(0, zipNoOffset.getCentralDirectorySize());
        assertEquals(0, zipNoOffset.getEocdOffset());
        assertEquals(22, zipNoOffset.getEocdSize());
        assertEquals(31, zipWithOffset.getCentralDirectoryOffset());
        assertEquals(0, zipWithOffset.getCentralDirectorySize());
        assertEquals(31, zipWithOffset.getEocdOffset());
        assertEquals(22, zipWithOffset.getEocdSize());

        /*
         * The EOCDs should not differ up until the end of the Central Directory size and should
         * not differ after the offset
         */
        int p1Start = 0;
        int p1Size = Eocd.F_CD_SIZE.endOffset();
        int p2Start = Eocd.F_CD_OFFSET.endOffset();
        int p2Size = (int) zipNoOffsetSize - p2Start;

        byte[] noOffsetData1 = FileUtils.readSegment(zipNoOffsetFile, p1Start, p1Size);
        byte[] noOffsetData2 = FileUtils.readSegment(zipNoOffsetFile, p2Start, p2Size);
        byte[] withOffsetData1 = FileUtils.readSegment(zipWithOffsetFile, 31, p1Size);
        byte[] withOffsetData2 = FileUtils.readSegment(zipWithOffsetFile, 31 + p2Start, p2Size);

        assertArrayEquals(noOffsetData1, withOffsetData1);
        assertArrayEquals(noOffsetData2, withOffsetData2);

        ZFile readWithOffset = new ZFile(zipWithOffsetFile);
        assertEquals(0, readWithOffset.entries().size());
    }

    @Test
    public void extraDirectoryOffsetNonEmptyFile() throws Exception {
        File zipNoOffsetFile = new File(mTemporaryFolder.getRoot(), "a.zip");
        File zipWithOffsetFile = new File(mTemporaryFolder.getRoot(), "b.zip");

        ZFile zipNoOffset = new ZFile(zipNoOffsetFile);
        ZFile zipWithOffset = new ZFile(zipWithOffsetFile);
        zipWithOffset.setExtraDirectoryOffset(37);

        zipNoOffset.add("x", new ByteArrayEntrySource(new byte[] { 1, 2 }),
                CompressionMethod.DEFLATE);
        zipWithOffset.add("x", new ByteArrayEntrySource(new byte[] { 1, 2 }),
                CompressionMethod.DEFLATE);

        zipNoOffset.close();
        zipWithOffset.close();

        long zipNoOffsetSize = zipNoOffsetFile.length();
        long zipWithOffsetSize = zipWithOffsetFile.length();

        assertEquals(zipNoOffsetSize + 37, zipWithOffsetSize);

        /*
         * Local file header has 30 bytes + name.
         * Central directory entry has 46 bytes + name
         * EOCD with no comment has 22 bytes.
         */
        assertEquals(30 + 1 + 2, zipNoOffset.getCentralDirectoryOffset());
        int cdSize = (int) zipNoOffset.getCentralDirectorySize();
        assertEquals(46 + 1, cdSize);
        assertEquals(30 + 1 + 2 + cdSize, zipNoOffset.getEocdOffset());
        assertEquals(22, zipNoOffset.getEocdSize());
        assertEquals(30 + 1 + 2 + 37, zipWithOffset.getCentralDirectoryOffset());
        assertEquals(cdSize, zipWithOffset.getCentralDirectorySize());
        assertEquals(30 + 1 + 2 + 37 + cdSize, zipWithOffset.getEocdOffset());
        assertEquals(22, zipWithOffset.getEocdSize());

        /*
         * The files should be equal: until the end of the first entry, from the beginning of the
         * central directory until the offset field in the EOCD and after the offset field.
         */
        int p1Start = 0;
        int p1Size = 30 + 1 + 2;
        int p2Start = 30 + 1 + 2;
        int p2Size = cdSize + Eocd.F_CD_SIZE.endOffset();
        int p3Start = p2Start + cdSize + Eocd.F_CD_OFFSET.endOffset();
        int p3Size = 22 - Eocd.F_CD_OFFSET.endOffset();

        byte[] noOffsetData1 = FileUtils.readSegment(zipNoOffsetFile, p1Start, p1Size);
        byte[] noOffsetData2 = FileUtils.readSegment(zipNoOffsetFile, p2Start, p2Size);
        byte[] noOffsetData3 = FileUtils.readSegment(zipNoOffsetFile, p3Start, p3Size);
        byte[] withOffsetData1 = FileUtils.readSegment(zipWithOffsetFile, p1Start, p1Size);
        byte[] withOffsetData2 = FileUtils.readSegment(zipWithOffsetFile, 37 + p2Start, p2Size);
        byte[] withOffsetData3 = FileUtils.readSegment(zipWithOffsetFile, 37 + p3Start, p3Size);

        assertArrayEquals(noOffsetData1, withOffsetData1);
        assertArrayEquals(noOffsetData2, withOffsetData2);
        assertArrayEquals(noOffsetData3, withOffsetData3);

        ZFile readWithOffset = new ZFile(zipWithOffsetFile);
        assertEquals(1, readWithOffset.entries().size());
    }

    @Test
    public void changeExtraDirectoryOffset() throws Exception {
        File zipFile = new File(mTemporaryFolder.getRoot(), "a.zip");

        ZFile zip = new ZFile(zipFile);
        zip.add("x", new ByteArrayEntrySource(new byte[] { 1, 2 }),
                CompressionMethod.DEFLATE);
        zip.close();

        long noOffsetSize = zipFile.length();

        zip.setExtraDirectoryOffset(177);
        zip.close();

        long withOffsetSize = zipFile.length();

        assertEquals(noOffsetSize + 177, withOffsetSize);
    }

    @Test
    public void computeOffsetWhenReadingEmptyFile() throws Exception {
        File zipFile = new File(mTemporaryFolder.getRoot(), "a.zip");

        ZFile zip = new ZFile(zipFile);
        zip.setExtraDirectoryOffset(18);
        zip.close();

        zip = new ZFile(zipFile);
        assertEquals(18, zip.getExtraDirectoryOffset());

        zip.close();
    }

    @Test
    public void computeOffsetWhenReadingNonEmptyFile() throws Exception {
        File zipFile = new File(mTemporaryFolder.getRoot(), "a.zip");

        ZFile zip = new ZFile(zipFile);
        zip.setExtraDirectoryOffset(287);
        zip.add("x", new ByteArrayEntrySource(new byte[] { 1, 2 }),
                CompressionMethod.DEFLATE);
        zip.close();

        zip = new ZFile(zipFile);
        assertEquals(287, zip.getExtraDirectoryOffset());

        zip.close();
    }

    @Test
    public void obtainingCDAndEocdWhenEntriesWrittenOnEmptyZip() throws Exception {
        File zipFile = new File(mTemporaryFolder.getRoot(), "a.zip");

        final byte[][] cd = new byte[1][];
        final byte[][] eocd = new byte[1][];

        final ZFile zip = new ZFile(zipFile);
        zip.addZFileExtension(new ZFileExtension() {
            @Override
            public void entriesWritten() throws IOException {
                cd[0] = zip.getCentralDirectoryBytes();
                eocd[0] = zip.getEocdBytes();
            }
        });

        zip.close();

        assertNotNull(cd[0]);
        assertEquals(0, cd[0].length);
        assertNotNull(eocd[0]);
        assertEquals(22, eocd[0].length);
    }

    @Test
    public void obtainingCDAndEocdWhenEntriesWrittenOnNonEmptyZip() throws Exception {
        File zipFile = new File(mTemporaryFolder.getRoot(), "a.zip");

        final byte[][] cd = new byte[1][];
        final byte[][] eocd = new byte[1][];

        final ZFile zip = new ZFile(zipFile);
        zip.add("foo", new ByteArrayEntrySource(new byte[0]), CompressionMethod.DEFLATE);
        zip.addZFileExtension(new ZFileExtension() {
            @Override
            public void entriesWritten() throws IOException {
                cd[0] = zip.getCentralDirectoryBytes();
                eocd[0] = zip.getEocdBytes();
            }
        });

        zip.close();

        /*
         * Central directory entry has 46 bytes + name
         * EOCD with no comment has 22 bytes.
         */
        assertNotNull(cd[0]);
        assertEquals(46 + 3, cd[0].length);
        assertNotNull(eocd[0]);
        assertEquals(22, eocd[0].length);
    }
}
