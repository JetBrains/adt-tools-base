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

package com.android.build.gradle.internal.externalBuild;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.IAndroidTarget;
import com.google.common.collect.ImmutableList;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Implementation of {@link com.android.sdklib.IAndroidTarget} used by the external build system.
 */
class ExternalBuildAndroidTarget implements IAndroidTarget {

    @NonNull
    private final File mAndroidJar;

    ExternalBuildAndroidTarget(@NonNull File androidJar) {
        mAndroidJar = androidJar;
    }

    @Override
    public String getLocation() {
        return mAndroidJar.getParentFile().getAbsolutePath();
    }

    @Override
    public String getVendor() {
        return null;
    }

    @Override
    public String getName() {
        return null;
    }

    @Override
    public String getFullName() {
        return null;
    }

    @Override
    public String getClasspathName() {
        return null;
    }

    @Override
    public String getShortClasspathName() {
        return null;
    }

    @Override
    public String getDescription() {
        return null;
    }

    @NonNull
    @Override
    public AndroidVersion getVersion() {
        return AndroidVersion.DEFAULT;
    }

    @Override
    public String getVersionName() {
        return null;
    }

    @Override
    public int getRevision() {
        return 0;
    }

    @Override
    public boolean isPlatform() {
        return false;
    }

    @Override
    public IAndroidTarget getParent() {
        return null;
    }

    @Override
    public String getPath(int pathId) {
        return null;
    }

    @Override
    public File getFile(int pathId) {
        return null;
    }

    @Override
    public BuildToolInfo getBuildToolInfo() {
        return null;
    }

    @NonNull
    @Override
    public List<String> getBootClasspath() {
        return ImmutableList.of(mAndroidJar.getAbsolutePath());
    }

    @NonNull
    @Override
    public List<OptionalLibrary> getOptionalLibraries() {
        return Collections.emptyList();
    }

    @NonNull
    @Override
    public List<OptionalLibrary> getAdditionalLibraries() {
        return Collections.emptyList();
    }

    @Override
    public boolean hasRenderingLibrary() {
        return false;
    }

    @NonNull
    @Override
    public File[] getSkins() {
        return new File[0];
    }

    @Nullable
    @Override
    public File getDefaultSkin() {
        return null;
    }

    @Override
    public String[] getPlatformLibraries() {
        return new String[0];
    }

    @Override
    public String getProperty(String name) {
        return null;
    }

    @Override
    public Map<String, String> getProperties() {
        return null;
    }

    @Override
    public boolean canRunOn(IAndroidTarget target) {
        return false;
    }

    @Override
    public String hashString() {
        return null;
    }

    @Override
    public int compareTo(IAndroidTarget o) {
        return getVersion().compareTo(o.getVersion());
    }
}
