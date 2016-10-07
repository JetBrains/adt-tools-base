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

package com.android.builder.internal.packaging;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.annotations.NonNull;
import com.android.builder.files.RelativeFile;
import com.android.ddmlib.SyncService;
import com.android.ide.common.res2.FileStatus;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.io.Closer;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Tests for {@link DexIncrementalRenameManager}.
 */
public class DexIncrementalRenameManagerTest {

    /**
     * Folder used for tests.
     */
    @Rule
    public final TemporaryFolder mTemporaryFolder = new TemporaryFolder();

    /**
     * Closer to use.
     */
    @NonNull
    private final Closer mCloser = Closer.create();

    @After
    public void after() throws Exception {
        mCloser.close();
    }

    /**
     * Create a new manager in the temporary folder.
     *
     * @return the manager
     * @throws Exception failed to create the manager
     */
    @NonNull
    private DexIncrementalRenameManager make() throws Exception {
        return mCloser.register(new DexIncrementalRenameManager(mTemporaryFolder.getRoot()));
    }

    /**
     * Transforms a relative file specification and returns an incremental relative set. The
     * provided set will contain all provided paths in {@link FileStatus#NEW} state.
     *
     * @param paths paths to include as specs (see {@link #makeRelative(String)})
     * @return the incremental set
     */
    @NonNull
    private static ImmutableMap<RelativeFile, FileStatus> makeNew(String... paths) {
        Map<RelativeFile, FileStatus> map = Maps.newHashMap();

        for (String path : paths) {
            map.put(makeRelative(path), FileStatus.NEW);
        }

        return ImmutableMap.copyOf(map);
    }

    /**
     * Creates a relative file from a spec. A spec is in the form {@code a/b} where {@code a} is the
     * base file name {@code b} the relative file name. {@code b} can have multiple slashes (only
     * slashes should be used as separators)
     *
     * @param path the path
     * @return the relative file
     */
    @NonNull
    private static RelativeFile makeRelative(@NonNull String path) {
        String[] split = path.split("/", 2);
        File base = new File(split[0]);
        File file = new File(base, FileUtils.toSystemDependentPath(split[1]));
        return new RelativeFile(base, file);
    }

    /**
     * Creates a packaged file update based on the provided data.
     *
     * @param path the relative file specification (see {@link #makeRelative(String)})
     * @param name the file name
     * @param status the file status
     * @return the packaged file update
     */
    @NonNull
    private static PackagedFileUpdate packaged(@NonNull String path, @NonNull String name,
            @NonNull FileStatus status) {
        return new PackagedFileUpdate(makeRelative(path), name, status);
    }

    @Test
    public void addFilesToEmptyArchive() throws Exception {
        DexIncrementalRenameManager mgr = make();
        ImmutableMap<RelativeFile, FileStatus> set = makeNew("a/b", "b/c");

        Set<PackagedFileUpdate> updates = mgr.update(set);
        assertEquals(2, updates.size());
        if (updates.contains(packaged("a/b", "classes.dex", FileStatus.NEW))) {
            assertTrue(updates.contains(packaged("b/c", "classes2.dex", FileStatus.NEW)));
        } else {
            assertTrue(updates.contains(packaged("a/b", "classes2.dex", FileStatus.NEW)));
            assertTrue(updates.contains(packaged("b/c", "classes.dex", FileStatus.NEW)));
        }

        set = makeNew("a/d");
        updates = mgr.update(set);
        assertEquals(1, updates.size());
        assertTrue(updates.contains(packaged("a/d", "classes3.dex", FileStatus.NEW)));
    }

    @Test
    public void updateFilesInArchive() throws Exception {
        DexIncrementalRenameManager mgr = make();
        ImmutableMap<RelativeFile, FileStatus> set = makeNew("a/b", "b/c");

        mgr.update(set);

        set = ImmutableMap.of(makeRelative("a/b"), FileStatus.CHANGED);
        Set<PackagedFileUpdate> updates = mgr.update(set);
        assertEquals(1, updates.size());
        assertTrue(updates.contains(packaged("a/b", "classes.dex", FileStatus.CHANGED)));
    }

    @Test
    public void deleteFilesInArchive() throws Exception {
        DexIncrementalRenameManager mgr = make();

        assertEquals(1, mgr.update(makeNew("x/a")).size());
        assertEquals(1, mgr.update(makeNew("x/b")).size());
        assertEquals(1, mgr.update(makeNew("x/c")).size());
        assertEquals(1, mgr.update(makeNew("x/d")).size());

        Set<PackagedFileUpdate> updates = mgr.update(
                ImmutableMap.of(makeRelative("x/d"), FileStatus.REMOVED));
        assertEquals(1, updates.size());
        assertTrue(updates.contains(packaged("x/d", "classes4.dex", FileStatus.REMOVED)));

        updates = mgr.update(ImmutableMap.of(makeRelative("x/b"), FileStatus.REMOVED));
        assertEquals(2, updates.size());
        assertTrue(updates.contains(packaged("x/c", "classes3.dex", FileStatus.REMOVED)));
        assertTrue(updates.contains(packaged("x/c", "classes2.dex", FileStatus.CHANGED)));
    }

    @Test
    public void addAndDeleteMoreAdds() throws Exception {
        DexIncrementalRenameManager mgr = make();

        assertEquals(1, mgr.update(makeNew("x/a")).size());
        assertEquals(1, mgr.update(makeNew("x/b")).size());
        assertEquals(1, mgr.update(makeNew("x/c")).size());
        assertEquals(1, mgr.update(makeNew("x/d")).size());

        Set<PackagedFileUpdate> updates = mgr.update(ImmutableMap.of(
                makeRelative("x/e"), FileStatus.NEW,
                makeRelative("x/f"), FileStatus.NEW,
                makeRelative("x/b"), FileStatus.REMOVED));
        assertEquals(2, updates.size());
        if (updates.contains(packaged("x/e", "classes2.dex", FileStatus.CHANGED))) {
            assertTrue(updates.contains(packaged("x/f", "classes5.dex", FileStatus.NEW)));
        } else {
            assertTrue(updates.contains(packaged("x/e", "classes5.dex", FileStatus.NEW)));
            assertTrue(updates.contains(packaged("x/f", "classes2.dex", FileStatus.CHANGED)));
        }
    }

    @Test
    public void addAndDeleteMoreDeletes() throws Exception {
        DexIncrementalRenameManager mgr = make();

        assertEquals(1, mgr.update(makeNew("x/a")).size());
        assertEquals(1, mgr.update(makeNew("x/b")).size());
        assertEquals(1, mgr.update(makeNew("x/c")).size());
        assertEquals(1, mgr.update(makeNew("x/d")).size());

        Set<PackagedFileUpdate> updates = mgr.update(ImmutableMap.of(
                makeRelative("x/e"), FileStatus.NEW,
                makeRelative("x/a"), FileStatus.REMOVED,
                makeRelative("x/b"), FileStatus.REMOVED));
        assertEquals(3, updates.size());
        assertTrue(updates.contains(packaged("x/e", "classes.dex", FileStatus.CHANGED)));
        assertTrue(updates.contains(packaged("x/d", "classes2.dex", FileStatus.CHANGED)));
        assertTrue(updates.contains(packaged("x/d", "classes4.dex", FileStatus.REMOVED)));
    }

    @Test
    public void saveState() throws Exception {
        DexIncrementalRenameManager mgr = make();

        assertEquals(1, mgr.update(makeNew("x/a")).size());
        assertEquals(1, mgr.update(makeNew("x/b")).size());

        mgr.close();

        mgr = make();
        Set<PackagedFileUpdate> updates = mgr.update(makeNew("x/c"));
        assertEquals(1, updates.size());
        assertTrue(updates.contains(packaged("x/c", "classes3.dex", FileStatus.NEW)));
    }

    @Test
    public void classesDexIsNotRenamed() throws Exception {
        DexIncrementalRenameManager mgr = make();

        PackagedFileUpdate pfu = Iterables.getOnlyElement(mgr.update(makeNew("x/y/classes.dex")));
        assertEquals("classes.dex", pfu.getName());
        assertEquals("y/classes.dex", pfu.getSource().getOsIndependentRelativePath());

        pfu = Iterables.getOnlyElement(mgr.update(makeNew("a/b/classes.dex")));
        assertEquals("classes2.dex", pfu.getName());
        assertEquals("b/classes.dex", pfu.getSource().getOsIndependentRelativePath());
    }

    @Test
    public void initialClassesDexNameIsKept() throws Exception {
        DexIncrementalRenameManager mgr = make();

        Set<PackagedFileUpdate> updates =
                mgr.update(makeNew("a/abc.dex", "a/classes.dex", "a/foo.dex"));
        PackagedFileUpdate cdex = Iterables.getOnlyElement(
                updates.stream()
                        .filter(u -> u.getName().equals("classes.dex"))
                        .collect(Collectors.toList()));
        assertEquals("classes.dex", cdex.getSource().getOsIndependentRelativePath());
    }

    @Test
    public void classesDexIsRemovedAndLaterAdded() throws Exception {
        DexIncrementalRenameManager mgr = make();

        PackagedFileUpdate pfu = Iterables.getOnlyElement(mgr.update(makeNew("x/y/classes.dex")));
        assertEquals("classes.dex", pfu.getName());

        PackagedFileUpdate pfu2 = Iterables.getOnlyElement(mgr.update(makeNew("x/y/aaa.dex")));
        assertEquals("classes2.dex", pfu2.getName());

        Set<PackagedFileUpdate> pfu3 = mgr.update(ImmutableMap.of(
                makeRelative("x/y/classes.dex"), FileStatus.REMOVED));
        assertEquals(2, pfu3.size());
        PackagedFileUpdate pfu3_1 = Iterables.getOnlyElement(pfu3.stream()
                .filter(u -> u.getName().equals("classes.dex"))
                .collect(Collectors.toList()));
        PackagedFileUpdate pfu3_2 = Iterables.getOnlyElement(pfu3.stream()
                .filter(u -> u.getName().equals("classes2.dex"))
                .collect(Collectors.toList()));

        assertEquals(FileStatus.CHANGED, pfu3_1.getStatus());
        assertEquals("classes.dex", pfu3_1.getName());
        assertEquals("y/aaa.dex", pfu3_1.getSource().getOsIndependentRelativePath());
        assertEquals(FileStatus.REMOVED, pfu3_2.getStatus());
        assertEquals("classes2.dex", pfu3_2.getName());
        assertEquals("y/aaa.dex", pfu3_2.getSource().getOsIndependentRelativePath());

        Set<PackagedFileUpdate> pfu4 = mgr.update(makeNew("x/y/z/classes.dex"));
        assertEquals(2, pfu4.size());
        PackagedFileUpdate pfu4_1 = Iterables.getOnlyElement(pfu4.stream()
                .filter(u -> u.getName().equals("classes.dex"))
                .collect(Collectors.toList()));
        PackagedFileUpdate pfu4_2 = Iterables.getOnlyElement(pfu4.stream()
                .filter(u -> u.getName().equals("classes2.dex"))
                .collect(Collectors.toList()));

        assertEquals(FileStatus.CHANGED, pfu4_1.getStatus());
        assertEquals("classes.dex", pfu4_1.getName());
        assertEquals("y/z/classes.dex", pfu4_1.getSource().getOsIndependentRelativePath());
        assertEquals(FileStatus.NEW, pfu4_2.getStatus());
        assertEquals("classes2.dex", pfu4_2.getName());
        assertEquals("y/aaa.dex", pfu4_2.getSource().getOsIndependentRelativePath());
    }
}
