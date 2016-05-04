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

package com.android.build.gradle.internal.dsl;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;

import com.google.common.collect.Sets;

import java.util.Collections;
import java.util.Set;

/**
 * DSL object for external native build ndk-build settings.
 */
public class ExternalNativeNdkBuildOptions implements CoreExternalNativeNdkBuildOptions {

    @Nullable
    private String cFlags;
    @Nullable
    private String cppFlags;
    @NonNull
    private Set<String> abiFilters = Sets.newHashSet();

    @Nullable
    @Override
    public String getcFlags() {
        return cFlags;
    }

    public void setcFlags(@Nullable String cFlags) {
        this.cFlags = cFlags;
    }

    @Nullable
    @Override
    public String getCppFlags() {
        return cppFlags;
    }

    public void setCppFlags(@Nullable String cppFlags) {
        this.cppFlags = cppFlags;
    }

    @NonNull
    @Override
    public Set<String> getAbiFilters() {
        return abiFilters;
    }

    @NonNull
    public ExternalNativeNdkBuildOptions abiFilter(String filter) {
        abiFilters.add(filter);
        return this;
    }

    @NonNull
    public ExternalNativeNdkBuildOptions abiFilters(String... filters) {
        Collections.addAll(abiFilters, filters);
        return this;
    }
}
