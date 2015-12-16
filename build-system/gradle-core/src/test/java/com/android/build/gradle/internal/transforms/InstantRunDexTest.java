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
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.android.build.api.transform.Context;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.build.gradle.AndroidGradleOptions;
import com.android.build.gradle.OptionalCompilationStep;
import com.android.build.gradle.internal.dsl.DexOptions;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.model.AndroidProject;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;

import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.runners.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Tests for the InstantRunDex transform.
 */
@RunWith(MockitoJUnitRunner.class)
public class InstantRunDexTest {

    @Mock
    VariantScope variantScope;

    @Mock
    GlobalScope globalScope;

    @Mock
    AndroidBuilder androidBuilder;

    @Mock
    TransformOutputProvider TransformOutputProvider;

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


    @Before
    public void setUp() throws IOException {

        final File reloadOutputFolder = Files.createTempDir();
        File oldDexFile = new File(reloadOutputFolder, "reload.dex");
        assertTrue(oldDexFile.createNewFile());

        final File restartOutputFolder = Files.createTempDir();
        File oldRestartFile = new File(restartOutputFolder, "restart.dex");
        assertTrue(oldRestartFile.createNewFile());

        when(variantScope.getInstantRunBuildContext()).thenReturn(instantRunBuildContext);
        when(variantScope.getRestartDexOutputFolder()).thenReturn(restartOutputFolder);
        when(variantScope.getReloadDexOutputFolder()).thenReturn(reloadOutputFolder);
        when(variantScope.getGlobalScope()).thenReturn(globalScope);
        when(variantScope.getGlobalScope().getProject()).thenReturn(project);
        when(project.getProperties()).then(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                return ImmutableMap.of("android.injected.build.api", "23");
            }
        });
        when(globalScope.isActive(OptionalCompilationStep.RESTART_DEX_ONLY))
                .thenReturn(Boolean.FALSE);
    }

    @Test
    public void testVerifierFlaggedClass()
            throws TransformException, InterruptedException, IOException {

        when(instantRunBuildContext.hasPassedVerification()).thenReturn(Boolean.FALSE);

        InstantRunDex instantRunDex = new InstantRunDex(
                variantScope,
                InstantRunBuildType.RELOAD,
                androidBuilder,
                dexOptions,
                logger,
                ImmutableSet.<QualifiedContent.ContentType>of());

        instantRunDex.transform(context,
                ImmutableList.<TransformInput>of() /* inputs */,
                ImmutableList.<TransformInput>of() /* referencedInputs */,
                TransformOutputProvider,
                false /* isIncremental */);

        assertThat(variantScope.getReloadDexOutputFolder().listFiles()).isEmpty();

        instantRunDex = new InstantRunDex(
                variantScope,
                InstantRunBuildType.RESTART,
                androidBuilder,
                dexOptions,
                logger,
                ImmutableSet.<QualifiedContent.ContentType>of());

        instantRunDex.transform(context,
                ImmutableList.<TransformInput>of() /* inputs */,
                ImmutableList.<TransformInput>of() /* referencedInputs */,
                TransformOutputProvider,
                false /* isIncremental */);
        assertThat(variantScope.getRestartDexOutputFolder().listFiles()).isNotEmpty();

        verify(instantRunBuildContext).addChangedFile(
                eq(InstantRunBuildContext.FileType.RESTART_DEX),
                any(File.class));
    }

    @Test
    public void testVerifierPassedClassOnLollipopOrAbove()
            throws TransformException, InterruptedException, IOException {
        when(instantRunBuildContext.hasPassedVerification()).thenReturn(Boolean.TRUE);

        InstantRunDex instantRunDex = new InstantRunDex(
                variantScope,
                InstantRunBuildType.RELOAD,
                androidBuilder,
                dexOptions,
                logger,
                ImmutableSet.<QualifiedContent.ContentType>of());

        instantRunDex.transform(context,
                ImmutableList.<TransformInput>of() /* inputs */,
                ImmutableList.<TransformInput>of() /* referencedInputs */,
                TransformOutputProvider,
                false /* isIncremental */);

        assertThat(variantScope.getReloadDexOutputFolder().listFiles()).isNotEmpty();
        verify(instantRunBuildContext).addChangedFile(
                eq(InstantRunBuildContext.FileType.RELOAD_DEX),
                any(File.class));

        instantRunDex = new InstantRunDex(
                variantScope,
                InstantRunBuildType.RESTART,
                androidBuilder,
                dexOptions,
                logger,
                ImmutableSet.<QualifiedContent.ContentType>of());

        instantRunDex.transform(context,
                ImmutableList.<TransformInput>of() /* inputs */,
                ImmutableList.<TransformInput>of() /* referencedInputs */,
                TransformOutputProvider,
                false /* isIncremental */);
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

        InstantRunDex instantRunDex = new InstantRunDex(
                variantScope,
                InstantRunBuildType.RELOAD,
                androidBuilder,
                dexOptions,
                logger,
                ImmutableSet.<QualifiedContent.ContentType>of());

        instantRunDex.transform(context,
                ImmutableList.<TransformInput>of() /* inputs */,
                ImmutableList.<TransformInput>of() /* referencedInputs */,
                TransformOutputProvider,
                false /* isIncremental */);

        assertThat(variantScope.getReloadDexOutputFolder().listFiles()).isNotEmpty();

        instantRunDex = new InstantRunDex(
                variantScope,
                InstantRunBuildType.RESTART,
                androidBuilder,
                dexOptions,
                logger,
                ImmutableSet.<QualifiedContent.ContentType>of());

        instantRunDex.transform(context,
                ImmutableList.<TransformInput>of() /* inputs */,
                ImmutableList.<TransformInput>of() /* referencedInputs */,
                TransformOutputProvider,
                false /* isIncremental */);
        // since the verifier passed, the restart.dex is not present.
        assertThat(variantScope.getRestartDexOutputFolder().listFiles()).isEmpty();
    }
}
