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

package com.android.build.gradle.managed;

import com.android.annotations.NonNull;

import org.gradle.model.Managed;

import java.util.List;
import java.util.Set;

/**
 * DSL object for variant specific ndk-build settings.
 */
@Managed
public interface ExternalNativeNdkBuildOptions {
    /**
     * The ndk-build build system flags
     */
    @NonNull
    List<String> getArguments();

    /**
     * The ABI Filters.  Leave empty to include all supported ABI.
     */
    @NonNull
    Set<String> getAbiFilters();

    /**
     * The C Flags
     */
    @NonNull
    List<String> getcFlags();

    /**
     * The CPP Flags
     */
    @NonNull
    List<String> getCppFlags();

    /**
     * The build targets
     */
    @NonNull
    Set<String> getTargets();
}
