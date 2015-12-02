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

package com.android.builder.dependency;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.JavaLibrary;
import com.android.builder.model.MavenCoordinates;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import java.io.File;
import java.util.List;

/**
 * Represents a Jar dependency. This could be the output of a Java project.
 *
 */
public class JarDependency implements JavaLibrary {

    @NonNull
    private final File mJarFile;

    private final boolean mCompiled;
    private final boolean mPackaged;
    private final boolean mProguarded;

    /** if the dependency is a sub-project, then the project path */
    @Nullable
    private final String mProjectPath;

    @Nullable
    private final MavenCoordinates mResolvedCoordinates;

    public JarDependency(
            @NonNull File jarFile,
            boolean compiled,
            boolean packaged,
            boolean proguarded,
            @Nullable MavenCoordinates resolvedCoordinates,
            @Nullable String projectPath) {
        Preconditions.checkNotNull(jarFile);
        mJarFile = jarFile;
        mCompiled = compiled;
        mPackaged = packaged;
        mProguarded = proguarded;
        mResolvedCoordinates = resolvedCoordinates;
        mProjectPath = projectPath;
    }

    public JarDependency(
            @NonNull File jarFile,
            boolean compiled,
            boolean packaged,
            @Nullable MavenCoordinates resolvedCoordinates,
            @Nullable String projectPath) {
        this(jarFile, compiled, packaged, true, resolvedCoordinates, projectPath);
    }

    @Nullable
    @Override
    public String getProject() {
        return mProjectPath;
    }

    @Nullable
    @Override
    public String getName() {
        if (mResolvedCoordinates != null) {
            return mResolvedCoordinates.toString();
        }

        return mJarFile.getName();
    }

    @Override
    @NonNull
    public File getJarFile() {
        return mJarFile;
    }

    public boolean isCompiled() {
        return mCompiled;
    }

    public boolean isPackaged() {
        return mPackaged;
    }

    public boolean isProguarded() {
        return mProguarded;
    }

    @Override
    public boolean isProvided() {
        return mCompiled && !mPackaged;
    }

    @NonNull
    @Override
    public List<? extends JavaLibrary> getDependencies() {
        return ImmutableList.of();
    }

    @Nullable
    @Override
    public MavenCoordinates getRequestedCoordinates() {
        return null;
    }

    @Override
    @Nullable
    public MavenCoordinates getResolvedCoordinates() {
        return mResolvedCoordinates;
    }

    @Nullable
    public String getProjectPath() {
        return mProjectPath;
    }

    @Override
    public String toString() {
        return "JarDependency{" +
                "mJarFile=" + mJarFile +
                ", mCompiled=" + mCompiled +
                ", mPackaged=" + mPackaged +
                ", mProguarded=" + mProguarded +
                ", mResolvedCoordinates=" + mResolvedCoordinates +
                '}';
    }
}
