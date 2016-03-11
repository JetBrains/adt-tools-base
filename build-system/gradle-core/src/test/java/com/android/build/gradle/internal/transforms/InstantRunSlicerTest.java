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
import com.android.build.api.transform.SecondaryInput;
import com.android.build.api.transform.Context;
import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.Format;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.Status;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.build.gradle.OptionalCompilationStep;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.pipeline.TransformInvocationBuilder;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.tasks.ColdswapArtifactsKickerTask;
import com.android.build.gradle.tasks.MarkerFile;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;

import org.gradle.api.logging.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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

    @Mock
    VariantScope variantScope;

    @Mock
    InstantRunBuildContext instantRunBuildContext;

    File instantRunSupportDir;

    @Before
    public void beforeTest() throws IOException {
        MockitoAnnotations.initMocks(this);
        instantRunSupportDir = createTmpDirectory("instantRunSupport");
        when(variantScope.getInstantRunSupportDir()).thenReturn(instantRunSupportDir);
        MarkerFile.createMarkerFile(
                ColdswapArtifactsKickerTask.ConfigAction.getMarkerFile(variantScope),
                MarkerFile.Command.RUN);
        when(variantScope.getInstantRunBuildContext()).thenReturn(instantRunBuildContext);
    }

    @After
    public void afterTest() throws IOException {
        FileUtils.deleteFolder(instantRunSupportDir);
    }


    @Test
    public void testNonIncrementalModeOnlyDirs()
            throws IOException, TransformException, InterruptedException {

        InstantRunSlicer slicer = new InstantRunSlicer(logger, variantScope);

        final File inputDir = createTmpDirectory("inputDir");
        final Map<File, Status> changedFiles;
        ImmutableMap.Builder<File, Status> builder = ImmutableMap.builder();
        List<String> listOfFiles = new ArrayList<String>();
        for (int i=0; i<21; i++) {
            listOfFiles.add("file" + i + ".class");
            builder.put(createFile(inputDir, "file" + i + ".class"), Status.ADDED);
        }
        changedFiles = builder.build();

        final File outputDir  = createTmpDirectory("outputDir");
        File jarOutputDir = createTmpDirectory("jarOutputDir");
        final File jarOutput = new File(jarOutputDir, "output.jar");

        slicer.transform(new TransformInvocationBuilder(context)
                .addInputs(ImmutableList.of(getInput(inputDir, changedFiles)))
                .addOutputProvider(getOutputProvider(outputDir, jarOutput))
                .build());

        // assert the file was copied in the output directory.
        File[] files = outputDir.listFiles();
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
                assertThat(listOfFiles.remove(sliceFile.getName())).isTrue();
            }
        }
        assertThat(listOfFiles).isEmpty();
        FileUtils.deleteFolder(jarOutputDir);
        FileUtils.deleteFolder(inputDir);
    }

    @Test
    public void testIncrementalModeOnlyDirs()
            throws IOException, TransformException, InterruptedException {

        InstantRunSlicer slicer = new InstantRunSlicer(logger, variantScope);

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

        slicer.transform(new TransformInvocationBuilder(context)
                .addInputs(ImmutableList.of(getInput(inputDir, changedFiles)))
                .addOutputProvider(getOutputProvider(outputDir, jarOutput))
                .build());

        // incrementally change a few slices.
        File file7 = createFile(inputDir, "file7.class", "updated file7.class");
        changedFiles = ImmutableMap.of();

        File incrementalChanges = InstantRunBuildType.RESTART.getIncrementalChangesFile(variantScope);
        Files.write(
                "CHANGED," + file7.getAbsolutePath() + "\n" +
                "REMOVED," + new File(inputDir, "file14.class").getAbsolutePath(),
                incrementalChanges, Charsets.UTF_8);

        slicer.transform(new TransformInvocationBuilder(context)
                .addInputs(ImmutableList.of(getInput(inputDir, changedFiles)))
                .addOutputProvider(getOutputProvider(outputDir, jarOutput))
                .setIncrementalMode(true)
                .build());

        // assert the file was copied in the output directory.
        File[] slices = outputDir.listFiles();
        assertNotNull(slices);
        assertThat(slices.length).isEqualTo(InstantRunSlicer.NUMBER_OF_SLICES_FOR_PROJECT_CLASSES);

        // file7 should be in the last slice slice.
        int bucket = Math.abs((file7.getName()).hashCode() % 10);
        File file7Slice = getFileByName(slices, "slice_" + bucket);
        assertNotNull(file7Slice);
        File[] slice6Files = file7Slice.listFiles();
        File updatedFile7 = getFileByName(slice6Files, "file7.class");
        assertNotNull(updatedFile7);
        assertThat(FileUtils.loadFileWithUnixLineSeparators(updatedFile7))
                .isEqualTo("updated file7.class");

        // file14 was in the slice 3, it should be gone.
        bucket = Math.abs(("file14.class").hashCode() % 10);
        File file14Bucket = getFileByName(slices, "slice_" + bucket);
        assertNotNull(file14Bucket);
        File[] file14BucketFiles = file14Bucket.listFiles();
        assertNotNull(file14BucketFiles);
        assertNull(getFileByName(file14BucketFiles, "file14.class"));

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
