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
import com.android.build.transform.api.TransformInput;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;

import java.io.File;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Implementation of TransformInput.
 */
@Immutable
class TransformInputImpl extends ScopedContentImpl implements TransformInput {

    @NonNull
    private final Collection<File> files;
    @NonNull
    private final Map<File, FileStatus> changedFiles;

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Set<ContentType> contentTypes;
        private Set<Scope> scopes;
        private Collection<File> files;
        private final Map<File, FileStatus> changedFiles = Maps.newHashMap();
        private Format format = Format.SINGLE_FOLDER;

        public TransformInput build() {
            return new TransformInputImpl(
                    contentTypes,
                    scopes,
                    format,
                    files,
                    ImmutableMap.copyOf(changedFiles));
        }

        public Builder from(@NonNull TransformStream stream) {
            contentTypes = ImmutableSet.copyOf(stream.getContentTypes());
            scopes = ImmutableSet.copyOf(stream.getScopes());
            files = stream.getFiles().get();
            format = stream.getFormat();
            return this;
        }

        public Builder setFormat(@NonNull Format format) {
            this.format = format;
            return this;
        }

        public Builder addChangedFile(@NonNull File file, @NonNull FileStatus status) {
            changedFiles.put(file, status);
            return this;
        }
    }

    private TransformInputImpl(
            @NonNull Set<ContentType> contentTypes,
            @NonNull Set<Scope> scopes,
            @NonNull Format format,
            @NonNull Collection<File> files,
            @NonNull Map<File, FileStatus> changedFiles) {
        super(contentTypes, scopes, format);
        this.files = files;
        this.changedFiles = changedFiles;
    }

    @NonNull
    @Override
    public Collection<File> getFiles() {
        return files;
    }

    @NonNull
    @Override
    public Map<File, FileStatus> getChangedFiles() {
        return changedFiles;
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .add("super", super.toString())
                .add("files", files)
                .add("changedFiles", changedFiles)
                .toString();
    }
}
