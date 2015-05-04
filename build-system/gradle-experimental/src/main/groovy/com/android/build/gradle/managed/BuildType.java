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

import org.gradle.model.Managed;
import org.gradle.model.collection.ManagedSet;

/**
 * A Managed build type.
 */
@Managed
public interface BuildType {

    String getName();
    void setName(String name);

    ManagedSet<ClassField> getBuildConfigFields();

    ManagedSet<ClassField> getResValues();

    // TODO: Add the commented fields.
    //ManagedSet<String> getProguardFiles();

    //ManagedSet<String> getConsumerProguardFiles();

    //Map<String, Object> getManifestPlaceholders();

    Boolean getMultiDexEnabled();
    void setMultiDexEnabled(Boolean multiDexEnabled);

    String getMultiDexKeepFile();
    void setMultiDexKeepFile(String multiDexKeepFile);

    String getMultiDexKeepProguard();
    void setMultiDexKeepProguard(String multiDexKeepProguard);

    Boolean getIsDebuggable();
    void setIsDebuggable(Boolean isDebuggable);

    Boolean getIsTestCoverageEnabled();
    void setIsTestCoverageEnabled(Boolean isTestCoverageEnabled);

    Boolean getIsPseudoLocalesEnabled();
    void setIsPseudoLocalesEnabled(Boolean isPseudoLocalesEnabled);

    Boolean getIsJniDebuggable();
    void setIsJniDebuggable(Boolean isJniDebuggable);

    Boolean getIsRenderscriptDebuggable();
    void setIsRenderscriptDebuggable(Boolean isRenderscriptDebuggable);

    Integer getRenderscriptOptimLevel();
    void setRenderscriptOptimLevel(Integer renderscriptOptimLevel);

    String getApplicationIdSuffix();
    void setApplicationIdSuffix(String applicationIdSuffix);

    String getVersionNameSuffix();
    void setVersionNameSuffix(String versionNameSuffix);

    Boolean getIsMinifyEnabled();
    void setIsMinifyEnabled(Boolean isMinifyEnabled);

    Boolean getIsZipAlignEnabled();
    void setIsZipAlignEnabled(Boolean isZipAlignEnabled);

    Boolean getIsEmbedMicroApp();
    void setIsEmbedMicroApp(Boolean isEmbedMicroApp);

    SigningConfig getSigningConfig();
    void setSigningConfig(SigningConfig signingConfig);

    Boolean getUseJack();
    void setUseJack(Boolean useJack);

    Boolean getShrinkResources();
    void setShrinkResources(Boolean shrinkResources);
}
