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
package com.android.builder.dependency;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.JavaLibrary;
import com.android.builder.model.MavenCoordinates;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class LibraryDependency extends AbstractLibraryDependency implements SkippableLibrary {

    @NonNull
    private final List<LibraryDependency> androidDependencies;
    @NonNull
    private final Collection<JarDependency> jarDependencies;

    @Nullable
    private final String variantName;

    @Nullable
    private final MavenCoordinates requestedCoordinates;

    @NonNull
    private final MavenCoordinates resolvedCoordinates;

    private final AtomicBoolean skipped = new AtomicBoolean(false);

    public LibraryDependency(
            @NonNull File bundle,
            @NonNull File explodedBundle,
            @NonNull List<LibraryDependency> androidDependencies,
            @NonNull Collection<JarDependency> jarDependencies,
            @Nullable String name,
            @Nullable String variantName,
            @Nullable String projectPath,
            @Nullable MavenCoordinates requestedCoordinates,
            @NonNull MavenCoordinates resolvedCoordinates,
            boolean isProvided) {
        super(bundle, explodedBundle, name, projectPath, isProvided);
        this.androidDependencies = ImmutableList.copyOf(androidDependencies);
        this.jarDependencies = ImmutableList.copyOf(jarDependencies);
        this.variantName = variantName;
        this.requestedCoordinates = requestedCoordinates;
        this.resolvedCoordinates = resolvedCoordinates;
    }

    @NonNull
    @Override
    public List<? extends AndroidLibrary> getLibraryDependencies() {
        return androidDependencies;
    }

    @NonNull
    @Override
    public Collection<? extends JavaLibrary> getJavaDependencies() {
        return jarDependencies;
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

    @NonNull
    @Override
    public MavenCoordinates getResolvedCoordinates() {
        return resolvedCoordinates;
    }

    @Override
    public boolean isSkipped() {
        return skipped.get();
    }

    @Override
    public void skip() {
        skipped.set(true);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        LibraryDependency that = (LibraryDependency) o;
        return Objects.equal(androidDependencies, that.androidDependencies) &&
                Objects.equal(jarDependencies, that.jarDependencies) &&
                Objects.equal(variantName, that.variantName) &&
                Objects.equal(requestedCoordinates, that.requestedCoordinates) &&
                Objects.equal(resolvedCoordinates, that.resolvedCoordinates);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(
                super.hashCode(),
                androidDependencies,
                jarDependencies,
                variantName,
                requestedCoordinates,
                resolvedCoordinates);
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .add("androidDependencies", androidDependencies)
                .add("jarDependencies", jarDependencies)
                .add("variantName", variantName)
                .add("requestedCoordinates", requestedCoordinates)
                .add("resolvedCoordinates", resolvedCoordinates)
                .add("super", super.toString())
                .toString();
    }
}
