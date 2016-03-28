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
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.dsl.CoreExternalNativeCmakeOptions;
import com.android.build.gradle.managed.ExternalNativeCmakeOptions;

import java.util.Set;

/**
 * An adaptor to convert a ExternalNativeCmakeOptions to CoreExternalNativeCmakeOptions.
 */
public class ExternalNativeCmakeOptionsAdaptor implements CoreExternalNativeCmakeOptions {

    private final ExternalNativeCmakeOptions options;

    public ExternalNativeCmakeOptionsAdaptor(@NonNull ExternalNativeCmakeOptions options) {
        this.options = options;
    }

    @Nullable
    @Override
    public String getcFlags() {
        return options.getcFlags();
    }

    @Nullable
    @Override
    public Set<String> getAbiFilters() {
        return options.getAbiFilters();
    }
}

