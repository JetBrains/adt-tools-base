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
import com.android.annotations.concurrency.Immutable;
import com.android.build.transform.api.ScopedContent;
import com.android.build.transform.api.Transform;
import com.android.build.transform.api.TransformOutput;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Representation of a stream for internal usage of the {@link TransformManager} to wire up
 * the different Transforms.
 *
 * Transforms read from and write into TransformStreams, via a custom view of them:
 * {@link com.android.build.transform.api.TransformInput}, and {@link TransformOutput}.
 *
 * This contains information about the content via {@link ScopedContent}, as well as the files
 * of the stream (as a {@link Supplier} so that it can be lazy), and the dependencies of the stream.
 *
 * The dependencies is what triggers the creation of the files and any Transform (task) consuming
 * the files must be made to depend on these objects.
 */
@Immutable
public class TransformStream extends ScopedContentImpl {

    private final Supplier<Collection<File>> files;
    @NonNull
    private final List<? extends Object> dependencies;
    @Nullable
    private final TransformStream parentStream;

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Set<ContentType> contentTypes = Sets.newHashSet();
        private Set<Scope> scopes = Sets.newHashSet();
        private Supplier<Collection<File>> files;
        private List<? extends Object> dependencies;
        private Format format = null;
        private TransformStream parentStream = null;

        public TransformStream build() {
            Preconditions.checkNotNull(format, "StreamFormat cannot be null");
            return new TransformStream(
                    Sets.immutableEnumSet(contentTypes),
                    Sets.immutableEnumSet(scopes),
                    format,
                    files,
                    dependencies != null ? dependencies : ImmutableList.of(),
                    parentStream);
        }

        /**
         * Initialises the builder with the given {@link TransformStream}.
         * @param stream the stream.
         * @return this
         */
        public Builder from(@NonNull TransformStream stream) {
            contentTypes.addAll(stream.getContentTypes());
            scopes.addAll(stream.getScopes());
            format = stream.getFormat();
            files = stream.getFiles();
            dependencies = stream.getDependencies();
            return this;
        }

        /**
         * Initializes with the given {@link TransformStream} but restrict the content type
         * to a new set of values.
         *
         * @param stream the stream.
         * @param types the new content type values
         * @return this
         */
        public Builder copyWithRestrictedTypes(
                @NonNull TransformStream stream,
                @NonNull Set<ContentType> types) {
            contentTypes.addAll(types);
            scopes.addAll(stream.getScopes());
            format = stream.getFormat();
            files = stream.getFiles();
            dependencies = stream.getDependencies();
            return this;
        }

        public Builder addContentTypes(@NonNull Set<ContentType> types) {
            this.contentTypes.addAll(types);
            return this;
        }

        public Builder addContentTypes(@NonNull ContentType... types) {
            this.contentTypes.addAll(Arrays.asList(types));
            return this;
        }

        public Builder addContentType(@NonNull ContentType type) {
            this.contentTypes.add(type);
            return this;
        }

        public Builder addScopes(@NonNull Set<Scope> scopes) {
            this.scopes.addAll(scopes);
            return this;
        }

        public Builder addScopes(@NonNull Scope... scopes) {
            this.scopes.addAll(Arrays.asList(scopes));
            return this;
        }

        public Builder addScope(@NonNull Scope scope) {
            this.scopes.add(scope);
            return this;
        }

        public Builder setFormat(@NonNull Format format) {
            this.format = format;
            return this;
        }

        public Builder setFiles(@NonNull final Collection<File> files) {
            this.files = Suppliers.ofInstance(files);
            return this;
        }

        public Builder setFiles(@NonNull final File file) {
            this.files = Suppliers.ofInstance((Collection<File>) ImmutableList.of(file));
            return this;
        }

        public Builder setFiles(@NonNull Supplier<Collection<File>> inputCallable) {
            this.files = inputCallable;
            return this;
        }

        public Builder setDependencies(@NonNull List<? extends Object> dependencies) {
            this.dependencies = ImmutableList.copyOf(dependencies);
            return this;
        }

        public Builder setDependency(@NonNull Object dependency) {
            this.dependencies = ImmutableList.of(dependency);
            return this;
        }

        public Builder setParentStream(@NonNull TransformStream parentStream) {
            this.parentStream = parentStream;
            return this;
        }
    }

    private TransformStream(
            @NonNull Set<ContentType> contentTypes,
            @NonNull Set<Scope> scopes,
            @NonNull Format format,
            @NonNull Supplier<Collection<File>> files,
            @NonNull List<? extends Object> dependencies,
            @Nullable TransformStream parentStream) {
        super(contentTypes, scopes, format);
        this.files = files;
        this.dependencies = dependencies;
        this.parentStream = parentStream;
    }

    /**
     * Returns the files that make up the streams. The callable allows for resolving this lazily.
     */
    @NonNull
    public Supplier<Collection<File>> getFiles() {
        return files;
    }

    /**
     * Returns the dependency objects that generate the stream files
     */
    @NonNull
    public List<? extends Object> getDependencies() {
        return dependencies;
    }

    /**
     * Returns an optional parent stream.
     *
     * If a stream is the output of a Transform with type {@link Transform.Type#AS_INPUT}, there is
     * a connection between the input and output stream (each input has a matching output).
     *
     * This method returns the matching input stream, if this stream is the output of such a
     * Transform.
     */
    @Nullable
    public TransformStream getParentStream() {
        return parentStream;
    }

    /**
     * Returns a new view of this content as a {@link TransformOutput}.
     */
    @NonNull
    public TransformOutput asOutput() {
        if (TransformManager.DISALLOWED_OUTPUT_FORMATS.contains(getFormat())) {
            throw new RuntimeException(
                    "Can't make a TransformOutput from a ScopedContent with format:" + getFormat());
        }

        return new TransformOutput() {
            @NonNull
            @Override
            public File getOutFile() {
                return Iterables.getOnlyElement(getFiles().get());
            }

            @NonNull
            @Override
            public Set<ContentType> getContentTypes() {
                return TransformStream.this.getContentTypes();
            }

            @NonNull
            @Override
            public Set<Scope> getScopes() {
                return TransformStream.this.getScopes();
            }

            @NonNull
            @Override
            public Format getFormat() {
                return TransformStream.this.getFormat();
            }
        };
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .add("super", super.toString())
                .add("files", files)
                .add("dependencies", dependencies)
                .add("parentStream", parentStream)
                .toString();
    }
}
