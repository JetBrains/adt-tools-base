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

package com.android.build.gradle.internal.core;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;

/**
 * Enum of valid toolchains you can specify for NDK.
 */
public enum Toolchain {
    //    name     version32 version64 gccVersion32 gccVersion64
    GCC  ("gcc",   "4.6",    "4.9",    "",          ""),
    CLANG("clang", "3.4",    "3.4",    "4.8",         "4.9");

    @NonNull
    private final String name;
    @NonNull
    private final String defaultVersion32;
    @NonNull
    private final String defaultVersion64;

    // Default GCC version used for non compilation stages (e.g. linking).
    @NonNull
    private final String defaultGccVersion32;
    @NonNull
    private final String defaultGccVersion64;

    @NonNull
    public static Toolchain getDefault() {
        return GCC;
    }

    @Nullable
    public static Toolchain getByName(@NonNull String toolchainName) {
        for (Toolchain toolchain : values()) {
            if (toolchain.name.equals(toolchainName)) {
                return toolchain;
            }
        }
        return null;
    }

    Toolchain(
            @NonNull String name,
            @NonNull String defaultVersion32,
            @NonNull String defaultVersion64,
            @NonNull String defaultGccVersion32,
            @NonNull String defaultGccVersion64) {
        this.name = name;
        this.defaultVersion32 = defaultVersion32;
        this.defaultVersion64 = defaultVersion64;
        this.defaultGccVersion32 = defaultGccVersion32;
        this.defaultGccVersion64 = defaultGccVersion64;
    }

    @NonNull
    public String getName() {
        return name;
    }

    @NonNull
    public String getDefaultVersion32() {
        return defaultVersion32;
    }

    @NonNull
    public String getDefaultVersion64() {
        return defaultVersion64;
    }

    @NonNull
    public String getDefaultGccVersion32() {
        return defaultGccVersion32;
    }

    @NonNull
    public String getDefaultGccVersion64() {
        return defaultGccVersion64;
    }
}
