/*
 * Copyright (C) 2012 The Android Open Source Project
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
package com.android.build.gradle.internal.dependency;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.concurrency.Immutable;
import com.android.builder.dependency.LibraryBundle;
import com.android.builder.dependency.LibraryDependency;
import com.android.builder.dependency.ManifestDependency;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.MavenCoordinates;

import java.io.File;
import java.util.List;

@Immutable
public class LibraryDependencyImpl extends LibraryBundle {

    @NonNull
    private final List<LibraryDependency> dependencies;

    @Nullable
    private final String variantName;

    @Nullable
    private final MavenCoordinates requestedCoordinates;

    @Nullable
    private final MavenCoordinates resolvedCoordinates;

    public LibraryDependencyImpl(@NonNull File bundle,
                                 @NonNull File explodedBundle,
                                 @NonNull List<LibraryDependency> dependencies,
                                 @Nullable String name,
                                 @Nullable String variantName,
                                 @Nullable MavenCoordinates requestedCoordinates,
                                 @Nullable MavenCoordinates resolvedCoordinates) {
        super(bundle, explodedBundle, name);
        this.dependencies = dependencies;
        this.variantName = variantName;
        this.requestedCoordinates = requestedCoordinates;
        this.resolvedCoordinates = resolvedCoordinates;
    }

    @NonNull
    @Override
    public List<? extends AndroidLibrary> getLibraryDependencies() {
        return dependencies;
    }

    @Override
    @NonNull
    public List<LibraryDependency> getDependencies() {
        return dependencies;
    }

    @Override
    @NonNull
    public List<? extends ManifestDependency> getManifestDependencies() {
        return dependencies;
    }

    @Nullable
    @Override
    public String getProjectVariant() {
        return variantName;
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
}
