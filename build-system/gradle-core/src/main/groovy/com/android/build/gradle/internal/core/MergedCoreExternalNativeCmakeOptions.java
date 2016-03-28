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

import com.android.annotations.Nullable;
import com.android.build.gradle.internal.dsl.CoreExternalNativeCmakeOptions;
import com.google.common.collect.Sets;

import java.util.Set;

/**
 * Implementation of CoreExternalNativeCmakeOptions used to merge multiple configs together.
 */
public class MergedCoreExternalNativeCmakeOptions implements CoreExternalNativeCmakeOptions {

    private String cFlags;
    private Set<String> abiFilters;

    public void append(CoreExternalNativeCmakeOptions options) {
        if (options.getAbiFilters() != null) {
            if (abiFilters == null) {
                abiFilters = Sets.newHashSetWithExpectedSize(options.getAbiFilters().size());
            }
            abiFilters.addAll(options.getAbiFilters());
        }

        if (cFlags == null) {
            cFlags = options.getcFlags();
        } else if (options.getcFlags() != null && !options.getcFlags().isEmpty()) {
            cFlags = cFlags + " " + options.getcFlags();
        }
    }

    @Nullable
    @Override
    public String getcFlags() {
        return cFlags;
    }

    @Nullable
    @Override
    public Set<String> getAbiFilters() {
        return abiFilters;
    }
}

