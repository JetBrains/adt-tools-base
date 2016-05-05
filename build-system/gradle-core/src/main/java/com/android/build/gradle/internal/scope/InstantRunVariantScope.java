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

package com.android.build.gradle.internal.scope;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;

import java.io.File;
import java.util.List;

/**
 * Scope for all variant scoped information related to supporting the Instant Run features.
 */
public interface InstantRunVariantScope {

    @NonNull
    TransformGlobalScope getGlobalScope();

    @NonNull
    List<File> getBootClasspath(boolean includeOptionalLibraries);

    @NonNull
    File getReloadDexOutputFolder();

    @NonNull
    File getRestartDexOutputFolder();

    @NonNull
    File getInstantRunSupportDir();

    @NonNull
    File getIncrementalVerifierDir();

    @NonNull
    InstantRunBuildContext getInstantRunBuildContext();

}
