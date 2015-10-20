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

import com.android.annotations.Nullable;
import com.android.build.gradle.internal.NativeDependencyLinkage;

import org.gradle.api.Named;
import org.gradle.model.Managed;
import org.gradle.model.Unmanaged;

import java.io.File;
import java.util.List;
import java.util.Set;

/**
 * Container for all native library artifacts.
 */
@Managed
public interface NativeLibraryArtifact extends Named {

    /**
     * List of libraries in this artifact.
     */
    List<File> getLibraries();

    /**
     * Folders contain the source files used to generate the libraries.
     */
    List<File> getSrcFolders();

    /**
     * Folders container the public headers for the libraries.
     */
    Set<File> getExportedHeaderDirectories();

    /**
     * Build type of the artifact.
     */
    @Nullable
    String getBuildType();
    void setBuildType(String buildType);

    /**
     * Product flavors of the artifact.
     */
    List<String> getProductFlavors();

    /**
     * Variant name of the artifact.
     */
    @Nullable
    String getVariantName();
    void setVariantName(String variantName);

    /**
     * Target ABI of the artfiact.
     */
    @Nullable
    String getAbi();
    void setAbi(String abi);

    /**
     *
     */
    @Nullable
    NativeDependencyLinkage getLinkage();
    void setLinkage(NativeDependencyLinkage linkage);

    @Unmanaged
    Object getBuiltBy();
    void setBuiltBy(Object binary);
}
