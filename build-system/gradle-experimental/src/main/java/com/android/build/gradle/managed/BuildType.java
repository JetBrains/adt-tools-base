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
    boolean getDebuggable();
    void setDebuggable(boolean isDebuggable);

    /**
     * Returns whether the build type is configured to be build with support for code coverage.
     *
     * @return true if code coverage is enabled.
     */
    boolean getTestCoverageEnabled();
    void setTestCoverageEnabled(boolean isTestCoverageEnabled);

    /**
     * Returns whether the build type is configured to be build with support for pseudolocales.
     *
     * @return true if code coverage is enabled.
     */
    boolean getPseudoLocalesEnabled();
    void setPseudoLocalesEnabled(boolean isPseudoLocalesEnabled);

    /**
     * Returns whether the build type is configured to generate an apk with debuggable
     * renderscript code.
     *
     * @return true if the apk is debuggable
     */
    boolean getRenderscriptDebuggable();
    void setRenderscriptDebuggable(boolean isRenderscriptDebuggable);

    /**
     * Returns the optimization level of the renderscript compilation.
     *
     * @return the optimization level.
     */
    Integer getRenderscriptOptimLevel();
    void setRenderscriptOptimLevel(Integer renderscriptOptimLevel);

    /**
     * Returns whether minification is enabled for this build type.
     *
     * @return true if minification is enabled.
     */
    boolean getMinifyEnabled();
    void setMinifyEnabled(boolean isMinifyEnabled);

    /**
     * Return whether zipalign is enabled for this build type.
     *
     * @return true if zipalign is enabled.
     */
    boolean getZipAlignEnabled();
    void setZipAlignEnabled(boolean isZipAlignEnabled);

    /**
     * Returns whether the variant embeds the micro app.
     */
    boolean getEmbedMicroApp();
    void setEmbedMicroApp(boolean isEmbedMicroApp);

    /**
     * Returns the associated signing config or null if none are set on the build type.
     */
    SigningConfig getSigningConfig();
    void setSigningConfig(SigningConfig signingConfig);

    /**
     * Returns the Jack options for this build type.
     */
    JackOptions getJackOptions();

    JavaCompileOptions getJavaCompileOptions();

    /**
     * Returns the shader compiler options for this build type.
     */
    ShaderOptions getShaders();

    boolean getShrinkResources();
    void setShrinkResources(boolean shrinkResources);

    NdkBuildType getNdk();

    ExternalNativeBuildOptions getExternalNativeBuild();

    boolean getUseProguard();
    void setUseProguard(boolean useProguard);
}
