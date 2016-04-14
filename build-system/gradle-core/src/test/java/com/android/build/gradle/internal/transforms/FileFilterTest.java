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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.dsl.PackagingOptions;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;

/**
 * Tests for {@link FileFilter}
 */
public class FileFilterTest {

    @Rule
    public TemporaryFolder tmpFolder = new TemporaryFolder();

    private PackagingOptions mPackagingOptions;
    private FileFilter mFileFilter;
    private File mMergedFolder;
    private File mExpandedJar1, mExpandedJar2, mExpandedJar3;
    private List<FileFilter.SubStream> mPackagedJarExpansionSubStreams;

    /**
     * Create temporary folders to simulate expanded folders with java resources embedded
     */
    @Before
    public void prepareFolders() throws IOException {
        mExpandedJar1 = tmpFolder.newFolder("jar1");
        mExpandedJar2 = tmpFolder.newFolder("jar2");
        mExpandedJar3 = tmpFolder.newFolder("jar3");

        mPackagedJarExpansionSubStreams = ImmutableList.of(
                new FileFilter.SubStream(mExpandedJar1, "sExpandedJar1"),
                new FileFilter.SubStream(mExpandedJar2, "sExpandedJar2"),
                new FileFilter.SubStream(mExpandedJar3, "sExpandedJar3")
        );

        mMergedFolder = tmpFolder.newFolder("merged");

        assertMergedFilesCount(0);
    }

    @Before
    public void createPackagingOptions() {
        mPackagingOptions = new PackagingOptions();
    }

    private void assertMergedFilesCount(int i) {
        File[] files = mMergedFolder.listFiles();
        assertNotNull(files);
        assertTrue(files.length == i);
    }

    @Test
    public void testSimpleCopy() throws IOException {
        mFileFilter = new FileFilter(
                mPackagedJarExpansionSubStreams,
                mPackagingOptions);


        assertFalse(new File(mMergedFolder, "foo/text.properties").exists());
        File changedFile = createFile(mExpandedJar1, "foo/text.properties");
        mFileFilter.handleChanged(mMergedFolder, changedFile);
        assertTrue(new File(mMergedFolder, "foo/text.properties").exists());
    }

    @Test
    public void testSimpleExclusion() throws IOException {
        setExcludes(ImmutableSet.of(FileUtils.join("foo", "text.properties")));
        mFileFilter = new FileFilter(
                mPackagedJarExpansionSubStreams,
                mPackagingOptions);

        mFileFilter.handleChanged(mMergedFolder, createFile(mExpandedJar1, "foo/text.properties"));
        assertMergedFilesCount(0);
    }

    @Test
    public void testExclusionFromMultipleFiles() throws IOException {
        setExcludes(ImmutableSet.of(FileUtils.join("foo", "text.properties")));
        mFileFilter = new FileFilter(
                mPackagedJarExpansionSubStreams,
                mPackagingOptions);

        mFileFilter.handleChanged(mMergedFolder, createFile(mExpandedJar1, "foo/text.properties"));
        mFileFilter.handleChanged(mMergedFolder, createFile(mExpandedJar2, "foo/text.properties"));
        assertMergedFilesCount(0);
    }

    @Test
    public void testMultipleExclusions() throws IOException {
        setExcludes(ImmutableSet.of(
                FileUtils.join("foo", "text.properties"),
                FileUtils.join("bar", "other.properties")));
        mFileFilter = new FileFilter(
                mPackagedJarExpansionSubStreams,
                mPackagingOptions);

        mFileFilter.handleChanged(mMergedFolder, createFile(mExpandedJar1, "foo/text.properties"));
        mFileFilter.handleChanged(mMergedFolder, createFile(mExpandedJar2, "bar/other.properties"));
        assertMergedFilesCount(0);
    }

    @Test
    public void textNonExclusion() throws IOException {
        setExcludes(ImmutableSet.of(
                FileUtils.join("foo", "text.properties"),
                FileUtils.join("bar", "other.properties")));
        mFileFilter = new FileFilter(
                mPackagedJarExpansionSubStreams,
                mPackagingOptions);

        mFileFilter.handleChanged(mMergedFolder, createFile(mExpandedJar1, "foo/text.properties"));
        mFileFilter.handleChanged(mMergedFolder, createFile(mExpandedJar2, "bar/other.properties"));
        // this one should be copied over.
        mFileFilter.handleChanged(mMergedFolder, createFile(mExpandedJar2, "bar/foo.properties"));
        assertMergedFilesCount(1);
        assertTrue(new File(mMergedFolder, "bar/foo.properties").exists());
    }

    @Test
    public void testSingleMerge() throws IOException {
        setMerges(ImmutableSet.of(
                FileUtils.join("foo", "text.properties")));
        mFileFilter = new FileFilter(
                mPackagedJarExpansionSubStreams,
                mPackagingOptions);

        createFile(mExpandedJar1, "foo/text.properties", "one");
        File secondFile = createFile(mExpandedJar2, "foo/text.properties", "two");

        // one has changed...
        mFileFilter.handleChanged(mMergedFolder, secondFile);

        File mergedFile = new File(mMergedFolder, "foo/text.properties");
        assertTrue(mergedFile.exists());
        assertContentInAnyOrder(
                Files.asCharSource(mergedFile, Charset.defaultCharset()).read()
                , ImmutableList.of("one", "two"));
    }

    @Test
    public void testMultipleMerges() throws IOException {
        setMerges(ImmutableSet.of(
                FileUtils.join("foo" , "text.properties")));
        mFileFilter = new FileFilter(
                mPackagedJarExpansionSubStreams,
                mPackagingOptions);

        createFile(mExpandedJar1, "foo/text.properties", "one");
        File secondFile = createFile(mExpandedJar2, "foo/text.properties", "two");
        createFile(mExpandedJar3, "foo/text.properties", "three");

        // one has changed...
        mFileFilter.handleChanged(mMergedFolder, secondFile);

        File mergedFile = new File(mMergedFolder, "foo/text.properties");
        assertTrue(mergedFile.exists());
        assertContentInAnyOrder(
                Files.asCharSource(mergedFile, Charset.defaultCharset()).read()
                , ImmutableList.of("one", "two", "three"));
    }

    @Test
    public void testMergeAddon() throws IOException {
        setMerges(ImmutableSet.of(
                FileUtils.join("foo", "text.properties")));
        mFileFilter = new FileFilter(
                mPackagedJarExpansionSubStreams,
                mPackagingOptions);

        createFile(mExpandedJar1, "foo/text.properties", "one");
        File secondFile = createFile(mExpandedJar2, "foo/text.properties", "two");

        // simulate one has changed to create initial version
        mFileFilter.handleChanged(mMergedFolder, secondFile);

        File mergedFile = new File(mMergedFolder, "foo/text.properties");
        assertTrue(mergedFile.exists());
        assertContentInAnyOrder(
                Files.asCharSource(mergedFile, Charset.defaultCharset()).read()
                , ImmutableList.of("one", "two"));

        // add a new one.
        File thirdFile = createFile(mExpandedJar3, "foo/text.properties", "three");
        mFileFilter.handleChanged(mMergedFolder, thirdFile);

        assertTrue(mergedFile.exists());
        assertContentInAnyOrder(
                Files.asCharSource(mergedFile, Charset.defaultCharset()).read()
                , ImmutableList.of("one", "two", "three"));
    }

    @Test
    public void testMergeUpdate() throws IOException {
        setMerges(ImmutableSet.of(
                FileUtils.join("foo", "text.properties")));
        mFileFilter = new FileFilter(
                mPackagedJarExpansionSubStreams,
                mPackagingOptions);

        createFile(mExpandedJar1, "foo/text.properties", "one");
        File secondFile = createFile(mExpandedJar2, "foo/text.properties", "two");
        createFile(mExpandedJar3, "foo/text.properties", "three");

        // simulate one has changed to create initial version
        mFileFilter.handleChanged(mMergedFolder, secondFile);

        File mergedFile = new File(mMergedFolder, "foo/text.properties");
        assertTrue(mergedFile.exists());
        assertContentInAnyOrder(
                Files.asCharSource(mergedFile, Charset.defaultCharset()).read()
                , ImmutableList.of("one", "two", "three"));

        // change one...
        assertTrue(secondFile.delete());
        secondFile = createFile(mExpandedJar2, "foo/text.properties", "deux");

        mFileFilter.handleChanged(mMergedFolder, secondFile);

        assertTrue(mergedFile.exists());
        assertContentInAnyOrder(
                Files.asCharSource(mergedFile, Charset.defaultCharset()).read()
                , ImmutableList.of("one", "deux", "three"));
    }

    @Test
    public void testMergeRemoval() throws IOException {
        setMerges(ImmutableSet.of(
                FileUtils.join("foo", "text.properties")));
        mFileFilter = new FileFilter(
                mPackagedJarExpansionSubStreams,
                mPackagingOptions);

        createFile(mExpandedJar1, "foo/text.properties", "one");
        File secondFile = createFile(mExpandedJar2, "foo/text.properties", "two");
        createFile(mExpandedJar3, "foo/text.properties", "three");

        // simulate one has changed to create initial version
        mFileFilter.handleChanged(mMergedFolder, secondFile);

        File mergedFile = new File(mMergedFolder, "foo/text.properties");
        assertTrue(mergedFile.exists());
        assertContentInAnyOrder(
                Files.asCharSource(mergedFile, Charset.defaultCharset()).read()
                , ImmutableList.of("one", "two", "three"));

        // remove one...
        assertTrue(secondFile.delete());

        mFileFilter.handleRemoved(mMergedFolder, FileUtils.join("foo", "text.properties"));

        assertTrue(mergedFile.exists());
        assertContentInAnyOrder(
                Files.asCharSource(mergedFile, Charset.defaultCharset()).read()
                , ImmutableList.of("one", "three"));
    }

    @Test
    public void testPickFirst() throws IOException {
        setPickFirsts(ImmutableSet.of("foo/text.properties"));
        mFileFilter = new FileFilter(
                mPackagedJarExpansionSubStreams,
                mPackagingOptions);

        // simulate the three elements were added.
        mFileFilter.handleChanged(mMergedFolder,
                createFile(mExpandedJar1, "foo/text.properties", "one"));
        mFileFilter.handleChanged(mMergedFolder,
                createFile(mExpandedJar2, "foo/text.properties", "two"));
        mFileFilter.handleChanged(mMergedFolder,
                createFile(mExpandedJar3, "foo/text.properties", "three"));

        File mergedFile = new File(mMergedFolder, "foo/text.properties");
        assertTrue(mergedFile.exists());
        String mergedContent = Files.asCharSource(mergedFile, Charset.defaultCharset()).read();
        assertTrue(mergedContent.equals("one")
                || mergedContent.equals("two")
                || mergedContent.equals("three"));
    }

    @Test
    public void testPickFirstUpdate() throws IOException {
        setPickFirsts(ImmutableSet.of("foo/text.properties"));
        mFileFilter = new FileFilter(
                mPackagedJarExpansionSubStreams,
                mPackagingOptions);

        File firstFile = createFile(mExpandedJar1, "foo/text.properties", "one");
        File secondFile = createFile(mExpandedJar2, "foo/text.properties", "two");
        File thirdFile = createFile(mExpandedJar3, "foo/text.properties", "three");

        // simulate the three elements were added.
        mFileFilter.handleChanged(mMergedFolder, firstFile);
        mFileFilter.handleChanged(mMergedFolder, secondFile);
        mFileFilter.handleChanged(mMergedFolder, thirdFile);

        File mergedFile = new File(mMergedFolder, "foo/text.properties");
        assertTrue(mergedFile.exists());
        String mergedContent = Files.asCharSource(mergedFile, Charset.defaultCharset()).read();
        if (mergedContent.equals("one")) {
            assertTrue(firstFile.delete());
            createFile(mExpandedJar1, "foo/text.properties", "un");
            mFileFilter.handleChanged(mMergedFolder, firstFile);
            assertEquals("un", Files.asCharSource(mergedFile, Charset.defaultCharset()).read());
        }
        if (mergedContent.equals("two")) {
            assertTrue(thirdFile.delete());
            createFile(mExpandedJar2, "foo/text.properties", "deux");
            mFileFilter.handleChanged(mMergedFolder, secondFile);
            assertEquals("deux", Files.asCharSource(mergedFile, Charset.defaultCharset()).read());
        }
        if (mergedContent.equals("three")) {
            assertTrue(thirdFile.delete());
            createFile(mExpandedJar3, "foo/text.properties", "trois");
            mFileFilter.handleChanged(mMergedFolder, thirdFile);
            assertEquals("trois", Files.asCharSource(mergedFile, Charset.defaultCharset()).read());
        }
    }

    @Test
    public void testPickFirstRemoval() throws IOException {
        setPickFirsts(ImmutableSet.of(
                FileUtils.join("foo", "text.properties")));
        mFileFilter = new FileFilter(
                mPackagedJarExpansionSubStreams,
                mPackagingOptions);

        File firstFile = createFile(mExpandedJar1, "foo/text.properties", "one");
        File secondFile = createFile(mExpandedJar2, "foo/text.properties", "two");
        File thirdFile = createFile(mExpandedJar3, "foo/text.properties", "three");

        // simulate the three elements were added.
        mFileFilter.handleChanged(mMergedFolder, firstFile);
        mFileFilter.handleChanged(mMergedFolder, secondFile);
        mFileFilter.handleChanged(mMergedFolder, thirdFile);

        File mergedFile = new File(mMergedFolder, "foo/text.properties");
        assertTrue(mergedFile.exists());
        String mergedContent = Files.asCharSource(mergedFile, Charset.defaultCharset()).read();
        if (mergedContent.equals("one")) {
            assertTrue(firstFile.delete());
            mFileFilter.handleRemoved(mMergedFolder, "foo/text.properties");
            mergedContent = Files.asCharSource(mergedFile, Charset.defaultCharset()).read();
            assertTrue(mergedContent.equals("two") || mergedContent.equals("three"));
        }
        if (mergedContent.equals("two")) {
            assertTrue(thirdFile.delete());
            mFileFilter.handleRemoved(mMergedFolder, "foo/text.properties");
            mergedContent = Files.asCharSource(mergedFile, Charset.defaultCharset()).read();
            assertTrue(mergedContent.equals("one") || mergedContent.equals("three"));
        }
        if (mergedContent.equals("three")) {
            assertTrue(thirdFile.delete());
            mFileFilter.handleRemoved(mMergedFolder, "foo/text.properties");
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

    private void setPickFirsts(ImmutableSet<String> paths) {
        mPackagingOptions.setPickFirsts(paths);
    }

    private void setMerges(ImmutableSet<String> paths) {
        mPackagingOptions.setMerges(paths);
    }

    private void setExcludes(ImmutableSet<String> paths) {
        mPackagingOptions.setExcludes(paths);
    }
}
