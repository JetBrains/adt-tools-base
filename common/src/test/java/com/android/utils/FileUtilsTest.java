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

package com.android.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.common.base.Strings;
import com.google.common.io.Files;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;

/**
 * Test cases for {@link FileUtils}.
 */
public class FileUtilsTest {
    @Rule
    public TemporaryFolder mTemporaryFolder = new TemporaryFolder();

    @Test
    public void computeRelativePathOfFile() throws Exception {
        File d1 = mTemporaryFolder.newFolder("foo");
        File f2 = new File(d1, "bar");
        Files.touch(f2);

        assertEquals("bar", FileUtils.relativePath(f2, d1));
    }

    @Test
    public void computeRelativePathOfDirectory() throws Exception {
        File d1 = mTemporaryFolder.newFolder("foo");
        File f2 = new File(d1, "bar");
        f2.mkdir();

        assertEquals("bar" + File.separator, FileUtils.relativePath(f2, d1));
    }

    @Test
    public void testGetValidFileName() {
        File folder = new File("/Users/foo");

        // Test difference cases
        assertEquals("foo.txt", FileUtils.getValidFileName("foo", "txt", folder));

        assertEquals("foo_bar_9b26b3a10a2a56167a248302b46e3b89dfad1436.txt",
                FileUtils.getValidFileName("foo/bar", "txt", folder));

        assertEquals("foo_txt_8cde625cbac2b10d192205d5ff4d4c7f0e0f6652",
                FileUtils.getValidFileName("foo.txt", "", folder));

        assertEquals("0e90102b9cef85ad1b63495b745fd88493434e27.txt",
                FileUtils.getValidFileName(Strings.repeat("a", 252), "txt", folder));

        // Test with real-world strings
        assertEquals("support_annotations_23_3_0_jar_b6069f782045b0d1d75f482dc7b50ab5a47ca301"
                + "_build_23_0_3_jumbo_true_multidex_false_optimize_true"
                + "_2a17141d2f7e7cf2f843981b64cdba23ff6a89eb",
                FileUtils.getValidFileName("support-annotations-23.3.0.jar"
                        + "_b6069f782045b0d1d75f482dc7b50ab5a47ca301"
                        + "_build=23.0.3_jumbo=true_multidex=false_optimize=true", "", folder));

        assertEquals("support_annotations_23_3_0_jar_b6069f782045b0d1d75f482dc7b50ab5a47ca301"
                + "_build_23_0_3_jumbo_true_multidex_false_optimize_true"
                + "_f4a3588c7a7c868d1744db3efec62a1a55a09feb.jar",
                FileUtils.getValidFileName("support-annotations-23.3.0.jar"
                        + "_b6069f782045b0d1d75f482dc7b50ab5a47ca301"
                        + "_build=23.0.3_jumbo=true_multidex=false_optimize=true", "jar", folder));

        assertEquals("com_android_support_design_23_3_0_jars_classes_jar"
                + "_build_23_0_jumbo_false_multidex_true_optimize_false"
                + "_257309c81655b3994736fe539357cce8b601039b",
                FileUtils.getValidFileName("com.android.support/design/23.3.0/jars/classes.jar"
                        + "_build=23.0_jumbo=false_multidex=true_optimize=false", "", folder));

        assertEquals("com_android_support_design_23_3_0_jars_classes_jar"
                + "_build_23_0_jumbo_false_multidex_true_optimize_false"
                + "_b96934d0692bb33631e089a71698a446d847ba2c.jar",
                FileUtils.getValidFileName("com.android.support/design/23.3.0/jars/classes.jar"
                        + "_build=23.0_jumbo=false_multidex=true_optimize=false", "jar", folder));
    }

    @Test
    public void testIsFilePathTooLong() throws IOException {
        File folder = new File("/Users/foo");
        assertFalse(FileUtils.isFilePathTooLong("bar", folder));
        assertTrue(FileUtils.isFilePathTooLong(Strings.repeat("a", 256), folder));
    }
}
