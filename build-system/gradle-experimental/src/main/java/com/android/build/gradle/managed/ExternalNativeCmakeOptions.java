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

import org.gradle.model.Managed;

import java.util.Set;

/**
 * DSL object for variant specific cmake settings.
 */
@Managed
public interface ExternalNativeCmakeOptions {
    /**
     * The ABI Filters.  Leave empty to include all supported ABI.
     */
    Set<String> getAbiFilters();
    void setAbiFilters(Set<String> abiFilters);

    /**
     * The C Flags
     */
    String getcFlags();
    void setcFlags(String flags);
}
