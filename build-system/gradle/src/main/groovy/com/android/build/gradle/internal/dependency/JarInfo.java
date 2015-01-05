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

package com.android.build.gradle.internal.dependency;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.dependency.JarDependency;
import com.android.builder.model.MavenCoordinates;
import com.google.common.collect.Lists;

import java.io.File;
import java.util.List;

/**
 * Information about a jar dependency as well as its transitive dependencies.
 */
public class JarInfo {

    @NonNull
    private final File jarFile;

    private boolean compiled = false;
    private boolean packaged = false;

    @NonNull
    final List<JarInfo> dependencies = Lists.newArrayList();

    @Nullable
    private final MavenCoordinates resolvedCoordinates;

    public JarInfo(
            @NonNull File jarFile,
            @Nullable MavenCoordinates resolvedCoordinates,
            @NonNull List<JarInfo> dependencies) {
        this.jarFile = jarFile;
        this.resolvedCoordinates = resolvedCoordinates;
        this.dependencies.addAll(dependencies);
    }

    public void setCompiled(boolean compiled) {
        this.compiled = compiled;
    }

    public void setPackaged(boolean packaged) {
        this.packaged = packaged;
    }

    @NonNull
    public File getJarFile() {
        return jarFile;
    }

    @Nullable
    public MavenCoordinates getResolvedCoordinates() {
        return resolvedCoordinates;
    }

    @NonNull
    public List<JarInfo> getDependencies() {
        return dependencies;
    }

    @NonNull
    public JarDependency createJarDependency() {
        return new JarDependency(
                jarFile,
                compiled,
                packaged,
                true /*proguarded*/,
                resolvedCoordinates
        );
    }

    @Override
    public String toString() {
        return "JarInfo{" +
                "jarFile=" + jarFile +
                ", compiled=" + compiled +
                ", packaged=" + packaged +
                ", dependencies=" + dependencies +
                ", resolvedCoordinates=" + resolvedCoordinates +
                '}';
    }
}