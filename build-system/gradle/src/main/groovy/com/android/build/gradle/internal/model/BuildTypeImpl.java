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
import com.android.builder.model.SigningConfig;

import java.io.Serializable;

/**
 * Implementation of BuildType that is serializable. Objects used in the DSL cannot be
 * serialized.
 */
class BuildTypeImpl extends BaseConfigImpl implements BuildType, Serializable {
    private static final long serialVersionUID = 1L;

    private String name;
    private boolean debuggable;
    private boolean testCoverageEnabled;
    private boolean jniDebugBuild;
    private boolean renderscriptDebugBuild;
    private int renderscriptOptimLevel;
    private String applicationIdSuffix;
    private String versionNameSuffix;
    private boolean minifyEnabled;
    private boolean zipAlign;
    private boolean embedMicroApp;

    @NonNull
    static BuildTypeImpl cloneBuildType(@NonNull BuildType buildType) {
        BuildTypeImpl clonedBuildType = new BuildTypeImpl(buildType);

        clonedBuildType.name = buildType.getName();
        clonedBuildType.debuggable = buildType.isDebuggable();
        clonedBuildType.testCoverageEnabled = buildType.isTestCoverageEnabled();
        clonedBuildType.jniDebugBuild = buildType.isJniDebugBuild();
        clonedBuildType.renderscriptDebugBuild = buildType.isRenderscriptDebugBuild();
        clonedBuildType.renderscriptOptimLevel = buildType.getRenderscriptOptimLevel();
        clonedBuildType.applicationIdSuffix = buildType.getApplicationIdSuffix();
        clonedBuildType.versionNameSuffix = buildType.getVersionNameSuffix();
        clonedBuildType.minifyEnabled = buildType.isMinifyEnabled();
        clonedBuildType.zipAlign = buildType.isZipAlign();
        clonedBuildType.embedMicroApp = buildType.isEmbedMicroApp();

        return clonedBuildType;
    }

    private BuildTypeImpl(@NonNull BuildType buildType) {
        super(buildType);
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
    public boolean isTestCoverageEnabled() {
        return testCoverageEnabled;
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
    public String getApplicationIdSuffix() {
        return applicationIdSuffix;
    }

    @Nullable
    @Override
    public String getVersionNameSuffix() {
        return versionNameSuffix;
    }

    @Override
    public boolean isMinifyEnabled() {
        return minifyEnabled;
    }

    @Override
    public boolean isZipAlign() {
        return zipAlign;
    }

    @Override
    public boolean isEmbedMicroApp() {
        return embedMicroApp;
    }

    @Nullable
    @Override
    public SigningConfig getSigningConfig() {
        return null;
    }

    @Override
    public String toString() {
        return "BuildTypeImpl{" +
                "name='" + name + '\'' +
                ", debuggable=" + debuggable +
                ", testCoverageEnabled=" + testCoverageEnabled +
                ", jniDebugBuild=" + jniDebugBuild +
                ", renderscriptDebugBuild=" + renderscriptDebugBuild +
                ", renderscriptOptimLevel=" + renderscriptOptimLevel +
                ", applicationIdSuffix='" + applicationIdSuffix + '\'' +
                ", versionNameSuffix='" + versionNameSuffix + '\'' +
                ", minifyEnabled=" + minifyEnabled +
                ", zipAlign=" + zipAlign +
                ", embedMicroApp=" + embedMicroApp +
                "} " + super.toString();
    }
}
