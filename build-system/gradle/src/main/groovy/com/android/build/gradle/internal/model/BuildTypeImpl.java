/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.build.gradle.internal.model;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.BuildType;
import com.android.builder.model.ClassField;
import com.android.builder.model.NdkConfig;

import java.io.File;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Implementation of BuildType that is serializable. Objects used in the DSL cannot be
 * serialized.
 */
class BuildTypeImpl implements BuildType, Serializable {
    private static final long serialVersionUID = 1L;

    private String name;
    private boolean debuggable;
    private boolean jniDebugBuild;
    private boolean renderscriptDebugBuild;
    private int renderscriptOptimLevel;
    private String packageNameSuffix;
    private String versionNameSuffix;
    private boolean runProguard;
    private boolean zipAlign;

    @NonNull
    static BuildTypeImpl cloneBuildType(BuildType buildType) {
        BuildTypeImpl clonedBuildType = new BuildTypeImpl();
        clonedBuildType.name = buildType.getName();
        clonedBuildType.debuggable = buildType.isDebuggable();
        clonedBuildType.jniDebugBuild = buildType.isJniDebugBuild();
        clonedBuildType.renderscriptDebugBuild = buildType.isRenderscriptDebugBuild();
        clonedBuildType.renderscriptOptimLevel = buildType.getRenderscriptOptimLevel();
        clonedBuildType.packageNameSuffix = buildType.getPackageNameSuffix();
        clonedBuildType.versionNameSuffix = buildType.getVersionNameSuffix();
        clonedBuildType.runProguard = buildType.isRunProguard();
        clonedBuildType.zipAlign = buildType.isZipAlign();

        return clonedBuildType;
    }

    private BuildTypeImpl() {
    }

    @NonNull
    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean isDebuggable() {
        return debuggable;
    }

    @Override
    public boolean isJniDebugBuild() {
        return jniDebugBuild;
    }

    @Override
    public boolean isRenderscriptDebugBuild() {
        return renderscriptDebugBuild;
    }

    @Override
    public int getRenderscriptOptimLevel() {
        return renderscriptOptimLevel;
    }

    @Nullable
    @Override
    public String getPackageNameSuffix() {
        return packageNameSuffix;
    }

    @Nullable
    @Override
    public String getVersionNameSuffix() {
        return versionNameSuffix;
    }

    @Override
    public boolean isRunProguard() {
        return runProguard;
    }

    @Override
    public boolean isZipAlign() {
        return zipAlign;
    }

    @NonNull
    @Override
    public Map<String, ClassField> getBuildConfigFields() {
        return Collections.emptyMap();
    }

    @NonNull
    @Override
    public List<File> getProguardFiles() {
        return Collections.emptyList();
    }

    @NonNull
    @Override
    public List<File> getConsumerProguardFiles() {
        return Collections.emptyList();
    }

    @Override
    @Nullable
    public NdkConfig getNdkConfig() {
        return null;
    }
}
