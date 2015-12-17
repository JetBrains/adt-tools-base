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
import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.QualifiedContent.ContentType;
import com.android.build.api.transform.QualifiedContent.Scope;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformOutputProvider;

import java.io.File;
import java.util.List;
import java.util.Set;

/**
 * Representation of a stream for internal usage of the {@link TransformManager} to wire up
 * the different Transforms.
 *
 * Transforms read from and write into TransformStreams, via a custom view of them:
 * {@link TransformInput}, and {@link TransformOutputProvider}.
 *
 * This contains information about the content via {@link QualifiedContent}, dependencies, and the
 * actual file information.
 *
 * The dependencies is what triggers the creation of the files and any Transform (task) consuming
 * the files must be made to depend on these objects.
 */
@Immutable
public abstract class TransformStream {

    @NonNull
    private final Set<ContentType> contentTypes;
    @NonNull
    private final Set<Scope> scopes;
    @NonNull
    private final List<? extends Object> dependencies;

    protected TransformStream(
            @NonNull Set<ContentType> contentTypes,
            @NonNull Set<Scope> scopes,
            @NonNull List<? extends Object> dependencies) {
        this.contentTypes = contentTypes;
        this.scopes = scopes;
        this.dependencies = dependencies;
    }

    /**
     * Returns the type of content that the stream represents.
     *
     * <p/>
     * It's never null nor empty, but can contain several types.
     */
    @NonNull
    public Set<ContentType> getContentTypes() {
        return contentTypes;
    }

    /**
     * Returns the scope of the stream.
     *
     * <p/>
     * It's never null nor empty, but can contain several scopes.
     */
    @NonNull
    public Set<Scope> getScopes() {
        return scopes;
    }

    /**
     * Returns the dependency objects that generate the stream files
     */
    @NonNull
    public List<? extends Object> getDependencies() {
        return dependencies;
    }

    @NonNull
    abstract List<File> getInputFiles();

    /**
     * Returns the transform input for this stream.
     *
     * All the {@link JarInput} and {@link DirectoryInput} will be in non-incremental mode.
     *
     * @return the transform input.
     */
    @NonNull
    abstract TransformInput asNonIncrementalInput();

    /**
     * Returns a list of QualifiedContent for the jars and one for the folders.
     *
     */
    @NonNull
    abstract IncrementalTransformInput asIncrementalInput();

    abstract TransformStream makeRestrictedCopy(
            @NonNull Set<ContentType> types,
            @NonNull Set<Scope> scopes);
}
