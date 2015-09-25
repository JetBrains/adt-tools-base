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
import com.android.annotations.Nullable;
import com.android.build.transform.api.AsInputTransform;
import com.android.build.transform.api.CombinedTransform;
import com.android.build.transform.api.Context;
import com.android.build.transform.api.ForkTransform;
import com.android.build.transform.api.NoOpTransform;
import com.android.build.transform.api.ScopedContent.ContentType;
import com.android.build.transform.api.ScopedContent.Format;
import com.android.build.transform.api.ScopedContent.Scope;
import com.android.build.transform.api.Transform;
import com.android.build.transform.api.TransformException;
import com.android.build.transform.api.TransformInput;
import com.android.build.transform.api.TransformOutput;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import org.junit.Assert;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
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
    private final Set<Scope> refedScopes;
    private final Format format;
    private final boolean isIncremental;
    private final List<File> secondaryFileInputs;

    // data passed to transform() so that it can be queried later by tests.
    protected Collection<TransformInput> referencedInputs;
    protected boolean isIncrementalInputs;

    static Builder builder() {
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
        return refedScopes;
    }

    @NonNull
    @Override
    public Format getOutputFormat() {
        return format;
    }

    @NonNull
    @Override
    public Collection<File> getSecondaryFileInputs() {
        return secondaryFileInputs;
    }

    @Override
    public boolean isIncremental() {
        return isIncremental;
    }

    public Collection<TransformInput> getReferencedInputs() {
        return referencedInputs;
    }
    public boolean isIncrementalInputs() {
        return isIncrementalInputs;
    }

    private TestTransform(
            @NonNull String name,
            @NonNull Set<ContentType> inputTypes,
            @NonNull Set<ContentType> outputTypes,
            @NonNull Set<Scope> scopes,
            @NonNull Set<Scope> refedScopes,
            @NonNull Format format,
            boolean isIncremental,
            @NonNull List<File> secondaryFileInputs) {
        this.name = name;
        this.inputTypes = inputTypes;
        this.outputTypes = outputTypes;
        this.scopes = scopes;
        this.refedScopes = refedScopes;
        this.format = format;
        this.isIncremental = isIncremental;
        this.secondaryFileInputs = ImmutableList.copyOf(secondaryFileInputs);
    }

    public static class TestForkTransform extends TestTransform implements ForkTransform {

        // data passed to transform() so that it can be queried later by tests.
        private Map<TransformInput, Collection<TransformOutput>> inputs;

        private TestForkTransform(
                @NonNull String name,
                @NonNull Set<ContentType> inputTypes,
                @NonNull Set<ContentType> outputTypes,
                @NonNull Set<Scope> scopes,
                @NonNull Set<Scope> referencedScopes,
                @NonNull Format format,
                boolean isIncremental,
                @NonNull List<File> secondaryFileInputs) {
            super(name, inputTypes, outputTypes, scopes, referencedScopes, format, isIncremental, secondaryFileInputs);
        }

        public Map<TransformInput, Collection<TransformOutput>> getInputs() {
            return inputs;
        }

        @Override
        public void transform(
                @NonNull Context context,
                @NonNull Map<TransformInput, Collection<TransformOutput>> inputs,
                @NonNull Collection<TransformInput> referencedInputs, boolean isIncremental)
                throws IOException, TransformException, InterruptedException {
            this.inputs = inputs;
            this.referencedInputs = referencedInputs;
            this.isIncrementalInputs = isIncremental;
        }
    }

    public static class TestAsInputTransform extends TestTransform implements AsInputTransform {

        // data passed to transform() so that it can be queried later by tests.
        private Map<TransformInput, TransformOutput> inputs;

        private TestAsInputTransform(
                @NonNull String name,
                @NonNull Set<ContentType> inputTypes,
                @NonNull Set<ContentType> outputTypes,
                @NonNull Set<Scope> scopes,
                @NonNull Set<Scope> refedScopes,
                @NonNull Format format,
                boolean isIncremental,
                @NonNull List<File> secondaryFileInputs) {
            super(name, inputTypes, outputTypes, scopes, refedScopes, format, isIncremental, secondaryFileInputs);
        }

        public Map<TransformInput, TransformOutput> getInputs() {
            return inputs;
        }

        @Override
        public void transform(
                @NonNull Context context,
                @NonNull Map<TransformInput, TransformOutput> inputs,
                @NonNull Collection<TransformInput> referencedInputs, boolean isIncremental)
                throws IOException, TransformException, InterruptedException {
            this.inputs = inputs;
            this.referencedInputs = referencedInputs;
            this.isIncrementalInputs = isIncremental;
        }
    }

    public static class TestCombinedTransform extends TestTransform implements CombinedTransform {

        // data passed to transform() so that it can be queried later by tests.
        private Collection<TransformInput> inputs;
        private TransformOutput output;

        public TestCombinedTransform(
                @NonNull String name,
                @NonNull Set<ContentType> inputTypes,
                @NonNull Set<ContentType> outputTypes,
                @NonNull Set<Scope> scopes,
                @NonNull Set<Scope> refedScopes,
                @NonNull Format format,
                boolean isIncremental,
                @NonNull List<File> secondaryFileInputs) {
            super(name, inputTypes, outputTypes, scopes, refedScopes, format, isIncremental, secondaryFileInputs);
        }

        public Collection<TransformInput> getInputs() {
            return inputs;
        }
        public TransformOutput getOutput() {
            return output;
        }

        @Override
        public void transform(
                @NonNull Context context,
                @NonNull Collection<TransformInput> inputs,
                @NonNull Collection<TransformInput> referencedInputs,
                @NonNull TransformOutput output,
                boolean isIncremental)
                throws IOException, TransformException, InterruptedException {
            this.inputs = inputs;
            this.referencedInputs = referencedInputs;
            this.output = output;
            this.isIncrementalInputs = isIncremental;
        }
    }

    public static class TestNoOpTransform extends TestTransform implements NoOpTransform {

        // data passed to transform() so that it can be queried later by tests.
        private Collection<TransformInput> inputs;

        public TestNoOpTransform(
                @NonNull String name,
                @NonNull Set<ContentType> inputTypes,
                @NonNull Set<ContentType> outputTypes,
                @NonNull Set<Scope> scopes,
                @NonNull Set<Scope> referencedScopes,
                @NonNull Format format,
                boolean isIncremental,
                @NonNull List<File> secondaryFileInputs) {
            super(name, inputTypes, outputTypes, scopes, referencedScopes, format, isIncremental, secondaryFileInputs);
        }

        public Collection<TransformInput> getInputs() {
            return inputs;
        }

        @Override
        public void transform(
                @NonNull Context context,
                @NonNull Collection<TransformInput> inputs,
                @NonNull Collection<TransformInput> referencedInputs,
                boolean isIncremental)
                throws IOException, TransformException, InterruptedException {
            this.inputs = inputs;
            this.referencedInputs = referencedInputs;
            this.isIncrementalInputs = isIncremental;
        }
    }

    /**
     * Builder for the transforms.
     */
    static final class Builder {

        private String name;

        private final Set<ContentType> inputTypes = EnumSet
                .noneOf(ContentType.class);

        private Set<ContentType> outputTypes;

        private final Set<Scope> scopes = EnumSet.noneOf(Scope.class);

        private final Set<Scope> referencedScopes = EnumSet.noneOf(Scope.class);

        private Class<?> transformType;

        private Format format = Format.SINGLE_FOLDER;

        private boolean isIncremental = false;

        private final List<File> secondaryFileInputs = Lists.newArrayList();

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
                outputTypes = EnumSet.noneOf(ContentType.class);
            }
            outputTypes.addAll(Arrays.asList(types));
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

        Builder setTransformType(@NonNull Class<?> transformType) {
            this.transformType = transformType;
            return this;
        }

        Builder setFormat(@Nullable Format format) {
            this.format = format;
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

        @NonNull
        TestTransform build() {
            String name = this.name != null ? this.name : "transform name";
            Assert.assertFalse(this.inputTypes.isEmpty());
            Set<ContentType> inputTypes = Sets.immutableEnumSet(this.inputTypes);
            Set<ContentType> outputTypes = this.outputTypes != null ?
                    Sets.immutableEnumSet(this.outputTypes) : inputTypes;
            Set<Scope> scopes = Sets.immutableEnumSet(this.scopes);
            Set<Scope> refedScopes = Sets.immutableEnumSet(this.referencedScopes);
            Format format = this.format;

            if (transformType == AsInputTransform.class) {
                return new TestAsInputTransform(
                        name,
                        inputTypes,
                        outputTypes,
                        scopes,
                        refedScopes,
                        format,
                        isIncremental,
                        secondaryFileInputs);

            } else if (transformType == CombinedTransform.class) {
                return new TestCombinedTransform(
                        name,
                        inputTypes,
                        outputTypes,
                        scopes,
                        refedScopes,
                        format,
                        isIncremental,
                        secondaryFileInputs);

            } else if (transformType == NoOpTransform.class) {
                return new TestNoOpTransform(
                        name,
                        inputTypes,
                        outputTypes,
                        scopes,
                        refedScopes,
                        format,
                        isIncremental,
                        secondaryFileInputs);

            } else if (transformType == ForkTransform.class) {
                return new TestForkTransform(
                        name,
                        inputTypes,
                        outputTypes,
                        scopes,
                        refedScopes,
                        format,
                        isIncremental,
                        secondaryFileInputs);
            }

            throw new UnsupportedOperationException("Unsupported transform type: " + transformType);
        }
    }
}
