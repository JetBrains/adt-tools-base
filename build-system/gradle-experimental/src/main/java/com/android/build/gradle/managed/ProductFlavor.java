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
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.DimensionAware;
import com.android.builder.model.Variant;

import org.gradle.model.Managed;

import java.util.Set;

/**
 * A Managed product flavor.
 *
 * TODO: Convert Unmanaged Collection to Managed type when Gradle provides ModelSet for basic class.
 */
@Managed
public interface ProductFlavor extends DimensionAware, BaseConfig {

    /**
     * Returns the flavor dimension or null if not applicable.
     */
    @Override
    @Nullable
    String getDimension();
    void setDimension(String dimension);

    /**
     * Returns the name of the product flavor. This is only the value set on this product flavor.
     * To get the final application id name, use {@link AndroidArtifact#getApplicationId()}.
     *
     * @return the application id.
     */
    @Nullable
    String getApplicationId();
    void setApplicationId(String applicationId);

    /**
     * Returns the version code associated with this flavor or null if none have been set.
     * This is only the value set on this product flavor, not necessarily the actual
     * version code used.
     *
     * @return the version code, or null if not specified
     */
    @Nullable
    Integer getVersionCode();
    void setVersionCode(Integer versionCode);

    /**
     * Returns the version name. This is only the value set on this product flavor.
     * To get the final value, use {@link Variant#getMergedFlavor()} with
     * {@link #getVersionNameSuffix()} and {@link BuildType#getVersionNameSuffix()}.
     *
     * @return the version name.
     */
    @Nullable
    String getVersionName();
    void setVersionName(String versionName);

    /**
     * Returns the minSdkVersion. This is only the value set on this product flavor.
     *
     * @return the minSdkVersion, or null if not specified
     */
    @Nullable
    ApiVersion getMinSdkVersion();

    /**
     * Returns the targetSdkVersion. This is only the value set on this product flavor.
     *
     * @return the targetSdkVersion, or null if not specified
     */
    @Nullable
    ApiVersion getTargetSdkVersion();

    /**
     * Returns the maxSdkVersion. This is only the value set on this produce flavor.
     *
     * @return the maxSdkVersion, or null if not specified
     */
    @Nullable
    Integer getMaxSdkVersion();
    void setMaxSdkVersion(Integer maxSdkVersion);

    /**
     * Returns the renderscript target api. This is only the value set on this product flavor.
     * TODO: make final renderscript target api available through the model
     *
     * @return the renderscript target api, or null if not specified
     */
    @Nullable
    Integer getRenderscriptTargetApi();
    void setRenderscriptTargetApi(Integer renderscriptTargetApi);

    /**
     * Returns whether the renderscript code should be compiled in support mode to
     * make it compatible with older versions of Android.
     *
     * @return true if support mode is enabled, false if not, and null if not specified.
     */
    @Nullable
    Boolean getRenderscriptSupportModeEnabled();
    void setRenderscriptSupportModeEnabled(Boolean renderscriptSupportModeEnabled);

    /**
     * Returns whether the renderscript BLAS support lib should be used to
     * make it compatible with older versions of Android.
     *
     * @return true if BLAS support lib is enabled, false if not, and null if not specified.
     */
    @Nullable
    Boolean getRenderscriptSupportModeBlasEnabled();
    void setRenderscriptSupportModeBlasEnabled(Boolean renderscriptSupportModeBlasEnabled);

    /**
     * Returns whether the renderscript code should be compiled to generate C/C++ bindings.
     * @return true for C/C++ generation, false for Java, null if not specified.
     */
    @Nullable
    Boolean getRenderscriptNdkModeEnabled();
    void setRenderscriptNdkModeEnabled(Boolean renderscriptNdkModeEnabled);

    /**
     * Returns the test application id. This is only the value set on this product flavor.
     * To get the final value, use {@link Variant#getExtraAndroidArtifacts()} with
     * {@link AndroidProject#ARTIFACT_ANDROID_TEST} and then
     * {@link AndroidArtifact#getApplicationId()}
     *
     * @return the test package name.
     */
    @Nullable
    String getTestApplicationId();
    void setTestApplicationId(String testApplicationId);

    /**
     * Returns the test instrumentation runner. This is only the value set on this product flavor.
     * TODO: make test instrumentation runner available through the model.
     *
     * @return the test package name.
     */
    @Nullable
    String getTestInstrumentationRunner();
    void setTestInstrumentationRunner(String testInstrumentationRunner);

    /**
     * Returns the handlingProfile value. This is only the value set on this product flavor.
     *
     *  @return the handlingProfile value.
     */
    @Nullable
    Boolean getTestHandleProfiling();
    void setTestHandleProfiling(Boolean testHandleProfiling);

    /**
     * Returns the functionalTest value. This is only the value set on this product flavor.
     *
     * @return the functionalTest value.
     */
    @Nullable
    Boolean getTestFunctionalTest();
    void setTestFunctionalTest(Boolean testFunctionalTest);

    /**
     * Returns the resource configuration for this variant.
     *
     * This is the list of -c parameters for aapt.
     *
     * @return the resource configuration options.
     */
    @NonNull
    Set<String> getResourceConfigurations();

    /**
     * Returns the associated signing config or null if none are set on the product flavor.
     */
    SigningConfig getSigningConfig();
    void setSigningConfig(SigningConfig signingConfig);

    /**
     * Returns the Jack options for this product flavor.
     */
    JackOptions getJackOptions();

    /**
     * Returns the apt options for this product flavor.
     */
    JavaCompileOptions getJavaCompileOptions();

    /**
     * Returns the shader compiler options for this product flavor.
     */
    ShaderOptions getShaders();

    NdkOptions getNdk();

    /**
     * Returns the native build options for this product flavor.
     */
    ExternalNativeBuildOptions getExternalNativeBuild();

    @NonNull
    VectorDrawablesOptions getVectorDrawables();

    /**
     * Returns whether to enable unbundling mode for embedded wear app.
     *
     * If true, this enables the app to transition from an embedded wear app to one
     * distributed by the play store directly.
     */
    Boolean getWearAppUnbundled();
    void setWearAppUnbundled(Boolean wearAppUnbundled);
}
