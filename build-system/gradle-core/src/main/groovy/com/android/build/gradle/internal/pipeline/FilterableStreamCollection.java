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
import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.Format;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.TransformInput;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.io.File;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * A collection of {@link TransformStream} that can be queried.
 */
public abstract class FilterableStreamCollection {

    public interface StreamFilter {
        boolean accept(
                @NonNull Set<QualifiedContent.ContentType> types,
                @NonNull Set<QualifiedContent.Scope> scopes);
    }

    @NonNull
    abstract Collection<TransformStream> getStreams();

    @NonNull
    public ImmutableList<TransformStream> getStreams(@NonNull StreamFilter streamFilter) {
        ImmutableList.Builder<TransformStream> streamsByType = ImmutableList.builder();
        for (TransformStream s : getStreams()) {
            if (streamFilter.accept(s.getContentTypes(), s.getScopes())) {
                streamsByType.add(s);
            }
        }

        return streamsByType.build();
    }

    @NonNull
    public Map<File, Format> getPipelineOutput(
            @NonNull StreamFilter streamFilter) {
        ImmutableList<TransformStream> streams = getStreams(streamFilter);
        if (streams.isEmpty()) {
            return ImmutableMap.of();
        }

        ImmutableMap.Builder<File, Format> builder = ImmutableMap.builder();
        for (TransformStream stream : streams) {
            // get the input for it
            TransformInput input = stream.asNonIncrementalInput();

            for (JarInput jarInput : input.getJarInputs()) {
                builder.put(jarInput.getFile(), Format.JAR);
            }
            for (DirectoryInput directoryInput : input.getDirectoryInputs()) {
                builder.put(directoryInput.getFile(), Format.DIRECTORY);
            }
        }

        return builder.build();
    }
}
