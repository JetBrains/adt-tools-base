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

package com.android.build.gradle.managed.adaptor;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.dsl.CoreNdkOptions;
import com.android.build.gradle.internal.dsl.CoreBuildType;
import com.android.build.gradle.managed.BuildType;
import com.android.builder.internal.ClassFieldImpl;
import com.android.builder.model.ClassField;
import com.android.builder.model.SigningConfig;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * An adaptor to convert a BuildType to a CoreBuildType.
 */
public class BuildTypeAdaptor extends BaseConfigAdaptor implements CoreBuildType {
    @NonNull
    private final BuildType buildType;

    public BuildTypeAdaptor(@NonNull BuildType buildType) {
        super(buildType);
        this.buildType = buildType;
    }

    @Override
    public boolean isDebuggable() {
        return buildType.getDebuggable();
    }

    @Override
    public boolean isTestCoverageEnabled() {
        return buildType.getTestCoverageEnabled();
    }

    @Override
    public boolean isJniDebuggable() {
        return Objects.firstNonNull(buildType.getNdk().getDebuggable(), false);
    }

    @Override
    public boolean isPseudoLocalesEnabled() {
        return buildType.getPseudoLocalesEnabled();
    }

    @Override
    public boolean isRenderscriptDebuggable() {
        return buildType.getRenderscriptDebuggable();
    }

    @Override
    public int getRenderscriptOptimLevel() {
        return buildType.getRenderscriptOptimLevel();
    }

    @Nullable
    @Override
    public String getVersionNameSuffix() {
        return buildType.getVersionNameSuffix();
    }

    @Override
    public boolean isMinifyEnabled() {
        return buildType.getMinifyEnabled();
    }

    @Override
    public boolean isZipAlignEnabled() {
        return buildType.getZipAlignEnabled();
    }

    @Override
    public boolean isEmbedMicroApp() {
        return buildType.getEmbedMicroApp();
    }

    @Nullable
    @Override
    public SigningConfig getSigningConfig() {
        return buildType.getSigningConfig() == null ? null : new SigningConfigAdaptor(buildType.getSigningConfig());
    }

    @Override
    public CoreNdkOptions getNdkConfig() {
        return new NdkOptionsAdaptor(buildType.getNdk());
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
