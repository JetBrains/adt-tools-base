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
import com.android.build.OutputFile;
import com.android.build.gradle.api.ApkOutputFile;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.scope.GenericVariantScopeImpl;
import com.android.build.gradle.internal.scope.InstantRunVariantScope;
import com.android.build.gradle.internal.scope.TransformGlobalScope;
import com.android.build.gradle.internal.scope.TransformVariantScope;
import com.android.builder.core.ManifestAttributeSupplier;
import com.android.builder.model.AaptOptions;
import com.android.sdklib.IAndroidTarget;
import com.android.utils.FileUtils;
import com.android.utils.StringHelper;
import com.google.common.collect.ImmutableList;

import java.io.File;
import java.util.Collection;
import java.util.Collections;

/**
 * Implementation of the {@link TransformVariantScope} for external build system integration.
 */
 class ExternalBuildVariantScope extends GenericVariantScopeImpl
        implements TransformVariantScope, InstantRunVariantScope {

    private final TransformGlobalScope globalScope;
    private final File outputRootFolder;
    private final ExternalBuildContext externalBuildContext;
    private final InstantRunBuildContext mInstantRunBuildContext = new InstantRunBuildContext();
    private final AaptOptions aaptOptions;
    private final ManifestAttributeSupplier manifestAttributeSupplier;

    ExternalBuildVariantScope(
            @NonNull TransformGlobalScope globalScope,
            @NonNull File outputRootFolder,
            @NonNull ExternalBuildContext externalBuildContext,
            @NonNull AaptOptions aaptOptions,
            @NonNull ManifestAttributeSupplier manifestAttributeSupplier) {
        this.globalScope = globalScope;
        this.outputRootFolder = outputRootFolder;
        this.externalBuildContext = externalBuildContext;
        this.aaptOptions = aaptOptions;
        this.manifestAttributeSupplier = manifestAttributeSupplier;
    }

    @NonNull
    @Override
    public TransformGlobalScope getGlobalScope() {
        return globalScope;
    }

    @NonNull
    @Override
    public String getTaskName(@NonNull String prefix) {
        return prefix + StringHelper.capitalize(getFullVariantName());
    }

    @NonNull
    @Override
    public String getTaskName(@NonNull String prefix, @NonNull String suffix) {
        return prefix + StringHelper.capitalize(getFullVariantName()) + suffix;
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
    public ImmutableList<File> getInstantRunBootClasspath() {
        IAndroidTarget target = externalBuildContext.getAndroidBuilder().getTarget();
        ImmutableList.Builder<File> fileListBuilder = ImmutableList.builder();
        if (target != null) {
            for (String classpathElement : target.getBootClasspath()) {
                fileListBuilder.add(new File(classpathElement));
            }
        }
        return fileListBuilder.build();
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

    @Override
    @NonNull
    public File getIncrementalApplicationSupportDir() {
        return new File(outputRootFolder, "/incremental-classes/app-support");
    }

    @NonNull
    @Override
    public File getInstantRunResourcesFile() {
        return new File(outputRootFolder, "/instant-run-resources/debug.ir.ap_");
    }

    @NonNull
    @Override
    public File getIncrementalRuntimeSupportJar() {
        return new File(outputRootFolder, "/incremental-runtime-classes/instant-run.jar");
    }

    @NonNull
    @Override
    public File getInstantRunPastIterationsFolder() {
        return FileUtils.join(outputRootFolder, "intermediates", "builds", getFullVariantName());
    }

    @NonNull
    @Override
    public File getInstantRunSliceSupportDir() {
        return FileUtils.join(
                outputRootFolder, "intermediates", "instant-run-slices", getFullVariantName());
    }

    @NonNull
    @Override
    public TransformVariantScope getTransformVariantScope() {
        return this;
    }

    @NonNull
    public ApkOutputFile getMainOutputFile() {
        return new ApkOutputFile(
                OutputFile.OutputType.MAIN,
                Collections.emptySet(),
                () -> new File(outputRootFolder, "/outputs/apk/debug.apk"));
    }

    public File getIntermediateApk() {
        return new File(outputRootFolder, "/outputs/apk/debug-unaligned.apk");
    }

    public File getPreDexOutputDir() {
        return FileUtils.join(outputRootFolder, "intermediates", "pre-dexed");
    }

    public File getIncrementalDir(String name) {
        return FileUtils.join(outputRootFolder, "incremental", name);
    }

    public File getInstantRunSplitApkOutputFolder() {
        return FileUtils.join(outputRootFolder, "incremental", "splits");
    }

    public AaptOptions getAaptOptions() {
        return aaptOptions;
    }

    public String getApplicationId() {
        return manifestAttributeSupplier.getPackage();
    }

    public int getVersionCode() {
        return manifestAttributeSupplier.getVersionCode();
    }

    public String getVersionName() {
        return manifestAttributeSupplier.getVersionName();
    }

    public File getAssetsDir() {
        return new File(outputRootFolder, "assets");
    }
}
