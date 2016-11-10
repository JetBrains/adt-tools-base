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
import static org.junit.Assert.assertTrue;

import com.android.builder.internal.packaging.zip.ZFile;
import com.android.ide.common.res2.FileStatus;
import com.google.common.base.Functions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.Closer;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.Collections;

/**
 * Tests for {@link IncrementalRelativeFileSets}.
 */
public class IncrementalRelativeFileSetsTest {

    /**
     * Temporary folder used for tests.
     */
    @Rule
    public TemporaryFolder mTemporaryFolder = new TemporaryFolder();

    @Test
    public void readEmptyDirectory() {
        ImmutableMap<RelativeFile, FileStatus> set = IncrementalRelativeFileSets.fromDirectory(
                mTemporaryFolder.getRoot());

        assertEquals(0, set.size());
    }

    @Test
    public void readDirectory() throws Exception {
        File a = mTemporaryFolder.newFolder("a");
        File ab = new File(a, "ab");
        @SuppressWarnings("unused")
        boolean ignored1 = ab.createNewFile();
        File ac = new File(a, "ac");
        @SuppressWarnings("unused")
        boolean ignored2 = ac.createNewFile();
        File d = mTemporaryFolder.newFile("d");

        RelativeFile expectedB = new RelativeFile(mTemporaryFolder.getRoot(), ab);
        RelativeFile expectedC = new RelativeFile(mTemporaryFolder.getRoot(), ac);
        RelativeFile expectedD = new RelativeFile(mTemporaryFolder.getRoot(), d);

        ImmutableMap<RelativeFile, FileStatus> set = IncrementalRelativeFileSets.fromDirectory(
                mTemporaryFolder.getRoot());

        assertEquals(3, set.size());
        assertTrue(set.containsKey(expectedB));
        assertTrue(set.containsKey(expectedC));
        assertTrue(set.containsKey(expectedD));
        assertEquals(FileStatus.NEW, set.get(expectedB));
        assertEquals(FileStatus.NEW, set.get(expectedC));
        assertEquals(FileStatus.NEW, set.get(expectedD));
    }

    @Test
    public void readEmptyZip() throws Exception {
        File zipFile = new File(mTemporaryFolder.getRoot(), "foo");

        Closer closer = Closer.create();
        try {
            ZFile zf = closer.register(new ZFile(zipFile));
            zf.close();
        } catch (Throwable t) {
            throw closer.rethrow(t);
        } finally {
            closer.close();
        }

        ImmutableMap<RelativeFile, FileStatus> set = IncrementalRelativeFileSets.fromZip(zipFile);

        assertEquals(0, set.size());
    }

    @Test
    public void readZip() throws Exception {
        File zipFile = new File(mTemporaryFolder.getRoot(), "foo");

        RelativeFile expectedB = new RelativeFile(zipFile,
                new File(zipFile, "a" + File.separator + "b"));
        RelativeFile expectedC = new RelativeFile(zipFile,
                new File(zipFile, "a" + File.separator + "c"));
        RelativeFile expectedD = new RelativeFile(zipFile, new File(zipFile, "d"));

        Closer closer = Closer.create();
        try {
            ZFile zf = closer.register(new ZFile(zipFile));
            zf.add("a/", new ByteArrayInputStream(new byte[0]));
            zf.add("a/b", new ByteArrayInputStream(new byte[0]));
            zf.add("a/c", new ByteArrayInputStream(new byte[0]));
            zf.add("d", new ByteArrayInputStream(new byte[0]));
            zf.close();
        } catch (Throwable t) {
            throw closer.rethrow(t);
        } finally {
            closer.close();
        }

        ImmutableMap<RelativeFile, FileStatus> set = IncrementalRelativeFileSets.fromZip(zipFile);

        assertEquals(3, set.size());
        assertTrue(set.containsKey(expectedB));
        assertTrue(set.containsKey(expectedC));
        assertTrue(set.containsKey(expectedD));
        assertEquals(FileStatus.NEW, set.get(expectedB));
        assertEquals(FileStatus.NEW, set.get(expectedC));
        assertEquals(FileStatus.NEW, set.get(expectedD));
    }

    @Test
    public void unionOfEmptySet() throws Exception {
        ImmutableMap<RelativeFile, FileStatus> set = IncrementalRelativeFileSets.union(
                Sets.<ImmutableMap<RelativeFile, FileStatus>>newHashSet());

        assertEquals(0, set.size());
    }

    @Test
    public void unionOfSets() throws Exception {
        File zipFile = new File(mTemporaryFolder.getRoot(), "foo1");

        RelativeFile expectedB = new RelativeFile(zipFile,
                new File(zipFile, "a" + File.separator + "b"));
        RelativeFile expectedC = new RelativeFile(zipFile,
                new File(zipFile, "a" + File.separator + "c"));
        RelativeFile expectedD = new RelativeFile(zipFile, new File(zipFile, "d"));

        ImmutableMap<RelativeFile, FileStatus> set1;
        ImmutableMap<RelativeFile, FileStatus> set2;

        Closer closer = Closer.create();
        try {
            ZFile zf1 = closer.register(new ZFile(zipFile));
            zf1.add("a/", new ByteArrayInputStream(new byte[0]));
            zf1.add("a/b", new ByteArrayInputStream(new byte[0]));
            zf1.add("d", new ByteArrayInputStream(new byte[0]));
            zf1.close();

            set1 = IncrementalRelativeFileSets.fromZip(zipFile);

            @SuppressWarnings("unused")
            boolean ignored = zipFile.delete();

            ZFile zf2 = closer.register(new ZFile(zipFile));
            zf2.add("a/", new ByteArrayInputStream(new byte[0]));
            zf2.add("a/c", new ByteArrayInputStream(new byte[0]));
            zf2.add("d", new ByteArrayInputStream(new byte[0]));
            zf2.close();

            set2 = IncrementalRelativeFileSets.fromZip(zipFile);
            set2 = ImmutableMap.copyOf(
                    Maps.transformValues(
                            set2,
                            Functions.constant(FileStatus.CHANGED)));
        } catch (Throwable t) {
            throw closer.rethrow(t);
        } finally {
            closer.close();
        }

        @SuppressWarnings("unchecked")
        ImmutableMap<RelativeFile, FileStatus> set = IncrementalRelativeFileSets.union(
                Sets.newHashSet(set1, set2));

        assertEquals(3, set.size());
        assertTrue(set.containsKey(expectedB));
        assertTrue(set.containsKey(expectedC));
        assertTrue(set.containsKey(expectedD));
        assertEquals(FileStatus.NEW, set.get(expectedB));
        assertEquals(FileStatus.CHANGED, set.get(expectedC));
        FileStatus dStatus = set.get(expectedD);
        assertTrue(dStatus == FileStatus.NEW || dStatus == FileStatus.CHANGED);
    }

    @Test
    public void baseDirectoryCountOnEmpty() {
        ImmutableMap<RelativeFile, FileStatus> set = ImmutableMap.copyOf(
                Maps.<RelativeFile, FileStatus>newHashMap());
        assertEquals(0, IncrementalRelativeFileSets.getBaseDirectoryCount(set));
    }

    @Test
    public void baseDirectoryCountOneDirectoryMultipleEntries() throws Exception {
        File dir = mTemporaryFolder.newFolder("foo");
        File f0 = new File(dir, "f0");
        assertTrue(f0.createNewFile());
        File f1 = new File(dir, "f1");
        assertTrue(f1.createNewFile());

        ImmutableMap<RelativeFile, FileStatus> set = IncrementalRelativeFileSets.fromDirectory(dir);
        assertEquals(1, IncrementalRelativeFileSets.getBaseDirectoryCount(set));
    }

    @Test
    public void baseDirectoryCountTwoDirectoriesTwoZipsMultipleEntries() throws Exception {
        File foo = mTemporaryFolder.newFolder("foo");
        File f0 = new File(foo, "f0");
        assertTrue(f0.createNewFile());
        File f1 = new File(foo, "f1");
        assertTrue(f1.createNewFile());

        File bar = mTemporaryFolder.newFolder("bar");
        File b0 = new File(bar, "b0");
        assertTrue(b0.createNewFile());
        File b1 = new File(bar, "b1");
        assertTrue(b1.createNewFile());

        File fooz = new File(mTemporaryFolder.getRoot(), "fooz");
        File barz = new File(mTemporaryFolder.getRoot(), "barz");

        Closer closer = Closer.create();

        try {
            ZFile foozZip = closer.register(new ZFile(fooz));
            foozZip.add("f0z", new ByteArrayInputStream(new byte[0]));
            foozZip.close();

            ZFile barzZip = closer.register(new ZFile(barz));
            barzZip.add("f0z", new ByteArrayInputStream(new byte[0]));
            barzZip.close();
        } catch (Throwable t) {
            throw closer.rethrow(t);
        } finally {
            closer.close();
        }

        ImmutableMap<RelativeFile, FileStatus> set1 = IncrementalRelativeFileSets.fromDirectory(bar);
        ImmutableMap<RelativeFile, FileStatus> set2 = IncrementalRelativeFileSets.fromDirectory(foo);
        ImmutableMap<RelativeFile, FileStatus> set3 = IncrementalRelativeFileSets.fromZip(fooz);
        ImmutableMap<RelativeFile, FileStatus> set4 = IncrementalRelativeFileSets.fromZip(barz);
        @SuppressWarnings("unchecked")
        ImmutableMap<RelativeFile, FileStatus> set = IncrementalRelativeFileSets.union(
                Sets.newHashSet(set1, set2, set3, set4));
        assertEquals(2, IncrementalRelativeFileSets.getBaseDirectoryCount(set));
    }

    @Test
    public void makingFromBaseFilesIgnoresDirectories() throws Exception {
        File foo = mTemporaryFolder.newFolder("foo");

        File f0 = new File(foo, "f0");
        assertTrue(f0.createNewFile());
        File bar = new File(foo, "bar");
        bar.mkdir();
        File f1 = new File(bar, "f1");
        assertTrue(f1.createNewFile());

        RelativeFile expectedF0 = new RelativeFile(mTemporaryFolder.getRoot(), f0);
        RelativeFile expectedF1 = new RelativeFile(mTemporaryFolder.getRoot(), f1);

        FileCacheByPath cache = new FileCacheByPath(mTemporaryFolder.newFolder());
        ImmutableMap<RelativeFile, FileStatus> set =
                IncrementalRelativeFileSets.makeFromBaseFiles(
                        Collections.singleton(mTemporaryFolder.getRoot()),
                        ImmutableMap.of(
                                f0, FileStatus.NEW,
                                f1, FileStatus.NEW,
                                bar, FileStatus.NEW),
                        cache);

        assertEquals(2, set.size());
        assertTrue(set.containsKey(expectedF0));
        assertTrue(set.containsKey(expectedF1));
        assertEquals(FileStatus.NEW, set.get(expectedF0));
        assertEquals(FileStatus.NEW, set.get(expectedF1));
    }

    @Test
    public void makeFromDirectoryIgnoresDirectories() throws Exception {
        File foo = mTemporaryFolder.newFolder("foo");
        File f0 = new File(foo, "f0");
        assertTrue(f0.createNewFile());
        File bar = new File(foo, "bar");
        bar.mkdir();
        File f1 = new File(bar, "f1");
        assertTrue(f1.createNewFile());

        RelativeFile expectedF0 = new RelativeFile(mTemporaryFolder.getRoot(), f0);
        RelativeFile expectedF1 = new RelativeFile(mTemporaryFolder.getRoot(), f1);

        ImmutableMap<RelativeFile, FileStatus> set =
                IncrementalRelativeFileSets.fromDirectory(mTemporaryFolder.getRoot());

        assertEquals(2, set.size());
        assertTrue(set.containsKey(expectedF0));
        assertTrue(set.containsKey(expectedF1));
        assertEquals(FileStatus.NEW, set.get(expectedF0));
        assertEquals(FileStatus.NEW, set.get(expectedF1));
    }
}
