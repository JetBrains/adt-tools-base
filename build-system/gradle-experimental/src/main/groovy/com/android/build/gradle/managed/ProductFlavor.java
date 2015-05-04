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

import com.android.annotations.Nullable;
import com.android.build.gradle.ndk.managed.NdkConfig;

import org.gradle.model.Managed;
import org.gradle.model.collection.ManagedSet;

/**
 * A Managed product flavor.
 */
@Managed
public interface ProductFlavor {

    String getName();
    void setName(String name);

    @Nullable
    String getDimension();
    void setDimension(String dimension);

    @Nullable
    ManagedSet<ClassField> getBuildConfigFields();

    @Nullable
    ManagedSet<ClassField> getResValues();

    // TODO: Add the commented fields.
    //ManagedSet<String> getProguardFiles();

    //ManagedSet<String> getConsumerProguardFiles();

    //Map<String, Object> getManifestPlaceholders();

    @Nullable
    Boolean getMultiDexEnabled();
    void setMultiDexEnabled(Boolean multiDexEnabled);

    @Nullable
    String getMultiDexKeepFile();
    void setMultiDexKeepFile(String multiDexKeepFile);

    @Nullable
    String getMultiDexKeepProguard();
    void setMultiDexKeepProguard(String multiDexKeepProguard);

    @Nullable
    String getApplicationId();
    void setApplicationId(String applicationId);

    @Nullable
    Integer getVersionCode();
    void setVersionCode(Integer versionCode);

    @Nullable
    String getVersionName();
    void setVersionName(String versionName);

    @Nullable
    ApiVersion getMinSdkVersion();

    @Nullable
    ApiVersion getTargetSdkVersion();

    @Nullable
    Integer getMaxSdkVersion();
    void setMaxSdkVersion(Integer maxSdkVersion);

    @Nullable
    Integer getRenderscriptTargetApi();
    void setRenderscriptTargetApi(Integer renderscriptTargetApi);

    @Nullable
    Boolean getRenderscriptSupportModeEnabled();
    void setRenderscriptSupportModeEnabled(Boolean renderscriptSupportModeEnabled);

    @Nullable
    Boolean getRenderscriptNdkModeEnabled();
    void setRenderscriptNdkModeEnabled(Boolean renderscriptNdkModeEnabled);

    @Nullable
    String getTestApplicationId();
    void setTestApplicationId(String testApplicationId);

    @Nullable
    String getTestInstrumentationRunner();
    void setTestInstrumentationRunner(String testInstrumentationRunner);

    @Nullable
    Boolean getTestHandleProfiling();
    void setTestHandleProfiling(Boolean testHandleProfiling);

    @Nullable
    Boolean getTestFunctionalTest();
    void setTestFunctionalTest(Boolean testFunctionalTest);

    //@NonNull
    //Collection<String> getResourceConfigurations();
    //void setResourceConfigurations(Collection<String> resourceConfigurations);

    SigningConfig getSigningConfig();
    void setSigningConfig(SigningConfig signingConfig);

    Boolean getUseJack();
    void setUseJack(Boolean useJack);

    NdkConfig getNdkConfig();
}
