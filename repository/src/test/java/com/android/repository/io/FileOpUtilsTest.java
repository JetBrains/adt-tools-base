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

package com.android.repository.io;

import com.android.repository.io.impl.FileOpImpl;

import junit.framework.TestCase;

import java.io.IOException;

/**
 * Tests for FileOpUtils
 * TODO: more tests
 */
public class FileOpUtilsTest extends TestCase {
    public void testMakeRelative() throws Exception {
        assertEquals("dir3",
                FileOpUtils.makeRelativeImpl("/dir1/dir2",
                        "/dir1/dir2/dir3",
                        false, "/"));

        assertEquals("../../../dir3",
                FileOpUtils.makeRelativeImpl("/dir1/dir2/dir4/dir5/dir6",
                        "/dir1/dir2/dir3",
                        false, "/"));

        assertEquals("dir3/dir4/dir5/dir6",
                FileOpUtils.makeRelativeImpl("/dir1/dir2/",
                        "/dir1/dir2/dir3/dir4/dir5/dir6",
                        false, "/"));

        // case-sensitive on non-Windows.
        assertEquals("../DIR2/dir3/DIR4/dir5/DIR6",
                FileOpUtils.makeRelativeImpl("/dir1/dir2/",
                        "/dir1/DIR2/dir3/DIR4/dir5/DIR6",
                        false, "/"));

        // same path: empty result.
        assertEquals("",
                FileOpUtils.makeRelativeImpl("/dir1/dir2/dir3",
                        "/dir1/dir2/dir3",
                        false, "/"));

        // same drive letters on Windows
        assertEquals("..\\..\\..\\dir3",
                FileOpUtils.makeRelativeImpl("C:\\dir1\\dir2\\dir4\\dir5\\dir6",
                        "C:\\dir1\\dir2\\dir3",
                        true, "\\"));

        // not case-sensitive on Windows, results will be mixed.
        assertEquals("dir3/DIR4/dir5/DIR6",
                FileOpUtils.makeRelativeImpl("/DIR1/dir2/",
                        "/dir1/DIR2/dir3/DIR4/dir5/DIR6",
                        true, "/"));

        // UNC path on Windows
        assertEquals("..\\..\\..\\dir3",
                FileOpUtils.makeRelativeImpl("\\\\myserver.domain\\dir1\\dir2\\dir4\\dir5\\dir6",
                        "\\\\myserver.domain\\dir1\\dir2\\dir3",
                        true, "\\"));

        // different drive letters are not supported
        try {
            FileOpUtils.makeRelativeImpl("C:\\dir1\\dir2\\dir4\\dir5\\dir6",
                    "D:\\dir1\\dir2\\dir3",
                    true, "\\");
            fail("Expected: IOException. Actual: no exception.");
        } catch (IOException e) {
            assertEquals("makeRelative: incompatible drive letters", e.getMessage());
        }
    }

}
