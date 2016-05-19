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

package com.android.build.gradle.internal.externalBuild;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.scope.InstantRunVariantScope;
import com.android.build.gradle.internal.scope.TransformGlobalScope;
import com.android.build.gradle.internal.scope.TransformVariantScope;
import com.google.common.collect.ImmutableList;

import java.io.File;
import java.util.Collection;
import java.util.List;

/**
 * Implementation of the {@link TransformVariantScope} for external build system integration.
 */
 class ExternalBuildVariantScope implements TransformVariantScope, InstantRunVariantScope {

    private final TransformGlobalScope globalScope;
    private final File outputRootFolder;
    private final ExternalBuildContext externalBuildContext;
    private final InstantRunBuildContext mInstantRunBuildContext = new InstantRunBuildContext();

    ExternalBuildVariantScope(TransformGlobalScope globalScope,
            File outputRootFolder,
            ExternalBuildContext externalBuildContext) {
        this.globalScope = globalScope;
        this.outputRootFolder = outputRootFolder;
        this.externalBuildContext = externalBuildContext;
    }

    @NonNull
    @Override
    public TransformGlobalScope getGlobalScope() {
        return globalScope;
    }

    @NonNull
    @Override
    public String getTaskName(@NonNull String prefix) {
        return prefix;
    }

    @NonNull
    @Override
    public String getTaskName(@NonNull String prefix, @NonNull String suffix) {
        return prefix + suffix;
    }

    @NonNull
    @Override
    public String getDirName() {
        return "debug";
    }

    @NonNull
    @Override
    public Collection<String> getDirectorySegments() {
        return ImmutableList.of("debug");
    }

    @NonNull
    @Override
    public String getFullVariantName() {
        return "debug";
    }

    @NonNull
    @Override
    public List<File> getBootClasspath(boolean includeOptionalLibraries) {
        return ImmutableList.of(
                new File(externalBuildContext.getBuildManifest().getAndroidSdk().getAndroidJar()));
    }

    @NonNull
    @Override
    public File getReloadDexOutputFolder() {
        return new File(outputRootFolder, "/reload-dex/debug");
    }

    @NonNull
    @Override
    public File getRestartDexOutputFolder() {
        return new File(outputRootFolder, "/reload-dex/debug");
    }

    @NonNull
    @Override
    public File getInstantRunSupportDir() {
        return new File(outputRootFolder, "/instant-run-support/debug");
    }

    @NonNull
    @Override
    public File getIncrementalVerifierDir() {
        return new File(outputRootFolder, "/incremental-verifier/debug");
    }

    @NonNull
    @Override
    public InstantRunBuildContext getInstantRunBuildContext() {
        return mInstantRunBuildContext;
    }
}
