/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.build.gradle.internal.model;

import com.android.annotations.Nullable;
import com.android.annotations.concurrency.Immutable;
import com.android.builder.model.Library;
import com.android.builder.model.MavenCoordinates;
import com.google.common.base.Objects;

import java.io.Serializable;

/**
 * Serializable implementation of Library for use in the model.
 */
@Immutable
class LibraryImpl implements Library, Serializable {
    private static final long serialVersionUID = 1L;

    @Nullable
    private final String project;
    @Nullable
    private final String name;

    @Nullable
    private final MavenCoordinates requestedCoordinates;

    @Nullable
    private final MavenCoordinates resolvedCoordinates;

    LibraryImpl(
            @Nullable String project,
            @Nullable MavenCoordinates requestedCoordinates,
            @Nullable MavenCoordinates resolvedCoordinates) {
        this.project = project;
        this.name = resolvedCoordinates != null ? resolvedCoordinates.toString() : null;
        this.requestedCoordinates = requestedCoordinates;
        this.resolvedCoordinates = resolvedCoordinates;
    }

    @Override
    @Nullable
    public String getProject() {
        return project;
    }

    @Nullable
    @Override
    public String getName() {
        return name;
    }

    @Nullable
    @Override
    public MavenCoordinates getRequestedCoordinates() {
        return requestedCoordinates;
    }

    @Nullable
    @Override
    public MavenCoordinates getResolvedCoordinates() {
        return resolvedCoordinates;
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .add("name", name)
                .add("requestedCoordinates", requestedCoordinates)
                .add("resolvedCoordinates", resolvedCoordinates)
                .toString();
    }
}
