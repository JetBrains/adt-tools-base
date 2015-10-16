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

package com.android.build.gradle.internal.transforms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.PackagingOptions;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;

/**
 * Tests for {@link FileFilter}
 */
public class FileFilterTest {

    @Mock
    PackagingOptions packagingOptions;

    FileFilter fileFilter;

    private static File sMergedFolder;
    private static File sExpandedJar1, sExpandedJar2, sExpandedJar3;
    private static List<FileFilter.SubStream> sPackagedJarExpansionSubStreams;

    /**
     * Create temporary folders to simulate expanded folders with java resources embedded
     */
    @BeforeClass
    public static void prepareFolders() throws IOException {
        File rootTmpFolder = createTmpFolder(null /* parent */);

        sExpandedJar1 = createTmpFolder(rootTmpFolder);
        sExpandedJar2 = createTmpFolder(rootTmpFolder);
        sExpandedJar3 = createTmpFolder(rootTmpFolder);

        sPackagedJarExpansionSubStreams = ImmutableList.of(
                new FileFilter.SubStream(sExpandedJar1, "sExpandedJar1"),
                new FileFilter.SubStream(sExpandedJar2, "sExpandedJar2"),
                new FileFilter.SubStream(sExpandedJar3, "sExpandedJar3")
        );

        sMergedFolder = createTmpFolder(null /* parent */);
    }

    /**
     * After each test, all folders are cleaned.
     */
    @After
    public void cleanUp() {
        cleanContents(sExpandedJar1);
        cleanContents(sExpandedJar2);
        cleanContents(sExpandedJar3);
        cleanContents(sMergedFolder);
    }

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        assertTrue(listFiles(sMergedFolder).length == 0);
    }

    @Test
    public void testSimpleCopy() throws IOException {
        fileFilter = new FileFilter(
                sPackagedJarExpansionSubStreams,
                packagingOptions);


        assertFalse(new File(sMergedFolder, "foo/text.properties").exists());
        File changedFile = createFile(sExpandedJar1, "foo/text.properties");
        fileFilter.handleChanged(sMergedFolder, changedFile);
        assertTrue(new File(sMergedFolder, "foo/text.properties").exists());
    }

    @Test
    public void testSimpleExclusion() throws IOException {
        when(packagingOptions.getExcludes()).thenReturn(
                ImmutableSet.of(FileUtils.join("foo", "text.properties")));
        fileFilter = new FileFilter(
                sPackagedJarExpansionSubStreams,
                packagingOptions);

        fileFilter.handleChanged(sMergedFolder, createFile(sExpandedJar1, "foo/text.properties"));
        assertTrue(listFiles(sMergedFolder).length == 0);
    }

    @Test
    public void testExclusionFromMultipleFiles() throws IOException {
        when(packagingOptions.getExcludes()).thenReturn(
                ImmutableSet.of(FileUtils.join("foo", "text.properties")));
        fileFilter = new FileFilter(
                sPackagedJarExpansionSubStreams,
                packagingOptions);

        fileFilter.handleChanged(sMergedFolder, createFile(sExpandedJar1, "foo/text.properties"));
        fileFilter.handleChanged(sMergedFolder, createFile(sExpandedJar2, "foo/text.properties"));
        assertTrue(listFiles(sMergedFolder).length == 0);
    }

    @Test
    public void testMultipleExclusions() throws IOException {
        when(packagingOptions.getExcludes()).thenReturn(
                ImmutableSet.of(
                        FileUtils.join("foo", "text.properties"),
                        FileUtils.join("bar", "other.properties")));
        fileFilter = new FileFilter(
                sPackagedJarExpansionSubStreams,
                packagingOptions);

        fileFilter.handleChanged(sMergedFolder, createFile(sExpandedJar1, "foo/text.properties"));
        fileFilter.handleChanged(sMergedFolder, createFile(sExpandedJar2, "bar/other.properties"));
        assertTrue(listFiles(sMergedFolder).length == 0);
    }

    @Test
    public void textNonExclusion() throws IOException {
        when(packagingOptions.getExcludes()).thenReturn(
                ImmutableSet.of(
                        FileUtils.join("foo", "text.properties"),
                        FileUtils.join("bar", "other.properties")));
        fileFilter = new FileFilter(
                sPackagedJarExpansionSubStreams,
                packagingOptions);

        fileFilter.handleChanged(sMergedFolder, createFile(sExpandedJar1, "foo/text.properties"));
        fileFilter.handleChanged(sMergedFolder, createFile(sExpandedJar2, "bar/other.properties"));
        // this one should be copied over.
        fileFilter.handleChanged(sMergedFolder, createFile(sExpandedJar2, "bar/foo.properties"));
        assertTrue(listFiles(sMergedFolder).length == 1);
        assertTrue(new File(sMergedFolder, "bar/foo.properties").exists());
    }

    @Test
    public void testSingleMerge() throws IOException {
        when(packagingOptions.getMerges()).thenReturn(ImmutableSet.of(
                FileUtils.join("foo", "text.properties")));
        fileFilter = new FileFilter(
                sPackagedJarExpansionSubStreams,
                packagingOptions);

        createFile(sExpandedJar1, "foo/text.properties", "one");
        File secondFile = createFile(sExpandedJar2, "foo/text.properties", "two");

        // one has changed...
        fileFilter.handleChanged(sMergedFolder, secondFile);

        File mergedFile = new File(sMergedFolder, "foo/text.properties");
        assertTrue(mergedFile.exists());
        assertContentInAnyOrder(
                Files.asCharSource(mergedFile, Charset.defaultCharset()).read()
                , ImmutableList.of("one", "two"));
    }

    @Test
    public void testMultipleMerges() throws IOException {
        when(packagingOptions.getMerges()).thenReturn(ImmutableSet.of(
                FileUtils.join("foo" , "text.properties")));
        fileFilter = new FileFilter(
                sPackagedJarExpansionSubStreams,
                packagingOptions);

        createFile(sExpandedJar1, "foo/text.properties", "one");
        File secondFile = createFile(sExpandedJar2, "foo/text.properties", "two");
        createFile(sExpandedJar3, "foo/text.properties", "three");

        // one has changed...
        fileFilter.handleChanged(sMergedFolder, secondFile);

        File mergedFile = new File(sMergedFolder, "foo/text.properties");
        assertTrue(mergedFile.exists());
        assertContentInAnyOrder(
                Files.asCharSource(mergedFile, Charset.defaultCharset()).read()
                , ImmutableList.of("one", "two", "three"));
    }

    @Test
    public void testMergeAddon() throws IOException {
        when(packagingOptions.getMerges()).thenReturn(ImmutableSet.of(
                FileUtils.join("foo", "text.properties")));
        fileFilter = new FileFilter(
                sPackagedJarExpansionSubStreams,
                packagingOptions);

        createFile(sExpandedJar1, "foo/text.properties", "one");
        File secondFile = createFile(sExpandedJar2, "foo/text.properties", "two");

        // simulate one has changed to create initial version
        fileFilter.handleChanged(sMergedFolder, secondFile);

        File mergedFile = new File(sMergedFolder, "foo/text.properties");
        assertTrue(mergedFile.exists());
        assertContentInAnyOrder(
                Files.asCharSource(mergedFile, Charset.defaultCharset()).read()
                , ImmutableList.of("one", "two"));

        // add a new one.
        File thirdFile = createFile(sExpandedJar3, "foo/text.properties", "three");
        fileFilter.handleChanged(sMergedFolder, thirdFile);

        assertTrue(mergedFile.exists());
        assertContentInAnyOrder(
                Files.asCharSource(mergedFile, Charset.defaultCharset()).read()
                , ImmutableList.of("one", "two", "three"));
    }

    @Test
    public void testMergeUpdate() throws IOException {
        when(packagingOptions.getMerges()).thenReturn(ImmutableSet.of(
                FileUtils.join("foo", "text.properties")));
        fileFilter = new FileFilter(
                sPackagedJarExpansionSubStreams,
                packagingOptions);

        createFile(sExpandedJar1, "foo/text.properties", "one");
        File secondFile = createFile(sExpandedJar2, "foo/text.properties", "two");
        createFile(sExpandedJar3, "foo/text.properties", "three");

        // simulate one has changed to create initial version
        fileFilter.handleChanged(sMergedFolder, secondFile);

        File mergedFile = new File(sMergedFolder, "foo/text.properties");
        assertTrue(mergedFile.exists());
        assertContentInAnyOrder(
                Files.asCharSource(mergedFile, Charset.defaultCharset()).read()
                , ImmutableList.of("one", "two", "three"));

        // change one...
        assertTrue(secondFile.delete());
        secondFile = createFile(sExpandedJar2, "foo/text.properties", "deux");

        fileFilter.handleChanged(sMergedFolder, secondFile);

        assertTrue(mergedFile.exists());
        assertContentInAnyOrder(
                Files.asCharSource(mergedFile, Charset.defaultCharset()).read()
                , ImmutableList.of("one", "deux", "three"));
    }

    @Test
    public void testMergeRemoval() throws IOException {
        when(packagingOptions.getMerges()).thenReturn(ImmutableSet.of(
                FileUtils.join("foo", "text.properties")));
        fileFilter = new FileFilter(
                sPackagedJarExpansionSubStreams,
                packagingOptions);

        createFile(sExpandedJar1, "foo/text.properties", "one");
        File secondFile = createFile(sExpandedJar2, "foo/text.properties", "two");
        createFile(sExpandedJar3, "foo/text.properties", "three");

        // simulate one has changed to create initial version
        fileFilter.handleChanged(sMergedFolder, secondFile);

        File mergedFile = new File(sMergedFolder, "foo/text.properties");
        assertTrue(mergedFile.exists());
        assertContentInAnyOrder(
                Files.asCharSource(mergedFile, Charset.defaultCharset()).read()
                , ImmutableList.of("one", "two", "three"));

        // remove one...
        assertTrue(secondFile.delete());

        fileFilter.handleRemoved(sMergedFolder, FileUtils.join("foo", "text.properties"));

        assertTrue(mergedFile.exists());
        assertContentInAnyOrder(
                Files.asCharSource(mergedFile, Charset.defaultCharset()).read()
                , ImmutableList.of("one", "three"));
    }

    @Test
    public void testPickFirst() throws IOException {
        when(packagingOptions.getPickFirsts()).thenReturn(ImmutableSet.of("foo/text.properties"));
        fileFilter = new FileFilter(
                sPackagedJarExpansionSubStreams,
                packagingOptions);

        // simulate the three elements were added.
        fileFilter.handleChanged(sMergedFolder,
                createFile(sExpandedJar1, "foo/text.properties", "one"));
        fileFilter.handleChanged(sMergedFolder,
                createFile(sExpandedJar2, "foo/text.properties", "two"));
        fileFilter.handleChanged(sMergedFolder,
                createFile(sExpandedJar3, "foo/text.properties", "three"));

        File mergedFile = new File(sMergedFolder, "foo/text.properties");
        assertTrue(mergedFile.exists());
        String mergedContent = Files.asCharSource(mergedFile, Charset.defaultCharset()).read();
        assertTrue(mergedContent.equals("one")
                || mergedContent.equals("two")
                || mergedContent.equals("three"));
    }

    @Test
    public void testPickFirstUpdate() throws IOException {
        when(packagingOptions.getPickFirsts()).thenReturn(ImmutableSet.of("foo/text.properties"));
        fileFilter = new FileFilter(
                sPackagedJarExpansionSubStreams,
                packagingOptions);

        File firstFile = createFile(sExpandedJar1, "foo/text.properties", "one");
        File secondFile = createFile(sExpandedJar2, "foo/text.properties", "two");
        File thirdFile = createFile(sExpandedJar3, "foo/text.properties", "three");

        // simulate the three elements were added.
        fileFilter.handleChanged(sMergedFolder, firstFile);
        fileFilter.handleChanged(sMergedFolder, secondFile);
        fileFilter.handleChanged(sMergedFolder, thirdFile);

        File mergedFile = new File(sMergedFolder, "foo/text.properties");
        assertTrue(mergedFile.exists());
        String mergedContent = Files.asCharSource(mergedFile, Charset.defaultCharset()).read();
        if (mergedContent.equals("one")) {
            assertTrue(firstFile.delete());
            createFile(sExpandedJar1, "foo/text.properties", "un");
            fileFilter.handleChanged(sMergedFolder, firstFile);
            assertEquals("un", Files.asCharSource(mergedFile, Charset.defaultCharset()).read());
        }
        if (mergedContent.equals("two")) {
            assertTrue(thirdFile.delete());
            createFile(sExpandedJar2, "foo/text.properties", "deux");
            fileFilter.handleChanged(sMergedFolder, secondFile);
            assertEquals("deux", Files.asCharSource(mergedFile, Charset.defaultCharset()).read());
        }
        if (mergedContent.equals("three")) {
            assertTrue(thirdFile.delete());
            createFile(sExpandedJar3, "foo/text.properties", "trois");
            fileFilter.handleChanged(sMergedFolder, thirdFile);
            assertEquals("trois", Files.asCharSource(mergedFile, Charset.defaultCharset()).read());
        }
    }

    @Test
    public void testPickFirstRemoval() throws IOException {
        when(packagingOptions.getPickFirsts()).thenReturn(ImmutableSet.of(
                FileUtils.join("foo", "text.properties")));
        fileFilter = new FileFilter(
                sPackagedJarExpansionSubStreams,
                packagingOptions);

        File firstFile = createFile(sExpandedJar1, "foo/text.properties", "one");
        File secondFile = createFile(sExpandedJar2, "foo/text.properties", "two");
        File thirdFile = createFile(sExpandedJar3, "foo/text.properties", "three");

        // simulate the three elements were added.
        fileFilter.handleChanged(sMergedFolder, firstFile);
        fileFilter.handleChanged(sMergedFolder, secondFile);
        fileFilter.handleChanged(sMergedFolder, thirdFile);

        File mergedFile = new File(sMergedFolder, "foo/text.properties");
        assertTrue(mergedFile.exists());
        String mergedContent = Files.asCharSource(mergedFile, Charset.defaultCharset()).read();
        if (mergedContent.equals("one")) {
            assertTrue(firstFile.delete());
            fileFilter.handleRemoved(sMergedFolder, "foo/text.properties");
            mergedContent = Files.asCharSource(mergedFile, Charset.defaultCharset()).read();
            assertTrue(mergedContent.equals("two") || mergedContent.equals("three"));
        }
        if (mergedContent.equals("two")) {
            assertTrue(thirdFile.delete());
            fileFilter.handleRemoved(sMergedFolder, "foo/text.properties");
            mergedContent = Files.asCharSource(mergedFile, Charset.defaultCharset()).read();
            assertTrue(mergedContent.equals("one") || mergedContent.equals("three"));
        }
        if (mergedContent.equals("three")) {
            assertTrue(thirdFile.delete());
            fileFilter.handleRemoved(sMergedFolder, "foo/text.properties");
            mergedContent = Files.asCharSource(mergedFile, Charset.defaultCharset()).read();
            assertTrue(mergedContent.equals("one") || mergedContent.equals("two"));
        }
    }

    private static void assertContentInAnyOrder(String content, Iterable<String> subStrings) {
        int length = 0;
        for (String subString : subStrings) {
            length += subString.length();
            assertTrue(content.contains(subString));
        }
        assertEquals(length, content.length());
    }

    private static File createTmpFolder(@Nullable File parent) throws IOException {
        File folder = File.createTempFile("tmp", "dir");
        assertTrue(folder.delete());
        if (parent != null) {
            folder = new File(parent, folder.getName());
        }
        assertTrue(folder.mkdirs());
        return folder;
    }

    @NonNull private static File[] listFiles(@NonNull File folder) {
        File[] files = folder.listFiles();
        if (files == null) {
            return new File[0];
        }
        return files;
    }

    private static void cleanContents(File folder) {
        for (File f : listFiles(folder)) {
            if (f.isDirectory()) {
                cleanContents(f);
            }
            assertTrue(f.delete());
        }
    }

    @NonNull private static File createFile(
            @NonNull File parent,
            @NonNull String archivePath) throws IOException {
        return createFile(parent, archivePath.replace('/', File.separatorChar), null /* content */);
    }

    @NonNull private static File createFile(
            @NonNull File parent,
            @NonNull String archivePath,
            @Nullable String content) throws IOException {

        File newFile = new File(parent, archivePath);
        if (!newFile.getParentFile().exists()) {
            assertTrue(newFile.getParentFile().mkdirs());
        }
        String fileContent = content == null ? "test!" : content;
        Files.append(fileContent, newFile, Charset.defaultCharset());
        return newFile;
    }
}
