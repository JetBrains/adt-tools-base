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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.annotations.Nullable;
import com.android.utils.FileUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.File;

public class ZFileSortTest {
    @Rule
    public TemporaryFolder mTemporaryFolder = new TemporaryFolder();
    private File mFile;
    private ZFile mZFile;
    private StoredEntry mMaryEntry;
    private long mMaryOffset;
    private StoredEntry mAndrewEntry;
    private long mAndrewOffset;
    private StoredEntry mBethEntry;
    private long mBethOffset;
    private StoredEntry mPeterEntry;
    private long mPeterOffset;

    @Before
    public final void before() throws Exception {
        mFile = new File(mTemporaryFolder.getRoot(), "a.zip");
        setupZFile(null);
    }

    @After
    public final void after() throws Exception {
        mZFile.close();
    }

    /**
     * Recreates the zip file, if one already exist.
     *
     * @param options the options for the file, may be {@code null} in which case the default
     * options will be used
     * @throws Exception failed to re-create the file
     */
    private void setupZFile(@Nullable ZFileOptions options) throws Exception {
        if (mZFile != null) {
            mZFile.close();
        }

        if (mFile.exists()) {
            FileUtils.delete(mFile);
        }

        if (options == null) {
            options = new ZFileOptions();
        }

        mZFile = new ZFile(mFile, options);

        mZFile.add("Mary.xml", new ByteArrayInputStream(new byte[] { 1, 2, 3 }));
        mZFile.add("Andrew.txt", new ByteArrayInputStream(new byte[] { 4, 5 }));
        mZFile.add("Beth.png", new ByteArrayInputStream(new byte[] { 6, 7, 8, 9 }));
        mZFile.add("Peter.html", new ByteArrayInputStream(new byte[] { 10 }));
        mZFile.finishAllBackgroundTasks();
    }

    private void readEntries() throws Exception {
        mMaryEntry = mZFile.get("Mary.xml");
        assertNotNull(mMaryEntry);
        mMaryOffset = mMaryEntry.getCentralDirectoryHeader().getOffset();
        assertArrayEquals(new byte[] { 1, 2, 3 }, mMaryEntry.read());

        mAndrewEntry = mZFile.get("Andrew.txt");
        assertNotNull(mAndrewEntry);
        mAndrewOffset = mAndrewEntry.getCentralDirectoryHeader().getOffset();
        assertArrayEquals(new byte[] { 4, 5 }, mAndrewEntry.read());

        mBethEntry = mZFile.get("Beth.png");
        assertNotNull(mBethEntry);
        mBethOffset = mBethEntry.getCentralDirectoryHeader().getOffset();
        assertArrayEquals(new byte[] { 6, 7, 8, 9 }, mBethEntry.read());

        mPeterEntry = mZFile.get("Peter.html");
        assertNotNull(mPeterEntry);
        mPeterOffset = mPeterEntry.getCentralDirectoryHeader().getOffset();
        assertArrayEquals(new byte[] { 10 }, mPeterEntry.read());
    }

    @Test
    public void noSort() throws Exception {
        readEntries();

        assertEquals(-1, mMaryOffset);
        assertEquals(-1, mAndrewOffset);
        assertEquals(-1, mBethOffset);
        assertEquals(-1, mPeterOffset);

        mZFile.update();

        readEntries();

        assertTrue(mMaryOffset >= 0);
        assertTrue(mMaryOffset < mAndrewOffset);
        assertTrue(mAndrewOffset < mBethOffset);
        assertTrue(mBethOffset < mPeterOffset);
    }

    @Test
    public void sortFilesBeforeUpdate() throws Exception {
        readEntries();
        mZFile.sortZipContents();

        mZFile.update();

        readEntries();

        assertTrue(mAndrewOffset >= 0);
        assertTrue(mBethOffset > mAndrewOffset);
        assertTrue(mMaryOffset > mBethOffset);
        assertTrue(mPeterOffset > mMaryOffset);
    }

    @Test
    public void autoSort() throws Exception {
        ZFileOptions options = new ZFileOptions();
        options.setAutoSortFiles(true);
        setupZFile(options);

        readEntries();

        mZFile.update();

        readEntries();

        assertTrue(mAndrewOffset >= 0);
        assertTrue(mBethOffset > mAndrewOffset);
        assertTrue(mMaryOffset > mBethOffset);
        assertTrue(mPeterOffset > mMaryOffset);
    }

    @Test
    public void sortFilesAfterUpdate() throws Exception {
        readEntries();

        mZFile.update();

        mZFile.sortZipContents();

        readEntries();

        assertEquals(-1, mMaryOffset);
        assertEquals(-1, mAndrewOffset);
        assertEquals(-1, mBethOffset);
        assertEquals(-1, mPeterOffset);

        mZFile.update();

        readEntries();

        assertTrue(mAndrewOffset >= 0);
        assertTrue(mBethOffset > mAndrewOffset);
        assertTrue(mMaryOffset > mBethOffset);
        assertTrue(mPeterOffset > mMaryOffset);
    }

    @Test
    public void sortFilesWithAlignment() throws Exception {
        mZFile.close();

        ZFileOptions options = new ZFileOptions();
        options.setAlignmentRule(AlignmentRules.constantForSuffix(".xml", 1024));
        mZFile = new ZFile(mFile, options);

        mZFile.sortZipContents();
        mZFile.update();

        readEntries();
        assertTrue(mAndrewOffset >= 0);
        assertTrue(mBethOffset > mAndrewOffset);
        assertTrue(mPeterOffset > mBethOffset);
        assertTrue(mMaryOffset > mPeterOffset);
    }

    @Test
    public void sortFilesOnClosedFile() throws Exception {
        mZFile.close();
        mZFile = new ZFile(mFile);
        mZFile.sortZipContents();
        mZFile.update();

        readEntries();

        assertTrue(mAndrewOffset >= 0);
        assertTrue(mBethOffset > mAndrewOffset);
        assertTrue(mMaryOffset > mBethOffset);
        assertTrue(mPeterOffset > mMaryOffset);
    }
}
