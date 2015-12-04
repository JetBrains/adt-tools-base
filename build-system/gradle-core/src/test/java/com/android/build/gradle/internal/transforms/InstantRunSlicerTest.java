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

package com.android.build.gradle.internal.transforms;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.transform.Context;
import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.Format;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.Status;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import org.gradle.api.logging.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Test for the {@link InstantRunSlicer}
 *
 * The tests creates 20 files in an input folder that should be initially sliced into 10 slices
 * according to their natural order.
 *
 * For instance, slice-0 will contain file1.class, file0.class, file10.class
 */
public class InstantRunSlicerTest {

    @Mock
    Context context;

    @Mock
    Logger logger;

    File instantRunSupportDir;

    @Before
    public void beforeTest() throws IOException {
        MockitoAnnotations.initMocks(this);
        instantRunSupportDir = createTmpDirectory("instantRunSupport");
    }

    @After
    public void afterTest() throws IOException {
        FileUtils.deleteFolder(instantRunSupportDir);
    }


    @Test
    public void testNonIncrementalModeOnlyDirs()
            throws IOException, TransformException, InterruptedException {

        InstantRunSlicer slicer = new InstantRunSlicer(logger, instantRunSupportDir);

        final File inputDir = createTmpDirectory("inputDir");
        final Map<File, Status> changedFiles;
        ImmutableMap.Builder<File, Status> builder = ImmutableMap.builder();
        for (int i=0; i<21; i++) {
            builder.put(createFile(inputDir, "file" + i + ".class"), Status.ADDED);
        }
        changedFiles = builder.build();

        final File outputDir  = createTmpDirectory("outputDir");
        File jarOutputDir = createTmpDirectory("jarOutputDir");
        final File jarOutput = new File(jarOutputDir, "output.jar");

        slicer.transform(context,
                ImmutableList.of(getInput(inputDir, changedFiles)),
                ImmutableList.<TransformInput>of(),
                getOutputProvider(outputDir, jarOutput),
                false /* isIncremental */);

        // assert the file was copied in the output directory.
        File[] files = outputDir.listFiles();
        assertNotNull(files);
        assertThat(files.length).isEqualTo(7);
        for (int i=0; i<7; i++) {
            assertThat(files[0]).named("slice-" + i);
            File slice = files[i];
            assertThat(slice.isDirectory()).isTrue();
            File[] sliceFiles = slice.listFiles();
            assertNotNull(sliceFiles);
            assertThat(sliceFiles.length).isEqualTo(3);
            for (int j=0; j<3; j++) {
                assertThat(sliceFiles[j]).named("file" + (i*5 + j) + ".class");
            }
        }
        FileUtils.deleteFolder(jarOutputDir);
        FileUtils.deleteFolder(inputDir);
    }

    @Test
    public void testIncrementalModeOnlyDirs()
            throws IOException, TransformException, InterruptedException {

        InstantRunSlicer slicer = new InstantRunSlicer(logger, instantRunSupportDir);

        final File inputDir = createTmpDirectory("inputDir");
        Map<File, Status> changedFiles;
        ImmutableMap.Builder<File, Status> builder = ImmutableMap.builder();
        for (int i=0; i<21; i++) {
            builder.put(createFile(inputDir, "file" + i + ".class"), Status.CHANGED);
        }
        changedFiles = builder.build();

        final File outputDir  = createTmpDirectory("outputDir");
        File jarOutputDir = createTmpDirectory("jarOutputDir");
        final File jarOutput = new File(jarOutputDir, "output.jar");

        slicer.transform(context,
                ImmutableList.of(getInput(inputDir, changedFiles)),
                ImmutableList.<TransformInput>of(),
                getOutputProvider(outputDir, jarOutput),
                false /* isIncremental */);

        // incrementally change a few slices.
        File file7 = createFile(inputDir, "file7.class", "updated file7.class");
        changedFiles = ImmutableMap.of(
                file7, Status.CHANGED,
                new File(inputDir, "file14.class"), Status.REMOVED);

        slicer.transform(context,
                ImmutableList.of(getInput(inputDir, changedFiles)),
                ImmutableList.<TransformInput>of(),
                getOutputProvider(outputDir, jarOutput),
                true /* isIncremental */);

        // assert the file was copied in the output directory.
        File[] slices = outputDir.listFiles();
        assertNotNull(slices);
        assertThat(slices.length).isEqualTo(7);

        // file7 should be in the last slice slice.
        File slice6 = getFileByName(slices, "slice-6");
        assertNotNull(slice6);
        File[] slice6Files = slice6.listFiles();
        File updatedFile7 = getFileByName(slice6Files, "file7.class");
        assertNotNull(updatedFile7);
        assertThat("updated file7.class").isEqualTo(
                FileUtils.loadFileWithUnixLineSeparators(updatedFile7));

        // file14 was in the slice 3, it should be gone.
        File slice3 = getFileByName(slices, "slice-2");
        assertNotNull(slice3);
        File[] slice3Files = slice3.listFiles();
        assertNotNull(slice3Files);
        assertNull(getFileByName(slice3Files, "file14.class"));

        FileUtils.deleteFolder(jarOutputDir);
        FileUtils.deleteFolder(inputDir);
    }

    @Nullable
    private static File getFileByName(@Nullable File[] files, @NonNull String fileName) {
        if (files == null) {
            return null;
        }
        for (File file : files) {
            if (file.getName().equals(fileName)) {
                return file;
            }
        }
        return null;
    }

    private static TransformInput getInput(final File inputDir, final Map<File, Status> changedFiles) {
        return new TransformInput() {
            @NonNull
            @Override
            public Collection<JarInput> getJarInputs() {
                return ImmutableList.of();
            }

            @NonNull
            @Override
            public Collection<DirectoryInput> getDirectoryInputs() {
                return ImmutableList.<DirectoryInput>of(
                        new DirectoryInputForTests() {
                            @NonNull
                            @Override
                            public File getFile() {
                                return inputDir;
                            }

                            @NonNull
                            @Override
                            public Map<File, Status> getChangedFiles() {
                                return changedFiles;
                            }
                        });
            }
        };
    }

    private static TransformOutputProvider getOutputProvider(final File outputDir, final File jarOutput) {
        return new TransformOutputProvider() {

            @Override
            public void deleteAll() throws IOException {

            }

            @NonNull
            @Override
            public File getContentLocation(@NonNull String name,
                    @NonNull Set<QualifiedContent.ContentType> types,
                    @NonNull Set<QualifiedContent.Scope> scopes, @NonNull Format format) {
                if (format == Format.DIRECTORY) {
                    return new File(outputDir, name);
                }
                assertTrue(format == Format.JAR);
                return jarOutput;
            }
        };
    }

    private abstract static class DirectoryInputForTests implements DirectoryInput {

        @NonNull
        @Override
        public String getName() {
            return "test";
        }

        @NonNull
        @Override
        public Set<ContentType> getContentTypes() {
            return ImmutableSet.<ContentType>of(DefaultContentType.CLASSES);
        }

        @NonNull
        @Override
        public Set<Scope> getScopes() {
            return ImmutableSet.of(Scope.PROJECT);
        }
    }

    private static File createTmpDirectory(String name) throws IOException {
        File dir = File.createTempFile(name, "");
        assertTrue(dir.delete());
        assertTrue(dir.mkdirs());
        return dir;
    }

    private static File createFile(File directory, String name) throws IOException {
        return createFile(directory, name, name);
    }

    private static File createFile(File directory, String name, String content) throws IOException {
        File newFile = new File(directory, name);
        FileWriter writer = new FileWriter(newFile);
        try {
            writer.append(content);
        } finally {
            writer.close();
        }
        return newFile;
    }
}
