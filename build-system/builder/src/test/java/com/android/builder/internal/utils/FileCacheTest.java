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

import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.io.Files;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.List;

/** Test cases for {@link FileCache}. */
public class FileCacheTest {

    @Rule public TemporaryFolder cacheFolder = new TemporaryFolder();

    @Rule public TemporaryFolder testFolder = new TemporaryFolder();

    @Test
    public void testSameFileSameKey() throws IOException {
        FileCache fileCache = FileCache.withSingleProcessLocking(cacheFolder.getRoot());

        File outputFile = testFolder.newFile();
        String fileKey = "foo";
        String[] fileContents = {"Foo line", "Bar line"};

        boolean result =
                fileCache.getOrCreateFile(
                        outputFile,
                        fileKey,
                        (newFile) -> {
                            Files.write(fileContents[0], newFile, Charsets.UTF_8);
                            return null;
                        });
        assertTrue(result);

        assertEquals(0, fileCache.getHits());
        assertEquals(1, fileCache.getMisses());
        assertEquals(fileContents[0], Files.toString(outputFile, Charsets.UTF_8));

        outputFile.delete();

        fileCache.getOrCreateFile(
                outputFile,
                fileKey,
                (newFile) -> {
                    Files.write(fileContents[1], newFile, Charsets.UTF_8);
                    return null;
                });

        assertEquals(1, fileCache.getHits());
        assertEquals(1, fileCache.getMisses());
        assertEquals(fileContents[0], Files.toString(outputFile, Charsets.UTF_8));
    }

    @Test
    public void testSameFileDifferentKeys() throws IOException {
        FileCache fileCache = FileCache.withSingleProcessLocking(cacheFolder.getRoot());

        File outputFile = testFolder.newFile();
        String[] fileKeys = {"foo", "bar"};
        String[] fileContents = {"Foo line", "Bar line"};

        for (int i = 0; i < 2; i++) {
            final int idx = i;
            boolean result =
                    fileCache.getOrCreateFile(
                            outputFile,
                            fileKeys[i],
                            (newFile) -> {
                                Files.write(fileContents[idx], newFile, Charsets.UTF_8);
                                return null;
                            });
            assertTrue(result);
        }

        assertEquals(0, fileCache.getHits());
        assertEquals(2, fileCache.getMisses());
        assertEquals(fileContents[1], Files.toString(outputFile, Charsets.UTF_8));
    }

    @Test
    public void testDifferentFilesSameKey() throws IOException {
        FileCache fileCache = FileCache.withSingleProcessLocking(cacheFolder.getRoot());

        File[] outputFiles = {testFolder.newFile(), testFolder.newFile()};
        String fileKey = "foo";
        String[] fileContents = {"Foo line", "Bar line"};

        for (int i = 0; i < 2; i++) {
            final int idx = i;
            boolean result =
                    fileCache.getOrCreateFile(
                            outputFiles[i],
                            fileKey,
                            (newFile) -> {
                                Files.write(fileContents[idx], newFile, Charsets.UTF_8);
                                return null;
                            });
            assertTrue(result);
        }

        assertEquals(1, fileCache.getHits());
        assertEquals(1, fileCache.getMisses());
        assertEquals(fileContents[0], Files.toString(outputFiles[1], Charsets.UTF_8));
    }

    @Test
    public void testDifferentFilesDifferentKeys() throws IOException {
        FileCache fileCache = FileCache.withSingleProcessLocking(cacheFolder.getRoot());

        File[] outputFiles = {testFolder.newFile(), testFolder.newFile()};
        String[] fileKeys = {"foo", "bar"};
        String[] fileContents = {"Foo line", "Bar line"};

        for (int i = 0; i < 2; i++) {
            final int idx = i;
            boolean result =
                    fileCache.getOrCreateFile(
                            outputFiles[i],
                            fileKeys[i],
                            (newFile) -> {
                                Files.write(fileContents[idx], newFile, Charsets.UTF_8);
                                return null;
                            });
            assertTrue(result);
        }

        assertEquals(0, fileCache.getHits());
        assertEquals(2, fileCache.getMisses());
        assertEquals(fileContents[1], Files.toString(outputFiles[1], Charsets.UTF_8));
    }

    @Test
    public void testDirectory() throws IOException {
        FileCache fileCache = FileCache.withSingleProcessLocking(cacheFolder.getRoot());

        // Test different directories same key
        File[] outputDirs = {testFolder.newFolder(), testFolder.newFolder()};
        Files.touch(new File(outputDirs[0], "tmp0"));
        Files.touch(new File(outputDirs[1], "tmp1"));

        String dirKey = "foo";
        String[] fileNames = {"fooFile", "barFile"};
        String[] fileContents = {"Foo line", "Bar line"};

        for (int i = 0; i < 2; i++) {
            final int idx = i;
            boolean result =
                    fileCache.getOrCreateFile(
                            outputDirs[i],
                            dirKey,
                            (newFolder) -> {
                                FileUtils.mkdirs(newFolder);
                                Files.write(
                                        fileContents[idx],
                                        new File(newFolder, fileNames[idx]),
                                        Charsets.UTF_8);
                                return null;
                            });
            assertTrue(result);
        }

        assertEquals(1, fileCache.getHits());
        assertEquals(1, fileCache.getMisses());
        assertEquals(1, outputDirs[0].list().length);
        assertEquals(1, outputDirs[1].list().length);
        assertEquals(
                fileContents[0],
                Files.toString(new File(outputDirs[0], fileNames[0]), Charsets.UTF_8));
        assertEquals(
                fileContents[0],
                Files.toString(new File(outputDirs[1], fileNames[0]), Charsets.UTF_8));
    }

    @Test
    public void testMultiThreadsWithInterProcessLockingDisabled() throws IOException {
        testMultiThreads(false);
    }

    @Test
    public void testMultiThreadsWithInterProcessLockingEnabled() throws IOException {
        testMultiThreads(true);
    }

    private void testMultiThreads(boolean interProcessLocking) throws IOException {
        FileCache fileCache =
                interProcessLocking
                        ? FileCache.withInterProcessLocking(cacheFolder.getRoot())
                        : FileCache.withSingleProcessLocking(cacheFolder.getRoot());

        // Test different files same key
        File[] outputFiles = {testFolder.newFile(), testFolder.newFile()};
        String fileKey = "foo";
        String fileContent = "Foo line";

        // Since we use the same key, the threads are not allowed to run concurrently.
        ConcurrencyTestHelper helper = new ConcurrencyTestHelper(false);
        List<Thread> threads = Lists.newLinkedList();
        for (File outputFile : outputFiles) {
            Runnable runnable = () -> {
                try {
                    boolean result =
                            fileCache.getOrCreateFile(
                                    outputFile,
                                    fileKey,
                                    (newFile) -> {
                                        helper.threadExecuted();
                                        Files.write(fileContent, newFile, Charsets.UTF_8);
                                        helper.waitToBeAllowedToFinish();
                                        return null;
                                    });
                    assertTrue(result);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            };
            threads.add(new Thread(runnable));
        }

        helper.startThreads(threads);
        helper.waitForThreadsToExecute();

        // Make sure that only one thread has executed so far
        assertEquals(1, helper.getExecutedThreadCount());

        helper.allowThreadsToFinish();
        helper.waitForThreadsToFinish();

        assertEquals(1, fileCache.getHits());
        assertEquals(1, fileCache.getMisses());
        assertEquals(fileContent, Files.toString(outputFiles[0], Charsets.UTF_8));
        assertEquals(fileContent, Files.toString(outputFiles[1], Charsets.UTF_8));
    }

    @Test
    public void testDifferentCaches() throws IOException {
        FileCache[] fileCaches = {
            FileCache.withSingleProcessLocking(cacheFolder.newFolder()),
            FileCache.withSingleProcessLocking(cacheFolder.newFolder())
        };

        // Test different files same key, different caches
        File[] outputFiles = {testFolder.newFile(), testFolder.newFile()};
        String fileKey = "foo";
        String[] fileContents = {"Foo line", "Bar line"};

        // Since we use different caches, the threads are allowed to run concurrently.
        ConcurrencyTestHelper helper = new ConcurrencyTestHelper(true);
        List<Thread> threads = Lists.newLinkedList();
        for (int i = 0; i < 2; i++) {
            final int idx = i;
            Runnable runnable = () -> {
                try {
                    boolean result =
                            fileCaches[idx].getOrCreateFile(
                                    outputFiles[idx],
                                    fileKey,
                                    (newFile) -> {
                                        helper.threadExecuted();
                                        Files.write(fileContents[idx], newFile, Charsets.UTF_8);
                                        helper.waitToBeAllowedToFinish();
                                        return null;
                                    });
                    assertTrue(result);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            };
            threads.add(new Thread(runnable));
        }

        helper.startThreads(threads);
        helper.waitForThreadsToExecute();

        // Make sure that more than one thread have executed so far
        assertTrue(helper.getExecutedThreadCount() > 1);

        helper.allowThreadsToFinish();
        helper.waitForThreadsToFinish();

        assertEquals(0, fileCaches[0].getHits());
        assertEquals(1, fileCaches[0].getMisses());
        assertEquals(0, fileCaches[1].getHits());
        assertEquals(1, fileCaches[1].getMisses());
        assertEquals(fileContents[0], Files.toString(outputFiles[0], Charsets.UTF_8));
        assertEquals(fileContents[1], Files.toString(outputFiles[1], Charsets.UTF_8));
    }

    @Test
    public void testCacheDirectoryNotAlreadyExist() throws IOException {
        FileCache fileCache =
                FileCache.withSingleProcessLocking(new File(cacheFolder.getRoot(), "foo"));

        File outputFile = testFolder.newFile();
        String fileKey = "foo";
        String fileContent = "Foo line";

        boolean result =
                fileCache.getOrCreateFile(
                        outputFile,
                        fileKey,
                        (newFile) -> {
                            Files.write(fileContent, newFile, Charsets.UTF_8);
                            return null;
                        });
        assertTrue(result);

        assertEquals(0, fileCache.getHits());
        assertEquals(1, fileCache.getMisses());
        assertEquals(fileContent, Files.toString(outputFile, Charsets.UTF_8));
    }

    @Test
    public void testComplexFileKey() throws IOException {
        FileCache fileCache = FileCache.withSingleProcessLocking(cacheFolder.getRoot());

        File outputFile = testFolder.newFile();
        String fileKey = "foo`-=[]\\;',./~!@#$%^&*()_+{}|:\"<>?";
        String fileContent = "Foo line";

        boolean result =
                fileCache.getOrCreateFile(
                        outputFile,
                        fileKey,
                        (newFile) -> {
                            Files.write(fileContent, newFile, Charsets.UTF_8);
                            return null;
                        });
        assertTrue(result);

        assertEquals(0, fileCache.getHits());
        assertEquals(1, fileCache.getMisses());
        assertEquals(fileContent, Files.toString(outputFile, Charsets.UTF_8));
    }

    @Test
    public void testFileNotCreated() throws IOException {
        FileCache fileCache = FileCache.withSingleProcessLocking(cacheFolder.getRoot());

        File outputFile = testFolder.newFile();
        String fileKey = "foo";

        boolean result = fileCache.getOrCreateFile(outputFile, fileKey, (newFile) -> null);
        assertFalse(result);

        assertEquals(0, fileCache.getHits());
        assertEquals(1, fileCache.getMisses());
    }

    @Test
    public void testFileNotCreatedIfNoFileExtension() throws IOException {
        FileCache fileCache = FileCache.withSingleProcessLocking(cacheFolder.getRoot());

        File outputFile = testFolder.newFile("x.bar");
        String fileKey = "foo";
        String fileContent = "Foo line";

        boolean result =
                fileCache.getOrCreateFile(
                        outputFile,
                        fileKey,
                        (newFile) -> {
                            if (Files.getFileExtension(newFile.getName()).equals("bar")) {
                                Files.write(fileContent, newFile, Charsets.UTF_8);
                            }
                            return null;
                        });
        assertTrue(result);

        assertEquals(0, fileCache.getHits());
        assertEquals(1, fileCache.getMisses());
        assertEquals(fileContent, Files.toString(outputFile, Charsets.UTF_8));
    }

    @Test
    public void testInterThreadLockingSameFile() {
        FileCache fileCache = FileCache.withSingleProcessLocking(cacheFolder.getRoot());
        String[] fileNames = {"foo", "foo", "foobar".substring(0, 3)};

        // Since we access the same file, the threads are not allowed to run concurrently.
        for (boolean withInterProcessLocking : new boolean[] {true, false}) {
            ConcurrencyTestHelper helper = new ConcurrencyTestHelper(false);
            List<Thread> threads = Lists.newLinkedList();
            for (String fileName : fileNames) {
                Runnable runnable = () -> {
                    try {
                        fileCache.doLocked(
                                new File(testFolder.getRoot(), fileName),
                                () -> {
                                    helper.threadExecuted();
                                    helper.waitToBeAllowedToFinish();
                                },
                                withInterProcessLocking);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                };
                threads.add(new Thread(runnable));
            }

            helper.startThreads(threads);
            helper.waitForThreadsToExecute();

            // Make sure that only one thread has executed so far
            assertEquals(1, helper.getExecutedThreadCount());

            helper.allowThreadsToFinish();
            helper.waitForThreadsToFinish();
        }
    }

    @Test
    public void testInterThreadLockingDifferentFiles() {
        FileCache fileCache = FileCache.withSingleProcessLocking(cacheFolder.getRoot());
        String[] fileNames = {"foo1", "foo2", "foo3"};

        // Since we access different files, the threads are allowed to run concurrently.
        for (boolean withInterProcessLocking : new boolean[] {true, false}) {
            ConcurrencyTestHelper helper = new ConcurrencyTestHelper(true);
            List<Thread> threads = Lists.newLinkedList();
            for (String fileName : fileNames) {
                Runnable runnable = () -> {
                    try {
                        fileCache.doLocked(
                                new File(testFolder.getRoot(), fileName),
                                () -> {
                                    helper.threadExecuted();
                                    helper.waitToBeAllowedToFinish();
                                },
                                withInterProcessLocking);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                };
                threads.add(new Thread(runnable));
            }

            helper.startThreads(threads);
            helper.waitForThreadsToExecute();

            // Make sure that more than one thread have executed so far
            assertTrue(helper.getExecutedThreadCount() > 1);

            helper.allowThreadsToFinish();
            helper.waitForThreadsToFinish();
        }
    }
}
