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
import static org.junit.Assert.assertTrue;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.testutils.TestUtils;
import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;

import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class ZipToolsTestCase {
    @Nullable
    private String mZipFile;

    @Nullable
    private List<String> mUnzipCommand;

    @Nullable
    private String mUnzipLineRegex;

    private int mUnzipRegexNameGroup;

    private int mUnzipRegexSizeGroup;

    private boolean mToolStoresDirectories;

    @Rule
    @NonNull
    public TemporaryFolder mTemporaryFolder = new TemporaryFolder();

    protected void configure(@NonNull String zipFile, @NonNull String unzipCommand[],
            @NonNull String unzipLineRegex, int nameGroup, int sizeGroup,
            boolean toolStoresDirectories) {
        mZipFile = zipFile;
        mUnzipCommand = Arrays.asList(unzipCommand);
        mUnzipLineRegex = unzipLineRegex;
        mUnzipRegexNameGroup = nameGroup;
        mUnzipRegexSizeGroup = sizeGroup;
        mToolStoresDirectories = toolStoresDirectories;
    }

    private File rsrcFile(@NonNull String name) {
        File packagingRoot = TestUtils.getRoot("packaging");
        String rsrcPath = packagingRoot.getAbsolutePath() + "/" + name;
        File rsrcFile = new File(rsrcPath);
        return rsrcFile;
    }

    private File cloneZipFile() throws Exception {
        File zfile = mTemporaryFolder.newFile("file.zip");
        Files.copy(rsrcFile(mZipFile), zfile);
        return zfile;
    }

    private void assertFileInZip(@NonNull ZFile zfile, @NonNull String name) throws Exception {
        StoredEntry root = zfile.get(name);
        assertNotNull(root);

        InputStream is = root.open();
        byte[] inZipData = ByteStreams.toByteArray(is);
        is.close();

        byte[] inFileData = Files.toByteArray(rsrcFile(name));
        assertArrayEquals(inFileData, inZipData);
    }

    @Test
    public void zfileReadsZipFile() throws Exception {
        ZFile zf = new ZFile(cloneZipFile());

        if (mToolStoresDirectories) {
            assertEquals(6, zf.entries().size());
        } else {
            assertEquals(4, zf.entries().size());
        }

        assertFileInZip(zf, "root");
        assertFileInZip(zf, "images/lena.png");
        assertFileInZip(zf, "text-files/rfc2460.txt");
        assertFileInZip(zf, "text-files/wikipedia.html");
    }

    @Test
    public void toolReadsZfFile() throws Exception {
        testReadZFile(false);
    }

    @Test
    public void toolReadsAlignedZfFile() throws Exception {
        testReadZFile(true);
    }

    private void testReadZFile(boolean align) throws Exception {
        String unzipcmd = mUnzipCommand.get(0);
        Assume.assumeTrue(new File(unzipcmd).canExecute());

        File zfile = new File (mTemporaryFolder.getRoot(), "zfile.zip");
        ZFile zf = new ZFile(zfile);

        if (align) {
            zf.getAlignmentRules().add(new AlignmentRule(Pattern.compile(".*"), 500));
        }

        zf.add("root", new FileEntrySource(rsrcFile("root")), CompressionMethod.DEFLATE);
        zf.add("images/", new ByteArrayEntrySource(new byte[0]), CompressionMethod.DEFLATE);
        zf.add("images/lena.png", new FileEntrySource(rsrcFile("images/lena.png")),
                CompressionMethod.DEFLATE);
        zf.add("text-files/", new ByteArrayEntrySource(new byte[0]), CompressionMethod.DEFLATE);
        zf.add("text-files/rfc2460.txt", new FileEntrySource(rsrcFile("text-files/rfc2460.txt")),
                CompressionMethod.DEFLATE);
        zf.add("text-files/wikipedia.html",
                new FileEntrySource(rsrcFile("text-files/wikipedia.html")),
                CompressionMethod.DEFLATE);
        zf.close();

        List<String> command = Lists.newArrayList(mUnzipCommand);
        command.add(zfile.getAbsolutePath());
        ProcessBuilder pb = new ProcessBuilder(command);
        Process proc = pb.start();
        InputStream is = proc.getInputStream();
        byte output[] = ByteStreams.toByteArray(is);
        String text = new String(output, Charsets.US_ASCII);
        String lines[] = text.split("\n");
        Map<String, Integer> sizes = Maps.newHashMap();
        for (String l : lines) {
            Matcher m = Pattern.compile(mUnzipLineRegex).matcher(l);
            if (m.matches()) {
                String sizeTxt = m.group(mUnzipRegexSizeGroup);
                int size = Integer.parseInt(sizeTxt);
                String name = m.group(mUnzipRegexNameGroup);
                sizes.put(name, size);
            }
        }

        assertEquals(6, sizes.size());
        assertTrue(sizes.containsKey("images/"));
        assertEquals(0, sizes.get("images/").intValue());
        assertTrue(sizes.containsKey("text-files/"));
        assertEquals(0, sizes.get("text-files/").intValue());
        assertTrue(sizes.containsKey("root"));
        assertEquals(rsrcFile("root").length(), (long) sizes.get("root"));
        assertEquals(rsrcFile("images/lena.png").length(), (long) sizes.get("images/lena.png"));
        assertEquals(rsrcFile("text-files/rfc2460.txt").length(), (long) sizes.get(
                "text-files/rfc2460.txt"));
        assertEquals(rsrcFile("text-files/wikipedia.html").length(), (long) sizes.get(
                "text-files/wikipedia.html"));
    }
}
