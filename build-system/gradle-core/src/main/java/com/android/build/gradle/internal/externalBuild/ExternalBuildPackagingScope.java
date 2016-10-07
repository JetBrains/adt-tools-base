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
import com.android.annotations.Nullable;
import com.android.build.gradle.api.ApkOutputFile;
import com.android.build.gradle.internal.dsl.CoreSigningConfig;
import com.android.build.gradle.internal.dsl.PackagingOptions;
import com.android.build.gradle.internal.dsl.SigningConfig;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.pipeline.StreamFilter;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.scope.PackagingScope;
import com.android.build.gradle.internal.variant.SplitHandlingPolicy;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.DefaultApiVersion;
import com.android.builder.core.VariantType;
import com.android.builder.model.AaptOptions;
import com.android.builder.model.ApiVersion;
import com.google.devtools.build.lib.rules.android.apkmanifest.ExternalBuildApkManifest;

import org.gradle.api.Project;

import java.io.File;
import java.util.Collections;
import java.util.Set;

/**
 * A {@link PackagingScope} used with external build plugin.
 */
public class ExternalBuildPackagingScope implements PackagingScope {

    private final Project mProject;
    private final ExternalBuildContext mExternalBuildContext;
    private final ExternalBuildApkManifest.ApkManifest mBuildManifest;
    @NonNull
    private final ExternalBuildVariantScope mVariantScope;
    @NonNull
    private final TransformManager mTransformManager;
    private InstantRunBuildContext
            mInstantRunBuildContext;
    @Nullable
    private final SigningConfig mSigningConfig;

    public ExternalBuildPackagingScope(
            @NonNull Project project,
            @NonNull ExternalBuildContext externalBuildContext,
            @NonNull ExternalBuildVariantScope variantScope,
            @NonNull TransformManager transformManager,
            @Nullable SigningConfig signingConfig) {
        mProject = project;
        mExternalBuildContext = externalBuildContext;
        mBuildManifest = externalBuildContext.getBuildManifest();
        mVariantScope = variantScope;
        mTransformManager = transformManager;
        mSigningConfig = signingConfig;
        mInstantRunBuildContext = mVariantScope.getInstantRunBuildContext();
    }

    @NonNull
    @Override
    public AndroidBuilder getAndroidBuilder() {
        return mExternalBuildContext.getAndroidBuilder();
    }

    @NonNull
    @Override
    public File getFinalResourcesFile() {
        return new File(
                mExternalBuildContext.getExecutionRoot(),
                mBuildManifest.getResourceApk().getExecRootPath());
    }

    @NonNull
    @Override
    public String getFullVariantName() {
        return mVariantScope.getFullVariantName();
    }

    @NonNull
    @Override
    public ApiVersion getMinSdkVersion() {
        return new DefaultApiVersion(mInstantRunBuildContext.getFeatureLevel());
    }

    @NonNull
    @Override
    public InstantRunBuildContext getInstantRunBuildContext() {
        return mInstantRunBuildContext;
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
        return mTransformManager.getPipelineOutput(StreamFilter.DEX).keySet();
    }

    @NonNull
    @Override
    public Set<File> getJavaResources() {
        // TODO: do we want to support java resources?
        return Collections.emptySet();
    }

    @NonNull
    @Override
    public Set<File> getJniFolders() {
        return Collections.emptySet();
    }

    @NonNull
    @Override
    public SplitHandlingPolicy getSplitHandlingPolicy() {
        return SplitHandlingPolicy.RELEASE_21_AND_AFTER_POLICY;
    }

    @NonNull
    @Override
    public Set<String> getAbiFilters() {
        return Collections.emptySet();
    }

    @NonNull
    @Override
    public ApkOutputFile getMainOutputFile() {
        return mVariantScope.getMainOutputFile();
    }

    @Nullable
    @Override
    public Set<String> getSupportedAbis() {
        return null;
    }

    @Override
    public boolean isDebuggable() {
        return true;
    }

    @Override
    public boolean isJniDebuggable() {
        return false;
    }

    @Nullable
    @Override
    public CoreSigningConfig getSigningConfig() {
        return mSigningConfig;
    }

    @NonNull
    @Override
    public PackagingOptions getPackagingOptions() {
        return new PackagingOptions();
    }

    @NonNull
    @Override
    public String getTaskName(@NonNull String name) {
        return name + getFullVariantName();
    }

    @NonNull
    @Override
    public Project getProject() {
        return mProject;
    }

    @NonNull
    @Override
    public File getOutputApk() {
        return getMainOutputFile().getOutputFile();
    }

    @NonNull
    @Override
    public File getIntermediateApk() {
        return mVariantScope.getIntermediateApk();
    }

    @NonNull
    @Override
    public File getAssetsDir() {
        return mVariantScope.getAssetsDir();
    }

    @NonNull
    @Override
    public File getInstantRunSplitApkOutputFolder() {
        return mVariantScope.getInstantRunSplitApkOutputFolder();
    }

    @NonNull
    @Override
    public String getApplicationId() {
        return mVariantScope.getApplicationId();
    }

    @Override
    public int getVersionCode() {
        return mVariantScope.getVersionCode();
    }

    @Nullable
    @Override
    public String getVersionName() {
        return mVariantScope.getVersionName();
    }

    @NonNull
    @Override
    public AaptOptions getAaptOptions() {
        return mVariantScope.getAaptOptions();
    }

    @NonNull
    @Override
    public VariantType getVariantType() {
        return VariantType.DEFAULT;
    }

    @NonNull
    @Override
    public File getManifestFile() {
        return new File(
                mExternalBuildContext.getExecutionRoot(),
                mBuildManifest.getAndroidManifest().getExecRootPath());
    }
}
