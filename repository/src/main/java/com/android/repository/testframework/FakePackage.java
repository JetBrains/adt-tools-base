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
package com.android.repository.testframework;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.Revision;
import com.android.repository.api.*;
import com.android.repository.impl.meta.Archive;
import com.android.repository.impl.meta.CommonFactory;
import com.android.repository.impl.meta.TypeDetails;
import com.google.common.collect.ImmutableList;

import java.io.File;
import java.util.Collection;

/**
 * A fake {@link RepoPackage} (implementing both {@link LocalPackage} and {@link RemotePackage},
 * for use in unit tests.
 *
 * Currently not especially fully-featured.
 */
@SuppressWarnings("ConstantConditions")
public class FakePackage implements LocalPackage, RemotePackage {
    private final String mPath;
    private final Revision mVersion;
    private final Collection<Dependency> mDependencies;
    private TypeDetails mDetails;

    public FakePackage(String path, Revision version, Collection<Dependency> dependencies) {
        mPath = path;
        mVersion = version;
        mDependencies = dependencies == null ? ImmutableList.<Dependency>of() : dependencies;
    }

    @NonNull
    @Override
    public RepositorySource getSource() {
        return null;
    }

    @Override
    public void setSource(@NonNull RepositorySource source) {
    }

    @Nullable
    @Override
    public Archive getArchive() {
        return null;
    }

    public void setTypeDetails(TypeDetails details) {
        mDetails = details;
    }

    @Nullable
    @Override
    public TypeDetails getTypeDetails() {
        return mDetails;
    }

    @NonNull
    @Override
    public Revision getVersion() {
        return mVersion;
    }

    @NonNull
    @Override
    public String getDisplayName() {
        return null;
    }

    @Nullable
    @Override
    public License getLicense() {
        return null;
    }

    @NonNull
    @Override
    public Collection<Dependency> getAllDependencies() {
        return mDependencies;
    }

    @NonNull
    @Override
    public String getPath() {
        return mPath;
    }

    @Override
    public boolean obsolete() {
        return false;
    }

    @NonNull
    @Override
    public CommonFactory createFactory() {
        return null;
    }

    @Override
    public int compareTo(@NonNull RepoPackage o) {
        return 0;
    }

    @NonNull
    @Override
    public File getLocation() {
        return null;
    }

    @Override
    public void setInstalledPath(@NonNull File root) {
    }

    @Override
    public String toString() {
        return mPath;
    }
}
