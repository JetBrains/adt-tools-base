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

import static com.android.SdkConstants.FN_ANDROID_MANIFEST_XML;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.AndroidBundle;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.JavaLibrary;
import com.android.builder.model.MavenCoordinates;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;

import java.io.File;
import java.util.Collection;
import java.util.List;

/**
 * Default implementation of the AndroidBundle interface that handles a default
 * bundle project structure.
 */
public abstract class AbstractBundleDependency implements AndroidBundle {

    public static final String FN_PROGUARD_TXT = "proguard.txt";

    @NonNull
    private final File mBundle;
    @NonNull
    private final File mBundleFolder;
    @NonNull
    private final List<LibraryDependency> mLibraryDependencies;
    @NonNull
    private final Collection<JarDependency> mJarDependencies;
    @Nullable
    private final String mName;
    @Nullable
    private final String mProjectPath;
    @Nullable
    private final String mProjectVariant;
    @Nullable
    private final MavenCoordinates mRequestedCoordinates;
    @NonNull
    private final MavenCoordinates mResolvedCoordinates;

    /**
     * Creates the mBundle dependency with an optional mName.
     *
     * @param bundle the library's aar mBundle file.
     * @param bundleFolder the folder containing the unarchived library content.
     * @param libraryDependencies the Android library dependencies.
     * @param jarDependencies the java jar dependencies.
     * @param name an optional name.
     * @param projectVariant an optional project variant.
     * @param projectPath an optional project path.
     * @param requestedCoordinates the optional maven coordinates.
     * @param resolvedCoordinates the resolved maven coordinates.
     */
    public AbstractBundleDependency(
            @NonNull File bundle,
            @NonNull File bundleFolder,
            @NonNull List<LibraryDependency> libraryDependencies,
            @NonNull Collection<JarDependency> jarDependencies,
            @Nullable String name,
            @Nullable String projectVariant,
            @Nullable String projectPath,
            @Nullable MavenCoordinates requestedCoordinates,
            @NonNull MavenCoordinates resolvedCoordinates) {
        this.mBundle = bundle;
        this.mBundleFolder = bundleFolder;
        this.mLibraryDependencies = ImmutableList.copyOf(libraryDependencies);
        this.mJarDependencies = ImmutableList.copyOf(jarDependencies);
        this.mName = name;
        this.mProjectVariant = projectVariant;
        this.mProjectPath = projectPath;
        this.mRequestedCoordinates = requestedCoordinates;
        this.mResolvedCoordinates = resolvedCoordinates;
    }

    // Library implementation

    @Nullable
    @Override
    public String getProject() {
        return mProjectPath;
    }

    @Override
    @Nullable
    public String getName() {
        return mName;
    }

    @Nullable
    @Override
    public MavenCoordinates getRequestedCoordinates() {
        return mRequestedCoordinates;
    }

    @NonNull
    @Override
    public MavenCoordinates getResolvedCoordinates() {
        return mResolvedCoordinates;
    }

    // AndroidBundle implementation.

    @Nullable
    @Override
    public String getProjectVariant() {
        return mProjectVariant;
    }

    @Override
    @NonNull
    public File getBundle() {
        return mBundle;
    }

    @Override
    @NonNull
    public File getFolder() {
        return mBundleFolder;
    }

    @NonNull
    @Override
    public List<? extends AndroidLibrary> getLibraryDependencies() {
        return mLibraryDependencies;
    }

    @NonNull
    @Override
    public Collection<? extends JavaLibrary> getJavaDependencies() {
        return mJarDependencies;
    }

    @Override
    @NonNull
    public File getManifest() {
        return new File(mBundleFolder, FN_ANDROID_MANIFEST_XML);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AbstractBundleDependency that = (AbstractBundleDependency) o;
        return Objects.equal(mBundle, that.mBundle) &&
                Objects.equal(mBundleFolder, that.mBundleFolder) &&
                Objects.equal(mLibraryDependencies, that.mLibraryDependencies) &&
                Objects.equal(mJarDependencies, that.mJarDependencies) &&
                Objects.equal(mName, that.mName) &&
                Objects.equal(mProjectPath, that.mProjectPath) &&
                Objects.equal(mProjectVariant, that.mProjectVariant) &&
                Objects.equal(mRequestedCoordinates, that.mRequestedCoordinates) &&
                Objects.equal(mResolvedCoordinates, that.mResolvedCoordinates);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(
                mBundle,
                mBundleFolder,
                mLibraryDependencies,
                mJarDependencies,
                mName,
                mProjectPath,
                mProjectVariant,
                mRequestedCoordinates,
                mResolvedCoordinates);
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .add("mBundle", mBundle)
                .add("mBundleFolder", mBundleFolder)
                .add("mLibraryDependencies", mLibraryDependencies)
                .add("mJarDependencies", mJarDependencies)
                .add("mName", mName)
                .add("mProjectPath", mProjectPath)
                .add("mProjectVariant", mProjectVariant)
                .add("mRequestedCoordinates", mRequestedCoordinates)
                .add("mResolvedCoordinates", mResolvedCoordinates)
                .toString();
    }

}
