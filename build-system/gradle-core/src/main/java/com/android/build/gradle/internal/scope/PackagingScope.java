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
import com.android.build.gradle.internal.variant.SplitHandlingPolicy;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.VariantType;
import com.android.builder.model.AaptOptions;
import com.android.builder.model.ApiVersion;

import org.gradle.api.Project;

import java.io.File;
import java.util.Set;

/**
 * Data needed by the packaging tasks.
 */
public interface PackagingScope {

    /**
     * The {@link AndroidBuilder} to use.
     */
    @NonNull
    AndroidBuilder getAndroidBuilder();

    /**
     * Location of the *.ap_ file with processed resources.
     */
    @NonNull
    File getFinalResourcesFile();

    /**
     * Full name of the variant.
     */
    @NonNull
    String getFullVariantName();

    /**
     * Min SDK version of the artifact to create.
     */
    @NonNull
    ApiVersion getMinSdkVersion();

    @NonNull
    InstantRunBuildContext getInstantRunBuildContext();

    /**
     * Directory with instant run support files.
     */
    @NonNull
    File getInstantRunSupportDir();

    /**
     * Returns the directory for storing incremental files.
     */
    @NonNull
    File getIncrementalDir(@NonNull String name);

    @NonNull
    Set<File> getDexFolders();

    @NonNull
    Set<File> getJavaResources();

    @NonNull
    File getAssetsDir();

    @NonNull
    Set<File> getJniFolders();

    @NonNull
    SplitHandlingPolicy getSplitHandlingPolicy();

    @NonNull
    Set<String> getAbiFilters();

    @NonNull
    ApkOutputFile getMainOutputFile();

    @Nullable
    Set<String> getSupportedAbis();

    boolean isDebuggable();

    boolean isJniDebuggable();

    @Nullable
    CoreSigningConfig getSigningConfig();

    @NonNull
    PackagingOptions getPackagingOptions();

    @NonNull
    String getTaskName(@NonNull String name);

    @NonNull
    Project getProject();

    @NonNull
    File getOutputApk();

    @NonNull
    File getIntermediateApk();

    @NonNull
    File getInstantRunSplitApkOutputFolder();

    @NonNull
    String getApplicationId();

    int getVersionCode();

    @Nullable
    String getVersionName();

    @NonNull
    AaptOptions getAaptOptions();

    @NonNull
    VariantType getVariantType();

    @NonNull
    File getManifestFile();
}
