/*
 * Copyright (C) 2016 The Android Open Source Project
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import com.android.annotations.NonNull;
import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.Status;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.incremental.InstantRunVerifierStatus;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.google.common.collect.ImmutableList;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.IOException;
import java.util.Collection;

/**
 * Tests for the {@link NoChangesVerifierTransform}
 */
@RunWith(MockitoJUnitRunner.class)
public class NoChangesVerifierTransformTest {
    @Mock
    TransformInvocation input;
    @Mock
    InstantRunBuildContext buildContext;

    @Test
    public void testNonIncTransformInvocation()
            throws TransformException, InterruptedException, IOException {
        NoChangesVerifierTransform checker = new NoChangesVerifierTransform(
                "name", buildContext,
                TransformManager.CONTENT_CLASS,
                TransformManager.SCOPE_FULL_PROJECT,
                InstantRunVerifierStatus.DEPENDENCY_CHANGED);

        assertThat(checker.isIncremental()).isTrue();
        when(input.isIncremental()).thenReturn(Boolean.FALSE);

        checker.transform(input);

        // make sure the verifier is not set by the constructor.
        ArgumentCaptor<InstantRunVerifierStatus> verifierStatusCaptor =
                ArgumentCaptor.forClass(InstantRunVerifierStatus.class);
        Mockito.verify(buildContext, times(1)).setVerifierResult(verifierStatusCaptor.capture());

        assertThat(verifierStatusCaptor.getValue()).isEqualTo(
                InstantRunVerifierStatus.DEPENDENCY_CHANGED);
    }

    @Test
    public void testIncTransformInvocation()
            throws TransformException, InterruptedException, IOException {
        NoChangesVerifierTransform checker = new NoChangesVerifierTransform(
                "name", buildContext,
                TransformManager.CONTENT_CLASS,
                TransformManager.SCOPE_FULL_PROJECT,
                InstantRunVerifierStatus.DEPENDENCY_CHANGED);

        assertThat(checker.isIncremental()).isTrue();
        when(input.isIncremental()).thenReturn(Boolean.TRUE);
        JarInput jarInput = Mockito.mock(JarInput.class);
        when(jarInput.getStatus()).thenReturn(Status.ADDED);

        ImmutableList<TransformInput> transformInputs =
                ImmutableList.of(new TransformInput() {
                    @NonNull
                    @Override
                    public Collection<JarInput> getJarInputs() {
                        return ImmutableList.of(jarInput);
                    }

                    @NonNull
                    @Override
                    public Collection<DirectoryInput> getDirectoryInputs() {
                        return ImmutableList.of();
                    }
                });

        when(input.getReferencedInputs()).thenReturn(transformInputs);

        checker.transform(input);

        // make sure the verifier is not set by the constructor.
        ArgumentCaptor<InstantRunVerifierStatus> verifierStatusCaptor =
                ArgumentCaptor.forClass(InstantRunVerifierStatus.class);
        Mockito.verify(buildContext, times(1)).setVerifierResult(verifierStatusCaptor.capture());

        assertThat(verifierStatusCaptor.getValue()).isEqualTo(
                InstantRunVerifierStatus.DEPENDENCY_CHANGED);
    }
}
