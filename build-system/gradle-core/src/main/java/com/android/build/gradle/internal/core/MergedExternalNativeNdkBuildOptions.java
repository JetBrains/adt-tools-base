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

package com.android.build.gradle.internal.core;


import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.dsl.CoreExternalNativeNdkBuildOptions;
import com.google.common.base.Strings;
import com.google.common.collect.Sets;

import java.util.Set;

/**
 * Implementation of CoreExternalNativeNdkBuildOptions used to merge multiple configs together.
 */
public class MergedExternalNativeNdkBuildOptions implements CoreExternalNativeNdkBuildOptions {

    @Nullable
    private String cFlags = null;
    @Nullable
    private String cppFlags = null;
    @NonNull
    private Set<String> abiFilters = Sets.newHashSet();

    public void reset() {
        cFlags = null;
        cppFlags = null;
        abiFilters.clear();
    }

    public void append(@NonNull CoreExternalNativeNdkBuildOptions options) {
        if (cFlags == null) {
            cFlags = options.getcFlags();
        } else if (!Strings.isNullOrEmpty(options.getcFlags())) {
            cFlags = cFlags + " " + options.getcFlags();
        }

        if (cppFlags == null) {
            cppFlags = options.getCppFlags();
        } else if (!Strings.isNullOrEmpty(options.getCppFlags())) {
            cppFlags = cppFlags + " " + options.getCppFlags();
        }
        abiFilters.addAll(options.getAbiFilters());

    }

    @Nullable
    @Override
    public String getcFlags() {
        return cFlags;
    }

    @Nullable
    @Override
    public String getCppFlags() {
        return cppFlags;
    }

    @NonNull
    @Override
    public Set<String> getAbiFilters() {
        return abiFilters;
    }
}
