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

import com.android.annotations.NonNull;
import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent.ContentType;
import com.android.build.api.transform.QualifiedContent.Scope;
import com.android.build.api.transform.Status;
import com.android.build.api.transform.Transform;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.gradle.internal.incremental.InstantRunVerifierStatus;
import com.android.build.gradle.internal.scope.VariantScope;
import com.google.common.collect.ImmutableSet;

import java.io.IOException;
import java.util.Collection;
import java.util.Set;

/**
 * No-op transform that verifies if any Java resources changed.
 */
public class NoChangesVerifierTransform extends Transform {

    @NonNull
    private final VariantScope variantScope;
    @NonNull
    private final Set<ContentType> inputTypes;
    @NonNull
    private final Set<Scope> mergeScopes;
    @NonNull
    private final InstantRunVerifierStatus failureStatus;
    private final boolean abortBuild;

    public NoChangesVerifierTransform(
            @NonNull VariantScope variantScope,
            @NonNull Set<ContentType> inputTypes,
            @NonNull Set<Scope> mergeScopes,
            @NonNull InstantRunVerifierStatus failureStatus,
            boolean abortBuild) {
        this.variantScope = variantScope;
        this.inputTypes = inputTypes;
        this.mergeScopes = mergeScopes;
        this.failureStatus = failureStatus;
        this.abortBuild = abortBuild;
    }

    @NonNull
    @Override
    public String getName() {
        return "javaResourcesVerifier";
    }

    @NonNull
    @Override
    public Set<ContentType> getInputTypes() {
        return inputTypes;
    }

    @NonNull
    @Override
    public Set<Scope> getScopes() {
        return ImmutableSet.of();
    }

    @NonNull
    @Override
    public Set<Scope> getReferencedScopes() {
        return mergeScopes;
    }

    @Override
    public boolean isIncremental() {
        return true;
    }

    @Override
    public void transform(@NonNull TransformInvocation transformInvocation)
            throws TransformException, InterruptedException, IOException {
        // This task will not be invoked on the initial assemble build.  For subsequent instant run
        // build, we want to fail the verifier if any Java resource changed.  (Native libraries are
        // treated as Java resources in the plugin)
        if (hasChangedInputs(transformInvocation.getReferencedInputs())) {
            variantScope.getInstantRunBuildContext().setVerifierResult(failureStatus);
            if (abortBuild) {
                variantScope.getInstantRunBuildContext().abort();
            }
        }
    }

    private static boolean hasChangedInputs(Collection<TransformInput> inputs) {
        for (TransformInput input : inputs) {
            for (DirectoryInput directoryInput : input.getDirectoryInputs()) {
                if (!directoryInput.getChangedFiles().isEmpty()) {
                    return true;
                }
            }
            for (JarInput jarInput : input.getJarInputs()) {
                if (jarInput.getStatus() != Status.NOTCHANGED) {
                    return true;
                }
            }
        }
        return false;
    }
}
