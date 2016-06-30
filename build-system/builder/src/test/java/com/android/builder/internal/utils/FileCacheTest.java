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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.repository.Revision;
import com.android.utils.FileUtils;
import com.google.common.base.Joiner;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Test cases for {@link FileCache}. */
public class FileCacheTest {

    @Rule public TemporaryFolder cacheFolder = new TemporaryFolder();

    @Rule public TemporaryFolder testFolder = new TemporaryFolder();

    @Test
    public void testSameInputSameOutput() throws IOException {
        FileCache fileCache = FileCache.withSingleProcessLocking(cacheFolder.getRoot());

        File outputFile = testFolder.newFile();
        FileCache.Inputs inputs =
                new FileCache.Inputs.Builder().put("file", new File("foo")).build();
        String[] fileContents = {"Foo line", "Bar line"};

        boolean result =
                fileCache.getOrCreateFile(
                        outputFile,
                        inputs,
                        (newFile) -> {
                            Files.write(fileContents[0], newFile, StandardCharsets.UTF_8);
                            return null;
                        });
        assertTrue(result);

        assertEquals(0, fileCache.getHits());
        assertEquals(1, fileCache.getMisses());
        assertEquals(fileContents[0], Files.toString(outputFile, StandardCharsets.UTF_8));

        outputFile.delete();

        fileCache.getOrCreateFile(
                outputFile,
                inputs,
                (newFile) -> {
                    Files.write(fileContents[1], newFile, StandardCharsets.UTF_8);
                    return null;
                });

        assertEquals(1, fileCache.getHits());
        assertEquals(1, fileCache.getMisses());
        assertEquals(fileContents[0], Files.toString(outputFile, StandardCharsets.UTF_8));
    }

    @Test
    public void testSameInputDifferentOutputs() throws IOException {
        FileCache fileCache = FileCache.withSingleProcessLocking(cacheFolder.getRoot());

        File[] outputFiles = {testFolder.newFile(), testFolder.newFile()};
        FileCache.Inputs inputs =
                new FileCache.Inputs.Builder().put("file", new File("foo")).build();
        String[] fileContents = {"Foo line", "Bar line"};

        for (int i = 0; i < 2; i++) {
            File outputFile = outputFiles[i];
            String fileContent = fileContents[i];

            boolean result =
                    fileCache.getOrCreateFile(
                            outputFile,
                            inputs,
                            (newFile) -> {
                                Files.write(fileContent, newFile, StandardCharsets.UTF_8);
                                return null;
                            });
            assertTrue(result);
        }

        assertEquals(1, fileCache.getHits());
        assertEquals(1, fileCache.getMisses());
        assertEquals(fileContents[0], Files.toString(outputFiles[1], StandardCharsets.UTF_8));
    }

    @Test
    public void testDifferentInputsSameOutput() throws IOException {
        FileCache fileCache = FileCache.withSingleProcessLocking(cacheFolder.getRoot());

        File outputFile = testFolder.newFile();
        FileCache.Inputs[] inputList = {
            new FileCache.Inputs.Builder().put("file1", new File("foo")).build(),
            new FileCache.Inputs.Builder().put("file2", new File("bar")).build(),
        };
        String[] fileContents = {"Foo line", "Bar line"};

        for (int i = 0; i < 2; i++) {
            FileCache.Inputs inputs = inputList[i];
            String fileContent = fileContents[i];

            boolean result =
                    fileCache.getOrCreateFile(
                            outputFile,
                            inputs,
                            (newFile) -> {
                                Files.write(fileContent, newFile, StandardCharsets.UTF_8);
                                return null;
                            });
            assertTrue(result);
        }

        assertEquals(0, fileCache.getHits());
        assertEquals(2, fileCache.getMisses());
        assertEquals(fileContents[1], Files.toString(outputFile, StandardCharsets.UTF_8));
    }

    @Test
    public void testDifferentInputsDifferentOutputs() throws IOException {
        FileCache fileCache = FileCache.withSingleProcessLocking(cacheFolder.getRoot());

        File[] outputFiles = {testFolder.newFile(), testFolder.newFile()};
        FileCache.Inputs[] inputList = {
            new FileCache.Inputs.Builder().put("file1", new File("foo")).build(),
            new FileCache.Inputs.Builder().put("file2", new File("bar")).build(),
        };
        String[] fileContents = {"Foo line", "Bar line"};

        for (int i = 0; i < 2; i++) {
            File outputFile = outputFiles[i];
            FileCache.Inputs inputs = inputList[i];
            String fileContent = fileContents[i];

            boolean result =
                    fileCache.getOrCreateFile(
                            outputFile,
                            inputs,
                            (newFile) -> {
                                Files.write(fileContent, newFile, StandardCharsets.UTF_8);
                                return null;
                            });
            assertTrue(result);
        }

        assertEquals(0, fileCache.getHits());
        assertEquals(2, fileCache.getMisses());
        assertEquals(fileContents[1], Files.toString(outputFiles[1], StandardCharsets.UTF_8));
    }

    @Test
    public void testDirectory() throws IOException {
        FileCache fileCache = FileCache.withSingleProcessLocking(cacheFolder.getRoot());

        // Test same input different outputs
        File[] outputDirs = {testFolder.newFolder(), testFolder.newFolder()};
        Files.touch(new File(outputDirs[0], "tmp0"));
        Files.touch(new File(outputDirs[1], "tmp1"));

        FileCache.Inputs inputs =
                new FileCache.Inputs.Builder().put("file", new File("foo")).build();
        String[] fileNames = {"fooFile", "barFile"};
        String[] fileContents = {"Foo line", "Bar line"};

        for (int i = 0; i < 2; i++) {
            File outputDir = outputDirs[i];
            String fileName = fileNames[i];
            String fileContent = fileContents[i];

            boolean result =
                    fileCache.getOrCreateFile(
                            outputDir,
                            inputs,
                            (newFolder) -> {
                                FileUtils.mkdirs(newFolder);
                                Files.write(
                                        fileContent,
                                        new File(newFolder, fileName),
                                        StandardCharsets.UTF_8);
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
                Files.toString(new File(outputDirs[0], fileNames[0]), StandardCharsets.UTF_8));
        assertEquals(
                fileContents[0],
                Files.toString(new File(outputDirs[1], fileNames[0]), StandardCharsets.UTF_8));
    }

    @Test
    public void testCacheDirectoryNotAlreadyExist() throws IOException {
        FileCache fileCache =
                FileCache.withSingleProcessLocking(new File(cacheFolder.getRoot(), "foo"));

        File outputFile = testFolder.newFile();
        FileCache.Inputs inputs =
                new FileCache.Inputs.Builder().put("file", new File("foo")).build();
        String fileContent = "Foo line";

        boolean result =
                fileCache.getOrCreateFile(
                        outputFile,
                        inputs,
                        (newFile) -> {
                            Files.write(fileContent, newFile, StandardCharsets.UTF_8);
                            return null;
                        });
        assertTrue(result);

        assertEquals(0, fileCache.getHits());
        assertEquals(1, fileCache.getMisses());
        assertEquals(fileContent, Files.toString(outputFile, StandardCharsets.UTF_8));
    }

    @Test
    public void testUnusualInput() throws IOException {
        FileCache fileCache = FileCache.withSingleProcessLocking(cacheFolder.getRoot());

        File outputFile = testFolder.newFile();
        FileCache.Inputs inputs = new FileCache.Inputs.Builder().put("file", new File("")).build();
        String fileContent = "Foo line";

        boolean result =
                fileCache.getOrCreateFile(
                        outputFile,
                        inputs,
                        (newFile) -> {
                            Files.write(fileContent, newFile, StandardCharsets.UTF_8);
                            return null;
                        });
        assertTrue(result);

        assertEquals(0, fileCache.getHits());
        assertEquals(1, fileCache.getMisses());
        assertEquals(fileContent, Files.toString(outputFile, StandardCharsets.UTF_8));
    }

    @Test
    public void testFileProducerPreconditions() throws Exception {
        FileCache fileCache = FileCache.withSingleProcessLocking(cacheFolder.getRoot());

        // Test the case when the output file already exists
        File outputFile = testFolder.newFile();
        FileCache.Inputs inputs =
                new FileCache.Inputs.Builder().put("file", new File("foo")).build();
        fileCache.getOrCreateFile(
                outputFile,
                inputs,
                (newFile) -> {
                    assertFalse(newFile.exists());
                    assertTrue(newFile.getParentFile().exists());
                    return null;
                });

        // Test the case when the output file does not already exist
        outputFile = new File(testFolder.getRoot(), "tmp1/tmp2");
        inputs = new FileCache.Inputs.Builder().put("file", new File("bar")).build();
        fileCache.getOrCreateFile(
                outputFile,
                inputs,
                (newFile) -> {
                    assertFalse(newFile.exists());
                    assertTrue(newFile.getParentFile().exists());
                    return null;
                });
    }

    @Test
    public void testOutputFileNotCreated() throws IOException {
        FileCache fileCache = FileCache.withSingleProcessLocking(cacheFolder.getRoot());

        File outputFile = testFolder.newFile();
        FileCache.Inputs inputs =
                new FileCache.Inputs.Builder().put("file", new File("foo")).build();

        boolean result = fileCache.getOrCreateFile(outputFile, inputs, (newFile) -> null);
        assertFalse(result);

        assertEquals(0, fileCache.getHits());
        assertEquals(1, fileCache.getMisses());
    }

    @Test
    public void testOutputFileNotCreatedIfNoFileExtension() throws IOException {
        FileCache fileCache = FileCache.withSingleProcessLocking(cacheFolder.getRoot());

        File outputFile = testFolder.newFile("x.bar");
        FileCache.Inputs inputs =
                new FileCache.Inputs.Builder().put("file", new File("foo")).build();
        String fileContent = "Foo line";

        boolean result =
                fileCache.getOrCreateFile(
                        outputFile,
                        inputs,
                        (newFile) -> {
                            if (Files.getFileExtension(newFile.getName()).equals("bar")) {
                                Files.write(fileContent, newFile, StandardCharsets.UTF_8);
                            }
                            return null;
                        });
        assertTrue(result);

        assertEquals(0, fileCache.getHits());
        assertEquals(1, fileCache.getMisses());
        assertEquals(fileContent, Files.toString(outputFile, StandardCharsets.UTF_8));
    }

    @Test
    public void testNoCache() throws IOException {
        FileCache fileCache = FileCache.NO_CACHE;

        // Test the case when the output file already exists
        File outputFile = testFolder.newFile();
        FileCache.Inputs inputs =
                new FileCache.Inputs.Builder().put("file", new File("foo")).build();
        String fileContent = "Foo line";

        boolean result =
                fileCache.getOrCreateFile(
                        outputFile,
                        inputs,
                        (newFile) -> {
                            assertFalse(newFile.exists());
                            assertTrue(newFile.getParentFile().exists());
                            Files.write(fileContent, newFile, StandardCharsets.UTF_8);
                            return null;
                        });
        assertTrue(result);

        assertEquals(0, fileCache.getHits());
        assertEquals(0, fileCache.getMisses());
        assertEquals(fileContent, Files.toString(outputFile, StandardCharsets.UTF_8));

        // Test the case when the output file does not already exist
        outputFile = new File(testFolder.getRoot(), "tmp1/tmp2");
        inputs = new FileCache.Inputs.Builder().put("file", new File("bar")).build();

        result =
                fileCache.getOrCreateFile(
                        outputFile,
                        inputs,
                        (newFile) -> {
                            assertFalse(newFile.exists());
                            assertTrue(newFile.getParentFile().exists());
                            Files.write(fileContent, newFile, StandardCharsets.UTF_8);
                            return null;
                        });
        assertTrue(result);

        assertEquals(0, fileCache.getHits());
        assertEquals(0, fileCache.getMisses());
        assertEquals(fileContent, Files.toString(outputFile, StandardCharsets.UTF_8));
    }

    @Test
    public void testInputsGetKey() {
        // Test all types of input parameters
        File inputFile =
                new File(
                        "/Users/foo/Android/Sdk/extras/android/m2repository/com/android/support/"
                                + "support-annotations/23.3.0/support-annotations-23.3.0.jar");
        FileCache.Inputs inputs =
                new FileCache.Inputs.Builder()
                        .put("file", inputFile)
                        .put("buildToolsRevision", Revision.parseRevision("23.0.3").toString())
                        .put("jumboMode", true)
                        .put("optimize", false)
                        .put("multiDex", true)
                        .put("classpath", new File("foo"))
                        .build();
        assertThat(inputs.toString())
                .isEqualTo(
                        Joiner.on(System.lineSeparator())
                                .join(
                                        "file=" + inputFile.getPath(),
                                        "buildToolsRevision=23.0.3",
                                        "jumboMode=true",
                                        "optimize=false",
                                        "multiDex=true",
                                        "classpath=foo"));
        // In the assertion below, we represent the expected hash code as the regular expression
        // \w{40} since the computed hash code in the short key might be different on different
        // platforms
        assertThat(inputs.getKey()).matches("\\w{40}");

        // Test relative input file path
        inputFile = new File("com.android.support/design/23.3.0/jars/classes.jar");
        inputs = new FileCache.Inputs.Builder().put("file", inputFile).build();
        assertThat(inputs.toString()).isEqualTo("file=" + inputFile.getPath());
        assertThat(inputs.getKey()).isEqualTo("25dcc2247956f01b9dbdca420eff87c96aaf2874");

        // Test Windows-based input file path
        inputFile =
                new File(
                        "C:\\Users\\foo\\Android\\Sdk\\extras\\android\\m2repository\\"
                                + "com\\android\\support\\support-annotations\\23.3.0\\"
                                + "support-annotations-23.3.0.jar");
        inputs = new FileCache.Inputs.Builder().put("file", inputFile).build();
        assertThat(inputs.toString()).isEqualTo("file=" + inputFile.getPath());
        assertThat(inputs.getKey()).isEqualTo("d78d98a050e19057d83ad84ddebde43e2f8e67d7");

        // Test unusual inputs
        inputFile = new File("foo`-=[]\\\\;',./~!@#$%^&*()_+{}|:\\\"<>?");
        inputs = new FileCache.Inputs.Builder().put("file", inputFile).build();
        assertThat(inputs.toString()).isEqualTo("file=" + inputFile.getPath());
        assertThat(inputs.getKey()).isEqualTo("7f205499565a454d0186f34313e63281c7192a43");

        // Test empty-string input
        inputFile = new File("");
        inputs = new FileCache.Inputs.Builder().put("file", inputFile).build();
        assertThat(inputs.toString()).isEqualTo("file=");
        assertThat(inputs.getKey()).isEqualTo("3fe9ece2d6113d8db5c0c5576cc1378823d839ab");

        // Test empty inputs
        try {
            new FileCache.Inputs.Builder().build();
            fail("expected IllegalStateException");
        } catch (IllegalStateException exception) {
            assertThat(exception).hasMessage("Inputs must not be empty.");
        }

        // Test duplicate parameters with the same name and type
        inputs = new FileCache.Inputs.Builder().put("arg", "true").put("arg", "false").build();
        assertThat(inputs.toString()).isEqualTo("arg=false");

        // Test duplicate parameters with the same name and different types
        inputs = new FileCache.Inputs.Builder().put("arg", "true").put("arg", false).build();
        assertThat(inputs.toString()).isEqualTo("arg=false");

        // Test duplicate parameters interleaved with other parameters
        inputs =
                new FileCache.Inputs.Builder()
                        .put("arg1", "true")
                        .put("arg2", "true")
                        .put("arg1", "false")
                        .build();
        assertThat(inputs.toString())
                .isEqualTo("arg1=false" + System.lineSeparator() + "arg2=true");
    }
}
