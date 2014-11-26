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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.annotations.NonNull;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.ByteStreams;

import org.junit.Assert;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Helper to help read/test the content of generated zip file.
 */
public class ZipHelper {

    /**
     * Checks that a zip file contains a specific file.
     */
    public static void checkFileExists(
            @NonNull File archive,
            @NonNull String path)
            throws IOException {
        Map<String, String> pathToContent = Collections.singletonMap(path, null);
        checkArchive(archive, pathToContent, ImmutableSet.<String>of());
    }

    /**
     * Checks that a zip file contains all files in the list.
     */
    public static void checkFileExists(
            @NonNull File archive,
            @NonNull List<String> paths)
            throws IOException {
        Map<String, String> pathToContent = Maps.newLinkedHashMap();
        for (String path : paths) {
            pathToContent.put(path, null);
        }
        checkArchive(archive, pathToContent, ImmutableSet.<String>of());
    }

    /**
     * Checks that a zip file contains a file with the specified content
     * @param archive the zip file to check.
     * @param path an expected file inside archive
     * @param content the expected content of the file inside archive
     * @throws IOException
     */
    public static void checkContent(
            @NonNull File archive,
            @NonNull String path,
            @NonNull String content)
            throws IOException {
        Map<String, String> pathToContent = Collections.singletonMap(path, content);
        checkArchive(archive, pathToContent, ImmutableSet.<String>of());
    }

    /**
     * Checks that a zip file contains files, optionally with specific content.
     * @param archive the file to check.
     * @param pathToContents a map of zip entry path and text-content. if content is null, only
     *                       presence of file inside the zip is checked.
     * @param notPresentPaths a list of paths that should *not* be present in the archive.
     * @throws IOException
     */
    public static void checkArchive(
            @NonNull File archive,
            @NonNull Map<String, String> pathToContents,
            @NonNull Set<String> notPresentPaths)
            throws IOException {
        assertTrue("File '" + archive.getPath() + "' does not exist.", archive.isFile());
        ZipInputStream zis = null;
        FileInputStream fis;
        Set<String> notFound = Sets.newHashSet();
        notFound.addAll(pathToContents.keySet());
        fis = new FileInputStream(archive);
        try {
            zis = new ZipInputStream(fis);

            ZipEntry entry = zis.getNextEntry();
            while (entry != null) {
                String name = entry.getName();

                assertFalse("Found not expected path: " + name,
                        notPresentPaths.contains(name));

                String expected = pathToContents.get(name);
                if (expected != null) {
                    notFound.remove(name);
                    if (!entry.isDirectory()) {
                        byte[] bytes = ByteStreams.toByteArray(zis);
                        if (bytes != null) {
                            String contents = new String(bytes, Charsets.UTF_8).trim();
                            Assert.assertEquals("Contents in " + name + " did not match",
                                    expected, contents);
                        }
                    }
                } else if (pathToContents.keySet().contains(name)) {
                    notFound.remove(name);
                }
                entry = zis.getNextEntry();
            }
        } finally {
            fis.close();
            if (zis != null) {
                zis.close();
            }
        }

        assertTrue("Did not find the following paths in the " + archive.getPath() + " file: " +
                notFound, notFound.isEmpty());
    }

}
