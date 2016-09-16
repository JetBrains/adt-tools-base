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
import com.android.annotations.VisibleForTesting;

import org.gradle.api.Action;
import org.gradle.internal.reflect.Instantiator;

/**
 * DSL object for per-variant CMake and ndk-build configurations.
 */
public class ExternalNativeBuildOptions implements CoreExternalNativeBuildOptions {
    @NonNull
    private ExternalNativeNdkBuildOptions ndkBuildOptions;
    @NonNull
    private ExternalNativeCmakeOptions cmakeOptions;

    @VisibleForTesting
    public ExternalNativeBuildOptions() {
        ndkBuildOptions = new ExternalNativeNdkBuildOptions();
        cmakeOptions = new ExternalNativeCmakeOptions();
    }

    public ExternalNativeBuildOptions(@NonNull Instantiator instantiator) {
        ndkBuildOptions = instantiator.newInstance(ExternalNativeNdkBuildOptions.class);
        cmakeOptions = instantiator.newInstance(ExternalNativeCmakeOptions.class);
    }

    @Nullable
    @Override
    public CoreExternalNativeNdkBuildOptions getExternalNativeNdkBuildOptions() {
        return ndkBuildOptions;
    }

    /**
     * Encapsulates per-variant ndk-build configurations, such as compiler flags and toolchain
     * arguments. To enable external native builds and set the path to your Android.mk script, use
     * {@link com.android.build.gradle.internal.dsl.NdkBuildOptions:path android.externalNativeBuild.ndkBuild.path}.
     */
    public ExternalNativeNdkBuildOptions getNdkBuild() {
        return ndkBuildOptions;
    }

    public void ndkBuild(Action<CoreExternalNativeNdkBuildOptions> action) {
        action.execute(ndkBuildOptions);
    }

    @Nullable
    @Override
    public CoreExternalNativeCmakeOptions getExternalNativeCmakeOptions() {
        return cmakeOptions;
    }

    /**
     * Encapsulates per-variant CMake configurations, such as compiler flags and toolchain arguments.
     * To enable external native builds and set the path to your CMakeLists.txt script, use
     * {@link com.android.build.gradle.internal.dsl.CmakeOptions:path android.externalNativeBuild.cmake.path}.
     */
    public ExternalNativeCmakeOptions getCmake() {
        return cmakeOptions;
    }

    public void cmake(Action<CoreExternalNativeCmakeOptions> action) {
        action.execute(cmakeOptions);
    }
}
