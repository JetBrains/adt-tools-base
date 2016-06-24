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
import com.android.build.gradle.internal.dsl.CoreExternalNativeBuildOptions;
import com.android.build.gradle.internal.dsl.CoreExternalNativeCmakeOptions;
import com.android.build.gradle.internal.dsl.CoreExternalNativeNdkBuildOptions;
import com.android.build.gradle.managed.ExternalNativeBuildOptions;

/**
 * An adaptor to convert a ExternalNativeBuildOptions to CoreExternalNativeBuildOptions.
 */
public class ExternalNativeBuildOptionsAdaptor implements CoreExternalNativeBuildOptions {

    @NonNull
    private final ExternalNativeBuildOptions options;

    public ExternalNativeBuildOptionsAdaptor(@NonNull ExternalNativeBuildOptions options) {
        this.options = options;
    }

    @Nullable
    @Override
    public CoreExternalNativeNdkBuildOptions getExternalNativeNdkBuildOptions() {
        return new ExternalNativeNdkBuildOptionsAdaptor(options.getNdkBuild());
    }

    @Nullable
    @Override
    public CoreExternalNativeCmakeOptions getExternalNativeCmakeOptions() {
        return new ExternalNativeCmakeOptionsAdaptor(options.getCmake());
    }
}
