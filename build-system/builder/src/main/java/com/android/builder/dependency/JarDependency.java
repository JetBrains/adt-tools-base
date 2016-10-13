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
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Represents a Jar dependency. This could be the output of a Java project.
 *
 */
public class JarDependency implements JavaLibrary, SkippableLibrary {

    public static final String LOCAL_JAR_GROUPID = "__local_jars__";
    @NonNull
    private final File mJarFile;

    private final boolean mIsProvided;

    /** if the dependency is a sub-project, then the project path */
    @Nullable
    private final String mProjectPath;

    private final List<JarDependency> mDependencies;

    @NonNull
    private final MavenCoordinates mResolvedCoordinates;

    private final AtomicBoolean skipped = new AtomicBoolean(false);

    public JarDependency(
            @NonNull File jarFile,
            @NonNull List<JarDependency> dependencies,
            @NonNull MavenCoordinates resolvedCoordinates,
            @Nullable String projectPath,
            boolean isProvided) {
        Preconditions.checkNotNull(jarFile);
        mJarFile = jarFile;
        mIsProvided = isProvided;
        mDependencies = ImmutableList.copyOf(dependencies);
        mResolvedCoordinates = resolvedCoordinates;
        mProjectPath = projectPath;
    }

    /**
     * Local Jar creator
     * @param jarFile the jar file location
     */
    public JarDependency(@NonNull File jarFile) {
        this(
                jarFile,
                ImmutableList.<JarDependency>of(),
                getCoordForLocalJar(jarFile),
                null /*projectPath*/,
                false /*isProvided*/);
    }

    @NonNull
    public static MavenCoordinatesImpl getCoordForLocalJar(@NonNull File jarFile) {
        return new MavenCoordinatesImpl(LOCAL_JAR_GROUPID, jarFile.getPath(), "unspecified");
    }

    @Nullable
    @Override
    public String getProject() {
        return mProjectPath;
    }

    @Nullable
    @Override
    public String getName() {
        return mResolvedCoordinates.toString();
    }

    @Override
    @NonNull
    public File getJarFile() {
        return mJarFile;
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
    public boolean isProvided() {
        return mIsProvided;
    }

    @NonNull
    @Override
    public List<? extends JavaLibrary> getDependencies() {
        return mDependencies;
    }

    @Nullable
    @Override
    public MavenCoordinates getRequestedCoordinates() {
        return null;
    }

    @Override
    @NonNull
    public MavenCoordinates getResolvedCoordinates() {
        return mResolvedCoordinates;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        JarDependency that = (JarDependency) o;
        return mIsProvided == that.mIsProvided &&
                Objects.equal(mJarFile, that.mJarFile) &&
                Objects.equal(mProjectPath, that.mProjectPath) &&
                Objects.equal(mDependencies, that.mDependencies) &&
                Objects.equal(mResolvedCoordinates, that.mResolvedCoordinates) &&
                Objects.equal(isSkipped(), that.isSkipped()); // AtomicBoolean does not implements Equals!
    }

    @Override
    public int hashCode() {
        return HashCodeUtils.hashCode(
                mJarFile,
                mIsProvided,
                mProjectPath,
                mDependencies,
                mResolvedCoordinates,
                isSkipped()); // AtomicBoolean does not implements hashCode!
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .add("mJarFile", mJarFile)
                .add("mIsProvided", mIsProvided)
                .add("mProjectPath", mProjectPath)
                .add("mDependencies", mDependencies)
                .add("mResolvedCoordinates", mResolvedCoordinates)
                .add("skipped", skipped)
                .toString();
    }
}
