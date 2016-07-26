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
import com.android.annotations.Nullable;
import com.android.build.gradle.api.ApkOutputFile;
import com.android.build.gradle.internal.dsl.CoreSigningConfig;
import com.android.build.gradle.internal.dsl.PackagingOptions;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.pipeline.StreamFilter;
import com.android.build.gradle.internal.variant.SplitHandlingPolicy;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.VariantType;
import com.android.builder.model.AaptOptions;
import com.android.builder.model.ApiVersion;

import org.gradle.api.Project;

import java.io.File;
import java.util.Set;

/**
 * Implementation of {@link PackagingScope} which delegates to *Scope objects available during
 * normal Gradle builds.
 */
public class DefaultGradlePackagingScope implements PackagingScope {

    private final VariantOutputScope mVariantOutputScope;
    private final VariantScope mVariantScope;
    private final GlobalScope mGlobalScope;

    public DefaultGradlePackagingScope(@NonNull VariantOutputScope variantOutputScope) {
        mVariantOutputScope = variantOutputScope;
        mVariantScope = mVariantOutputScope.getVariantScope();
        mGlobalScope = mVariantScope.getGlobalScope();
    }

    @NonNull
    @Override
    public AndroidBuilder getAndroidBuilder() {
        return mGlobalScope.getAndroidBuilder();
    }

    @NonNull
    @Override
    public File getFinalResourcesFile() {
        return mVariantOutputScope.getFinalResourcesFile();
    }

    @NonNull
    @Override
    public String getFullVariantName() {
        return mVariantScope.getFullVariantName();
    }

    @NonNull
    @Override
    public ApiVersion getMinSdkVersion() {
        return mVariantScope.getMinSdkVersion();
    }

    @NonNull
    @Override
    public InstantRunBuildContext getInstantRunBuildContext() {
        return mVariantScope.getInstantRunBuildContext();
    }

    @NonNull
    @Override
    public File getInstantRunSupportDir() {
        return mVariantScope.getInstantRunSupportDir();
    }

    @NonNull
    @Override
    public File getIncrementalDir(@NonNull String name) {
        return mVariantScope.getIncrementalDir(name);
    }

    @NonNull
    @Override
    public Set<File> getDexFolders() {
        return mVariantScope.getTransformManager()
                .getPipelineOutput(StreamFilter.DEX)
                .keySet();
    }

    @NonNull
    @Override
    public Set<File> getJavaResources() {
        return mVariantScope.getTransformManager()
                .getPipelineOutput(StreamFilter.RESOURCES)
                .keySet();
    }

    @NonNull
    @Override
    public Set<File> getJniFolders() {
        return mVariantScope.getTransformManager()
                .getPipelineOutput(StreamFilter.NATIVE_LIBS)
                .keySet();
    }

    @NonNull
    @Override
    public SplitHandlingPolicy getSplitHandlingPolicy() {
        return mVariantScope.getVariantData().getSplitHandlingPolicy();
    }

    @NonNull
    @Override
    public Set<String> getAbiFilters() {
        return mGlobalScope.getExtension().getSplits().getAbiFilters();
    }

    @NonNull
    @Override
    public ApkOutputFile getMainOutputFile() {
        return mVariantOutputScope.getMainOutputFile();
    }

    @Nullable
    @Override
    public Set<String> getSupportedAbis() {
        return mVariantScope.getVariantConfiguration().getSupportedAbis();
    }

    @Override
    public boolean isDebuggable() {
        return mVariantScope.getVariantConfiguration().getBuildType().isDebuggable();
    }

    @Override
    public boolean isJniDebuggable() {
        return mVariantScope.getVariantConfiguration().getBuildType().isJniDebuggable();
    }

    @Nullable
    @Override
    public CoreSigningConfig getSigningConfig() {
        return mVariantScope.getVariantConfiguration().getSigningConfig();
    }

    @NonNull
    @Override
    public PackagingOptions getPackagingOptions() {
        return mGlobalScope.getExtension().getPackagingOptions();
    }

    @NonNull
    @Override
    public String getTaskName(@NonNull String name) {
        return mVariantOutputScope.getTaskName(name);
    }

    @NonNull
    @Override
    public Project getProject() {
        return mGlobalScope.getProject();
    }

    @NonNull
    @Override
    public File getOutputApk() {
        return mVariantOutputScope.getFinalApk();
    }

    @NonNull
    @Override
    public File getIntermediateApk() {
        return mVariantOutputScope.getIntermediateApk();
    }

    @NonNull
    @Override
    public File getAssetsDir() {
        return mVariantScope.getMergeAssetsOutputDir();
    }

    @NonNull
    @Override
    public File getInstantRunSplitApkOutputFolder() {
        return mVariantScope.getInstantRunSplitApkOutputFolder();
    }

    @NonNull
    @Override
    public String getApplicationId() {
        return mVariantScope.getVariantConfiguration().getApplicationId();
    }

    @Override
    public int getVersionCode() {
        return mVariantScope.getVariantConfiguration().getVersionCode();
    }

    @Nullable
    @Override
    public String getVersionName() {
        return mVariantScope.getVariantConfiguration().getVersionName();
    }

    @NonNull
    @Override
    public AaptOptions getAaptOptions() {
        return mGlobalScope.getExtension().getAaptOptions();
    }

    @NonNull
    @Override
    public VariantType getVariantType() {
        return mVariantScope.getVariantConfiguration().getType();
    }

    @NonNull
    @Override
    public File getManifestFile() {
        return mVariantOutputScope.getManifestOutputFile();
    }
}
