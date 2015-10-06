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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.incremental.IncompatibleChange;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.transform.api.Context;
import com.android.build.transform.api.TransformException;
import com.android.build.transform.api.TransformInput;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Tests for the {@link InstantRunVerifierTransform}
 */
@RunWith(MockitoJUnitRunner.class)
public class InstantRunVerifierTransformTest {

    final Map<File, File> recordedVerification = new HashMap<File, File>();
    final Map<File, File> recordedCopies = new HashMap<File, File>();
    final File backupDir = Files.createTempDir();

    @Mock
    VariantScope variantScope;

    @Mock
    Context context;

    @Before
    public void setUpMock() {
        when(variantScope.getIncrementalVerifierDir()).thenReturn(backupDir);
    }

    @Test
    public void testNonIncrementalModel()
            throws TransformException, InterruptedException, IOException {

        InstantRunVerifierTransform transform = getTransform();
        final File tmpDir = Files.createTempDir();

        final File inputClass = new File(tmpDir, "com/foo/bar/InputFile.class");
        Files.createParentDirs(inputClass);
        assertTrue(inputClass.createNewFile());

        ImmutableList.Builder<TransformInput> transformInputs = ImmutableList.builder();
        transformInputs.add(new TransformInputForTests() {
            @NonNull
            @Override
            public Collection<File> getFiles() {
                return ImmutableList.of(tmpDir);
            }

            @NonNull
            @Override
            public Map<File, FileStatus> getChangedFiles() {
                return ImmutableMap.of();
            }
        });

        transform.transform(context,
                transformInputs.build(),
                ImmutableList.<TransformInput>of(), /* referencedInputs */
                false /*isIncremental*/);

        // clean up.
        FileUtils.deleteFolder(tmpDir);

        // input class should have been copied.
        assertThat(recordedVerification).isEmpty();
        assertThat(recordedCopies).hasSize(1);
        assertThat(recordedCopies).containsEntry(inputClass,
                new File(backupDir, "com/foo/bar/InputFile.class"));
    }

    @Test
    public void testIncrementalMode() throws TransformException, IOException, InterruptedException {

        InstantRunVerifierTransform transform = getTransform();
        final File tmpDir = Files.createTempDir();

        final File addedFile = new File(tmpDir, "com/foo/bar/NewInputFile.class");
        Files.createParentDirs(addedFile);
        assertTrue(addedFile.createNewFile());

        final File changedFile = new File(tmpDir, "com/foo/bar/ChangedFile.class");
        assertTrue(changedFile.createNewFile());
        final File lastIterationChangedFile =
                new File(backupDir, "com/foo/bar/ChangedFile.class");
        Files.createParentDirs(lastIterationChangedFile);
        assertTrue(lastIterationChangedFile.createNewFile());

        final File deletedFile = new File(tmpDir, "com/foo/bar/DeletedFile.class");

        ImmutableList.Builder<TransformInput> transformInputs = ImmutableList.builder();
        transformInputs.add(new TransformInputForTests() {
            @NonNull
            @Override
            public Collection<File> getFiles() {
                return ImmutableList.of(tmpDir);
            }

            @NonNull
            @Override
            public Map<File, FileStatus> getChangedFiles() {
                return ImmutableMap.<File, FileStatus>builder()
                        .put(addedFile, FileStatus.ADDED)
                        .put(changedFile, FileStatus.CHANGED)
                        .put(deletedFile, FileStatus.REMOVED)
                        .build();
            }
        });

        transform.transform(context,
                transformInputs.build(),
                ImmutableList.<TransformInput>of(), /* referencedInputs */
                true /*isIncremental*/);

        // clean up.
        FileUtils.deleteFolder(tmpDir);

        // changed class should have been verified
        assertThat(recordedVerification).hasSize(1);
        assertThat(recordedVerification).containsEntry(
                lastIterationChangedFile, changedFile);

        // new classes should have been copied, and changed ones updated.
        assertThat(recordedCopies).hasSize(2);
        assertThat(recordedCopies).containsEntry(
                changedFile, lastIterationChangedFile);
        assertThat(recordedCopies).containsEntry(addedFile,
                new File(backupDir, "com/foo/bar/NewInputFile.class"));
    }

    @Test
    public void testSeveralAddedFilesInIncrementalMode()
            throws IOException, TransformException, InterruptedException {
        InstantRunVerifierTransform transform = getTransform();
        final File tmpDir = Files.createTempDir();

        final File[] files = new File[5];
        for (int i = 0; i < 5; i++) {
            files[i] = new File(tmpDir, "com/foo/bar/NewInputFile-" + i + ".class");
            Files.createParentDirs(files[i]);
            assertTrue(files[i].createNewFile());
        }

        ImmutableList.Builder<TransformInput> transformInputs = ImmutableList.builder();
        transformInputs.add(new TransformInputForTests() {
            @NonNull
            @Override
            public Collection<File> getFiles() {
                return ImmutableList.of(tmpDir);
            }

            @NonNull
            @Override
            public Map<File, FileStatus> getChangedFiles() {
                ImmutableMap.Builder<File, FileStatus> builder = ImmutableMap.builder();
                for (int i=0; i<5; i++) {
                    builder.put(files[i], FileStatus.ADDED);
                }
                return builder.build();
            }
        });

        transform.transform(context,
                transformInputs.build(),
                ImmutableList.<TransformInput>of(), /* referencedInputs */
                true /*isIncremental*/);

        // clean up.
        FileUtils.deleteFolder(tmpDir);

        // input class should have been copied.
        assertThat(recordedCopies).hasSize(5);
        for (int i=0; i<5; i++) {
            assertThat(recordedCopies).containsEntry(files[i],
                    new File(backupDir, "com/foo/bar/" + files[i].getName()));
        }
        assertThat(recordedVerification).isEmpty();
    }

    @Test
    public void testSeveralChangedFilesInIncrementalMode()
            throws IOException, TransformException, InterruptedException {
        InstantRunVerifierTransform transform = getTransform();
        final File tmpDir = Files.createTempDir();

        final File[] files = new File[5];
        final File[] lastIterationFiles = new File[5];
        for (int i = 0; i < 5; i++) {
            files[i] = new File(tmpDir, "com/foo/bar/NewInputFile-" + i + ".class");
            Files.createParentDirs(files[i]);
            assertTrue(files[i].createNewFile());
            lastIterationFiles[i] = new File(backupDir, "com/foo/bar/NewInputFile-" + i + ".class");
            Files.createParentDirs(lastIterationFiles[i]);
            assertTrue(lastIterationFiles[i].createNewFile());
        }

        ImmutableList.Builder<TransformInput> transformInputs = ImmutableList.builder();
        transformInputs.add(new TransformInputForTests() {
            @NonNull
            @Override
            public Collection<File> getFiles() {
                return ImmutableList.of(tmpDir);
            }

            @NonNull
            @Override
            public Map<File, FileStatus> getChangedFiles() {
                ImmutableMap.Builder<File, FileStatus> builder = ImmutableMap.builder();
                for (int i=0; i<5; i++) {
                    builder.put(files[i], FileStatus.CHANGED);
                }
                return builder.build();
            }
        });

        transform.transform(context,
                transformInputs.build(),
                ImmutableList.<TransformInput>of(), /* referencedInputs */
                true /*isIncremental*/);

        // clean up.
        FileUtils.deleteFolder(tmpDir);

        // input class should have been verified.
        assertThat(recordedVerification).hasSize(5);
        for (int i=0; i<5; i++) {
            assertThat(recordedVerification).containsEntry(lastIterationFiles[i], files[i]);
        }
        // and updated...
        assertThat(recordedCopies).hasSize(5);
        for (int i=0; i<5; i++) {
            assertThat(recordedCopies).containsEntry(files[i], lastIterationFiles[i]);
        }    }

    private InstantRunVerifierTransform getTransform() {

        return new InstantRunVerifierTransform(variantScope) {
            @Override
            protected IncompatibleChange runVerifier(File originalClass, File updatedClass)
                    throws IOException {
                recordedVerification.put(originalClass, updatedClass);
                return null;
            }

            @Override
            protected void copyFile(File inputFile, File outputFile) throws IOException {
                recordedCopies.put(inputFile, outputFile);
            }
        };
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
}
