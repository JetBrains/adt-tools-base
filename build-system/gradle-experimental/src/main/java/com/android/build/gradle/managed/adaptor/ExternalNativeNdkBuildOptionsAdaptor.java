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

package com.android.build.gradle.managed.adaptor;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.dsl.CoreExternalNativeNdkBuildOptions;
import com.android.build.gradle.managed.ExternalNativeNdkBuildOptions;

import java.util.List;
import java.util.Set;

/**
 * An adaptor to convert a ExternalNativeNdkBuildOptions to CoreExternalNativeNdkBuildOptions.
 */
public class ExternalNativeNdkBuildOptionsAdaptor implements CoreExternalNativeNdkBuildOptions {

    @NonNull
    private final ExternalNativeNdkBuildOptions options;

    public ExternalNativeNdkBuildOptionsAdaptor(@NonNull ExternalNativeNdkBuildOptions options) {
        this.options = options;
    }

    @NonNull
    @Override
    public List<String> getArguments() {
        return options.getArguments();
    }

    @NonNull
    @Override
    public List<String> getcFlags() {
        return options.getcFlags();
    }

    @NonNull
    @Override
    public List<String> getCppFlags() {
        return options.getCppFlags();
    }

    @NonNull
    @Override
    public Set<String> getAbiFilters() {
        return options.getAbiFilters();
    }

    @NonNull
    @Override
    public Set<String> getTargets() {
        return options.getTargets();
    }
}
