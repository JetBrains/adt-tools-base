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
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import org.gradle.nativeplatform.NativeLibraryBinary;

import java.util.Collection;
import java.util.Set;

/**
 * Result of resolving dependencies for a native project.
 */
public class NativeDependencyResolveResult {

    @NonNull
    private Collection<NativeLibraryArtifact> nativeArtifacts = Lists.newArrayList();

    @NonNull
    private Set<NativeLibraryBinary> prebuiltLibraries = Sets.newHashSet();

    @NonNull
    public Collection<NativeLibraryArtifact> getNativeArtifacts() {
        return nativeArtifacts;
    }

    @NonNull
    public Set<NativeLibraryBinary> getPrebuiltLibraries() {
        return prebuiltLibraries;
    }
}
