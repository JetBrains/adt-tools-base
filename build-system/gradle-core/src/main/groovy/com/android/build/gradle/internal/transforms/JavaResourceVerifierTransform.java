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
import com.android.build.api.transform.QualifiedContent.ContentType;
import com.android.build.api.transform.QualifiedContent.Scope;
import com.android.build.api.transform.Transform;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.gradle.internal.incremental.InstantRunVerifierStatus;
import com.android.build.gradle.internal.scope.VariantScope;
import com.google.common.collect.ImmutableSet;

import java.io.IOException;
import java.util.Set;

/**
 * No-op transform that verifies if any Java resources changed.
 */
public class JavaResourceVerifierTransform extends Transform {

    @NonNull
    private final String name;
    @NonNull
    private final VariantScope variantScope;
    @NonNull
    private final Set<Scope> mergeScopes;
    @NonNull
    private final Set<ContentType> mergedType;

    public JavaResourceVerifierTransform(
            @NonNull String name,
            @NonNull VariantScope variantScope,
            @NonNull Set<Scope> mergeScopes,
            @NonNull ContentType mergedType) {
        this.name = name;
        this.variantScope = variantScope;
        this.mergeScopes = mergeScopes;
        this.mergedType = ImmutableSet.of(mergedType);
    }

    @NonNull
    @Override
    public String getName() {
        return name;
    }

    @NonNull
    @Override
    public Set<ContentType> getInputTypes() {
        return mergedType;
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
        if (!transformInvocation.getReferencedInputs().isEmpty()) {
            // This task will not be invoked on the assemble build.  getReferenceInputs will be
            // empty on subsequent instant run build if none of the native libray has changed.
            variantScope.getInstantRunBuildContext().setVerifierResult(
                    InstantRunVerifierStatus.JAVA_RESOURCES_CHANGED);
        }
    }
}
