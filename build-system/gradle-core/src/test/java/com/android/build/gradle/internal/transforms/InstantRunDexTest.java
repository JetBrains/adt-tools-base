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

import com.android.build.gradle.internal.dsl.DexOptions;
import com.android.build.gradle.internal.incremental.IncompatibleChange;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.transforms.InstantRunVerifierTransform.VerificationResult;
import com.android.build.transform.api.Context;
import com.android.build.transform.api.QualifiedContent;
import com.android.build.transform.api.TransformException;
import com.android.build.transform.api.TransformInput;
import com.android.build.transform.api.TransformOutputProvider;
import com.android.builder.core.AndroidBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;

import org.gradle.api.logging.Logger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.File;
import java.io.IOException;

/**
 * Tests for the InstantRunDex transform.
 */
@RunWith(MockitoJUnitRunner.class)
public class InstantRunDexTest {

    @Mock
    VariantScope variantScope;

    @Mock
    AndroidBuilder androidBuilder;

    @Mock
    TransformOutputProvider TransformOutputProvider;

    @Mock
    DexOptions dexOptions;

    @Mock
    Context context;

    @Mock
    Logger logger;

    @Test
    public void testVerifierFlaggedClass()
            throws TransformException, InterruptedException, IOException {

        final File outputFolder = Files.createTempDir();
        File oldDexFile = new File(outputFolder, "reload.dex");
        assertTrue(oldDexFile.createNewFile());

        when(variantScope.getVerificationResult()).thenReturn(
                new VerificationResult(IncompatibleChange.FIELD_ADDED));
        when(variantScope.getReloadDexOutputFolder()).thenReturn(outputFolder);

        InstantRunDex instantRunDex = new InstantRunDex(
                variantScope,
                InstantRunDex.BuildType.RELOAD,
                androidBuilder,
                dexOptions,
                logger,
                ImmutableSet.<QualifiedContent.ContentType>of());

        instantRunDex.transform(context,
                ImmutableList.<TransformInput>of() /* inputs */,
                ImmutableList.<TransformInput>of() /* referencedInputs */,
                TransformOutputProvider,
                false /* isIncremental */);

        assertThat(outputFolder.listFiles()).isEmpty();
    }

}
