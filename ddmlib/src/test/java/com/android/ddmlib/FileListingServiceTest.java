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
package com.android.ddmlib;

import java.util.regex.Matcher;
import junit.framework.TestCase;

public class FileListingServiceTest extends TestCase {
    public void test_LS_L_PATTERN() {
        Matcher m;

        // Traditional Android file output.
        m = FileListingService.LS_L_PATTERN.matcher(
                "-rw-r--r-- root     root          193 1970-01-01 00:00 build.prop");
        assertTrue(m.matches());
        assertEquals("193", m.group(4));

        // Traditional Android directory output.
        m = FileListingService.LS_L_PATTERN.matcher(
                "drwxrwx--- system   cache             2015-07-20 23:01 cache");
        assertTrue(m.matches());
        assertEquals("system", m.group(2));
        assertEquals("23:01", m.group(6));

        // POSIX file output.
        m = FileListingService.LS_L_PATTERN.matcher(
                "-rw-r--r--   1     root   root    193 1970-01-01 00:00 build.prop");
        assertTrue(m.matches());
        assertEquals("193", m.group(4));

        // POSIX directory output.
        m = FileListingService.LS_L_PATTERN.matcher(
                "drwxrwx---   5   system  cache   4096 2015-07-20 23:01 cache");
        assertTrue(m.matches());
        assertEquals("system", m.group(2));
        assertEquals("23:01", m.group(6));
    }

    public void test_LS_LD_PATTERN() {
        Matcher m;

        // adb shell toolbox ls -ld /init
        m = FileListingService.LS_LD_PATTERN.matcher(
                "-rwxr-x--- root     root       924136 1970-01-01 00:00 init");
        assertFalse(m.matches());
        // adb shell toolbox ls -ld /sdcard
        m = FileListingService.LS_LD_PATTERN.matcher(
                "lrwxrwxrwx root     root              2015-08-20 21:57 sdcard -> /storage/emulated/legacy");
        assertFalse(m.matches());
        // adb shell toolbox ls -ld /sdcard/
        m = FileListingService.LS_LD_PATTERN.matcher(
                "drwxrwx--x root     sdcard_r          2015-07-20 23:01 ");
        assertTrue(m.matches());

        // adb shell toybox ls -ld /init
        m = FileListingService.LS_LD_PATTERN.matcher(
                "-rwxr-x--- 1 root root 924136 1970-01-01 00:00 /init");
        assertFalse(m.matches());
        // adb shell toybox ls -ld /sdcard
        m = FileListingService.LS_LD_PATTERN.matcher(
                "lrwxrwxrwx 1 root root 24 2015-08-20 21:57 /sdcard -> /storage/emulated/legacy");
        assertFalse(m.matches());
        // adb shell toybox ls -ld /sdcard/
        m = FileListingService.LS_LD_PATTERN.matcher(
                "drwxrwx--x 12 root sdcard_r 4096 2015-07-20 23:01 /sdcard/");
        assertTrue(m.matches());
    }
}
