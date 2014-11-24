/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.build.gradle.integration.common.utils;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.integration.common.fixture.app.TestSourceFile;
import com.google.common.base.Charsets;
import com.google.common.base.Predicate;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.google.common.truth.Truth;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * Helper to help verify content of a file.
 */
public class FileHelper {

    /**
     * Return a list of relative path of the files in a directory.
     */
    public static List<String> listFiles(@NonNull File base) {
        assertTrue(base.isDirectory());

        List<String> fileList = Lists.newArrayList();
        for (File file : Files.fileTreeTraverser().preOrderTraversal(base).filter(
                new Predicate<File>() {
                    @Override
                    public boolean apply(@Nullable File file) {
                        return file != null && !file.isDirectory();
                    }
                })) {
            assertThat(file.toString()).startsWith(base.toString());
            String fileName = file.toString().substring(base.toString().length());
            fileList.add(fileName);

        }
        return fileList;
    }

    public static void checkContent(File file, String expectedContent) throws IOException {
        checkContent(file, Collections.singleton(expectedContent));
    }

    public static void checkContent(File file, Iterable<String> expectedContents) throws IOException {
        assertTrue("File '" + file.getAbsolutePath() + "' does not exist.", file.isFile());

        String contents = Files.toString(file, Charsets.UTF_8);
        for (String expectedContent : expectedContents) {
            assertTrue("File '" + file.getAbsolutePath() + "' does not contain: " + expectedContent,
                    contents.contains(expectedContent));
        }
    }

}
