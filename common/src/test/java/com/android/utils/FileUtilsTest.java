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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.SdkConstants;
import com.google.common.base.Strings;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

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
    public void testGetValidFileName() throws IOException {
        File directory = new File("/Users/foo");

        // Test the "normal" case
        assertThat(FileUtils.getValidFileName("foo", "txt", directory)).isEqualTo("foo.txt");

        // Test real-world file path
        assertThat(
                FileUtils.getValidFileName(
                        "com.android.support/design/23.3.0/jars/classes.jar"
                                + "_build=23.0_jumbo=false_multidex=true_optimize=false",
                        "jar",
                        directory))
                .isEqualTo(
                        "com_android_support_design_23_3_0_jars_classes_jar"
                                + "_build_23_0_jumbo_false_multidex_true_optimize_false"
                                + "_0979f3a79bbb13e755cfaa70190a4dbc116600f5.jar");

        // Test Windows-based file path
        assertThat(
                FileUtils.getValidFileName(
                        "com.android.support\\design\\23.3.0\\jars\\classes.jar"
                                + "_build=23.0_jumbo=false_multidex=true_optimize=false",
                        "jar",
                        directory))
                .isEqualTo(
                        "com_android_support_design_23_3_0_jars_classes_jar"
                                + "_build_23_0_jumbo_false_multidex_true_optimize_false"
                                + "_48d7fda3351450337c26a2990bef21df95f65685.jar");

        // Test unusual file name
        assertThat(
                FileUtils.getValidFileName(
                        "foo`-=[]\\\\;',./~!@#$%^&*()_+{}|:\\\"<>?", "...!@#", directory))
                .isEqualTo(
                        "foo__________________________________"
                                + "_387e0d11003e554ddceaeca12af15839dbd51643.______");

        // Test empty extension
        assertThat(FileUtils.getValidFileName("foo", "", directory)).isEqualTo("foo");

        // Test long file name
        assertThat(FileUtils.getValidFileName(Strings.repeat("a", 252), "txt", directory))
                .isEqualTo("7d872a53e320bcdb5adce13b87a7e7671fef6780.txt");

        // Test long file path
        try {
            FileUtils.getValidFileName("foo", "txt", new File("/", Strings.repeat("a", 4088)));
            fail("expected IOException");
        } catch (IOException exception) {
            assertTrue(exception.getMessage().startsWith("File name or file path is too long: "));
        }
    }

    @Test
    public void testIsFileNameTooLong() throws IOException {
        assertFalse(FileUtils.isFileNameTooLong(Strings.repeat("a", 255)));
        assertTrue(FileUtils.isFileNameTooLong(Strings.repeat("a", 256)));
    }

    @Test
    public void testIsFilePathTooLong() throws IOException {
        File directory = new File("/Users/foo");
        assertFalse(FileUtils.isFilePathTooLong("bar", directory));

        assertFalse(FileUtils.isFilePathTooLong(Strings.repeat("a", 255), directory));
        assertTrue(FileUtils.isFilePathTooLong(Strings.repeat("a", 256), directory));

        if (SdkConstants.currentPlatform() == SdkConstants.PLATFORM_WINDOWS) {
            assertFalse(
                    FileUtils.isFilePathTooLong(
                            "foo.txt", new File("C:\\", Strings.repeat("a", 249))));
            assertTrue(
                    FileUtils.isFilePathTooLong(
                            "foo.txt", new File("C:\\", Strings.repeat("a", 250))));
        } else {
            assertFalse(
                    FileUtils.isFilePathTooLong(
                            "foo.txt", new File("/", Strings.repeat("a", 4087))));
            assertTrue(
                    FileUtils.isFilePathTooLong(
                            "foo.txt", new File("/", Strings.repeat("a", 4088))));
        }

        // Test Windows-based file path
        assertFalse(FileUtils.isFilePathTooLong("bar", new File("C:\\Users\\foo")));
    }

    @Test
    public void testRelativePossiblyNonExistingPath() throws IOException {
        File inputDir = new File("/folders/1/5/main");
        File folder = new File(inputDir, "com/obsidian/v4/tv/home/playback");
        File fileToProcess = new File(folder, "CameraPlaybackGlue$1.class");
        assertEquals("com/obsidian/v4/tv/home/playback/CameraPlaybackGlue$1.class",
                FileUtils.relativePossiblyNonExistingPath(fileToProcess, inputDir));
        fileToProcess = new File(folder, "CameraPlaybackGlue$CameraPlaybackHost.class");
        assertEquals("com/obsidian/v4/tv/home/playback/CameraPlaybackGlue$CameraPlaybackHost.class",
                FileUtils.relativePossiblyNonExistingPath(fileToProcess, inputDir));
    }
}
