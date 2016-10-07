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
import com.android.build.gradle.internal.model.CoreExternalNativeBuild;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.internal.reflect.Instantiator;

/**
 * DSL object to configure external native builds.
 */
public class ExternalNativeBuild implements CoreExternalNativeBuild {
    private NdkBuildOptions ndkBuild;
    private CmakeOptions cmake;

    public ExternalNativeBuild(@NonNull Instantiator instantiator, @NonNull Project project) {
        ndkBuild = instantiator.newInstance(NdkBuildOptions.class, project);
        cmake = instantiator.newInstance(CmakeOptions.class, project);
    }

    /**
     * ndk-build options.
     */
    @NonNull
    @Override
    public NdkBuildOptions getNdkBuild() {
        return this.ndkBuild;
    }

    /**
     * Configures the {@link NdkBuildOptions}.
     */
    public NdkBuildOptions ndkBuild(Action<NdkBuildOptions> action) {
        action.execute(ndkBuild);
        return this.ndkBuild;
    }

    /**
     * CMake build options.
     */
    @NonNull
    @Override
    public CmakeOptions getCmake() {
        return cmake;
    }

    /**
     * Configures the {@link CmakeOptions}.
     */
    public CmakeOptions cmake(Action<CmakeOptions> action) {
        action.execute(cmake);
        return this.cmake;
    }
}
