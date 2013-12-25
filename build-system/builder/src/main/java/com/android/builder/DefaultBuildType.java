/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.builder;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.internal.BaseConfigImpl;
import com.android.builder.model.BuildType;
import com.android.builder.model.NdkConfig;
import com.android.builder.model.SigningConfig;
import com.google.common.base.Objects;

public class DefaultBuildType extends BaseConfigImpl implements BuildType {
    private static final long serialVersionUID = 1L;

    private final String mName;
    private boolean mDebuggable = false;
    private boolean mJniDebugBuild = false;
    private boolean mRenderscriptDebugBuild = false;
    private int mRenderscriptOptimLevel = 3;
    private String mPackageNameSuffix = null;
    private String mVersionNameSuffix = null;
    private boolean mRunProguard = false;
    private SigningConfig mSigningConfig = null;

    private boolean mZipAlign = true;

    public DefaultBuildType(@NonNull String name) {
        mName = name;
    }

    public DefaultBuildType initWith(DefaultBuildType that) {
        _initWith(that);

        setDebuggable(that.isDebuggable());
        setJniDebugBuild(that.isJniDebugBuild());
        setRenderscriptDebugBuild(that.isRenderscriptDebugBuild());
        setRenderscriptOptimLevel(that.getRenderscriptOptimLevel());
        setPackageNameSuffix(that.getPackageNameSuffix());
        setVersionNameSuffix(that.getVersionNameSuffix());
        setRunProguard(that.isRunProguard());
        setZipAlign(that.isZipAlign());
        setSigningConfig(that.getSigningConfig());

        return this;
    }

    @Override
    @NonNull
    public String getName() {
        return mName;
    }

    @NonNull
    public BuildType setDebuggable(boolean debuggable) {
        mDebuggable = debuggable;
        return this;
    }

    @Override
    public boolean isDebuggable() {
        return mDebuggable;
    }

    @NonNull
    public BuildType setJniDebugBuild(boolean jniDebugBuild) {
        mJniDebugBuild = jniDebugBuild;
        return this;
    }

    @Override
    public boolean isJniDebugBuild() {
        return mJniDebugBuild;
    }

    @Override
    public boolean isRenderscriptDebugBuild() {
        return mRenderscriptDebugBuild;
    }

    public void setRenderscriptDebugBuild(boolean renderscriptDebugBuild) {
        mRenderscriptDebugBuild = renderscriptDebugBuild;
    }

    @Override
    public int getRenderscriptOptimLevel() {
        return mRenderscriptOptimLevel;
    }

    public void setRenderscriptOptimLevel(int renderscriptOptimLevel) {
        mRenderscriptOptimLevel = renderscriptOptimLevel;
    }

    @NonNull
    public BuildType setPackageNameSuffix(@Nullable String packageNameSuffix) {
        mPackageNameSuffix = packageNameSuffix;
        return this;
    }

    @Override
    @Nullable
    public String getPackageNameSuffix() {
        return mPackageNameSuffix;
    }

    @NonNull
    public BuildType setVersionNameSuffix(@Nullable String versionNameSuffix) {
        mVersionNameSuffix = versionNameSuffix;
        return this;
    }

    @Override
    @Nullable
    public String getVersionNameSuffix() {
        return mVersionNameSuffix;
    }

    @NonNull
    public BuildType setRunProguard(boolean runProguard) {
        mRunProguard = runProguard;
        return this;
    }

    @Override
    public boolean isRunProguard() {
        return mRunProguard;
    }

    @NonNull
    public BuildType setZipAlign(boolean zipAlign) {
        mZipAlign = zipAlign;
        return this;
    }

    @Override
    public boolean isZipAlign() {
        return mZipAlign;
    }

    @NonNull
    public BuildType setSigningConfig(@Nullable SigningConfig signingConfig) {
        mSigningConfig = signingConfig;
        return this;
    }

    @Nullable
    public SigningConfig getSigningConfig() {
        return mSigningConfig;
    }

    @Override
    @Nullable
    public NdkConfig getNdkConfig() {
        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        DefaultBuildType buildType = (DefaultBuildType) o;

        if (!mName.equals(buildType.mName)) return false;
        if (mDebuggable != buildType.mDebuggable) return false;
        if (mJniDebugBuild != buildType.mJniDebugBuild) return false;
        if (mRenderscriptDebugBuild != buildType.mRenderscriptDebugBuild) return false;
        if (mRenderscriptOptimLevel != buildType.mRenderscriptOptimLevel) return false;
        if (mRunProguard != buildType.mRunProguard) return false;
        if (mZipAlign != buildType.mZipAlign) return false;
        if (mPackageNameSuffix != null ?
                !mPackageNameSuffix.equals(buildType.mPackageNameSuffix) :
                buildType.mPackageNameSuffix != null)
            return false;
        if (mVersionNameSuffix != null ?
                !mVersionNameSuffix.equals(buildType.mVersionNameSuffix) :
                buildType.mVersionNameSuffix != null)
            return false;
        if (mSigningConfig != null ?
                !mSigningConfig.equals(buildType.mSigningConfig) :
                buildType.mSigningConfig != null)
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (mName.hashCode());
        result = 31 * result + (mDebuggable ? 1 : 0);
        result = 31 * result + (mJniDebugBuild ? 1 : 0);
        result = 31 * result + (mRenderscriptDebugBuild ? 1 : 0);
        result = 31 * result + mRenderscriptOptimLevel;
        result = 31 * result + (mPackageNameSuffix != null ? mPackageNameSuffix.hashCode() : 0);
        result = 31 * result + (mVersionNameSuffix != null ? mVersionNameSuffix.hashCode() : 0);
        result = 31 * result + (mRunProguard ? 1 : 0);
        result = 31 * result + (mZipAlign ? 1 : 0);
        result = 31 * result + (mSigningConfig != null ? mSigningConfig.hashCode() : 0);
        return result;
    }

    @Override
    @NonNull
    public String toString() {
        return Objects.toStringHelper(this)
                .add("name", mName)
                .add("debuggable", mDebuggable)
                .add("jniDebugBuild", mJniDebugBuild)
                .add("renderscriptDebugBuild", mRenderscriptDebugBuild)
                .add("renderscriptOptimLevel", mRenderscriptOptimLevel)
                .add("packageNameSuffix", mPackageNameSuffix)
                .add("versionNameSuffix", mVersionNameSuffix)
                .add("runProguard", mRunProguard)
                .add("zipAlign", mZipAlign)
                .add("signingConfig", mSigningConfig)
                .toString();
    }
}
