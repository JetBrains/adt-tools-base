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
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.annotations.NonNull;
import com.android.build.api.transform.Context;
import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.Status;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.build.gradle.internal.scope.TransformVariantScope;
import com.android.builder.core.DexByteCodeConverter;
import com.android.builder.model.OptionalCompilationStep;
import com.android.build.gradle.internal.dsl.DexOptions;
import com.android.build.gradle.internal.pipeline.TransformInvocationBuilder;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.builder.core.AndroidBuilder;
import com.android.ide.common.process.ProcessException;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;

import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.runners.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tests for the InstantRunDex transform.
 */
@RunWith(MockitoJUnitRunner.class)
public class InstantRunDexTest {

    @Mock
    TransformVariantScope variantScope;

    @Mock
    GlobalScope globalScope;

    @Mock
    DexByteCodeConverter dexByteCodeConverter;

    @Mock
    TransformOutputProvider transformOutputProvider;

    @Mock
    InstantRunBuildContext instantRunBuildContext;

    @Mock
    DexOptions dexOptions;

    @Mock
    Context context;

    @Mock
    Logger logger;

    @Mock
    Project project;

    @Mock
    InstantRunDex.JarClassesBuilder jarClassesBuilder;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private File directoryInput;
    private File incrementalChanges;
    private File changedFile;


    @Before
    public void setUp() throws IOException {

        final File reloadOutputFolder = temporaryFolder.newFolder();
        File oldDexFile = new File(reloadOutputFolder, "reload.dex");
        assertTrue(oldDexFile.createNewFile());

        final File restartOutputFolder = temporaryFolder.newFolder();
        File oldRestartFile = new File(restartOutputFolder, "restart.dex");
        assertTrue(oldRestartFile.createNewFile());

        when(variantScope.getInstantRunBuildContext()).thenReturn(instantRunBuildContext);
        when(variantScope.getRestartDexOutputFolder()).thenReturn(restartOutputFolder);
        when(variantScope.getReloadDexOutputFolder()).thenReturn(reloadOutputFolder);
        when(variantScope.getGlobalScope()).thenReturn(globalScope);
        when(globalScope.getProject()).thenReturn(project);
        when(project.getProperties()).then(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                return ImmutableMap.of("android.injected.build.api", "23");
            }
        });
        when(globalScope.isActive(OptionalCompilationStep.RESTART_ONLY))
                .thenReturn(Boolean.FALSE);

        File tmp = new File(System.getProperty("java.io.tmpdir"));
        when(variantScope.getInstantRunSupportDir()).thenReturn(tmp);

        directoryInput = new File(tmp, "directory");
        changedFile = new File(directoryInput, "path/to/some/file");
        Files.createParentDirs(changedFile);
        Files.write("abcde", changedFile, Charsets.UTF_8);

        incrementalChanges = InstantRunBuildType.RESTART.getIncrementalChangesFile(variantScope);
        Files.write("CHANGED," + changedFile.getAbsolutePath(), incrementalChanges, Charsets.UTF_8);
    }

    @After
    public void takeDown() throws IOException {
        FileUtils.deletePath(directoryInput);
        if (incrementalChanges.isFile()) {
            assertThat(incrementalChanges.delete()).isTrue();
        }
    }

    @Test
    public void testVerifierFlaggedClass()
            throws TransformException, InterruptedException, IOException {

        when(instantRunBuildContext.hasPassedVerification()).thenReturn(Boolean.FALSE);

        final List<File> convertedFiles = new ArrayList<>();
        InstantRunDex instantRunDex = getTestedDex(convertedFiles, InstantRunBuildType.RELOAD);

        instantRunDex.transform(new TransformInvocationBuilder(context)
                .addReferencedInputs(ImmutableList.of(getTransformInput(directoryInput)))
                .addOutputProvider(transformOutputProvider)
                .build());

        assertThat(variantScope.getReloadDexOutputFolder().listFiles()).isEmpty();

        convertedFiles.clear();
        instantRunDex = getTestedDex(convertedFiles, InstantRunBuildType.RESTART);
        when(jarClassesBuilder.isEmpty()).thenReturn(Boolean.FALSE);

        instantRunDex.transform(new TransformInvocationBuilder(context)
                .addReferencedInputs(ImmutableList.of(getTransformInput(directoryInput)))
                .addOutputProvider(transformOutputProvider)
                .build());

        assertThat(variantScope.getRestartDexOutputFolder().listFiles()).isNotEmpty();
        assertThat(convertedFiles).hasSize(0);

        // should have been deleted by the transform.
        assertThat(incrementalChanges.isFile()).isFalse();
    }

    @Test
    public void testVerifierPassedClassOnLollipopOrAbove()
            throws TransformException, InterruptedException, IOException {
        when(instantRunBuildContext.hasPassedVerification()).thenReturn(Boolean.TRUE);

        List<File> convertedFiles = new ArrayList<>();
        InstantRunDex instantRunDex = getTestedDex(convertedFiles, InstantRunBuildType.RELOAD);

        instantRunDex.transform(new TransformInvocationBuilder(context)
                .addReferencedInputs(ImmutableList.of(getTransformInput(directoryInput)))
                .addOutputProvider(transformOutputProvider)
                .build());

        assertThat(variantScope.getReloadDexOutputFolder().listFiles()).isNotEmpty();
        verify(instantRunBuildContext).addChangedFile(
                eq(InstantRunBuildContext.FileType.RELOAD_DEX),
                any(File.class));

        instantRunDex = new InstantRunDex(
                variantScope,
                InstantRunBuildType.RESTART,
                ()-> dexByteCodeConverter,
                dexOptions,
                logger,
                ImmutableSet.<QualifiedContent.ContentType>of());

        instantRunDex.transform(new TransformInvocationBuilder(context)
                .addReferencedInputs(ImmutableList.of(getTransformInput(directoryInput)))
                .addOutputProvider(transformOutputProvider)
                .build());

        assertThat(variantScope.getRestartDexOutputFolder().listFiles()).isEmpty();
        verify(instantRunBuildContext, times(2)).hasPassedVerification();
        verify(instantRunBuildContext, times(1)).addChangedFile(
                any(InstantRunBuildContext.FileType.class), any(File.class));
    }

    @Test
    public void testVerifierPassedClassOnDalvik()
            throws TransformException, InterruptedException, IOException {
        when(instantRunBuildContext.hasPassedVerification()).thenReturn(Boolean.TRUE);
        when(project.getProperties()).then(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                return ImmutableMap.of("android.injected.build.api", "15");
            }
        });

        File incrementalChanges = InstantRunBuildType.RELOAD.getIncrementalChangesFile(
                variantScope);
        Files.write("CHANGED," + changedFile.getAbsolutePath(),
                incrementalChanges, Charsets.UTF_8);

        InstantRunDex instantRunDex = new InstantRunDex(
                variantScope,
                InstantRunBuildType.RELOAD,
                ()-> dexByteCodeConverter,
                dexOptions,
                logger,
                ImmutableSet.<QualifiedContent.ContentType>of());

        instantRunDex.transform(new TransformInvocationBuilder(context)
                .addReferencedInputs(ImmutableList.of(getTransformInput(directoryInput)))
                .addOutputProvider(transformOutputProvider)
                .build());

        assertThat(incrementalChanges.isFile()).isFalse();
        assertThat(variantScope.getReloadDexOutputFolder().listFiles()).isNotEmpty();

        instantRunDex = new InstantRunDex(
                variantScope,
                InstantRunBuildType.RESTART,
                ()-> dexByteCodeConverter,
                dexOptions,
                logger,
                ImmutableSet.<QualifiedContent.ContentType>of());

        instantRunDex.transform(new TransformInvocationBuilder(context)
                .addReferencedInputs(ImmutableList.of(getTransformInput(directoryInput)))
                .addOutputProvider(transformOutputProvider)
                .build());
        // since the verifier passed, the restart.dex is not present.
        assertThat(variantScope.getRestartDexOutputFolder().listFiles()).isEmpty();
    }

    @Test
    public void testNoChanges() throws TransformException, InterruptedException, IOException {
        when(instantRunBuildContext.hasPassedVerification()).thenReturn(Boolean.TRUE);
        when(project.getProperties()).then(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                return ImmutableMap.of("android.injected.build.api", "15");
            }
        });

        InstantRunDex instantRunDex = new InstantRunDex(
                variantScope,
                InstantRunBuildType.RELOAD,
                ()-> dexByteCodeConverter,
                dexOptions,
                logger,
                ImmutableSet.<QualifiedContent.ContentType>of());

        instantRunDex.transform(new TransformInvocationBuilder(context)
                .addOutputProvider(transformOutputProvider)
                .build());

        assertThat(variantScope.getReloadDexOutputFolder().listFiles()).isEmpty();

        instantRunDex = new InstantRunDex(
                variantScope,
                InstantRunBuildType.RESTART,
                ()-> dexByteCodeConverter,
                dexOptions,
                logger,
                ImmutableSet.<QualifiedContent.ContentType>of());

        instantRunDex.transform(new TransformInvocationBuilder(context)
                .addReferencedInputs(ImmutableList.of(getTransformInput(directoryInput)))
                .addOutputProvider(transformOutputProvider)
                .build());

        // since the verifier passed, the restart.dex is not present.
        assertThat(variantScope.getRestartDexOutputFolder().listFiles()).isEmpty();
    }

    private InstantRunDex getTestedDex(final List<File> convertedFiles, InstantRunBuildType type) {
        return  new InstantRunDex(
                variantScope,
                type,
                ()-> dexByteCodeConverter,
                dexOptions,
                logger,
                ImmutableSet.<QualifiedContent.ContentType>of()) {

            @Override
            protected JarClassesBuilder getJarClassBuilder(File outputFile) {
                return jarClassesBuilder;
            }

            @Override
            protected void convertByteCode(List<File> inputFiles, File outputFolder)
                    throws InterruptedException, ProcessException, IOException {
                convertedFiles.addAll(inputFiles);
            }
        };
    }

    private static TransformInput getTransformInput(
            final File directoryInput) {
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
                        new DirectoryInput() {
                            @NonNull
                            @Override
                            public Map<File, Status> getChangedFiles() {
                                return ImmutableMap.of();
                            }

                            @NonNull
                            @Override
                            public String getName() {
                                return "test-input";
                            }

                            @NonNull
                            @Override
                            public File getFile() {
                                return directoryInput;
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

                );
            }
        };
    }
}
