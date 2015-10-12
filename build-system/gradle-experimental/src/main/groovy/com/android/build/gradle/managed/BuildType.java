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

import com.android.builder.model.AndroidArtifact;

import org.gradle.api.Named;
import org.gradle.model.Managed;
import org.gradle.model.ModelSet;

import java.io.File;
import java.util.List;
import java.util.Set;

/**
 * A Managed build type.
 */
@Managed
public interface BuildType extends BaseConfig {

    /**
     * Returns whether the build type is configured to generate a debuggable apk.
     *
     * @return true if the apk is debuggable
     */
    Boolean getDebuggable();
    void setDebuggable(Boolean isDebuggable);

    /**
     * Returns whether the build type is configured to be build with support for code coverage.
     *
     * @return true if code coverage is enabled.
     */
    Boolean getTestCoverageEnabled();
    void setTestCoverageEnabled(Boolean isTestCoverageEnabled);

    /**
     * Returns whether the build type is configured to be build with support for pseudolocales.
     *
     * @return true if code coverage is enabled.
     */
    Boolean getPseudoLocalesEnabled();
    void setPseudoLocalesEnabled(Boolean isPseudoLocalesEnabled);

    /**
     * Returns whether the build type is configured to generate an apk with debuggable
     * renderscript code.
     *
     * @return true if the apk is debuggable
     */
    Boolean getRenderscriptDebuggable();
    void setRenderscriptDebuggable(Boolean isRenderscriptDebuggable);

    /**
     * Returns the optimization level of the renderscript compilation.
     *
     * @return the optimization level.
     */
    Integer getRenderscriptOptimLevel();
    void setRenderscriptOptimLevel(Integer renderscriptOptimLevel);

    /**
     * Returns the version name suffix.
     *
     * @return the version name suffix.
     */
    String getVersionNameSuffix();
    void setVersionNameSuffix(String versionNameSuffix);

    /**
     * Returns whether minification is enabled for this build type.
     *
     * @return true if minification is enabled.
     */
    Boolean getMinifyEnabled();
    void setMinifyEnabled(Boolean isMinifyEnabled);

    /**
     * Return whether zipalign is enabled for this build type.
     *
     * @return true if zipalign is enabled.
     */
    Boolean getZipAlignEnabled();
    void setZipAlignEnabled(Boolean isZipAlignEnabled);

    /**
     * Returns whether the variant embeds the micro app.
     */
    Boolean getEmbedMicroApp();
    void setEmbedMicroApp(Boolean isEmbedMicroApp);

    /**
     * Returns the associated signing config or null if none are set on the build type.
     */
    SigningConfig getSigningConfig();
    void setSigningConfig(SigningConfig signingConfig);

    Boolean getUseJack();
    void setUseJack(Boolean useJack);

    Boolean getShrinkResources();
    void setShrinkResources(Boolean shrinkResources);

    NdkBuildType getNdk();
}
