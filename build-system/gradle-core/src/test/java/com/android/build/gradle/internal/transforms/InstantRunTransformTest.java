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
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import com.android.annotations.NonNull;
import com.android.build.transform.api.Context;
import com.android.build.transform.api.TransformException;
import com.android.build.transform.api.TransformInput;
import com.android.build.transform.api.TransformOutput;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tests for the {@link InstantRunTransform} class
 */
public class InstantRunTransformTest {

    @Mock
    Context context;

    @Test
    public void incrementalModeTest() throws TransformException, InterruptedException, IOException {

        final ImmutableList.Builder<File> filesElectedForClasses2Transformation = ImmutableList.builder();
        final ImmutableList.Builder<File> filesElectedForClasses3Transformation = ImmutableList.builder();

        InstantRunTransform transform = new InstantRunTransform() {
            @Override
            protected void transformToClasses2Format(
                    File inputDir, File inputFile, File outputDir, RecordingPolicy recordingPolicy)
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

        final AtomicBoolean classes2Deleted = new AtomicBoolean(false);
        final File classes2ToBeDeleted = Mockito.mock(File.class);
        when(classes2ToBeDeleted.getAbsolutePath()).thenReturn("/out/file/to/delete.class");
        when(classes2ToBeDeleted.delete()).then(new Answer<Boolean>() {
            @Override
            public Boolean answer(InvocationOnMock invocation) throws Throwable {
                classes2Deleted.set(true);
                return true;
            }
        });
        when(classes2ToBeDeleted.exists()).thenReturn(true);

        final AtomicBoolean classes3Deleted = new AtomicBoolean(false);
        final File classes3ToBeDeleted = Mockito.mock(File.class);
        when(classes3ToBeDeleted.getAbsolutePath()).thenReturn("/out/file/to/delete$override.class");
        when(classes3ToBeDeleted.delete()).then(new Answer<Boolean>() {
            @Override
            public Boolean answer(InvocationOnMock invocation) throws Throwable {
                classes3Deleted.set(true);
                return true;
            }
        });
        when(classes3ToBeDeleted.exists()).thenReturn(true);

        final ImmutableList.Builder<File> filesElectedForClasses2Transformation = ImmutableList.builder();
        final ImmutableList.Builder<File> filesElectedForClasses3Transformation = ImmutableList.builder();

        InstantRunTransform transform = new InstantRunTransform() {
            @Override
            protected void transformToClasses2Format(
                    File inputDir, File inputFile, File outputDir, RecordingPolicy recordingPolicy)
                    throws IOException {
                filesElectedForClasses2Transformation.add(inputFile);
            }

            @Override
            protected void transformToClasses3Format(File inputDir, File inputFile, File outputDir)
                    throws IOException {
                filesElectedForClasses3Transformation.add(inputFile);
            }

            @Override
            protected File getOutputFile(File inputDir, File inputFile, File outputDir)
                    throws IOException {
                return classes2ToBeDeleted;
            }

            @Override
            protected File getOutputPatchFile(File inputDir, File inputFile, File outputDir)
                    throws IOException {
                return classes3ToBeDeleted;
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
                return ImmutableList.of(new File("/tmp"));
            }

            @NonNull
            @Override
            public Map<File, FileStatus> getChangedFiles() {
                return ImmutableMap.<File, FileStatus>builder()
                        .put(new File("/tmp/file/to/delete.class"), FileStatus.REMOVED)
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
        assertEquals("Wrong number of files elected for processing", 0, processedFiles.size());
        assertTrue("classes2 file was not deleted", classes2Deleted.get());
        assertTrue("classes3 file was not deleted", classes3Deleted.get());
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
