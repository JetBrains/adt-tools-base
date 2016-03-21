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

package com.android.builder.internal.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

public class CachedFileContentsTest {
    @Rule
    public TemporaryFolder mTemporaryFolder = new TemporaryFolder();

    private static void waitFilesystemTime() throws Exception {
        /*
         * How much time to wait until we are sure that the file system will update the last
         * modified timestamp of a file. This is usually related to the accuracy of last timestamps.
         * In modern windows systems 100ms be more than enough (NTFS has 100us accuracy --
         * see https://msdn.microsoft.com/en-us/library/windows/desktop/ms724290(v=vs.85).aspx).
         * In linux it will depend on the filesystem. ext4 has 1ns accuracy (if inodes are 256 byte
         * or larger), but ext3 has 1 second.
         */
        Thread.sleep(2000);
    }

    @Test
    public void createFileAndCheckWithNoChanges() throws Exception {
        File f = mTemporaryFolder.newFile("test");
        Files.write("abc", f, Charsets.US_ASCII);

        Object cache = new Object();

        CachedFileContents<Object> cachedFile = new CachedFileContents<Object>(f);
        cachedFile.closed(cache);

        waitFilesystemTime();

        assertTrue(cachedFile.isValid());
        assertSame(cache, cachedFile.getCache());
    }

    @Test
    public void createFileAndCheckChanges() throws Exception {
        File f = mTemporaryFolder.newFile("test");
        Files.write("abc", f, Charsets.US_ASCII);

        Object cache = new Object();

        CachedFileContents<Object> cachedFile = new CachedFileContents<Object>(f);
        cachedFile.closed(cache);

        waitFilesystemTime();

        Files.write("def", f, Charsets.US_ASCII);

        assertFalse(cachedFile.isValid());
        assertNull(cachedFile.getCache());
    }

    @Test
    public void createFileUpdateAndCheckChanges() throws Exception {
        File f = mTemporaryFolder.newFile("test");
        Files.write("abc", f, Charsets.US_ASCII);

        Object cache = new Object();

        CachedFileContents<Object> cachedFile = new CachedFileContents<Object>(f);
        cachedFile.closed(cache);

        waitFilesystemTime();

        Files.write("def", f, Charsets.US_ASCII);
        cachedFile.closed(cache);

        assertTrue(cachedFile.isValid());
        assertSame(cache, cachedFile.getCache());
    }

    @Test
    public void immediateChangesDetected() throws Exception {
        File f = mTemporaryFolder.newFile("foo");
        Files.write("bar", f, Charsets.US_ASCII);

        CachedFileContents<Object> cachedFile = new CachedFileContents<Object>(f);
        cachedFile.closed(null);

        Files.write("xpto", f, Charsets.US_ASCII);
        assertFalse(cachedFile.isValid());
    }

    @Test
    public void immediateChangesDetectedEvenWithHackedTs() throws Exception {
        File f = mTemporaryFolder.newFile("foo");
        Files.write("bar", f, Charsets.US_ASCII);

        CachedFileContents<Object> cachedFile = new CachedFileContents<Object>(f);
        cachedFile.closed(null);
        long lastTs = f.lastModified();

        Files.write("xpto", f, Charsets.US_ASCII);
        f.setLastModified(lastTs);
        assertFalse(cachedFile.isValid());
    }

    @Test
    public void immediateChangesWithNoContentChangeNotDetected() throws Exception {
        File f = mTemporaryFolder.newFile("foo");
        Files.write("bar", f, Charsets.US_ASCII);

        CachedFileContents<Object> cachedFile = new CachedFileContents<Object>(f);
        cachedFile.closed(null);
        long lastTs = f.lastModified();

        Files.write("bar", f, Charsets.US_ASCII);
        f.setLastModified(lastTs);
        assertTrue(cachedFile.isValid());
    }
}
