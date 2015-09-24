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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.transform.api.Context;
import com.android.build.transform.api.TransformException;
import com.android.build.transform.api.TransformInput;
import com.android.build.transform.api.TransformOutput;
import com.android.builder.core.AndroidBuilder;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Tests for the {@link InstantRunTransform} class
 */
public class InstantRunTransformTest {

    @Mock
    Context context;

    @Mock
    GlobalScope globalScope;

    @Before
    public void setUpMock() {
        MockitoAnnotations.initMocks(this);
        AndroidBuilder mockBuilder = Mockito.mock(AndroidBuilder.class);
        when(mockBuilder.getBootClasspath(true)).thenReturn(ImmutableList.<File>of());
        when(globalScope.getAndroidBuilder()).thenReturn(mockBuilder);
    }

    @Test
    public void incrementalModeTest() throws TransformException, InterruptedException, IOException {

        final ImmutableList.Builder<File> filesElectedForClasses2Transformation = ImmutableList.builder();
        final ImmutableList.Builder<File> filesElectedForClasses3Transformation = ImmutableList.builder();

        InstantRunTransform transform = new InstantRunTransform(globalScope) {
            @Override
            protected void transformToClasses2Format(
                    @NonNull File inputDir, @NonNull File inputFile, @NonNull File outputDir, @NonNull RecordingPolicy recordingPolicy)
                    throws IOException {
                filesElectedForClasses2Transformation.add(inputFile);
            }

            @Override
            protected void transformToClasses3Format(File inputDir, File inputFile, File outputDir)
                    throws IOException {
                filesElectedForClasses3Transformation.add(inputFile);
            }

            @Override
            protected void wrapUpOutputs(TransformOutput classes2Output,
                    TransformOutput classes3Output) throws IOException {
            }
        };

        ImmutableMap.Builder<TransformInput, Collection<TransformOutput>> input =
                ImmutableMap.builder();

        input.put(new TransformInputForTests() {
            @NonNull
            @Override
            public Collection<File> getFiles() {
                return ImmutableList.of(new File("/tmp"));
            }

            @NonNull
            @Override
            public Map<File, FileStatus> getChangedFiles() {
                return ImmutableMap.<File, FileStatus>builder()
                        .put(new File("/tmp/foo/bar/Changed.class"), FileStatus.CHANGED)
                        .put(new File("/tmp/foo/bar/Added.class"), FileStatus.ADDED)
                        .build();
            }

        }, ImmutableList.<TransformOutput>of(new TransformOutputForTests() {
            @NonNull
            @Override
            public Set<ContentType> getContentTypes() {
                return ImmutableSet.of(ContentType.CLASSES);
            }

            @NonNull
            @Override
            public File getOutFile() {
                return new File("out");
            }
        }, new TransformOutputForTests() {

            @NonNull
            @Override
            public Set<ContentType> getContentTypes() {
                return ImmutableSet.of(ContentType.CLASSES_ENHANCED);
            }

            @NonNull
            @Override
            public File getOutFile() {
                return new File("out.3");
            }
        }));
        transform.transform(context, input.build(), ImmutableList.<TransformInput>of(), true);

        ImmutableList<File> processedFiles = filesElectedForClasses2Transformation.build();
        assertEquals("Wrong number of files elected for classes 2 processing", 2, processedFiles.size());
        assertEquals("Output File path", "/tmp/foo/bar/Changed.class", processedFiles.get(0).getPath());
        assertEquals("Output File path", "/tmp/foo/bar/Added.class", processedFiles.get(1).getPath());
        processedFiles = filesElectedForClasses3Transformation.build();
        assertEquals("Wrong number of files elected for classes 3 processing", 1, processedFiles.size());
        assertEquals("Output File path", "/tmp/foo/bar/Changed.class", processedFiles.get(0).getPath());
    }

    @Test
    public void fileDeletionTest() throws IOException, TransformException, InterruptedException {

        final File tmpFolder = Files.createTempDir();

        final File inputFolder = new File(tmpFolder, "input");
        FileUtils.mkdirs(inputFolder);

        final File originalFile = createEmptyFile(inputFolder, "com/example/A.class");

        final File outputFolder = new File(tmpFolder, "output");
        final File outputFile = createEmptyFile(outputFolder, "com/example/A.class");

        final File outputEnhancedFolder = new File(tmpFolder, "outputEnhanced");
        final File outputEnhancedFile =
                createEmptyFile(outputEnhancedFolder, "com/example/A$override.class");

        assertTrue(outputFile.exists());
        assertTrue(outputEnhancedFile.exists());

        final ImmutableList.Builder<File> filesElectedForClasses2Transformation = ImmutableList.builder();
        final ImmutableList.Builder<File> filesElectedForClasses3Transformation = ImmutableList.builder();

        InstantRunTransform transform = new InstantRunTransform(globalScope) {
            @Override
            protected void transformToClasses2Format(
                    @NonNull File inputDir, @NonNull File inputFile, @NonNull File outputDir, @NonNull RecordingPolicy recordingPolicy)
                    throws IOException {
                filesElectedForClasses2Transformation.add(inputFile);
            }

            @Override
            protected void transformToClasses3Format(File inputDir, File inputFile, File outputDir)
                    throws IOException {
                filesElectedForClasses3Transformation.add(inputFile);
            }

            @Override
            protected void wrapUpOutputs(TransformOutput classes2Output,
                    TransformOutput classes3Output)
                    throws IOException {
            }
        };

        ImmutableMap.Builder<TransformInput, Collection<TransformOutput>> input =
                ImmutableMap.builder();

        input.put(new TransformInputForTests() {
            @NonNull
            @Override
            public Collection<File> getFiles() {
                return ImmutableList.of(inputFolder);
            }

            @NonNull
            @Override
            public Map<File, FileStatus> getChangedFiles() {
                return ImmutableMap.<File, FileStatus>builder()
                        .put(originalFile, FileStatus.REMOVED)
                        .build();
            }

        }, ImmutableList.<TransformOutput>of(new TransformOutputForTests() {
            @NonNull
            @Override
            public Set<ContentType> getContentTypes() {
                return ImmutableSet.of(ContentType.CLASSES);
            }

            @NonNull
            @Override
            public File getOutFile() {
                return outputFolder;
            }
        }, new TransformOutputForTests() {

            @NonNull
            @Override
            public Set<ContentType> getContentTypes() {
                return ImmutableSet.of(ContentType.CLASSES_ENHANCED);
            }

            @NonNull
            @Override
            public File getOutFile() {
                return outputEnhancedFolder;
            }
        }));
        transform.transform(context, input.build(), ImmutableList.<TransformInput>of(), true);

        ImmutableList<File> processedFiles = filesElectedForClasses2Transformation.build();
        assertEquals("Wrong number of files elected for processing", 0, processedFiles.size());

        assertFalse("Incremental support class file should have been deleted.", outputFile.exists());
        assertFalse("Enhanced class file should have been deleted.", outputEnhancedFile.exists());

        FileUtils.deleteFolder(tmpFolder);
    }

    private static File createEmptyFile(File folder, String path)
            throws IOException {
        File file = new File(folder, path);
        Files.createParentDirs(file);
        Files.touch(file);
        return file;
    }

    private abstract static class TransformInputForTests implements TransformInput {

        @NonNull
        @Override
        public Set<ContentType> getContentTypes() {
            return ImmutableSet.of(ContentType.CLASSES);
        }

        @NonNull
        @Override
        public Set<Scope> getScopes() {
            return ImmutableSet.of(Scope.PROJECT);
        }

        @NonNull
        @Override
        public Format getFormat() {
            return Format.SINGLE_FOLDER;
        }
    }

    private abstract static class TransformOutputForTests implements TransformOutput {

        @NonNull
        @Override
        public Set<Scope> getScopes() {
            return ImmutableSet.of(Scope.PROJECT);
        }

        @NonNull
        @Override
        public Format getFormat() {
            return Format.SINGLE_FOLDER;
        }
    }
}
