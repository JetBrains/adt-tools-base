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

package com.android.builder.core;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.internal.BaseConfigImpl;
import com.android.builder.model.ApiVersion;
import com.android.builder.model.ProductFlavor;
import com.android.builder.model.SigningConfig;
import com.google.common.base.Objects;
import com.google.common.collect.Sets;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;

/**
 * The configuration of a product flavor.
 *
 * This is also used to describe the default configuration of all builds, even those that
 * do not contain any flavors.
 */
public class DefaultProductFlavor extends BaseConfigImpl implements ProductFlavor {
    private static final long serialVersionUID = 1L;

    private final String mName;
    private ApiVersion mMinSdkVersion;
    private ApiVersion mTargetSdkVersion;
    private Integer mMaxSdkVersion;
    private Integer mRenderscriptTargetApi;
    private Boolean mRenderscriptSupportModeEnabled;
    private Boolean mRenderscriptNdkModeEnabled;
    private Integer mVersionCode;
    private String mVersionName;
    private String mApplicationId;
    private String mTestApplicationId;
    private String mTestInstrumentationRunner;
    private Boolean mTestHandleProfiling;
    private Boolean mTestFunctionalTest;
    private SigningConfig mSigningConfig;
    private Set<String> mResourceConfiguration;

    /**
     * Creates a ProductFlavor with a given name.
     *
     * Names can be important when dealing with flavor groups.
     * @param name the name of the flavor.
     *
     * @see BuilderConstants#MAIN
     */
    public DefaultProductFlavor(@NonNull String name) {
        mName = name;
    }

    @Override
    @NonNull
    public String getName() {
        return mName;
    }

    /**
     * Sets the application id.
     */
    @NonNull
    public ProductFlavor setApplicationId(String applicationId) {
        mApplicationId = applicationId;
        return this;
    }

    /**
     * Returns the application ID.
     *
     * <p>See <a href="http://tools.android.com/tech-docs/new-build-system/applicationid-vs-packagename">ApplicationId versus PackageName</a>
     */
    @Override
    @Nullable
    public String getApplicationId() {
        return mApplicationId;
    }

    /**
     * Sets the version code.
     *
     * @param versionCode the version code
     * @return the flavor object
     */
    @NonNull
    public ProductFlavor setVersionCode(Integer versionCode) {
        mVersionCode = versionCode;
        return this;
    }

    /**
     * Version code.
     *
     * <p>See <a href="http://developer.android.com/tools/publishing/versioning.html">Versioning Your Application</a>
     */
    @Override
    @Nullable
    public Integer getVersionCode() {
        return mVersionCode;
    }

    /**
     * Sets the version name.
     *
     * @param versionName the version name
     * @return the flavor object
     */
    @NonNull
    public ProductFlavor setVersionName(String versionName) {
        mVersionName = versionName;
        return this;
    }

    /**
     * Version name.
     *
     * <p>See <a href="http://developer.android.com/tools/publishing/versioning.html">Versioning Your Application</a>
     */
    @Override
    @Nullable
    public String getVersionName() {
        return mVersionName;
    }

    /**
     * Sets the minSdkVersion to the given value.
     */
    @NonNull
    public ProductFlavor setMinSdkVersion(ApiVersion minSdkVersion) {
        mMinSdkVersion = minSdkVersion;
        return this;
    }

    /**
     * Min SDK version.
     */
    @Override
    public ApiVersion getMinSdkVersion() {
        return mMinSdkVersion;
    }

    /** Sets the targetSdkVersion to the given value. */
    @NonNull
    public ProductFlavor setTargetSdkVersion(ApiVersion targetSdkVersion) {
        mTargetSdkVersion = targetSdkVersion;
        return this;
    }

    /**
     * Target SDK version.
     */
    @Override
    public ApiVersion getTargetSdkVersion() {
        return mTargetSdkVersion;
    }

    @NonNull
    public ProductFlavor setMaxSdkVersion(Integer maxSdkVersion) {
        mMaxSdkVersion = maxSdkVersion;
        return this;
    }

    @Override
    public Integer getMaxSdkVersion() {
        return mMaxSdkVersion;
    }

    @Override
    @Nullable
    public Integer getRenderscriptTargetApi() {
        return mRenderscriptTargetApi;
    }

    /** Sets the renderscript target API to the given value. */
    public void setRenderscriptTargetApi(Integer renderscriptTargetApi) {
        mRenderscriptTargetApi = renderscriptTargetApi;
    }

    @Override
    @Nullable
    public Boolean getRenderscriptSupportModeEnabled() {
        return mRenderscriptSupportModeEnabled;
    }

    /**
     * Sets whether the renderscript code should be compiled in support mode to make it compatible
     * with older versions of Android.
     */
    public ProductFlavor setRenderscriptSupportModeEnabled(Boolean renderscriptSupportMode) {
        mRenderscriptSupportModeEnabled = renderscriptSupportMode;
        return this;
    }

    @Override
    @Nullable
    public Boolean getRenderscriptNdkModeEnabled() {
        return mRenderscriptNdkModeEnabled;
    }


    /** Sets whether the renderscript code should be compiled to generate C/C++ bindings. */
    public ProductFlavor setRenderscriptNdkModeEnabled(Boolean renderscriptNdkMode) {
        mRenderscriptNdkModeEnabled = renderscriptNdkMode;
        return this;
    }

    /** Sets the test application ID. */
    @NonNull
    public ProductFlavor setTestApplicationId(String applicationId) {
        mTestApplicationId = applicationId;
        return this;
    }

    /**
     * Test application ID.
     *
     * <p>See <a href="http://tools.android.com/tech-docs/new-build-system/applicationid-vs-packagename">ApplicationId versus PackageName</a>
     */
    @Override
    @Nullable
    public String getTestApplicationId() {
        return mTestApplicationId;
    }

    /** Sets the test instrumentation runner to the given value. */
    @NonNull
    public ProductFlavor setTestInstrumentationRunner(String testInstrumentationRunner) {
        mTestInstrumentationRunner = testInstrumentationRunner;
        return this;
    }

    /**
     * Test instrumentation runner class name.
     *
     * <p>This is a fully qualified class name of the runner, e.g.
     * <code>android.test.InstrumentationTestRunner</code>
     *
     * <p>See <a href="http://developer.android.com/guide/topics/manifest/instrumentation-element.html">
     * instrumentation</a>.
     */
    @Override
    @Nullable
    public String getTestInstrumentationRunner() {
        return mTestInstrumentationRunner;
    }

    /**
     * See <a href="http://developer.android.com/guide/topics/manifest/instrumentation-element.html">
     * instrumentation</a>.
     */
    @Override
    @Nullable
    public Boolean getTestHandleProfiling() {
        return mTestHandleProfiling;
    }

    @NonNull
    public ProductFlavor setTestHandleProfiling(boolean handleProfiling) {
        mTestHandleProfiling = handleProfiling;
        return this;
    }

    /**
     * See <a href="http://developer.android.com/guide/topics/manifest/instrumentation-element.html">
     * instrumentation</a>.
     */
    @Override
    @Nullable
    public Boolean getTestFunctionalTest() {
        return mTestFunctionalTest;
    }

    @NonNull
    public ProductFlavor setTestFunctionalTest(boolean functionalTest) {
        mTestFunctionalTest = functionalTest;
        return this;
    }

    /**
     * Signing config used by this product flavor.
     */
    @Override
    @Nullable
    public SigningConfig getSigningConfig() {
        return mSigningConfig;
    }

    /** Sets the signing configuration. e.g.: {@code signingConfig signingConfigs.myConfig} */
    @NonNull
    public ProductFlavor setSigningConfig(SigningConfig signingConfig) {
        mSigningConfig = signingConfig;
        return this;
    }

    public void addResourceConfiguration(@NonNull String configuration) {
        if (mResourceConfiguration == null) {
            mResourceConfiguration = Sets.newHashSet();
        }

        mResourceConfiguration.add(configuration);
    }

    public void addResourceConfigurations(@NonNull String... configurations) {
        if (mResourceConfiguration == null) {
            mResourceConfiguration = Sets.newHashSet();
        }

        mResourceConfiguration.addAll(Arrays.asList(configurations));
    }

    public void addResourceConfigurations(@NonNull Collection<String> configurations) {
        if (mResourceConfiguration == null) {
            mResourceConfiguration = Sets.newHashSet();
        }

        mResourceConfiguration.addAll(configurations);
    }

    @NonNull
    @Override
    public Collection<String> getResourceConfigurations() {
        if (mResourceConfiguration == null) {
            mResourceConfiguration = Sets.newHashSet();
        }

        return mResourceConfiguration;
    }

    /**
     * Merges two flavors on top of one another and returns a new object with the result.
     *
     * The behavior is that if a value is present in the overlay, then it is used, otherwise
     * we use the value from the base.
     *
     * @param base the flavor to merge on top of
     * @param overlay the flavor to apply on top of the base.
     *
     * @return a new ProductFlavor that represents the merge.
     */
    @NonNull
    static ProductFlavor mergeFlavors(@NonNull ProductFlavor base, @NonNull ProductFlavor overlay) {
        DefaultProductFlavor flavor = new DefaultProductFlavor("");

        flavor.mMinSdkVersion = chooseNotNull(
                overlay.getMinSdkVersion(),
                base.getMinSdkVersion());
        flavor.mTargetSdkVersion = chooseNotNull(
                overlay.getTargetSdkVersion(),
                base.getTargetSdkVersion());
        flavor.mMaxSdkVersion = chooseNotNull(
                overlay.getMaxSdkVersion(),
                base.getMaxSdkVersion());

        flavor.mRenderscriptTargetApi = chooseNotNull(
                overlay.getRenderscriptTargetApi(),
                base.getRenderscriptTargetApi());
        flavor.mRenderscriptSupportModeEnabled = chooseNotNull(
                overlay.getRenderscriptSupportModeEnabled(),
                base.getRenderscriptSupportModeEnabled());
        flavor.mRenderscriptNdkModeEnabled = chooseNotNull(
                overlay.getRenderscriptNdkModeEnabled(),
                base.getRenderscriptNdkModeEnabled());

        flavor.mVersionCode = chooseNotNull(overlay.getVersionCode(), base.getVersionCode());
        flavor.mVersionName = chooseNotNull(overlay.getVersionName(), base.getVersionName());

        flavor.mApplicationId = chooseNotNull(overlay.getApplicationId(), base.getApplicationId());

        flavor.mTestApplicationId = chooseNotNull(
                overlay.getTestApplicationId(),
                base.getTestApplicationId());
        flavor.mTestInstrumentationRunner = chooseNotNull(
                overlay.getTestInstrumentationRunner(),
                base.getTestInstrumentationRunner());

        flavor.mTestHandleProfiling = chooseNotNull(
                overlay.getTestHandleProfiling(),
                base.getTestHandleProfiling());

        flavor.mTestFunctionalTest = chooseNotNull(
                overlay.getTestFunctionalTest(),
                base.getTestFunctionalTest());

        flavor.mSigningConfig = chooseNotNull(
                overlay.getSigningConfig(),
                base.getSigningConfig());

        flavor.addResourceConfigurations(base.getResourceConfigurations());
        flavor.addResourceConfigurations(overlay.getResourceConfigurations());

        flavor.addManifestPlaceholders(base.getManifestPlaceholders());
        flavor.addManifestPlaceholders(overlay.getManifestPlaceholders());

        flavor.addResValues(base.getResValues());
        flavor.addResValues(overlay.getResValues());

        flavor.addBuildConfigFields(base.getBuildConfigFields());
        flavor.addBuildConfigFields(overlay.getBuildConfigFields());

        flavor.setMultiDexEnabled(chooseNotNull(
                overlay.getMultiDexEnabled(), base.getMultiDexEnabled()));

        flavor.setMultiDexKeepFile(chooseNotNull(
                overlay.getMultiDexKeepFile(), base.getMultiDexKeepFile()));

        flavor.setMultiDexKeepProguard(chooseNotNull(
                overlay.getMultiDexKeepProguard(), base.getMultiDexKeepProguard()));
        return flavor;
    }

    /**
     * Clone a given product flavor.
     *
     * @param productFlavor the flavor to clone.
     *
     * @return a new instance that is a clone of the flavor.
     */
    @NonNull
    static ProductFlavor clone(@NonNull ProductFlavor productFlavor) {
        DefaultProductFlavor flavor = new DefaultProductFlavor(productFlavor.getName());
        flavor._initWith(productFlavor);

        flavor.mMinSdkVersion = productFlavor.getMinSdkVersion();
        flavor.mTargetSdkVersion = productFlavor.getTargetSdkVersion();
        flavor.mMaxSdkVersion = productFlavor.getMaxSdkVersion();
        flavor.mRenderscriptTargetApi = productFlavor.getRenderscriptTargetApi();
        flavor.mRenderscriptSupportModeEnabled = productFlavor.getRenderscriptSupportModeEnabled();
        flavor.mRenderscriptNdkModeEnabled = productFlavor.getRenderscriptNdkModeEnabled();

        flavor.mVersionCode = productFlavor.getVersionCode();
        flavor.mVersionName = productFlavor.getVersionName();

        flavor.mApplicationId = productFlavor.getApplicationId();

        flavor.mTestApplicationId = productFlavor.getTestApplicationId();
        flavor.mTestInstrumentationRunner = productFlavor.getTestInstrumentationRunner();
        flavor.mTestHandleProfiling = productFlavor.getTestHandleProfiling();
        flavor.mTestFunctionalTest = productFlavor.getTestFunctionalTest();

        flavor.mSigningConfig = productFlavor.getSigningConfig();

        flavor.addResourceConfigurations(productFlavor.getResourceConfigurations());
        flavor.addManifestPlaceholders(productFlavor.getManifestPlaceholders());

        flavor.addResValues(productFlavor.getResValues());
        flavor.addBuildConfigFields(productFlavor.getBuildConfigFields());

        flavor.setMultiDexEnabled(productFlavor.getMultiDexEnabled());

        flavor.setMultiDexKeepFile(productFlavor.getMultiDexKeepFile());
        flavor.setMultiDexKeepProguard(productFlavor.getMultiDexKeepProguard());

        return flavor;
    }

    private static <T> T chooseNotNull(T overlay, T base) {
        return overlay != null ? overlay : base;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        DefaultProductFlavor that = (DefaultProductFlavor) o;

        if (mApplicationId != null ? !mApplicationId.equals(that.mApplicationId)
                : that.mApplicationId != null) {
            return false;
        }
        if (mMaxSdkVersion != null ? !mMaxSdkVersion.equals(that.mMaxSdkVersion)
                : that.mMaxSdkVersion != null) {
            return false;
        }
        if (mMinSdkVersion != null ? !mMinSdkVersion.equals(that.mMinSdkVersion)
                : that.mMinSdkVersion != null) {
            return false;
        }
        if (!mName.equals(that.mName)) {
            return false;
        }
        if (mRenderscriptNdkModeEnabled != null ? !mRenderscriptNdkModeEnabled
                .equals(that.mRenderscriptNdkModeEnabled)
                : that.mRenderscriptNdkModeEnabled != null) {
            return false;
        }
        if (mRenderscriptSupportModeEnabled != null ? !mRenderscriptSupportModeEnabled
                .equals(that.mRenderscriptSupportModeEnabled) : that.mRenderscriptSupportModeEnabled
                != null) {
            return false;
        }
        if (mRenderscriptTargetApi != null ? !mRenderscriptTargetApi
                .equals(that.mRenderscriptTargetApi)
                : that.mRenderscriptTargetApi != null) {
            return false;
        }
        if (mResourceConfiguration != null ? !mResourceConfiguration
                .equals(that.mResourceConfiguration)
                : that.mResourceConfiguration != null) {
            return false;
        }
        if (mSigningConfig != null ? !mSigningConfig.equals(that.mSigningConfig)
                : that.mSigningConfig != null) {
            return false;
        }
        if (mTargetSdkVersion != null ? !mTargetSdkVersion.equals(that.mTargetSdkVersion)
                : that.mTargetSdkVersion != null) {
            return false;
        }
        if (mTestApplicationId != null ? !mTestApplicationId.equals(that.mTestApplicationId)
                : that.mTestApplicationId != null) {
            return false;
        }
        if (mTestFunctionalTest != null ? !mTestFunctionalTest.equals(that.mTestFunctionalTest)
                : that.mTestFunctionalTest != null) {
            return false;
        }
        if (mTestHandleProfiling != null ? !mTestHandleProfiling.equals(that.mTestHandleProfiling)
                : that.mTestHandleProfiling != null) {
            return false;
        }
        if (mTestInstrumentationRunner != null ? !mTestInstrumentationRunner
                .equals(that.mTestInstrumentationRunner)
                : that.mTestInstrumentationRunner != null) {
            return false;
        }
        if (mVersionCode != null ? !mVersionCode.equals(that.mVersionCode)
                : that.mVersionCode != null) {
            return false;
        }
        if (mVersionName != null ? !mVersionName.equals(that.mVersionName)
                : that.mVersionName != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + mName.hashCode();
        result = 31 * result + (mMinSdkVersion != null ? mMinSdkVersion.hashCode() : 0);
        result = 31 * result + (mTargetSdkVersion != null ? mTargetSdkVersion.hashCode() : 0);
        result = 31 * result + (mMaxSdkVersion != null ? mMaxSdkVersion.hashCode() : 0);
        result = 31 * result + (mRenderscriptTargetApi != null ? mRenderscriptTargetApi.hashCode()
                : 0);
        result = 31 * result + (mRenderscriptSupportModeEnabled != null ? mRenderscriptSupportModeEnabled
                .hashCode()
                : 0);
        result = 31 * result + (mRenderscriptNdkModeEnabled != null ? mRenderscriptNdkModeEnabled.hashCode() : 0);
        result = 31 * result + (mVersionCode != null ? mVersionCode.hashCode() : 0);
        result = 31 * result + (mVersionName != null ? mVersionName.hashCode() : 0);
        result = 31 * result + (mApplicationId != null ? mApplicationId.hashCode() : 0);
        result = 31 * result + (mTestApplicationId != null ? mTestApplicationId.hashCode() : 0);
        result = 31 * result + (mTestInstrumentationRunner != null ? mTestInstrumentationRunner
                .hashCode() : 0);
        result = 31 * result + (mTestHandleProfiling != null ? mTestHandleProfiling.hashCode() : 0);
        result = 31 * result + (mTestFunctionalTest != null ? mTestFunctionalTest.hashCode() : 0);
        result = 31 * result + (mSigningConfig != null ? mSigningConfig.hashCode() : 0);
        result = 31 * result + (mResourceConfiguration != null ? mResourceConfiguration.hashCode()
                : 0);
        return result;
    }

    @Override
    @NonNull
    public String toString() {
        return Objects.toStringHelper(this)
                .add("name", mName)
                .add("minSdkVersion", mMinSdkVersion)
                .add("targetSdkVersion", mTargetSdkVersion)
                .add("renderscriptTargetApi", mRenderscriptTargetApi)
                .add("renderscriptSupportModeEnabled", mRenderscriptSupportModeEnabled)
                .add("renderscriptNdkModeEnabled", mRenderscriptNdkModeEnabled)
                .add("versionCode", mVersionCode)
                .add("versionName", mVersionName)
                .add("applicationId", mApplicationId)
                .add("testApplicationId", mTestApplicationId)
                .add("testInstrumentationRunner", mTestInstrumentationRunner)
                .add("testHandleProfiling", mTestHandleProfiling)
                .add("testFunctionalTest", mTestFunctionalTest)
                .add("signingConfig", mSigningConfig)
                .add("resConfig", mResourceConfiguration)
                .add("mBuildConfigFields", getBuildConfigFields())
                .add("mResValues", getResValues())
                .add("mProguardFiles", getProguardFiles())
                .add("mConsumerProguardFiles", getConsumerProguardFiles())
                .add("mManifestPlaceholders", getManifestPlaceholders())
                .toString();
    }
}
