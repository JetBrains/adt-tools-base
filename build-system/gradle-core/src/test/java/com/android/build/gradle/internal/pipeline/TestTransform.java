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

package com.android.build.gradle.internal.pipeline;

import com.android.annotations.NonNull;
import com.android.build.api.transform.SecondaryFile;
import com.android.build.api.transform.SecondaryInput;
import com.android.build.api.transform.Context;
import com.android.build.api.transform.QualifiedContent.ContentType;
import com.android.build.api.transform.QualifiedContent.Scope;
import com.android.build.api.transform.Transform;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.api.transform.TransformOutputProvider;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import org.junit.Assert;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Implementation of Transforms for testing.
 *
 * This is not meant to be instantiated directly. Use
 * {@link com.android.build.gradle.internal.pipeline.TestTransform.Builder}.
 */
public class TestTransform extends Transform {

    // transform data
    private final String name;
    private final Set<ContentType> inputTypes;
    private final Set<ContentType> outputTypes;
    private final Set<Scope> scopes;
    private final Set<Scope> referencedScopes;
    private final boolean isIncremental;
    private final List<File> secondaryFileInputs;
    private final List<SecondaryFile> mSecondaryFiles;

    public static Builder builder() {
        return new Builder();
    }

    @NonNull
    @Override
    public String getName() {
        return name;
    }

    @NonNull
    @Override
    public Set<ContentType> getInputTypes() {
        return inputTypes;
    }

    @NonNull
    @Override
    public Set<ContentType> getOutputTypes() {
        return outputTypes;
    }

    @NonNull
    @Override
    public Set<Scope> getScopes() {
        return scopes;
    }

    @NonNull
    @Override
    public Set<Scope> getReferencedScopes() {
        return referencedScopes;
    }

    @Override
    @NonNull
    public Collection<File> getSecondaryFileInputs() {
        return secondaryFileInputs;
    }

    @NonNull
    @Override
    public Collection<SecondaryFile> getSecondaryFiles() {
        return mSecondaryFiles;
    }

    @Override
    public boolean isIncremental() {
        return isIncremental;
    }

    @Override
    public void transform(TransformInvocation invocation)
            throws IOException, TransformException, InterruptedException {
        this.invocation = invocation;
    }

    // --- data recorded during the fake execution
    private TransformInvocation invocation;

    public boolean isIncrementalInputs() {
        return invocation.isIncremental();
    }

    public Collection<TransformInput> getInputs() {
        return invocation.getInputs();
    }

    public Collection<TransformInput> getReferencedInputs() {
        return invocation.getReferencedInputs();
    }

    public TransformOutputProvider getOutput() {
        return invocation.getOutputProvider();
    }

    public Collection<SecondaryInput> getSecondaryInputs() {
        return invocation.getSecondaryInputs();
    }

    private TestTransform(
            @NonNull String name,
            @NonNull Set<ContentType> inputTypes,
            @NonNull Set<ContentType> outputTypes,
            @NonNull Set<Scope> scopes,
            @NonNull Set<Scope> refedScopes,
            boolean isIncremental,
            @NonNull List<File> secondaryFileInputs,
            @NonNull List<SecondaryFile> secondaryInputs) {
        this.name = name;
        this.inputTypes = inputTypes;
        this.outputTypes = outputTypes;
        this.scopes = scopes;
        this.referencedScopes = refedScopes;
        this.isIncremental = isIncremental;
        this.secondaryFileInputs = ImmutableList.copyOf(secondaryFileInputs);
        this.mSecondaryFiles = secondaryInputs;
    }

    /**
     * Builder for the transforms.
     */
    static final class Builder {

        private String name;
        private final Set<ContentType> inputTypes = new HashSet<ContentType>();
        private Set<ContentType> outputTypes;
        private final Set<Scope> scopes = EnumSet.noneOf(Scope.class);
        private final Set<Scope> referencedScopes = EnumSet.noneOf(Scope.class);
        private boolean isIncremental = false;
        private final List<File> secondaryFileInputs = Lists.newArrayList();
        private final List<SecondaryFile> mSecondaryFiles = Lists.newArrayList();

        Builder setName(String name) {
            this.name = name;
            return this;
        }

        Builder setInputTypes(@NonNull ContentType... types) {
            inputTypes.addAll(Arrays.asList(types));
            return this;
        }

        Builder setOutputTypes(@NonNull ContentType... types) {
            if (outputTypes == null) {
                outputTypes = new HashSet<ContentType>();
            }
            outputTypes.addAll(Arrays.asList(types));
            return this;
        }

        Builder setOutputTypes(@NonNull Set<ContentType> types) {
            outputTypes = ImmutableSet.copyOf(types);
            return this;
        }

        Builder setScopes(@NonNull Scope... scopes) {
            this.scopes.addAll(Arrays.asList(scopes));
            return this;
        }

        Builder setReferencedScopes(@NonNull Scope... scopes) {
            this.referencedScopes.addAll(Arrays.asList(scopes));
            return this;
        }

        Builder setIncremental(boolean isIncremental) {
            this.isIncremental = isIncremental;
            return this;
        }

        Builder setSecondaryFile(@NonNull File file) {
            secondaryFileInputs.add(file);
            return this;
        }

        Builder setSecondaryInput(@NonNull SecondaryFile secondaryFile) {
            mSecondaryFiles.add(secondaryFile);
            return this;
        }

        @NonNull
        TestTransform build() {
            String name = this.name != null ? this.name : "transform name";
            Assert.assertFalse(this.inputTypes.isEmpty());
            Set<ContentType> inputTypes = ImmutableSet.copyOf(this.inputTypes);
            Set<ContentType> outputTypes = this.outputTypes != null ?
                    ImmutableSet.copyOf(this.outputTypes) : inputTypes;
            Set<Scope> scopes = Sets.immutableEnumSet(this.scopes);
            Set<Scope> refedScopes = Sets.immutableEnumSet(this.referencedScopes);

            return new TestTransform(
                    name,
                    inputTypes,
                    outputTypes,
                    scopes,
                    refedScopes,
                    isIncremental,
                    secondaryFileInputs,
                    mSecondaryFiles);
        }
    }
}
