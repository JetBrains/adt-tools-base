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
import static org.mockito.Mockito.when;

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
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.incremental.InstantRunPatchingPolicy;
import com.android.build.gradle.internal.pipeline.TransformInvocationBuilder;
import com.android.build.gradle.internal.scope.InstantRunVariantScope;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;

import org.gradle.api.logging.Logger;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    @Mock
    InstantRunVariantScope variantScope;

    @Mock
    InstantRunBuildContext instantRunBuildContext;

    @Rule
    public TemporaryFolder instantRunSupportDir = new TemporaryFolder();
    @Rule
    public TemporaryFolder inputDir = new TemporaryFolder();
    @Rule
    public TemporaryFolder outputDir = new TemporaryFolder();
    @Rule
    public TemporaryFolder jarOutputDir = new TemporaryFolder();

    private File jarOutput;

    @Before
    public void beforeTest() throws IOException {
        MockitoAnnotations.initMocks(this);
        when(variantScope.getInstantRunSupportDir()).thenReturn(instantRunSupportDir.getRoot());
        when(variantScope.getRestartDexOutputFolder()).thenReturn(instantRunSupportDir.getRoot());
        when(instantRunBuildContext.getPatchingPolicy()).thenReturn(
                InstantRunPatchingPolicy.MULTI_DEX);
        when(variantScope.getInstantRunBuildContext()).thenReturn(instantRunBuildContext);

        jarOutput = new File(jarOutputDir.getRoot(), "output.jar");
    }

    @Test
    public void testNonIncrementalModeOnlyDirs()
            throws IOException, TransformException, InterruptedException {

        InstantRunSlicer slicer = new InstantRunSlicer(logger, variantScope);

        final Map<File, Status> changedFiles;
        ImmutableMap.Builder<File, Status> builder = ImmutableMap.builder();
        List<String> listOfFiles = new ArrayList<>();
        for (int i=0; i<21; i++) {
            listOfFiles.add("file" + i + ".class");
            builder.put(createFile(getInputDir(), "file" + i + ".class"), Status.ADDED);
        }
        changedFiles = builder.build();

        slicer.transform(new TransformInvocationBuilder(context)
                .addInputs(ImmutableList.of(getInput(getInputDir(), changedFiles)))
                .addOutputProvider(getOutputProvider(getOutputDir(), jarOutput))
                .build());

        // assert the file was copied in the output directory.
        File[] files = getOutputDir().listFiles();
        assertNotNull(files);

        assertThat(files.length).isEqualTo(InstantRunSlicer.NUMBER_OF_SLICES_FOR_PROJECT_CLASSES);
        for (int i=0; i<InstantRunSlicer.NUMBER_OF_SLICES_FOR_PROJECT_CLASSES; i++) {
            assertThat(files[0]).named("slice-" + i);
            File slice = files[i];
            assertThat(slice.isDirectory()).isTrue();
            File[] sliceFiles = slice.listFiles();
            assertNotNull(sliceFiles);
            assertThat(sliceFiles.length).isAtLeast(1);
            for (File sliceFile : sliceFiles) {
                if (sliceFile.getName().startsWith("file")) {
                    assertThat(listOfFiles.remove(sliceFile.getName())).isTrue();
                }
            }
        }
        assertThat(listOfFiles).isEmpty();
    }

    @Test
    public void testIncrementalModeOnlyDirs()
            throws IOException, TransformException, InterruptedException {

        InstantRunSlicer slicer = new InstantRunSlicer(logger, variantScope);

        Map<File, Status> changedFiles;
        ImmutableMap.Builder<File, Status> builder = ImmutableMap.builder();
        for (int i=0; i<21; i++) {
            builder.put(createFile(getInputDir(), "file" + i + ".class"), Status.CHANGED);
        }
        changedFiles = builder.build();

        slicer.transform(new TransformInvocationBuilder(context)
                .addInputs(ImmutableList.of(getInput(getInputDir(), changedFiles)))
                .addOutputProvider(getOutputProvider(getOutputDir(), jarOutput))
                .build());

        // incrementally change a few slices.
        File file7 = createFile(getInputDir(), "file7.class", "updated file7.class");
        changedFiles = ImmutableMap.of(
                file7, Status.CHANGED,
                new File(getInputDir(), "file14.class"), Status.REMOVED);

        slicer.transform(new TransformInvocationBuilder(context)
                .addInputs(ImmutableList.of(getInput(getInputDir(), changedFiles)))
                .addOutputProvider(getOutputProvider(getOutputDir(), jarOutput))
                .setIncrementalMode(true)
                .build());

        // assert the file was copied in the output directory.
        File[] slices = getOutputDir().listFiles();
        assertNotNull(slices);
        assertThat(slices.length).isEqualTo(InstantRunSlicer.NUMBER_OF_SLICES_FOR_PROJECT_CLASSES);

        // file7 should have been updated.
        int bucket = Math.abs((file7.getName()).hashCode() % 10);
        File file7Slice = getFileByName(slices, "slice_" + bucket);
        assertNotNull(file7Slice);
        File[] slice6Files = file7Slice.listFiles();
        File updatedFile7 = getFileByName(slice6Files, "file7.class");
        assertNotNull(updatedFile7);
        assertThat(FileUtils.loadFileWithUnixLineSeparators(updatedFile7))
                .isEqualTo("updated file7.class");

        // file14 should have been removed
        bucket = Math.abs(("file14.class").hashCode() % 10);
        File file14Bucket = getFileByName(slices, "slice_" + bucket);
        assertNotNull(file14Bucket);
        File[] file14BucketFiles = file14Bucket.listFiles();
        assertNotNull(file14BucketFiles);
        assertNull(getFileByName(file14BucketFiles, "file14.class"));
    }

    @Test
    public void testNonIncrementalRemovingClass()
            throws IOException, TransformException, InterruptedException {
        testRemovingClass(false  /* isIncremental */);
    }

    @Test
    public void testIncrementalRemovingClass()
            throws IOException, TransformException, InterruptedException {
        testRemovingClass(true /* isIncremental */);
    }

    private void testRemovingClass(boolean isIncremental)
            throws IOException, TransformException, InterruptedException {
        InstantRunSlicer slicer = new InstantRunSlicer(logger, variantScope);

        String packagePath = "com/foo/bar";
        File singleClassFile = createFile(new File(getInputDir(), packagePath), "file0.class");
        Map<File, Status> changedFiles = ImmutableMap.of(singleClassFile, Status.ADDED);

        slicer.transform(new TransformInvocationBuilder(context)
                .addInputs(ImmutableList.of(getInput(getInputDir(), changedFiles)))
                .addOutputProvider(getOutputProvider(getOutputDir(), jarOutput))
                .build());

        File[] outputSlices = getOutputDir().listFiles();
        assertThat(outputSlices).isNotNull();
        assertThat(outputSlices).hasLength(InstantRunSlicer.NUMBER_OF_SLICES_FOR_PROJECT_CLASSES);

        Optional<Integer> sliceForFile =
                findSliceForFile(getOutputDir(), getInputDir(), singleClassFile);
        assertThat(sliceForFile.isPresent());
        int slot = sliceForFile.get();

        File outputSlice = outputSlices[slot];
        String sliceName = outputSlice.getName();
        File outputFile = new File(new File(outputSlice, packagePath), "file0.class");
        assertThat(outputFile.exists()).isTrue();

        // delete the input file.
        assertThat(singleClassFile.delete()).isTrue();

        if (!isIncremental) {
            // delete the output directory and create a new clean one.
            FileUtils.deletePath(getOutputDir());
            outputDir.create();
            changedFiles = ImmutableMap.of();
        } else {
            changedFiles = ImmutableMap.of(singleClassFile, Status.REMOVED);
        }

        slicer.transform(new TransformInvocationBuilder(context)
                .addInputs(ImmutableList.of(getInput(getInputDir(), changedFiles)))
                .addOutputProvider(getOutputProvider(getOutputDir(), jarOutput))
                .setIncrementalMode(isIncremental)
                .build());

        outputSlices = getOutputDir().listFiles();
        assertThat(outputSlices).isNotNull();
        assertThat(outputSlices).hasLength(InstantRunSlicer.NUMBER_OF_SLICES_FOR_PROJECT_CLASSES);
        outputSlice = new File(getOutputDir(), sliceName);
        outputFile = new File(new File(outputSlice, packagePath), "file0.class");
        // check that the file was removed but the slice remained.
        assertThat(outputFile.exists()).isFalse();
    }

    @Test
    public void testEmptyPackages() throws IOException, TransformException, InterruptedException {
        InstantRunSlicer slicer = new InstantRunSlicer(logger, variantScope);

        // call with an empty input directory.
        slicer.transform(new TransformInvocationBuilder(context)
                .addInputs(ImmutableList.of(
                        getInput(getInputDir(), ImmutableMap.of())))
                .addOutputProvider(getOutputProvider(getOutputDir(), jarOutput))
                .build());

        File[] outputSlices = getOutputDir().listFiles();
        assertThat(outputSlices).isNotNull();
        assertThat(outputSlices).hasLength(InstantRunSlicer.NUMBER_OF_SLICES_FOR_PROJECT_CLASSES);

        for (File slice : outputSlices) {
            // ensure guard class presence.
            File guardClassPackage = new File(slice,
                    InstantRunSlicer.PACKAGE_FOR_GUARD_CLASSS
                            + File.separator + slice.getName() + ".class");
            assertThat(guardClassPackage.exists()).isTrue();
        }
    }

    @Test
    public void testAddingDirectory() throws IOException, TransformException, InterruptedException {
        InstantRunSlicer slicer = new InstantRunSlicer(logger, variantScope);

        String packagePath = "com/foo/bar";
        File folder = new File(getInputDir(), packagePath);
        File singleClassFile = createFile(folder, "file0.class");
        Map<File, Status> changedFiles = ImmutableMap.of(folder, Status.ADDED);

        slicer.transform(new TransformInvocationBuilder(context)
                .addInputs(ImmutableList.of(getInput(getInputDir(), changedFiles)))
                .addOutputProvider(getOutputProvider(getOutputDir(), jarOutput))
                .build());

        File[] outputSlices = getOutputDir().listFiles();
        assertThat(outputSlices).isNotNull();
        assertThat(outputSlices).hasLength(InstantRunSlicer.NUMBER_OF_SLICES_FOR_PROJECT_CLASSES);

        Optional<Integer> sliceForFile =
                findSliceForFile(getOutputDir(), getInputDir(), singleClassFile);
        assertThat(sliceForFile.isPresent());
        int slot = sliceForFile.get();

        File outputSlice = outputSlices[slot];
        File outputFile = new File(new File(outputSlice, packagePath), "file0.class");
        assertThat(outputFile.exists()).isTrue();
    }

    @Test
    public void testRemovingDirectory() throws IOException, TransformException,
            InterruptedException {
        InstantRunSlicer slicer = new InstantRunSlicer(logger, variantScope);

        String packagePath = "com/foo/bar";
        File folder = new File(getInputDir(), packagePath);
        File singleClassFile = createFile(folder, "file0.class");
        Map<File, Status> changedFiles = ImmutableMap.of(folder, Status.ADDED);

        slicer.transform(new TransformInvocationBuilder(context)
                .addInputs(ImmutableList.of(getInput(getInputDir(), changedFiles)))
                .addOutputProvider(getOutputProvider(getOutputDir(), jarOutput))
                .build());

        // now delete the input.
        FileUtils.deleteDirectoryContents(folder);
        assertTrue(folder.delete());
        changedFiles = ImmutableMap.of(folder, Status.REMOVED);

        slicer.transform(new TransformInvocationBuilder(context)
                .addInputs(ImmutableList.of(getInput(getInputDir(), changedFiles)))
                .addOutputProvider(getOutputProvider(getOutputDir(), jarOutput))
                .build());

        File[] outputSlices = getOutputDir().listFiles();
        assertThat(outputSlices).isNotNull();
        assertThat(outputSlices).hasLength(InstantRunSlicer.NUMBER_OF_SLICES_FOR_PROJECT_CLASSES);

        Optional<Integer> sliceForFile =
                findSliceForFile(getOutputDir(), getInputDir(), singleClassFile);
        assertThat(!sliceForFile.isPresent());
    }

    private static Optional<Integer> findSliceForFile(
            @NonNull File outputDir, @NonNull  File inputDir, File file) {

        File[] slices = outputDir.listFiles();
        if (slices == null) {
            return Optional.empty();
        }

        String relativePath = FileUtils.relativePossiblyNonExistingPath(file, inputDir);
        for (int i=0; i<slices.length; i++) {
            File potentialMatch = new File(slices[i], relativePath);
            if (potentialMatch.exists()) {
                return Optional.of(i);
            }
        }
        return Optional.empty();
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
                return ImmutableList.of(
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
            return ImmutableSet.of(DefaultContentType.CLASSES);
        }

        @NonNull
        @Override
        public Set<Scope> getScopes() {
            return ImmutableSet.of(Scope.PROJECT);
        }
    }

    private static File createFile(File directory, String name) throws IOException {
        return createFile(directory, name, name);
    }

    private static File createFile(File directory, String name, String content) throws IOException {
        File newFile = new File(directory, name);
        Files.createParentDirs(newFile);
        try (FileWriter writer = new FileWriter(newFile)) {
            writer.append(content);
        }
        return newFile;
    }

    private File getOutputDir() {
        return outputDir.getRoot();
    }

    private File getInputDir() {
        return inputDir.getRoot();
    }
}
