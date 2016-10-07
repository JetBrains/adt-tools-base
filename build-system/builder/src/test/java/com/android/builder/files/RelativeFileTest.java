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

package com.android.builder.files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.common.base.Predicate;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.Set;

/**
 * Tests for {@link RelativeFile} and {@link RelativeFiles}.
 */
public class RelativeFileTest {

    /**
     * Temporary folder to use in tests.
     */
    @Rule
    public TemporaryFolder mTemporaryFolder = new TemporaryFolder();

    /**
     * Finds a file in {@code files} that has the given name (not path).
     *
     * @param files the files to search
     * @param name the name of the file to find
     * @return the found relative file, {@code null} if no file was found
     */
    @Nullable
    private static RelativeFile findFile(@NonNull Set<RelativeFile> files, @NonNull String name) {
        for (RelativeFile rf : files) {
            if (rf.getFile().getName().equals(name)) {
                return rf;
            }
        }

        return null;
    }

    @Test
    public void loadEmptyDirectory() {
        Set<RelativeFile> files = RelativeFiles.fromDirectory(mTemporaryFolder.getRoot());
        assertTrue(files.isEmpty());
    }

    @Test
    public void loadFilesRecursively() throws Exception {
        mTemporaryFolder.newFile("foo");
        mTemporaryFolder.newFile("bar");
        mTemporaryFolder.newFolder("sub");
        mTemporaryFolder.newFile("sub" + File.separator + "file-in-sub");

        Set<RelativeFile> files = RelativeFiles.fromDirectory(mTemporaryFolder.getRoot());
        assertEquals(4, files.size());

        RelativeFile fooFile = findFile(files, "foo");
        assertNotNull(fooFile);
        assertEquals(mTemporaryFolder.getRoot(), fooFile.getBase());
        assertEquals("foo", fooFile.getOsIndependentRelativePath());
        assertTrue(fooFile.getFile().isFile());

        RelativeFile barFile = findFile(files, "bar");
        assertNotNull(barFile);
        assertEquals(mTemporaryFolder.getRoot(), barFile.getBase());
        assertEquals("bar", barFile.getOsIndependentRelativePath());
        assertTrue(barFile.getFile().isFile());

        RelativeFile subFile = findFile(files, "sub");
        assertNotNull(subFile);
        assertEquals(mTemporaryFolder.getRoot(), subFile.getBase());
        assertEquals("sub/", subFile.getOsIndependentRelativePath());
        assertTrue(subFile.getFile().isDirectory());

        RelativeFile fileInSubFile = findFile(files, "file-in-sub");
        assertNotNull(fileInSubFile);
        assertEquals(mTemporaryFolder.getRoot(), fileInSubFile.getBase());
        assertEquals("sub/file-in-sub", fileInSubFile.getOsIndependentRelativePath());
        assertTrue(fileInSubFile.getFile().isFile());
    }

    @Test
    public void fileFilter() throws Exception {
        mTemporaryFolder.newFile("foo");
        mTemporaryFolder.newFolder("dir");
        mTemporaryFolder.newFile("dir" + File.separator + "bar");

        Set<RelativeFile> files =
                RelativeFiles.fromDirectory(mTemporaryFolder.getRoot(), RelativeFiles.IS_FILE);
        assertEquals(2, files.size());

        assertNotNull(findFile(files, "foo"));
        assertNotNull(findFile(files, "bar"));
    }

    @Test
    public void directoryFilter() throws Exception {
        mTemporaryFolder.newFile("foo");
        mTemporaryFolder.newFolder("dir");
        mTemporaryFolder.newFile("dir" + File.separator + "bar");

        Set<RelativeFile> files =
                RelativeFiles.fromDirectory(
                        mTemporaryFolder.getRoot(),
                        relativeFile -> relativeFile.getFile().isDirectory());
        assertEquals(1, files.size());

        assertNotNull(findFile(files, "dir"));
    }

    @Test
    public void relativePathFilter() throws Exception {
        mTemporaryFolder.newFile("foo");
        mTemporaryFolder.newFolder("dir");
        mTemporaryFolder.newFile("dir" + File.separator + "bar");

        Set<RelativeFile> files = RelativeFiles.fromDirectory(mTemporaryFolder.getRoot(),
                RelativeFiles.fromPathPredicate(new Predicate<String>() {
                    @Override
                    public boolean apply(String input) {
                        int slashIdx = input.indexOf('/');
                        return slashIdx == -1 || slashIdx == input.length() - 1;
                    }
                }));

        assertEquals(2, files.size());
        assertNotNull(findFile(files, "foo"));
        assertNotNull(findFile(files, "dir"));
    }

    @Test
    public void relativeFileAcceptsNonExistingFileInBase() throws Exception {
        File existingBase = mTemporaryFolder.newFolder("foo");
        File nonExistingFile = new File(existingBase, "bar");
        @SuppressWarnings("unused")
        RelativeFile unused = new RelativeFile(existingBase, nonExistingFile);
    }

    @Test
    public void relativeFileAcceptsNonExistingFileInNonExistingBase() throws Exception {
        File existingBase = new File(mTemporaryFolder.getRoot(), "foo");
        File nonExistingFile = new File(existingBase, "bar");
        @SuppressWarnings("unused")
        RelativeFile unused = new RelativeFile(existingBase, nonExistingFile);
    }

    @Test
    public void relativeFileNotEqualsIfDifferentFile() throws Exception {
        File base = new File("base");
        File relative = new File(base, "relative");
        RelativeFile rf1 = new RelativeFile(base, relative);
        RelativeFile rf2 = new RelativeFile(base, new File("relative2"));
        assertFalse(rf1.equals(rf2));
    }

    @Test
    public void relativeFileNotEqualsIfDifferentBase() throws Exception {
        File base = new File("base");
        File relative = new File(base, "relative");
        RelativeFile rf1 = new RelativeFile(base, relative);
        File base2 = new File("base2");
        RelativeFile rf2 = new RelativeFile(base2, new File(base2, "relative"));
        assertFalse(rf1.equals(rf2));
    }

    @Test
    public void relativeFileEqualsIfSameBaseAndRelative() throws Exception {
        File base = new File("base");
        File relative = new File(base, "relative");
        RelativeFile rf1 = new RelativeFile(base, relative);
        RelativeFile rf2 = new RelativeFile(base, relative);
        assertTrue(rf1.equals(rf2));
        assertTrue(rf1.hashCode() == rf2.hashCode());
    }

    @Test
    public void relativeFileToString() {
        File base = new File("basic");
        File relative = new File(new File(base, "foo"), "bar");

        String toString = new RelativeFile(base, relative).toString();
        assertTrue(toString.contains("basic"));
        assertTrue(toString.contains("foo/bar"));
    }
}
