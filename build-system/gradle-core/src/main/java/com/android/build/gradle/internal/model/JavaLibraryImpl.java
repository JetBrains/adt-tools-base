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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.concurrency.Immutable;
import com.android.builder.model.JavaLibrary;
import com.android.builder.model.MavenCoordinates;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;

import java.io.File;
import java.io.Serializable;
import java.util.List;

/**
 * Serializable implementation of JavaLibrary for use in the model.
 */
@Immutable
public class JavaLibraryImpl extends LibraryImpl implements JavaLibrary, Serializable {
    private final File jarFile;
    private final List<JavaLibrary> dependencies;

    public JavaLibraryImpl(
            @NonNull File jarFile,
            @Nullable String project,
            @NonNull List<JavaLibrary> dependencies,
            @Nullable MavenCoordinates requestedCoordinates,
            @NonNull MavenCoordinates resolvedCoordinates,
            boolean isSkipped,
            boolean isProvided) {
        super(project, requestedCoordinates, resolvedCoordinates, isSkipped, isProvided);
        this.jarFile = jarFile;
        this.dependencies = ImmutableList.copyOf(dependencies);
    }

    @NonNull
    @Override
    public File getJarFile() {
        return jarFile;
    }

    @NonNull
    @Override
    public List<? extends JavaLibrary> getDependencies() {
        return dependencies;
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
        JavaLibraryImpl that = (JavaLibraryImpl) o;
        return Objects.equal(jarFile, that.jarFile) &&
                Objects.equal(dependencies, that.dependencies);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(super.hashCode(), jarFile, dependencies);
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .add("jarFile", jarFile)
                .add("dependencies", dependencies)
                .add("super", super.toString())
                .toString();
    }
}
