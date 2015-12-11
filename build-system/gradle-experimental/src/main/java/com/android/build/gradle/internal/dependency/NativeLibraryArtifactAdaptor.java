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

package com.android.build.gradle.internal.dependency;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.ProductFlavorCombo;
import com.android.utils.StringHelper;

import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.collections.SimpleFileCollection;
import org.gradle.nativeplatform.BuildType;
import org.gradle.nativeplatform.Flavor;
import org.gradle.nativeplatform.NativeLibraryBinary;
import org.gradle.nativeplatform.internal.DefaultBuildType;
import org.gradle.nativeplatform.internal.DefaultFlavor;
import org.gradle.nativeplatform.platform.NativePlatform;
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform;

/**
 * Adaptor for {@link NativeLibraryArtifact} to {@link NativeLibraryBinary}
 *
 * Used for adding artifact dependency to a NativeLibraryBinary.
 */
public class NativeLibraryArtifactAdaptor implements NativeLibraryBinary {
    @NonNull
    NativeLibraryArtifact artifact;

    public NativeLibraryArtifactAdaptor(@NonNull NativeLibraryArtifact artifact) {
        this.artifact = artifact;
    }

    @Override
    public FileCollection getHeaderDirs() {
        return new SimpleFileCollection(artifact.getExportedHeaderDirectories());
    }

    @Override
    public FileCollection getLinkFiles() {
        return new SimpleFileCollection(artifact.getLibraries());
    }

    @Override
    public FileCollection getRuntimeFiles() {
        return new SimpleFileCollection(artifact.getLibraries());
    }

    @Override
    public Flavor getFlavor() {
        return new DefaultFlavor(StringHelper.combineAsCamelCase(artifact.getProductFlavors()));
    }

    @Override
    public NativePlatform getTargetPlatform() {
        return new DefaultNativePlatform(artifact.getAbi());
    }

    @Override
    public BuildType getBuildType() {
        return new DefaultBuildType(artifact.getBuildType());
    }

    @Override
    public String getDisplayName() {
        return artifact.getName();
    }
}
