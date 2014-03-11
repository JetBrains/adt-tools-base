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
import com.android.builder.model.ClassField;
import com.android.builder.model.NdkConfig;
import com.android.builder.model.ProductFlavor;
import com.google.common.collect.Sets;

import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Implementation of ProductFlavor that is serializable. Objects used in the DSL cannot be
 * serialized.
 **/
class ProductFlavorImpl implements ProductFlavor, Serializable {
    private static final long serialVersionUID = 1L;

    private String name = null;
    private int mMinSdkVersion = -1;
    private int mTargetSdkVersion = -1;
    private int mRenderscriptTargetApi = -1;
    private boolean mRenderscriptSupportMode = false;
    private boolean mRenderscriptNdkMode = false;
    private int mVersionCode = -1;
    private String mVersionName = null;
    private String mPackageName = null;
    private String mTestPackageName = null;
    private String mTestInstrumentationRunner = null;
    private Boolean mTestHandleProfiling = null;
    private Boolean mTestFunctionalTest = null;
    private Set<String> mResourceConfigurations = null;

    @NonNull
    static ProductFlavorImpl cloneFlavor(ProductFlavor productFlavor) {
        ProductFlavorImpl clonedFlavor = new ProductFlavorImpl();
        clonedFlavor.name = productFlavor.getName();

        clonedFlavor.mMinSdkVersion = productFlavor.getMinSdkVersion();
        clonedFlavor.mTargetSdkVersion = productFlavor.getTargetSdkVersion();
        clonedFlavor.mRenderscriptTargetApi = productFlavor.getRenderscriptTargetApi();
        clonedFlavor.mRenderscriptSupportMode = productFlavor.getRenderscriptSupportMode();
        clonedFlavor.mRenderscriptNdkMode = productFlavor.getRenderscriptNdkMode();

        clonedFlavor.mVersionCode = productFlavor.getVersionCode();
        clonedFlavor.mVersionName = productFlavor.getVersionName();

        clonedFlavor.mPackageName = productFlavor.getPackageName();

        clonedFlavor.mTestPackageName = productFlavor.getTestPackageName();
        clonedFlavor.mTestInstrumentationRunner = productFlavor.getTestInstrumentationRunner();
        clonedFlavor.mTestHandleProfiling = productFlavor.getTestHandleProfiling();
        clonedFlavor.mTestFunctionalTest = productFlavor.getTestFunctionalTest();

        clonedFlavor.mResourceConfigurations = Sets.newHashSet(
                productFlavor.getResourceConfigurations());

        return clonedFlavor;
    }

    private ProductFlavorImpl() {
    }

    @NonNull
    @Override
    public String getName() {
        return name;
    }

    @Nullable
    @Override
    public String getPackageName() {
        return mPackageName;
    }

    @Override
    public int getVersionCode() {
        return mVersionCode;
    }

    @Nullable
    @Override
    public String getVersionName() {
        return mVersionName;
    }

    @Override
    public int getMinSdkVersion() {
        return mMinSdkVersion;
    }

    @Override
    public int getTargetSdkVersion() {
        return mTargetSdkVersion;
    }

    @Override
    public int getRenderscriptTargetApi() {
        return mRenderscriptTargetApi;
    }

    @Override
    public boolean getRenderscriptSupportMode() {
        return mRenderscriptSupportMode;
    }

    @Override
    public boolean getRenderscriptNdkMode() {
        return mRenderscriptNdkMode;
    }

    @Nullable
    @Override
    public String getTestPackageName() {
        return mTestPackageName;
    }

    @Nullable
    @Override
    public String getTestInstrumentationRunner() {
        return mTestInstrumentationRunner;
    }

    @Nullable
    @Override
    public Boolean getTestHandleProfiling() {
        return mTestHandleProfiling;
    }

    @Nullable
    @Override
    public Boolean getTestFunctionalTest() {
        return mTestFunctionalTest;
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

    @NonNull
    @Override
    public Collection<String> getResourceConfigurations() {
        return mResourceConfigurations;
    }

    @Override
    public String toString() {
        return "ProductFlavorImpl{" +
                "name='" + name + '\'' +
                ", mMinSdkVersion=" + mMinSdkVersion +
                ", mTargetSdkVersion=" + mTargetSdkVersion +
                ", mRenderscriptTargetApi=" + mRenderscriptTargetApi +
                ", mRenderscriptSupportMode=" + mRenderscriptSupportMode +
                ", mRenderscriptNdkMode=" + mRenderscriptNdkMode +
                ", mVersionCode=" + mVersionCode +
                ", mVersionName='" + mVersionName + '\'' +
                ", mPackageName='" + mPackageName + '\'' +
                ", mTestPackageName='" + mTestPackageName + '\'' +
                ", mTestInstrumentationRunner='" + mTestInstrumentationRunner + '\'' +
                ", mTestHandleProfiling='" + mTestHandleProfiling + '\'' +
                ", mTestFunctionalTest='" + mTestFunctionalTest + '\'' +
                ", mResourceConfigurations='" + mResourceConfigurations + '\'' +
                '}';
    }
}
