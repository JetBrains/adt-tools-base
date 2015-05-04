/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.build.gradle.managed;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.core.NdkConfig;
import com.android.build.gradle.internal.dsl.CoreBuildType;
import com.android.builder.model.ClassField;
import com.android.builder.model.SigningConfig;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import java.io.File;
import java.util.Collection;
import java.util.Map;

/**
 * An adaptor to convert a ManagedBuildType to a BuildType.
 */
public class BuildTypeAdaptor implements CoreBuildType {
    @NonNull
    private final BuildType buildType;

    public BuildTypeAdaptor(@NonNull BuildType buildType) {
        this.buildType = buildType;
    }

    @NonNull
    @Override
    public String getName() {
        return buildType.getName();
    }

    @NonNull
    @Override
    public Map<String, ClassField> getBuildConfigFields() {
        // TODO: To be implemented
        return Maps.newHashMap();
    }

    @NonNull
    @Override
    public Map<String, ClassField> getResValues() {
        // TODO: To be implemented
        return Maps.newHashMap();
    }

    @NonNull
    @Override
    public Collection<File> getTestProguardFiles() {
        // TODO: To be implemented
        return Lists.newArrayList();
    }

    @NonNull
    @Override
    public Collection<File> getProguardFiles() {
        // TODO: To be implemented
        return Lists.newArrayList();
    }

    @NonNull
    @Override
    public Collection<File> getConsumerProguardFiles() {
        // TODO: To be implemented
        return Lists.newArrayList();
    }

    @NonNull
    @Override
    public Map<String, Object> getManifestPlaceholders() {
        // TODO: To be implemented
        return Maps.newHashMap();
    }

    @Nullable
    @Override
    public Boolean getMultiDexEnabled() {
        return null;
    }

    @Nullable
    @Override
    public File getMultiDexKeepFile() {
        return null;
    }

    @Nullable
    @Override
    public File getMultiDexKeepProguard() {
        return null;
    }

    @Override
    public boolean isDebuggable() {
        return buildType.getIsDebuggable();
    }

    @Override
    public boolean isTestCoverageEnabled() {
        return buildType.getIsTestCoverageEnabled();
    }

    @Override
    public boolean isJniDebuggable() {
        return buildType.getIsJniDebuggable();
    }

    @Override
    public boolean isPseudoLocalesEnabled() {
        return buildType.getIsPseudoLocalesEnabled();
    }

    @Override
    public boolean isRenderscriptDebuggable() {
        return buildType.getIsRenderscriptDebuggable();
    }

    @Override
    public int getRenderscriptOptimLevel() {
        return buildType.getRenderscriptOptimLevel();
    }

    @Nullable
    @Override
    public String getApplicationIdSuffix() {
        return buildType.getApplicationIdSuffix();
    }

    @Nullable
    @Override
    public String getVersionNameSuffix() {
        return buildType.getVersionNameSuffix();
    }

    @Override
    public boolean isMinifyEnabled() {
        return buildType.getIsMinifyEnabled();
    }

    @Override
    public boolean isZipAlignEnabled() {
        return buildType.getIsZipAlignEnabled();
    }

    @Override
    public boolean isEmbedMicroApp() {
        return buildType.getIsEmbedMicroApp();
    }

    @Nullable
    @Override
    public SigningConfig getSigningConfig() {
        return buildType.getSigningConfig() == null ? null : new SigningConfigAdaptor(buildType.getSigningConfig());
    }

    @Override
    public NdkConfig getNdkConfig() {
        return null;
    }

    @Override
    public Boolean getUseJack() {
        return buildType.getUseJack();
    }

    @Override
    public boolean isShrinkResources() {
        return buildType.getShrinkResources();
    }
}
