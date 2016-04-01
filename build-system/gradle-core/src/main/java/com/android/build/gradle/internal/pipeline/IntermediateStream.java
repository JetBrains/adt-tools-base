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
import com.android.annotations.concurrency.Immutable;
import com.android.build.api.transform.QualifiedContent.ContentType;
import com.android.build.api.transform.QualifiedContent.Scope;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformOutputProvider;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Version of TransformStream handling outputs of transforms.
 */
@Immutable
class IntermediateStream extends TransformStream {

    private final Supplier<File> rootLocation;

    static Builder builder() {
        return new Builder();
    }

    static final class Builder {
        private Set<ContentType> contentTypes = Sets.newHashSet();
        private Set<Scope> scopes = Sets.newHashSet();
        private Supplier<File> rootLocation;
        private List<? extends Object> dependencies;

        public IntermediateStream build() {
            Preconditions.checkNotNull(rootLocation);
            Preconditions.checkState(!contentTypes.isEmpty());
            Preconditions.checkState(!scopes.isEmpty());
            return new IntermediateStream(
                    ImmutableSet.copyOf(contentTypes),
                    Sets.immutableEnumSet(scopes),
                    rootLocation,
                    dependencies != null ? dependencies : ImmutableList.of());
        }

        Builder addContentTypes(@NonNull Set<ContentType> types) {
            this.contentTypes.addAll(types);
            return this;
        }

        Builder addContentTypes(@NonNull ContentType... types) {
            this.contentTypes.addAll(Arrays.asList(types));
            return this;
        }

        Builder addScopes(@NonNull Set<Scope> scopes) {
            this.scopes.addAll(scopes);
            return this;
        }

        Builder addScopes(@NonNull Scope... scopes) {
            this.scopes.addAll(Arrays.asList(scopes));
            return this;
        }

        Builder setRootLocation(@NonNull final File rootLocation) {
            this.rootLocation = Suppliers.ofInstance(rootLocation);
            return this;
        }

        Builder setDependency(@NonNull Object dependency) {
            this.dependencies = ImmutableList.of(dependency);
            return this;
        }
    }

    private IntermediateStream(
            @NonNull Set<ContentType> contentTypes,
            @NonNull Set<Scope> scopes,
            @NonNull Supplier<File> rootLocation,
            @NonNull List<? extends Object> dependencies) {
        super(contentTypes, scopes, dependencies);
        this.rootLocation = rootLocation;
    }

    /**
     * Returns the files that make up the streams. The callable allows for resolving this lazily.
     */
    @NonNull
    Supplier<File> getRootLocation() {
        return rootLocation;
    }

    /**
     * Returns a new view of this content as a {@link TransformOutputProvider}.
     */
    @NonNull
    TransformOutputProvider asOutput() {
        return new TransformOutputProviderImpl(rootLocation.get());
    }

    @NonNull
    @Override
    List<File> getInputFiles() {
        return ImmutableList.of(rootLocation.get());
    }

    @NonNull
    @Override
    TransformInput asNonIncrementalInput() {
        return IntermediateFolderUtils.computeNonIncrementalInputFromFolder(
                rootLocation.get(),
                getContentTypes(),
                getScopes());
    }

    @NonNull
    @Override
    IncrementalTransformInput asIncrementalInput() {
        return IntermediateFolderUtils.computeIncrementalInputFromFolder(
                rootLocation.get(),
                getContentTypes(),
                getScopes());
    }

    @Override
    TransformStream makeRestrictedCopy(
            @NonNull Set<ContentType> types,
            @NonNull Set<Scope> scopes) {
        return new IntermediateStream(
                types,
                scopes,
                rootLocation,
                getDependencies());
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .add("rootLocation", rootLocation.get())
                .add("scopes", getScopes())
                .add("contentTypes", getContentTypes())
                .add("dependencies", getDependencies())
                .toString();
    }
}
