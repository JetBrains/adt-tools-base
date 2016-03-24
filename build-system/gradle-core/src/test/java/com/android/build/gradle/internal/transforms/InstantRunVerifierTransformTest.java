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
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import com.android.annotations.NonNull;
import com.android.build.api.transform.SecondaryInput;
import com.android.build.api.transform.Context;
import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.Status;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.build.gradle.OptionalCompilationStep;
import com.android.build.gradle.internal.incremental.InstantRunVerifierStatus;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.incremental.InstantRunVerifier;
import com.android.build.gradle.internal.pipeline.TransformInvocationBuilder;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
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
    private File backupDir;

    @Mock
    VariantScope variantScope;

    @Mock
    GlobalScope globalScope;

    @Mock
    Context context;

    @Mock
    TransformOutputProvider transformOutputProvider;

    @Mock
    InstantRunBuildContext instantRunBuildContext;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Before
    public void setUpMock() throws IOException {
        backupDir = temporaryFolder.newFolder();
        when(variantScope.getIncrementalVerifierDir()).thenReturn(backupDir);
        when(variantScope.getInstantRunBuildContext()).thenReturn(instantRunBuildContext);
        when(variantScope.getGlobalScope()).thenReturn(globalScope);
        when(globalScope.isActive(OptionalCompilationStep.RESTART_ONLY)).thenReturn(false);
    }

    @Test
    public void testNonIncrementalModel()
            throws TransformException, InterruptedException, IOException {

        InstantRunVerifierTransform transform = getTransform();
        final File tmpDir = temporaryFolder.newFolder();

        final File inputClass = new File(tmpDir, "com/foo/bar/InputFile.class");
        Files.createParentDirs(inputClass);
        assertTrue(inputClass.createNewFile());

        ImmutableList<TransformInput> transformInputs =
                ImmutableList.<TransformInput>of(new TransformInput() {
            @NonNull
            @Override
            public Collection<JarInput> getJarInputs() {
                return ImmutableList.of();
            }

            @NonNull
            @Override
            public Collection<DirectoryInput> getDirectoryInputs() {
                return ImmutableList.<DirectoryInput>of(new DirectoryInputForTests() {

                    @NonNull
                    @Override
                    public Map<File, Status> getChangedFiles() {
                        return ImmutableMap.of(inputClass, Status.ADDED);
                    }

                    @NonNull
                    @Override
                    public File getFile() {
                        return tmpDir;
                    }
                });
            }
        });

        transform.transform(new TransformInvocationBuilder(context)
            .addOutputProvider(transformOutputProvider)
            .addReferencedInputs(transformInputs)
            .build());

        // clean up.
        FileUtils.deleteFolder(tmpDir);

        // input class should have been copied.
        assertThat(recordedVerification).isEmpty();
        assertThat(recordedCopies).hasSize(1);
        assertThat(recordedCopies).containsEntry(inputClass,
                new File(backupDir, "com/foo/bar/InputFile.class"));
    }

    @Test
    public void testIncrementalMode_changedAndAdded() throws TransformException, IOException, InterruptedException {

        InstantRunVerifierTransform transform = getTransform();
        final File tmpDir = temporaryFolder.newFolder();

        final File addedFile = new File(tmpDir, "com/foo/bar/NewInputFile.class");
        Files.createParentDirs(addedFile);
        assertTrue(addedFile.createNewFile());

        final File changedFile = new File(tmpDir, "com/foo/bar/ChangedFile.class");
        assertTrue(changedFile.createNewFile());
        final File lastIterationChangedFile =
                new File(backupDir, "com/foo/bar/ChangedFile.class");
        Files.createParentDirs(lastIterationChangedFile);
        assertTrue(lastIterationChangedFile.createNewFile());

        ImmutableList<TransformInput> transformInputs =
                ImmutableList.<TransformInput>of(new TransformInput() {
                    @NonNull
                    @Override
                    public Collection<JarInput> getJarInputs() {
                        return ImmutableList.of();
                    }

                    @NonNull
                    @Override
                    public Collection<DirectoryInput> getDirectoryInputs() {
                        return ImmutableList.<DirectoryInput>of(new DirectoryInputForTests() {

                            @NonNull
                            @Override
                            public Map<File, Status> getChangedFiles() {
                                return ImmutableMap.<File, Status>builder()
                                        .put(addedFile, Status.ADDED)
                                        .put(changedFile, Status.CHANGED)
                                        .build();                            }

                            @NonNull
                            @Override
                            public File getFile() {
                                return tmpDir;
                            }
                        });
                    }
                });

        transform.transform(new TransformInvocationBuilder(context)
                .addOutputProvider(transformOutputProvider)
                .addReferencedInputs(transformInputs)
                .setIncrementalMode(true)
                .build());

        // clean up.
        FileUtils.deleteFolder(tmpDir);

        // changed class should have been verified
        assertThat(recordedVerification).isEmpty();

        // new classes should have been copied, and changed ones updated.
        assertThat(recordedCopies).hasSize(2);
        assertThat(recordedCopies).containsEntry(
                changedFile, lastIterationChangedFile);
        assertThat(recordedCopies).containsEntry(addedFile,
                new File(backupDir, "com/foo/bar/NewInputFile.class"));
    }

    @Test
    public void testIncrementalMode_changedAndDeleted() throws TransformException, IOException, InterruptedException {

        InstantRunVerifierTransform transform = getTransform();
        final File tmpDir = temporaryFolder.newFolder();

        final File changedFile = new File(tmpDir, "com/foo/bar/ChangedFile.class");
        Files.createParentDirs(changedFile);
        assertTrue(changedFile.createNewFile());
        final File lastIterationChangedFile =
                new File(backupDir, "com/foo/bar/ChangedFile.class");
        Files.createParentDirs(lastIterationChangedFile);
        assertTrue(lastIterationChangedFile.createNewFile());

        final File deletedFile = new File(tmpDir, "com/foo/bar/DeletedFile.class");

        ImmutableList<TransformInput> transformInputs =
                ImmutableList.<TransformInput>of(new TransformInput() {
                    @NonNull
                    @Override
                    public Collection<JarInput> getJarInputs() {
                        return ImmutableList.of();
                    }

                    @NonNull
                    @Override
                    public Collection<DirectoryInput> getDirectoryInputs() {
                        return ImmutableList.<DirectoryInput>of(new DirectoryInputForTests() {

                            @NonNull
                            @Override
                            public Map<File, Status> getChangedFiles() {
                                return ImmutableMap.<File, Status>builder()
                                        .put(changedFile, Status.CHANGED)
                                        .put(deletedFile, Status.REMOVED)
                                        .build();                            }

                            @NonNull
                            @Override
                            public File getFile() {
                                return tmpDir;
                            }
                        });
                    }
                });

        transform.transform(new TransformInvocationBuilder(context)
                .addOutputProvider(transformOutputProvider)
                .addReferencedInputs(transformInputs)
                .setIncrementalMode(true)
                .build());

        // clean up.
        FileUtils.deleteFolder(tmpDir);

        // changed class should have been verified
        assertThat(recordedVerification).hasSize(1);
        assertThat(recordedVerification).containsEntry(
                lastIterationChangedFile, changedFile);

        // new classes should have been copied, and changed ones updated.
        assertThat(recordedCopies).hasSize(1);
        assertThat(recordedCopies).containsEntry(
                changedFile, lastIterationChangedFile);
    }

    @Test
    public void testSeveralAddedFilesInIncrementalMode()
            throws IOException, TransformException, InterruptedException {
        InstantRunVerifierTransform transform = getTransform();
        final File tmpDir = temporaryFolder.newFolder();

        final File[] files = new File[5];
        for (int i = 0; i < 5; i++) {
            files[i] = new File(tmpDir, "com/foo/bar/NewInputFile-" + i + ".class");
            Files.createParentDirs(files[i]);
            assertTrue(files[i].createNewFile());
        }

        ImmutableList<TransformInput> transformInputs =
                ImmutableList.<TransformInput>of(new TransformInput() {
                    @NonNull
                    @Override
                    public Collection<JarInput> getJarInputs() {
                        return ImmutableList.of();
                    }

                    @NonNull
                    @Override
                    public Collection<DirectoryInput> getDirectoryInputs() {
                        return ImmutableList.<DirectoryInput>of(new DirectoryInputForTests() {

                            @NonNull
                            @Override
                            public Map<File, Status> getChangedFiles() {
                                ImmutableMap.Builder<File, Status> builder = ImmutableMap.builder();
                                for (int i=0; i<5; i++) {
                                    builder.put(files[i], Status.ADDED);
                                }
                                return builder.build();                            }

                            @NonNull
                            @Override
                            public File getFile() {
                                return tmpDir;
                            }
                        });
                    }
                });

        transform.transform(new TransformInvocationBuilder(context)
                .addOutputProvider(transformOutputProvider)
                .addReferencedInputs(transformInputs)
                .setIncrementalMode(true)
                .build());

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
        final File tmpDir = temporaryFolder.newFolder();

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

        ImmutableList<TransformInput> transformInputs =
                ImmutableList.<TransformInput>of(new TransformInput() {
                    @NonNull
                    @Override
                    public Collection<JarInput> getJarInputs() {
                        return ImmutableList.of();
                    }

                    @NonNull
                    @Override
                    public Collection<DirectoryInput> getDirectoryInputs() {
                        return ImmutableList.<DirectoryInput>of(new DirectoryInputForTests() {

                            @NonNull
                            @Override
                            public Map<File, Status> getChangedFiles() {
                                ImmutableMap.Builder<File, Status> builder = ImmutableMap.builder();
                                for (int i=0; i<5; i++) {
                                    builder.put(files[i], Status.CHANGED);
                                }
                                return builder.build();                            }

                            @NonNull
                            @Override
                            public File getFile() {
                                return tmpDir;
                            }
                        });
                    }
                });

        transform.transform(new TransformInvocationBuilder(context)
                .addOutputProvider(transformOutputProvider)
                .addReferencedInputs(transformInputs)
                .setIncrementalMode(true)
                .build());

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

            @NonNull
            @Override
            protected InstantRunVerifierStatus runVerifier(String name,
                    @NonNull final InstantRunVerifier.ClassBytesProvider originalClass ,
                    @NonNull final InstantRunVerifier.ClassBytesProvider updatedClass) throws IOException {

                recordedVerification.put(
                        ((InstantRunVerifier.ClassBytesFileProvider) originalClass).getFile(),
                        ((InstantRunVerifier.ClassBytesFileProvider) updatedClass).getFile());
                return InstantRunVerifierStatus.COMPATIBLE;
            }

            @Override
            protected void copyFile(File inputFile, File outputFile) {
                recordedCopies.put(inputFile, outputFile);
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
}
