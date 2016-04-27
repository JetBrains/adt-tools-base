/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.build.gradle.internal.core;

import static com.google.common.base.Preconditions.checkNotNull;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.AndroidConfig;
import com.android.build.gradle.TestAndroidConfig;
import com.android.build.gradle.internal.dsl.CoreBuildType;
import com.android.build.gradle.internal.dsl.CoreExternalNativeCmakeOptions;
import com.android.build.gradle.internal.dsl.CoreExternalNativeNdkBuildOptions;
import com.android.build.gradle.internal.dsl.CoreJackOptions;
import com.android.build.gradle.internal.dsl.CoreNdkOptions;
import com.android.build.gradle.internal.dsl.CoreProductFlavor;
import com.android.builder.core.VariantConfiguration;
import com.android.builder.core.VariantType;
import com.android.builder.model.SigningConfig;
import com.android.builder.model.SourceProvider;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Version of {@link com.android.builder.core.VariantConfiguration} that uses the specific
 * types used in the Gradle plugins.
 *
 * <p>It also adds support for Ndk support that is not ready to go in the builder library.
 */
public class GradleVariantConfiguration extends VariantConfiguration<CoreBuildType, CoreProductFlavor, CoreProductFlavor> {

    @Nullable
    private Boolean enableInstantRunOverride = null;
    private final MergedNdkConfig mMergedNdkConfig = new MergedNdkConfig();
    private final MergedJackOptions mMergedJackOptions = new MergedJackOptions();
    private final MergedCoreExternalNativeNdkBuildOptions mergedExternalNativeNdkBuildOptions
            = new MergedCoreExternalNativeNdkBuildOptions();
    private final MergedCoreExternalNativeCmakeOptions mergedExternalNativeCmakeOptions
            = new MergedCoreExternalNativeCmakeOptions();

    private GradleVariantConfiguration(
            @Nullable VariantConfiguration testedConfig,
            @NonNull CoreProductFlavor defaultConfig,
            @NonNull SourceProvider defaultSourceProvider,
            @NonNull CoreBuildType buildType,
            @Nullable SourceProvider buildTypeSourceProvider,
            @NonNull VariantType type,
            @Nullable SigningConfig signingConfigOverride) {
        super(defaultConfig, defaultSourceProvider, buildType, buildTypeSourceProvider, type,
                testedConfig, signingConfigOverride);
        computeJackOptions();
        computeNdkConfig();
        computeNdkBuildConfig();
        computeCmakeConfig();
    }

    /**
     * Creates a {@link GradleVariantConfiguration} for a testing variant derived from this variant.
     */
    public GradleVariantConfiguration getMyTestConfig(
            @NonNull SourceProvider defaultSourceProvider,
            @Nullable SourceProvider buildTypeSourceProvider,
            @NonNull VariantType type) {
        return new GradleVariantConfiguration(
                this,
                getDefaultConfig(),
                defaultSourceProvider,
                getBuildType(),
                buildTypeSourceProvider,
                type,
                getSigningConfig());
    }

    /**
     * Interface for building the {@link GradleVariantConfiguration} instances.
     */
    public interface Builder{
        /** Creates a variant configuration */
        @NonNull
        GradleVariantConfiguration create(
                @NonNull CoreProductFlavor defaultConfig,
                @NonNull SourceProvider defaultSourceProvider,
                @NonNull CoreBuildType buildType,
                @Nullable SourceProvider buildTypeSourceProvider,
                @NonNull VariantType type,
                @Nullable SigningConfig signingConfigOverride);
    }

    /** Builder for non-testing variant configurations */
    private static class VariantConfigurationBuilder implements Builder{
        @Override
        @NonNull
        public GradleVariantConfiguration create(
                @NonNull CoreProductFlavor defaultConfig,
                @NonNull SourceProvider defaultSourceProvider,
                @NonNull CoreBuildType buildType,
                @Nullable SourceProvider buildTypeSourceProvider,
                @NonNull VariantType type,
                @Nullable SigningConfig signingConfigOverride){
            return new GradleVariantConfiguration(
                    null /*testedConfig*/,
                    defaultConfig,
                    defaultSourceProvider,
                    buildType,
                    buildTypeSourceProvider,
                    type,
                    signingConfigOverride);
        }
    }

    /**
     * Creates a {@link GradleVariantConfiguration} for a testing module variant.
     *
     * <p>The difference from the regular modules is how the original application id,
     * and application id are resolved. Our build process supports the absence of manifest
     * file for these modules, and that is why the value resolution for these attributes
     * is different.
     */
    private static class TestModuleConfigurationBuilder implements Builder{

        @NonNull
        @Override
        public GradleVariantConfiguration create(
                @NonNull CoreProductFlavor defaultConfig,
                @NonNull SourceProvider defaultSourceProvider,
                @NonNull CoreBuildType buildType,
                @Nullable SourceProvider buildTypeSourceProvider,
                @NonNull VariantType type,
                @Nullable SigningConfig signingConfigOverride) {
            return new GradleVariantConfiguration(
                    null /*testedConfig*/,
                    defaultConfig,
                    defaultSourceProvider,
                    buildType,
                    buildTypeSourceProvider,
                    type,
                    signingConfigOverride){
                @NonNull
                @Override
                public String getApplicationId() {
                    String applicationId = getMergedFlavor().getTestApplicationId();

                    if (Strings.isNullOrEmpty(applicationId)){
                        applicationId = super.getApplicationId();
                    }

                    return applicationId;
                }

                @Nullable
                @Override
                public String getOriginalApplicationId() {
                    return getApplicationId();
                }

                @NonNull
                @Override
                public String getTestApplicationId() {
                    return getApplicationId();
                }

                @Override
                public GradleVariantConfiguration getMyTestConfig(
                        @NonNull SourceProvider defaultSourceProvider,
                        @Nullable SourceProvider buildTypeSourceProvider, @NonNull VariantType type) {
                    throw new UnsupportedOperationException("Test modules have no test variants.");
                }
            };
        }
    }

    /** Depending on the extension, gets appropriate variant configuration builder */
    public static Builder getBuilderForExtension(@NonNull AndroidConfig extension){
        if (extension instanceof TestAndroidConfig) {
            // if this is the test module
            return new TestModuleConfigurationBuilder();
        } else{
            // if this is non-test variant
            return new VariantConfigurationBuilder();
        }
    }

    @NonNull
    @Override
    public VariantConfiguration addProductFlavor(
            @NonNull CoreProductFlavor productFlavor,
            @NonNull SourceProvider sourceProvider,
            @NonNull String dimensionName) {
        checkNotNull(productFlavor);
        checkNotNull(sourceProvider);
        checkNotNull(dimensionName);
        super.addProductFlavor(productFlavor, sourceProvider, dimensionName);
        computeNdkConfig();
        computeNdkBuildConfig();
        computeCmakeConfig();
        return this;
    }

    @NonNull
    public CoreNdkOptions getNdkConfig() {
        return mMergedNdkConfig;
    }

    @NonNull
    public CoreExternalNativeNdkBuildOptions getExternalNativeNdkBuildOptions() {
        return mergedExternalNativeNdkBuildOptions;
    }

    @NonNull
    public CoreExternalNativeCmakeOptions getExternalNativeCmakeOptions() {
        return mergedExternalNativeCmakeOptions;
    }

    @NonNull
    public Set<String> getExternalNativeAbiFilters() {
        if (mergedExternalNativeCmakeOptions != null
                && mergedExternalNativeCmakeOptions.getAbiFilters() != null
                && !mergedExternalNativeCmakeOptions.getAbiFilters().isEmpty()) {
            return mergedExternalNativeCmakeOptions.getAbiFilters();
        }

        if (mergedExternalNativeNdkBuildOptions != null
                && mergedExternalNativeNdkBuildOptions.getAbiFilters() != null
                && !mergedExternalNativeNdkBuildOptions.getAbiFilters().isEmpty()) {
            return mergedExternalNativeNdkBuildOptions.getAbiFilters();
        }
        return Sets.newHashSet();
    }

    /**
     * Returns the ABI filters associated with the artifact, or null if there are no filters.
     *
     * If the list contains values, then the artifact only contains these ABIs and excludes
     * others.
     */
    @Nullable
    public Set<String> getSupportedAbis() {
        return mMergedNdkConfig.getAbiFilters();
    }

    /**
     * Returns whether the configuration has minification enabled.
     */
    public boolean isMinifyEnabled() {
        VariantType type = getType();
        // if type == test then getTestedConfig always returns non-null
        //noinspection ConstantConditions
        return getBuildType().isMinifyEnabled() &&
                (!type.isForTesting() || (getTestedConfig().getType() != VariantType.LIBRARY));
    }

    public CoreJackOptions getJackOptions() {
        return mMergedJackOptions;
    }

    private void computeJackOptions() {
        mMergedJackOptions.merge(getBuildType().getJackOptions());
        for (CoreProductFlavor productFlavor : getProductFlavors()) {
            mMergedJackOptions.merge(productFlavor.getJackOptions());
        }
        mMergedJackOptions.merge(getDefaultConfig().getJackOptions());
    }

    private void computeNdkConfig() {
        mMergedNdkConfig.reset();

        if (getDefaultConfig().getNdkConfig() != null) {
            mMergedNdkConfig.append(getDefaultConfig().getNdkConfig());
        }

        final List<CoreProductFlavor> flavors = getProductFlavors();
        for (int i = flavors.size() - 1 ; i >= 0 ; i--) {
            CoreNdkOptions ndkConfig = flavors.get(i).getNdkConfig();
            if (ndkConfig != null) {
                mMergedNdkConfig.append(ndkConfig);
            }
        }

        if (getBuildType().getNdkConfig() != null && !getType().isForTesting()) {
            mMergedNdkConfig.append(getBuildType().getNdkConfig());
        }
    }

    private void computeNdkBuildConfig() {
        if (getDefaultConfig().getExternalNativeNdkBuildOptions() != null) {
            mergedExternalNativeNdkBuildOptions.append(getDefaultConfig().getExternalNativeNdkBuildOptions());
        }

        final List<CoreProductFlavor> flavors = getProductFlavors();
        for (int i = flavors.size() - 1 ; i >= 0 ; i--) {
            CoreExternalNativeNdkBuildOptions options = flavors.get(i).getExternalNativeNdkBuildOptions();
            if (options != null) {
                mergedExternalNativeNdkBuildOptions.append(options);
            }
        }

        if (getBuildType().getNdkConfig() != null && !getType().isForTesting()) {
            mergedExternalNativeNdkBuildOptions.append(getBuildType().getExternalNativeNdkBuildOptions());
        }
    }

    private void computeCmakeConfig() {
        if (getDefaultConfig().getExternalNativeNdkBuildOptions() != null) {
            mergedExternalNativeCmakeOptions.append(getDefaultConfig().getExternalNativeCmakeOptions());
        }

        final List<CoreProductFlavor> flavors = getProductFlavors();
        for (int i = flavors.size() - 1 ; i >= 0 ; i--) {
            CoreExternalNativeCmakeOptions options = flavors.get(i).getExternalNativeCmakeOptions();
            if (options != null) {
                mergedExternalNativeCmakeOptions.append(options);
            }
        }

        if (getBuildType().getNdkConfig() != null && !getType().isForTesting()) {
            mergedExternalNativeCmakeOptions.append(getBuildType().getExternalNativeCmakeOptions());
        }
    }

    public boolean isInstantRunSupported() {
        if (enableInstantRunOverride != null) {
            return enableInstantRunOverride;
        }
        return getBuildType().isDebuggable()
                && !getType().isForTesting()
                && !getJackOptions().isEnabled();
    }

    public void setEnableInstantRunOverride(@Nullable Boolean enableInstantRunOverride) {
        this.enableInstantRunOverride = enableInstantRunOverride;
    }

    @NonNull
    public List<String> getDefautGlslcArgs() {
        Map<String, String> optionMap = Maps.newHashMap();

        // add the lower priority one, to override them with the higher priority ones.
        for (String option : getDefaultConfig().getShaders().getGlslcArgs()) {
            optionMap.put(getKey(option), option);
        }

        // cant use merge flavor as it's not a prop on the base class.
        // reverse loop for proper order
        List<CoreProductFlavor> flavors = getProductFlavors();
        for (int i = flavors.size() - 1; i >= 0; i--) {
            for (String option : flavors.get(i).getShaders().getGlslcArgs()) {
                optionMap.put(getKey(option), option);
            }
        }

        // then the build type
        for (String option : getBuildType().getShaders().getGlslcArgs()) {
            optionMap.put(getKey(option), option);
        }

        return Lists.newArrayList(optionMap.values());
    }

    @NonNull
    public Map<String, List<String>> getScopedGlslcArgs() {
        Map<String, List<String>> scopedArgs = Maps.newHashMap();

        // first collect all possible keys.
        Set<String> keys = getScopedGlslcKeys();

        for (String key : keys) {
            // first add to a temp map to resolve overridden values
            Map<String, String> optionMap = Maps.newHashMap();

            // we're going to go from lower priority, to higher priority elements, and for each
            // start with the non scoped version, and then add the scoped version.

            // 1. default config, global.
            for (String option : getDefaultConfig().getShaders().getGlslcArgs()) {
                optionMap.put(getKey(option), option);
            }

            // 1b. default config, scoped.
            for (String option : getDefaultConfig().getShaders().getScopedGlslcArgs().get(key)) {
                optionMap.put(getKey(option), option);
            }

            // 2. the flavors.
            // cant use merge flavor as it's not a prop on the base class.
            // reverse loop for proper order
            List<CoreProductFlavor> flavors = getProductFlavors();
            for (int i = flavors.size() - 1; i >= 0; i--) {
                // global
                for (String option : flavors.get(i).getShaders().getGlslcArgs()) {
                    optionMap.put(getKey(option), option);
                }

                // scoped.
                for (String option : flavors.get(i).getShaders().getScopedGlslcArgs().get(key)) {
                    optionMap.put(getKey(option), option);
                }
            }

            // 3. the build type, global
            for (String option : getBuildType().getShaders().getGlslcArgs()) {
                optionMap.put(getKey(option), option);
            }

            // 3b. the build type, scoped.
            for (String option : getBuildType().getShaders().getScopedGlslcArgs().get(key)) {
                optionMap.put(getKey(option), option);
            }

            // now add the full value list.
            scopedArgs.put(key, ImmutableList.copyOf(optionMap.values()));
        }

        return scopedArgs;
    }

    @NonNull
    private Set<String> getScopedGlslcKeys() {
        Set<String> keys = Sets.newHashSet();

        keys.addAll(getDefaultConfig().getShaders().getScopedGlslcArgs().keySet());

        for (CoreProductFlavor flavor : getProductFlavors()) {
            keys.addAll(flavor.getShaders().getScopedGlslcArgs().keySet());
        }

        keys.addAll(getBuildType().getShaders().getScopedGlslcArgs().keySet());

        return keys;
    }

    @NonNull
    private static String getKey(@NonNull String fullOption) {
        int pos = fullOption.lastIndexOf('=');
        if (pos == -1) {
            return fullOption;
        }

        return fullOption.substring(0, pos);
    }
}
