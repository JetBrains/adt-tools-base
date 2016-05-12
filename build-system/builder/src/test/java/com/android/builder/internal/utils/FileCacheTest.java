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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.annotations.NonNull;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.io.Files;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test cases for {@link FileCache}.
 */
public class FileCacheTest {

    @Rule
    public TemporaryFolder cacheFolder = new TemporaryFolder();

    @Rule
    public TemporaryFolder testFolder = new TemporaryFolder();

    @Test
    public void testSameFileSameKey() throws IOException {
        FileCache fileCache = new FileCache(cacheFolder.getRoot());

        File outputFile = testFolder.newFile();
        String fileKey = "foo";
        String[] fileContents = {"Foo line", "Bar line"};

        boolean result = fileCache.getOrCreateFile(outputFile, fileKey, (newFile) -> {
            Files.write(fileContents[0], newFile, Charsets.UTF_8);
            return null;
        });
        assertTrue(result);

        assertEquals(0, fileCache.getHits());
        assertEquals(1, fileCache.getMisses());
        checkFileContent(fileContents[0], outputFile);

        outputFile.delete();

        fileCache.getOrCreateFile(outputFile, fileKey, (newFile) -> {
            Files.write(fileContents[1], newFile, Charsets.UTF_8);
            return null;
        });

        assertEquals(1, fileCache.getHits());
        assertEquals(1, fileCache.getMisses());
        checkFileContent(fileContents[0], outputFile);
    }

    @Test
    public void testSameFileDifferentKeys() throws IOException {
        FileCache fileCache = new FileCache(cacheFolder.getRoot());

        File outputFile = testFolder.newFile();
        String[] fileKeys = {"foo", "bar"};
        String[] fileContents = {"Foo line", "Bar line"};

        for (int i = 0; i < 2; i++) {
            final int ii = i;
            boolean result = fileCache.getOrCreateFile(outputFile, fileKeys[i], (newFile) -> {
                Files.write(fileContents[ii], newFile, Charsets.UTF_8);
                return null;
            });
            assertTrue(result);
        }

        assertEquals(0, fileCache.getHits());
        assertEquals(2, fileCache.getMisses());
        checkFileContent(fileContents[1], outputFile);
    }

    @Test
    public void testDifferentFileSameKey() throws IOException {
        FileCache fileCache = new FileCache(cacheFolder.getRoot());

        File[] outputFiles = {testFolder.newFile(), testFolder.newFile()};
        String fileKey = "foo";
        String[] fileContents = {"Foo line", "Bar line"};

        for (int i = 0; i < 2; i++) {
            final int ii = i;
            boolean result = fileCache.getOrCreateFile(outputFiles[i], fileKey, (newFile) -> {
                Files.write(fileContents[ii], newFile, Charsets.UTF_8);
                return null;
            });
            assertTrue(result);
        }

        assertEquals(1, fileCache.getHits());
        assertEquals(1, fileCache.getMisses());
        checkFileContent(fileContents[0], outputFiles[1]);
    }

    @Test
    public void testDifferentFilesDifferentKeys() throws IOException {
        FileCache fileCache = new FileCache(cacheFolder.getRoot());

        File[] outputFiles = {testFolder.newFile(), testFolder.newFile()};
        String[] fileKeys = {"foo", "bar"};
        String[] fileContents = {"Foo line", "Bar line"};

        for (int i = 0; i < 2; i++) {
            final int ii = i;
            boolean result = fileCache.getOrCreateFile(outputFiles[i], fileKeys[i], (newFile) -> {
                Files.write(fileContents[ii], newFile, Charsets.UTF_8);
                return null;
            });
            assertTrue(result);
        }

        assertEquals(0, fileCache.getHits());
        assertEquals(2, fileCache.getMisses());
        checkFileContent(fileContents[1], outputFiles[1]);
    }

    @Test
    public void testFolder() throws IOException {
        FileCache fileCache = new FileCache(cacheFolder.getRoot());

        // Test different folders same key
        File[] outputFolders = {testFolder.newFolder(), testFolder.newFolder()};
        String folderKey = "foo";
        String[] fileNames = {"fooFile", "barFile"};
        String[] fileContents = {"Foo line", "Bar line"};

        for (int i = 0; i < 2; i++) {
            final int ii = i;
            boolean result = fileCache.getOrCreateFile(outputFolders[i], folderKey, (newFolder) -> {
                FileUtils.mkdirs(newFolder);
                Files.write(fileContents[ii], new File(newFolder, fileNames[ii]), Charsets.UTF_8);
                return null;
            });
            assertTrue(result);
        }

        assertEquals(1, fileCache.getHits());
        assertEquals(1, fileCache.getMisses());
        checkFileContent(fileContents[0], new File(outputFolders[1], fileNames[0]));
    }

    @Test
    public void testMultiThreads() throws IOException {
        FileCache fileCache = new FileCache(cacheFolder.getRoot());

        // Test different files same key
        File[] outputFiles = {testFolder.newFile(), testFolder.newFile()};
        String fileKey = "foo";
        String fileContent = "Foo line";

        CountDownLatch fileCreationLatch = new CountDownLatch(1);
        CountDownLatch threadFinishedLatch = new CountDownLatch(1);
        AtomicInteger executedThreadCount = new AtomicInteger(0);

        Thread[] threads = new Thread[2];
        for (int i = 0; i < 2; i++) {
            final int ii = i;
            threads[i] = new Thread(() -> {
                try {
                    boolean result = fileCache.getOrCreateFile(outputFiles[ii], fileKey,
                            (newFile) -> {
                                executedThreadCount.incrementAndGet();
                                // Notify the main thread that it is about to create the file
                                fileCreationLatch.countDown();
                                Files.write(fileContent, newFile, Charsets.UTF_8);
                                // Wait for the main thread to allow it to finish
                                try {
                                    threadFinishedLatch.await();
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                                return null;
                            });
                    assertTrue(result);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }

        // Execute the threads
        for (Thread thread : threads) {
            thread.start();
        }

        // Wait for notification from one of the threads that it is about to create the file
        try {
            fileCreationLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Make sure that only one thread has executed so far. (The threads are not allowed to
        // create the same file at the same time.)
        assertEquals(1, executedThreadCount.get());

        // Allow all the threads to finish
        threadFinishedLatch.countDown();

        // Wait for all the threads to finish
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        assertEquals(1, fileCache.getHits());
        assertEquals(1, fileCache.getMisses());
        checkFileContent(fileContent, outputFiles[0]);
        checkFileContent(fileContent, outputFiles[1]);
    }

    @Test
    public void testComplexFileKey() throws IOException {
        FileCache fileCache = new FileCache(cacheFolder.getRoot());

        File outputFile = testFolder.newFile();
        String fileKey = "foo`-=[]\\;',./~!@#$%^&*()_+{}|:\"<>?";
        String fileContent = "Foo line";

        boolean result = fileCache.getOrCreateFile(outputFile, fileKey, (newFile) -> {
            Files.write(fileContent, newFile, Charsets.UTF_8);
            return null;
        });
        assertTrue(result);

        assertEquals(0, fileCache.getHits());
        assertEquals(1, fileCache.getMisses());
        checkFileContent(fileContent, outputFile);
    }

    @Test
    public void testFileNotCreated() throws IOException {
        FileCache fileCache = new FileCache(cacheFolder.getRoot());

        File outputFile = testFolder.newFile();
        String fileKey = "foo";

        boolean result = fileCache.getOrCreateFile(outputFile, fileKey, (newFile) -> null);
        assertFalse(result);

        assertEquals(0, fileCache.getHits());
        assertEquals(1, fileCache.getMisses());
    }

    @Test
    public void testFileNotCreatedIfNoFileExtension() throws IOException {
        FileCache fileCache = new FileCache(cacheFolder.getRoot());

        File outputFile = testFolder.newFile("x.bar");
        String fileKey = "foo";
        String fileContent = "Foo line";

        boolean result = fileCache.getOrCreateFile(outputFile, fileKey, (newFile) -> {
            if (Files.getFileExtension(newFile.getName()).equals("bar")) {
                Files.write(fileContent, newFile, Charsets.UTF_8);
            }
            return null;
        });
        assertTrue(result);

        assertEquals(0, fileCache.getHits());
        assertEquals(1, fileCache.getMisses());
        checkFileContent(fileContent, outputFile);
    }

    private static void checkFileContent(@NonNull String content, @NonNull File file)
            throws IOException {
        List<String> lines = Files.readLines(file, Charsets.UTF_8);

        assertEquals(1, lines.size());
        assertEquals(content, lines.get(0));
    }

}
