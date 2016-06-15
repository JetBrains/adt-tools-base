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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.dependency.DependencyContainer;
import com.android.builder.internal.ClassFieldImpl;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.ApiVersion;
import com.android.builder.model.BuildType;
import com.android.builder.model.ClassField;
import com.android.builder.model.JavaLibrary;
import com.android.builder.model.MavenCoordinates;
import com.android.builder.model.ProductFlavor;
import com.android.builder.model.SigningConfig;
import com.android.builder.model.SourceProvider;
import com.android.ide.common.res2.AssetSet;
import com.android.ide.common.res2.ResourceSet;
import com.android.sdklib.SdkVersionInfo;
import com.android.utils.StringHelper;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A Variant configuration.
 *
 * Variants are made from the combination of:
 *
 * - a build type (base interface BuildType), and its associated sources.
 * - a default configuration (base interface ProductFlavor), and its associated sources.
 * - a optional list of product flavors (base interface ProductFlavor) and their associated sources.
 * - dependencies (both jar and aar).
 *
 * @param <T> the type used for the Build Type.
 * @param <D> the type used for the default config
 * @param <F> the type used for the product flavors.
 */
public class VariantConfiguration<T extends BuildType, D extends ProductFlavor, F extends ProductFlavor> {

    /**
     * Full, unique name of the variant in camel case, including BuildType and Flavors (and Test)
     */
    private String mFullName;
    /**
     * Flavor Name of the variant, including all flavors in camel case (starting with a lower
     * case).
     */
    private String mFlavorName;
    /**
     * Full, unique name of the variant, including BuildType, flavors and test, dash separated.
     * (similar to full name but with dashes)
     */
    private String mBaseName;
    /**
     * Unique directory name (can include multiple folders) for the variant, based on build type,
     * flavor and test.
     * This always uses forward slashes ('/') as separator on all platform.
     *
     */
    private String mDirName;
    private List<String> mDirSegments;

    @NonNull
    private final D mDefaultConfig;
    @NonNull
    private final SourceProvider mDefaultSourceProvider;

    @NonNull
    private final T mBuildType;
    /** SourceProvider for the BuildType. Can be null */
    @Nullable
    private final SourceProvider mBuildTypeSourceProvider;

    private final List<String> mFlavorDimensionNames = Lists.newArrayList();
    private final List<F> mFlavors = Lists.newArrayList();
    private final List<SourceProvider> mFlavorSourceProviders = Lists.newArrayList();

    /** Variant specific source provider, may be null */
    @Nullable
    private SourceProvider mVariantSourceProvider;

    /** MultiFlavors specific source provider, may be null */
    @Nullable
    private SourceProvider mMultiFlavorSourceProvider;

    @NonNull
    private final VariantType mType;

    /**
     * Optional tested config in case this variant is used for testing another variant.
     *
     * @see VariantType#isForTesting()
     */
    private final VariantConfiguration<T, D, F> mTestedConfig;

    /**
     * An optional output that is only valid if the type is Type#LIBRARY so that the test
     * for the library can use the library as if it was a normal dependency.
     */
    private AndroidLibrary mOutput;

    @NonNull
    private ProductFlavor mMergedFlavor;

    private DependencyContainer mFlatCompileDependencies;
    private DependencyContainer mFlatPackageDependencies;
    private DependencyContainer mCompileDependencies;
    private DependencyContainer mPackageDependencies;

    /**
     * Variant-specific build Config fields.
     */
    private final Map<String, ClassField> mBuildConfigFields = Maps.newTreeMap();

    /**
     * Variant-specific res values.
     */
    private final Map<String, ClassField> mResValues = Maps.newTreeMap();

    /**
     * Signing Override to be used instead of any signing config provided by Build Type or
     * Product Flavors.
     */
    private final SigningConfig mSigningConfigOverride;

    /**
     * For reading the attributes from the main manifest file in the default source set.
     */
    private ManifestAttributeSupplier mManifestAttributeSupplier = null;

    /**
     * Creates the configuration with the base source sets for a given {@link VariantType}. Meant
     * for non-testing variants.
     *
     * @param defaultConfig the default configuration. Required.
     * @param defaultSourceProvider the default source provider. Required
     * @param buildType the build type for this variant. Required.
     * @param buildTypeSourceProvider the source provider for the build type.
     * @param type the type of the project.
     * @param signingConfigOverride an optional Signing override to be used for signing.
     */
    public VariantConfiguration(
            @NonNull D defaultConfig,
            @NonNull SourceProvider defaultSourceProvider,
            @NonNull T buildType,
            @Nullable SourceProvider buildTypeSourceProvider,
            @NonNull VariantType type,
            @Nullable SigningConfig signingConfigOverride) {
        this(
                defaultConfig, defaultSourceProvider,
                buildType, buildTypeSourceProvider,
                type, null /*testedConfig*/, signingConfigOverride);
    }

    /**
     * Creates the configuration with the base source sets, and an optional tested variant.
     *
     * @param defaultConfig the default configuration. Required.
     * @param defaultSourceProvider the default source provider. Required
     * @param buildType the build type for this variant. Required.
     * @param buildTypeSourceProvider the source provider for the build type.
     * @param type the type of the project.
     * @param testedConfig the reference to the tested project. Required if type is Type.ANDROID_TEST
     * @param signingConfigOverride an optional Signing override to be used for signing.
     */
    public VariantConfiguration(
            @NonNull D defaultConfig,
            @NonNull SourceProvider defaultSourceProvider,
            @NonNull T buildType,
            @Nullable SourceProvider buildTypeSourceProvider,
            @NonNull VariantType type,
            @Nullable VariantConfiguration<T, D, F> testedConfig,
            @Nullable SigningConfig signingConfigOverride) {
        checkNotNull(defaultConfig);
        checkNotNull(defaultSourceProvider);
        checkNotNull(buildType);
        checkNotNull(type);
        checkArgument(
                !type.isForTesting() || testedConfig != null,
                "You have to specify the tested variant for this variant type.");
        checkArgument(
                type.isForTesting() || testedConfig == null,
                "This variant type doesn't need a tested variant.");

        mDefaultConfig = checkNotNull(defaultConfig);
        mDefaultSourceProvider = checkNotNull(defaultSourceProvider);
        mBuildType = checkNotNull(buildType);
        mBuildTypeSourceProvider = buildTypeSourceProvider;
        mType = checkNotNull(type);
        mTestedConfig = testedConfig;
        mSigningConfigOverride = signingConfigOverride;
        mMergedFlavor = DefaultProductFlavor.clone(mDefaultConfig);
    }

    /**
     * Returns the full, unique name of the variant in camel case (starting with a lower case),
     * including BuildType, Flavors and Test (if applicable).
     *
     * @return the name of the variant
     */
    @NonNull
    public String getFullName() {
        if (mFullName == null) {
            mFullName = computeFullName(getFlavorName(), mBuildType, mType);
        }

        return mFullName;
    }

    /**
     * Returns the full, unique name of the variant in camel case (starting with a lower case),
     * including BuildType, Flavors and Test (if applicable).
     *
     * @param flavorName the flavor name, as computed by {@link #computeFlavorName(List)}
     * @param buildType the build type
     * @param type the variant type
     *
     * @return the name of the variant
     */
    @NonNull
    public static <B extends BuildType> String computeFullName(
            @NonNull String flavorName,
            @NonNull B buildType,
            @NonNull VariantType type) {
        StringBuilder sb = new StringBuilder();
        if (!flavorName.isEmpty()) {
            sb.append(flavorName);
            sb.append(StringHelper.capitalize(buildType.getName()));
        } else {
            sb.append(buildType.getName());
        }

        if (type.isForTesting()) {
            sb.append(type.getSuffix());
        }
        return sb.toString();
    }

    /**
     * Returns a full name that includes the given splits name.
     * @param splitName the split name
     * @return a unique name made up of the variant and split names.
     */
    @NonNull
    public String computeFullNameWithSplits(@NonNull String splitName) {
        StringBuilder sb = new StringBuilder();
        String flavorName = getFlavorName();
        if (!flavorName.isEmpty()) {
            sb.append(flavorName);
            sb.append(StringHelper.capitalize(splitName));
        } else {
            sb.append(splitName);
        }

        sb.append(StringHelper.capitalize(mBuildType.getName()));

        if (mType.isForTesting()) {
            sb.append(mType.getSuffix());
        }

        return sb.toString();
    }

    /**
     * Returns the flavor name of the variant, including all flavors in camel case (starting
     * with a lower case). If the variant has no flavor, then an empty string is returned.
     *
     * @return the flavor name or an empty string.
     */
    @NonNull
    public String getFlavorName() {
        if (mFlavorName == null) {
            mFlavorName = computeFlavorName(mFlavors);
        }

        return mFlavorName;
    }

    /**
     * Returns the flavor name for a variant composed of the given flavors, including all
     * flavor names in camel case (starting with a lower case).
     *
     * If the flavor list is empty, then an empty string is returned.
     *
     * @param flavors the list of flavors
     * @return the flavor name or an empty string.
     */
    public static <F extends ProductFlavor> String computeFlavorName(@NonNull List<F> flavors) {
        if (flavors.isEmpty()) {
            return "";
        } else {
            StringBuilder sb = new StringBuilder(flavors.size() * 10);
            boolean first = true;
            for (F flavor : flavors) {
                sb.append(first ? flavor.getName() : StringHelper.capitalize(flavor.getName()));
                first = false;
            }

            return sb.toString();
        }
    }

    /**
     * Returns the full, unique name of the variant, including BuildType, flavors and test,
     * dash separated. (similar to full name but with dashes)
     *
     * @return the name of the variant
     */
    @NonNull
    public String getBaseName() {
        if (mBaseName == null) {
            StringBuilder sb = new StringBuilder();

            if (!mFlavors.isEmpty()) {
                for (ProductFlavor pf : mFlavors) {
                    sb.append(pf.getName()).append('-');
                }
            }

            sb.append(mBuildType.getName());

            if (mType.isForTesting()) {
                sb.append('-').append(mType.getPrefix());
            }

            mBaseName = sb.toString();
        }

        return mBaseName;
    }

    /**
     * Returns a base name that includes the given splits name.
     * @param splitName the split name
     * @return a unique name made up of the variant and split names.
     */
    @NonNull
    public String computeBaseNameWithSplits(@NonNull String splitName) {
        StringBuilder sb = new StringBuilder();

        if (!mFlavors.isEmpty()) {
            for (ProductFlavor pf : mFlavors) {
                sb.append(pf.getName()).append('-');
            }
        }

        sb.append(splitName).append('-');
        sb.append(mBuildType.getName());

        if (mType.isForTesting()) {
            sb.append('-').append(mType.getPrefix());
        }

        return sb.toString();
    }

    /**
     * Returns a unique directory name (can include multiple folders) for the variant,
     * based on build type, flavor and test.
     *
     * <p>This always uses forward slashes ('/') as separator on all platform.
     *
     * @return the directory name for the variant
     */
    @NonNull
    public String getDirName() {
        if (mDirName == null) {
            StringBuilder sb = new StringBuilder();

            if (mType.isForTesting()) {
                sb.append(mType.getPrefix()).append("/");
            }

            if (!mFlavors.isEmpty()) {
                boolean first = true;
                for (F flavor : mFlavors) {
                    sb.append(first ? flavor.getName() : StringHelper.capitalize(flavor.getName()));
                    first = false;
                }

                sb.append('/').append(mBuildType.getName());

            } else {
                sb.append(mBuildType.getName());
            }

            mDirName = sb.toString();

        }

        return mDirName;
    }

    /**
     * Returns a unique directory name (can include multiple folders) for the variant,
     * based on build type, flavor and test.
     *
     * @return the directory name for the variant
     */
    @NonNull
    public Collection<String> getDirectorySegments() {
        if (mDirSegments == null) {
            ImmutableList.Builder<String> builder = ImmutableList.builder();

            if (mType.isForTesting()) {
                builder.add(mType.getPrefix());
            }

            if (!mFlavors.isEmpty()) {
                StringBuilder sb = new StringBuilder(mFlavors.size() * 10);
                for (F flavor : mFlavors) {
                    StringHelper.appendCamelCase(sb, flavor.getName());
                }
                builder.add(sb.toString());

                builder.add(mBuildType.getName());

            } else {
                builder.add(mBuildType.getName());
            }

            mDirSegments = builder.build();
        }

        return mDirSegments;
    }
    /**
     * Returns a unique directory name (can include multiple folders) for the variant,
     * based on build type, flavor and test, and splits.
     *
     * <p>This always uses forward slashes ('/') as separator on all platform.
     *
     * @return the directory name for the variant
     */
    @NonNull
    public String computeDirNameWithSplits(@NonNull String... splitNames) {
        StringBuilder sb = new StringBuilder();

        if (mType.isForTesting()) {
            sb.append(mType.getPrefix()).append("/");
        }

        if (!mFlavors.isEmpty()) {
            for (F flavor : mFlavors) {
                sb.append(flavor.getName());
            }

            sb.append('/');
        }

        for (String splitName : splitNames) {
            if (splitName != null) {
                sb.append(splitName).append('/');
            }
        }

        sb.append(mBuildType.getName());

        return sb.toString();
    }

    /**
     * Return the names of the applied flavors.
     *
     * The list contains the dimension names as well.
     *
     * @return the list, possibly empty if there are no flavors.
     */
    @NonNull
    public List<String> getFlavorNamesWithDimensionNames() {
        if (mFlavors.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> names;
        int count = mFlavors.size();

        if (count > 1) {
            names = Lists.newArrayListWithCapacity(count * 2);

            for (int i = 0 ; i < count ; i++) {
                names.add(mFlavors.get(i).getName());
                names.add(mFlavorDimensionNames.get(i));
            }

        } else {
            names = Collections.singletonList(mFlavors.get(0).getName());
        }

        return names;
    }


    /**
     * Add a new configured ProductFlavor.
     *
     * If multiple flavors are added, the priority follows the order they are added when it
     * comes to resolving Android resources overlays (ie earlier added flavors supersedes
     * latter added ones).
     *
     * @param productFlavor the configured product flavor
     * @param sourceProvider the source provider for the product flavor
     * @param dimensionName the name of the dimension associated with the flavor
     *
     * @return the config object
     */
    @NonNull
    public VariantConfiguration addProductFlavor(
            @NonNull F productFlavor,
            @NonNull SourceProvider sourceProvider,
            @NonNull String dimensionName) {

        mFlavors.add(productFlavor);
        mFlavorSourceProviders.add(sourceProvider);
        mFlavorDimensionNames.add(dimensionName);

        if (mFlavors.size() == 1) {
            mMergedFlavor =
                    DefaultProductFlavor.mergeWithDefaultConfig(mMergedFlavor, productFlavor);
        } else {
            mMergedFlavor =
                    DefaultProductFlavor.mergeFlavors(productFlavor, mMergedFlavor);
        }

        return this;
    }

    /**
     * Sets the variant-specific source provider.
     * @param sourceProvider the source provider for the product flavor
     *
     * @return the config object
     */
    public VariantConfiguration setVariantSourceProvider(@Nullable SourceProvider sourceProvider) {
        mVariantSourceProvider = sourceProvider;
        return this;
    }

    /**
     * Sets the variant-specific source provider.
     * @param sourceProvider the source provider for the product flavor
     *
     * @return the config object
     */
    public VariantConfiguration setMultiFlavorSourceProvider(@Nullable SourceProvider sourceProvider) {
        mMultiFlavorSourceProvider = sourceProvider;
        return this;
    }

    /**
     * Returns the variant specific source provider
     * @return the source provider or null if none has been provided.
     */
    @Nullable
    public SourceProvider getVariantSourceProvider() {
        return mVariantSourceProvider;
    }

    @Nullable
    public SourceProvider getMultiFlavorSourceProvider() {
        return mMultiFlavorSourceProvider;
    }

    /**
     * Sets the dependencies
     *
     * @param compileDependencies the compile dependencies
     * @param packageDependencies the package dependencies
     * @return the config object
     */
    @NonNull
    public VariantConfiguration setDependencies(
            @NonNull DependencyContainer compileDependencies,
            @NonNull DependencyContainer packageDependencies) {
        // Output of mTestedConfig will not be initialized until the tasks for the tested config are
        // created.  If library output has never been added to mDirectLibraries, checked the output
        // of the mTestedConfig to see if the tasks are now created.
        AndroidLibrary testedLib = null;
        if (mTestedConfig != null &&
                mTestedConfig.mType == VariantType.LIBRARY &&
                mTestedConfig.mOutput != null) {
            testedLib = mTestedConfig.mOutput;
        }

        mCompileDependencies = compileDependencies;
        //noinspection VariableNotUsedInsideIf
        mFlatCompileDependencies = compileDependencies.flatten(
                testedLib,
                testedLib != null ? mTestedConfig.getCompileDependencies() : null);

        mPackageDependencies = packageDependencies;
        //noinspection VariableNotUsedInsideIf
        mFlatPackageDependencies = packageDependencies.flatten(
                testedLib,
                testedLib != null ? mTestedConfig.getPackageDependencies() : null);

        return this;
    }

    @NonNull
    public DependencyContainer getCompileDependencies() {
        return mCompileDependencies;
    }

    @NonNull
    public DependencyContainer getPackageDependencies() {
        return mPackageDependencies;
    }

    /**
     * Sets the output of this variant. This is required when the variant is a library so that
     * the variant that tests this library can properly include the tested library in its own
     * package.
     *
     * @param output the output of the library as an AndroidLibrary that will provides the
     *               location of all the created items.
     * @return the config object
     */
    @NonNull
    public VariantConfiguration setOutput(AndroidLibrary output) {
        mOutput = output;
        return this;
    }

    /**
     * Returns the {@link AndroidLibrary} that this library variant produces. Used so that
     * related test variants can use it as a dependency. Returns null if this is not a library
     * variant.
     *
     * @see #mOutput
     */
    @Nullable
    public AndroidLibrary getOutput() {
        return mOutput;
    }

    @NonNull
    public D getDefaultConfig() {
        return mDefaultConfig;
    }

    @NonNull
    public SourceProvider getDefaultSourceSet() {
        return mDefaultSourceProvider;
    }

    @NonNull
    public ProductFlavor getMergedFlavor() {
        return mMergedFlavor;
    }

    @NonNull
    public T getBuildType() {
        return mBuildType;
    }

    /**
     * The SourceProvider for the BuildType. Can be null.
     */
    @Nullable
    public SourceProvider getBuildTypeSourceSet() {
        return mBuildTypeSourceProvider;
    }

    public boolean hasFlavors() {
        return !mFlavors.isEmpty();
    }

    /**
     * Returns the product flavors. Items earlier in the list override later items.
     */
    @NonNull
    public List<F> getProductFlavors() {
        return mFlavors;
    }

    /**
     * Returns the list of SourceProviders for the flavors.
     *
     * The list is ordered from higher priority to lower priority.
     *
     * @return the list of Source Providers for the flavors. Never null.
     */
    @NonNull
    public List<SourceProvider> getFlavorSourceProviders() {
        return mFlavorSourceProviders;
    }

    /**
     * Returns the Android library dependency graph
     */
    @NonNull
    public List<AndroidLibrary> getCompileAndroidLibraries() {
        return mFlatCompileDependencies.getAndroidDependencies();
    }

    /**
     * Returns all the library dependencies, direct and transitive in a single flat list.
     */
    @NonNull
    public List<AndroidLibrary> getFlatPackageAndroidLibraries() {
        return mFlatPackageDependencies.getAndroidDependencies();
    }

    @NonNull
    public VariantType getType() {
        return mType;
    }

    @Nullable
    public VariantConfiguration getTestedConfig() {
        return mTestedConfig;
    }


    /**
     * Returns the original application ID before any overrides from flavors.
     * If the variant is a test variant, then the application ID is the one coming from the
     * configuration of the tested variant, and this call is similar to {@link #getApplicationId()}
     * @return the original application ID
     */
    @Nullable
    public String getOriginalApplicationId() {
        if (mType.isForTesting()) {
            return getApplicationId();
        }

        return getPackageFromManifest();
    }

    /**
     * Returns the application ID for this variant. This could be coming from the manifest or
     * could be overridden through the product flavors and/or the build type.
     * @return the application ID
     */
    @NonNull
    public String getApplicationId() {
        String id;

        if (mType.isForTesting()) {
            checkState(mTestedConfig != null);

            id = mMergedFlavor.getTestApplicationId();
            String testedPackage = mTestedConfig.getApplicationId();
            if (id == null) {
                id = testedPackage + ".test";
            } else {
                if (id.equals(testedPackage)) {
                    throw new RuntimeException(String.format("Application and test application id "
                                    + "cannot be the same: both are '%s' for %s",
                            id, getFullName()));
                }
            }

        } else {
            // first get package override.
            id = getIdOverride();
            // if it's null, this means we just need the default package
            // from the manifest since both flavor and build type do nothing.
            if (id == null) {
                id = getPackageFromManifest();
            }
        }

        if (id == null) {
            throw new RuntimeException("Failed to get application id for " + getFullName());
        }

        return id;
    }

    @NonNull
    public String getTestApplicationId(){
        checkState(mType.isForTesting());

        if (!Strings.isNullOrEmpty(mMergedFlavor.getTestApplicationId())) {
            // if it's specified through build file read from there
            return mMergedFlavor.getTestApplicationId();
        } else {
            // otherwise getApplicationId() contains rules for getting the
            // applicationId for the test app from the tested application
            return getApplicationId();
        }
    }

    @Nullable
    public String getTestedApplicationId() {
        if (mType.isForTesting()) {
            checkState(mTestedConfig != null);
            if (mTestedConfig.mType == VariantType.LIBRARY) {
                return getApplicationId();
            } else {
                return mTestedConfig.getApplicationId();
            }
        }

        return null;
    }

    /**
     * Returns the application id override value coming from the Product Flavor and/or the
     * Build Type. If the package/id is not overridden then this returns null.
     *
     * @return the id override or null
     */
    @Nullable
    public String getIdOverride() {
        String idName = mMergedFlavor.getApplicationId();

        String idSuffix = DefaultProductFlavor.mergeApplicationIdSuffix(
                mBuildType.getApplicationIdSuffix(), mMergedFlavor.getApplicationIdSuffix());

        if (!idSuffix.isEmpty()) {
            if (idName == null) {
                idName = getPackageFromManifest();
            }

            if (idSuffix.charAt(0) == '.') {
                idName = idName + idSuffix;
            } else {
                idName = idName + '.' + idSuffix;
            }
        }

        return idName;
    }

    /**
     * Returns the version name for this variant. This could be coming from the manifest or
     * could be overridden through the product flavors, and can have a suffix specified by
     * the build type.
     *
     * @return the version name
     */
    @Nullable
    public String getVersionName() {
        String versionName = mMergedFlavor.getVersionName();
        String versionSuffix = mMergedFlavor.getVersionNameSuffix();

        if (versionName == null && !mType.isForTesting()) {
            versionName = getVersionNameFromManifest();
        }

        versionSuffix = DefaultProductFlavor.mergeVersionNameSuffix(
                mBuildType.getVersionNameSuffix(), versionSuffix);

        if (versionSuffix != null && !versionSuffix.isEmpty()) {
            versionName = Strings.nullToEmpty(versionName) + versionSuffix;
        }

        return versionName;
    }

    /**
     * Returns the version code for this variant. This could be coming from the manifest or
     * could be overridden through the product flavors, and can have a suffix specified by
     * the build type.
     *
     * @return the version code or -1 if there was non defined.
     */
    public int getVersionCode() {
        int versionCode = mMergedFlavor.getVersionCode() != null ?
                mMergedFlavor.getVersionCode() : -1;

        if (versionCode == -1 && !mType.isForTesting()) {
            versionCode = getVersionCodeFromManifest();
        }

        return versionCode;
    }

    private static final String DEFAULT_TEST_RUNNER = "android.test.InstrumentationTestRunner";
    private static final String MULTIDEX_TEST_RUNNER = "com.android.test.runner.MultiDexTestRunner";
    private static final Boolean DEFAULT_HANDLE_PROFILING = false;
    private static final Boolean DEFAULT_FUNCTIONAL_TEST = false;

    /**
     * Returns the instrumentationRunner to use to test this variant, or if the
     * variant is a test, the one to use to test the tested variant.
     * @return the instrumentation test runner name
     */
    @NonNull
    public String getInstrumentationRunner() {
        VariantConfiguration config = this;
        if (mType.isForTesting()) {
            config = getTestedConfig();
            checkState(config != null);
        }
        String runner = config.mMergedFlavor.getTestInstrumentationRunner();
        if (runner != null) {
            return runner;
        }

        runner = getInstrumentationRunnerFromManifest();
        if (runner != null){
            return runner;
        }

        if (isMultiDexEnabled() && isLegacyMultiDexMode()) {
            return MULTIDEX_TEST_RUNNER;
        }

        return DEFAULT_TEST_RUNNER;
    }

    /**
     * Returns the instrumentationRunner arguments to use to test this variant, or if the
     * variant is a test, the ones to use to test the tested variant
     */
    @NonNull
    public Map<String, String> getInstrumentationRunnerArguments() {
        VariantConfiguration config = this;
        if (mType.isForTesting()) {
            config = getTestedConfig();
            checkState(config != null);
        }
        return config.mMergedFlavor.getTestInstrumentationRunnerArguments();
    }

    /**
     * Returns handleProfiling value to use to test this variant, or if the
     * variant is a test, the one to use to test the tested variant.
     *
     * @return the handleProfiling value
     */
    @NonNull
    public Boolean getHandleProfiling() {
        VariantConfiguration config = this;
        if (mType.isForTesting()) {
            config = getTestedConfig();
            checkState(config != null);
        }
        Boolean handleProfiling = config.mMergedFlavor.getTestHandleProfiling();
        if (handleProfiling == null){
            handleProfiling = getHandleProfilingFromManifest();
        }
        return handleProfiling != null ? handleProfiling : DEFAULT_HANDLE_PROFILING;
    }

    /**
     * Returns functionalTest value to use to test this variant, or if the
     * variant is a test, the one to use to test the tested variant.
     *
     * @return the functionalTest value
     */
    @NonNull
    public Boolean getFunctionalTest() {
        VariantConfiguration config = this;
        if (mType.isForTesting()) {
            config = getTestedConfig();
            checkState(config != null);
        }
        Boolean functionalTest = config.mMergedFlavor.getTestFunctionalTest();
        if (functionalTest == null){
            functionalTest = getFunctionalTestFromManifest();
        }

        return functionalTest != null ? functionalTest : DEFAULT_FUNCTIONAL_TEST;
    }

    /** Gets the test label for this variant */
    @Nullable
    public String getTestLabel(){
        return getTestLabelFromManifest();
    }

    /**
     * Reads the package name from the manifest. This is unmodified by the build type.
     */
    @Nullable
    public String getPackageFromManifest() {
        checkState(!mType.isForTesting());

        String packageName = getManifestAttributeSupplier().getPackage();
        if (packageName == null) {
            throw new RuntimeException(String.format("Cannot read packageName from %1$s",
                    mDefaultSourceProvider.getManifestFile().getAbsolutePath()));
        }
        return packageName;
    }

    @Nullable
    public String getVersionNameFromManifest() {
        return getManifestAttributeSupplier().getVersionName();
    }

    public int getVersionCodeFromManifest() {
        return getManifestAttributeSupplier().getVersionCode();
    }

    @Nullable
    public String getTestedApplicationIdFromManifest(){
        return getManifestAttributeSupplier().getTargetPackage();
    }

    @Nullable
    public String getInstrumentationRunnerFromManifest(){
        return getManifestAttributeSupplier().getInstrumentationRunner();
    }

    @Nullable
    public Boolean getFunctionalTestFromManifest(){
        return getManifestAttributeSupplier().getFunctionalTest();
    }

    @Nullable
    public Boolean getHandleProfilingFromManifest(){
        return getManifestAttributeSupplier().getHandleProfiling();
    }

    @Nullable
    public String getTestLabelFromManifest(){
        return getManifestAttributeSupplier().getTestLabel();
    }

    /**
     * Return the minSdkVersion for this variant.
     *
     * This uses both the value from the manifest (if present), and the override coming
     * from the flavor(s) (if present). The value of the minSdkVersion will be combined with
     * the value of the targetSdkVersion. For the details of the overlaying logic check
     * {@link #getCalculatedApiVersions(ApiVersion, ApiVersion)} method.
     *
     * @return the minSdkVersion
     */
    @NonNull
    public ApiVersion getMinSdkVersion() {
        if (mTestedConfig != null) {
            return mTestedConfig.getMinSdkVersion();
        }

        return getApiVersionsNonTestVariant().minSdkVersion;
    }

    /**
     * Return the targetSdkVersion for this variant.
     *
     * This uses both the value from the manifest (if present), and the override coming
     * from the flavor(s) (if present). The value of the targetSdkVersion will be combined with
     * the value of the minSdkVersion. For the details of the overlaying logic check
     * {@link #getCalculatedApiVersions(ApiVersion, ApiVersion)} method.
     *
     * @return the targetSdkVersion
     */
    @NonNull
    public ApiVersion getTargetSdkVersion() {
        if (mTestedConfig != null) {
            return mTestedConfig.getTargetSdkVersion();
        }

        return getApiVersionsNonTestVariant().targetSdkVersion;
    }

    /** Gets api versions for this variant that might be correlated, such as min and target sdk */
    private ApiVersions getApiVersionsNonTestVariant() {
        ApiVersion minSdkVersion = mMergedFlavor.getMinSdkVersion();
        if (minSdkVersion == null) {
            // read it from the main manifest
            minSdkVersion = DefaultApiVersion.create(
                    getManifestAttributeSupplier().getMinSdkVersion());
        }

        ApiVersion targetSdkVersion = mMergedFlavor.getTargetSdkVersion();
        if (targetSdkVersion == null) {
            // read it from the main manifest
            targetSdkVersion = DefaultApiVersion.create(
                    getManifestAttributeSupplier().getTargetSdkVersion());
        }

        return getCalculatedApiVersions(minSdkVersion, targetSdkVersion);
    }

    /**
     * Api versions for this variant.
     */
    public static class ApiVersions {
        public final ApiVersion minSdkVersion;
        public final ApiVersion targetSdkVersion;

        public ApiVersions(ApiVersion minSdkVersion, ApiVersion targetSdkVersion) {
            this.minSdkVersion = minSdkVersion;
            this.targetSdkVersion = targetSdkVersion;
        }
    }

    /**
     * Calculates the minimum sdk and target sdk versions using the following rules:
     * <ul>
     *     <li>If none of the versions is a preview version, it keeps the values as-is.
     *     <li>If one of the values is a preview version, it will pick the smallest one
     *     using string comparison.
     * </ul>
     *
     * @param minSdkVersion min sdk version for the variant
     * @param targetSdkVersion target sdk version for the variant
     * @return selected api version according to the rules outlined above
     */
    @NonNull
    public static ApiVersions getCalculatedApiVersions(
            @Nullable ApiVersion minSdkVersion, @Nullable ApiVersion targetSdkVersion) {
        List<ApiVersion> versions = Lists.newArrayList();
        if (minSdkVersion != null) {
            versions.add(minSdkVersion);
        }
        if (targetSdkVersion != null) {
            versions.add(targetSdkVersion);
        }

        Optional<ApiVersion> previewMinVersion =
                versions
                        .stream()
                        .filter(v -> v.getCodename() != null)
                        .sorted(Ordering.natural().onResultOf(ApiVersion::getCodename))
                        .findFirst();
        if (previewMinVersion.isPresent()) {
            return new ApiVersions(previewMinVersion.get(), previewMinVersion.get());
        } else {
            return new ApiVersions(minSdkVersion, targetSdkVersion);
        }
    }

    @Nullable
    public File getMainManifest() {
        File defaultManifest = mDefaultSourceProvider.getManifestFile();

        // this could not exist in a test project.
        if (defaultManifest.isFile()) {
            return defaultManifest;
        }

        return null;
    }

    /**
     * Returns a list of sorted SourceProvider in order of ascending order, meaning, the earlier
     * items are meant to be overridden by later items.
     *
     * @return a list of source provider
     */
    @NonNull
    public List<SourceProvider> getSortedSourceProviders() {
        List<SourceProvider> providers = Lists.newArrayList();

        // first the default source provider
        providers.add(mDefaultSourceProvider);

        // the list of flavor must be reversed to use the right overlay order.
        for (int n = mFlavorSourceProviders.size() - 1; n >= 0 ; n--) {
            providers.add(mFlavorSourceProviders.get(n));
        }

        // multiflavor specific overrides flavor
        if (mMultiFlavorSourceProvider != null) {
            providers.add(mMultiFlavorSourceProvider);
        }

        // build type overrides flavors
        if (mBuildTypeSourceProvider != null) {
            providers.add(mBuildTypeSourceProvider);
        }

        // variant specific overrides all
        if (mVariantSourceProvider != null) {
            providers.add(mVariantSourceProvider);
        }

        return providers;
    }

    @NonNull
    public List<File> getManifestOverlays() {
        List<File> inputs = Lists.newArrayList();

        if (mVariantSourceProvider != null) {
            File variantLocation = mVariantSourceProvider.getManifestFile();
            if (variantLocation.isFile()) {
                inputs.add(variantLocation);
            }
        }

        if (mBuildTypeSourceProvider != null) {
            File typeLocation = mBuildTypeSourceProvider.getManifestFile();
            if (typeLocation.isFile()) {
                inputs.add(typeLocation);
            }
        }

        if (mMultiFlavorSourceProvider != null) {
            File variantLocation = mMultiFlavorSourceProvider.getManifestFile();
            if (variantLocation.isFile()) {
                inputs.add(variantLocation);
            }
        }

        for (SourceProvider sourceProvider : mFlavorSourceProviders) {
            File f = sourceProvider.getManifestFile();
            if (f.isFile()) {
                inputs.add(f);
            }
        }

        return inputs;
    }

    /**
     * Returns the dynamic list of {@link ResourceSet} based on the configuration, its dependencies,
     * as well as tested config if applicable (test of a library).
     *
     * The list is ordered in ascending order of importance, meaning the first set is meant to be
     * overridden by the 2nd one and so on. This is meant to facilitate usage of the list in a
     * {@link com.android.ide.common.res2.ResourceMerger}.
     *
     * @param generatedResFolders a list of generated res folders
     * @param includeDependencies whether to include in the result the resources of the dependencies
     *
     * @return a list ResourceSet.
     */
    @NonNull
    public List<ResourceSet> getResourceSets(@NonNull List<File> generatedResFolders,
            boolean includeDependencies,
            boolean validateEnabled) {
        List<ResourceSet> resourceSets = Lists.newArrayList();

        // the list of dependency must be reversed to use the right overlay order.
        if (includeDependencies) {
            // use the package one to ignore the optional libs.
            for (AndroidLibrary dependency : mFlatPackageDependencies.getAndroidDependencies().reverse()) {
                File resFolder = dependency.getResFolder();
                if (resFolder.isDirectory()) {
                    ResourceSet resourceSet = new ResourceSet(dependency.getFolder().getName(),
                            dependency.getName(), validateEnabled);
                    resourceSet.addSource(resFolder);
                    resourceSet.setFromDependency(true);
                    resourceSets.add(resourceSet);
                }
            }
        }

        Collection<File> mainResDirs = mDefaultSourceProvider.getResDirectories();

        // the main + generated res folders are in the same ResourceSet
        ResourceSet resourceSet = new ResourceSet(BuilderConstants.MAIN, validateEnabled);
        resourceSet.addSources(mainResDirs);
        if (!generatedResFolders.isEmpty()) {
            for (File generatedResFolder : generatedResFolders) {
                resourceSet.addSource(generatedResFolder);

            }
        }
        resourceSets.add(resourceSet);

        // the list of flavor must be reversed to use the right overlay order.
        for (int n = mFlavorSourceProviders.size() - 1; n >= 0 ; n--) {
            SourceProvider sourceProvider = mFlavorSourceProviders.get(n);

            Collection<File> flavorResDirs = sourceProvider.getResDirectories();
            // we need the same of the flavor config, but it's in a different list.
            // This is fine as both list are parallel collections with the same number of items.
            resourceSet = new ResourceSet(sourceProvider.getName(), validateEnabled);
            resourceSet.addSources(flavorResDirs);
            resourceSets.add(resourceSet);
        }

        // multiflavor specific overrides flavor
        if (mMultiFlavorSourceProvider != null) {
            Collection<File> variantResDirs = mMultiFlavorSourceProvider.getResDirectories();
            resourceSet = new ResourceSet(getFlavorName(), validateEnabled);
            resourceSet.addSources(variantResDirs);
            resourceSets.add(resourceSet);
        }

        // build type overrides the flavors
        if (mBuildTypeSourceProvider != null) {
            Collection<File> typeResDirs = mBuildTypeSourceProvider.getResDirectories();
            resourceSet = new ResourceSet(mBuildType.getName(), validateEnabled);
            resourceSet.addSources(typeResDirs);
            resourceSets.add(resourceSet);
        }

        // variant specific overrides all
        if (mVariantSourceProvider != null) {
            Collection<File> variantResDirs = mVariantSourceProvider.getResDirectories();
            resourceSet = new ResourceSet(getFullName(), validateEnabled);
            resourceSet.addSources(variantResDirs);
            resourceSets.add(resourceSet);
        }

        return resourceSets;
    }

    /**
     * Returns the dynamic list of {@link AssetSet} based on the configuration, its dependencies,
     * as well as tested config if applicable (test of a library).
     *
     * The list is ordered in ascending order of importance, meaning the first set is meant to be
     * overridden by the 2nd one and so on. This is meant to facilitate usage of the list in a
     * {@link com.android.ide.common.res2.AssetMerger}.
     *
     * @return a list ResourceSet.
     */
    @NonNull
    public List<AssetSet> getAssetSets(
            @NonNull List<File> generatedAssetFolders,
            boolean includeDependencies) {
        List<AssetSet> assetSets = Lists.newArrayList();

        if (includeDependencies) {
            // use the package one to ignore the optional libs.
            List<AndroidLibrary> flatLibs = mFlatPackageDependencies.getAndroidDependencies();
            // the list of dependency must be reversed to use the right overlay order.
            for (int n = flatLibs.size() - 1 ; n >= 0 ; n--) {
                AndroidLibrary dependency = flatLibs.get(n);
                File assetFolder = dependency.getAssetsFolder();
                if (assetFolder.isDirectory()) {
                    AssetSet assetSet = new AssetSet(dependency.getFolder().getName());
                    assetSet.addSource(assetFolder);
                    assetSets.add(assetSet);
                }
            }
        }

        Collection<File> mainResDirs = mDefaultSourceProvider.getAssetsDirectories();

        // the main + generated asset folders are in the same AssetSet
        AssetSet assetSet = new AssetSet(BuilderConstants.MAIN);
        assetSet.addSources(mainResDirs);
        if (!generatedAssetFolders.isEmpty()) {
            for (File generatedResFolder : generatedAssetFolders) {
                assetSet.addSource(generatedResFolder);
            }
        }
        assetSets.add(assetSet);

        // the list of flavor must be reversed to use the right overlay order.
        for (int n = mFlavorSourceProviders.size() - 1; n >= 0 ; n--) {
            SourceProvider sourceProvider = mFlavorSourceProviders.get(n);

            Collection<File> flavorResDirs = sourceProvider.getAssetsDirectories();
            // we need the same of the flavor config, but it's in a different list.
            // This is fine as both list are parallel collections with the same number of items.
            assetSet = new AssetSet(mFlavors.get(n).getName());
            assetSet.addSources(flavorResDirs);
            assetSets.add(assetSet);
        }

        // multiflavor specific overrides flavor
        if (mMultiFlavorSourceProvider != null) {
            Collection<File> variantResDirs = mMultiFlavorSourceProvider.getAssetsDirectories();
            assetSet = new AssetSet(getFlavorName());
            assetSet.addSources(variantResDirs);
            assetSets.add(assetSet);
        }

        // build type overrides flavors
        if (mBuildTypeSourceProvider != null) {
            Collection<File> typeResDirs = mBuildTypeSourceProvider.getAssetsDirectories();
            assetSet = new AssetSet(mBuildType.getName());
            assetSet.addSources(typeResDirs);
            assetSets.add(assetSet);
        }

        // variant specific overrides all
        if (mVariantSourceProvider != null) {
            Collection<File> variantResDirs = mVariantSourceProvider.getAssetsDirectories();
            assetSet = new AssetSet(getFullName());
            assetSet.addSources(variantResDirs);
            assetSets.add(assetSet);
        }

        return assetSets;
    }

    /**
     * Returns the dynamic list of {@link AssetSet} based on the configuration, its dependencies,
     * as well as tested config if applicable (test of a library).
     *
     * The list is ordered in ascending order of importance, meaning the first set is meant to be
     * overridden by the 2nd one and so on. This is meant to facilitate usage of the list in a
     * {@link com.android.ide.common.res2.AssetMerger}.
     *
     * @return a list ResourceSet.
     */
    @NonNull
    public List<AssetSet> getJniLibsSets() {
        List<AssetSet> jniSets = Lists.newArrayList();

        Collection<File> mainJniLibsDirs = mDefaultSourceProvider.getJniLibsDirectories();

        // the main + generated asset folders are in the same AssetSet
        AssetSet jniSet = new AssetSet(BuilderConstants.MAIN);
        jniSet.addSources(mainJniLibsDirs);
        jniSets.add(jniSet);

        // the list of flavor must be reversed to use the right overlay order.
        for (int n = mFlavorSourceProviders.size() - 1; n >= 0 ; n--) {
            SourceProvider sourceProvider = mFlavorSourceProviders.get(n);

            Collection<File> flavorJniDirs = sourceProvider.getJniLibsDirectories();
            // we need the same of the flavor config, but it's in a different list.
            // This is fine as both list are parallel collections with the same number of items.
            jniSet = new AssetSet(mFlavors.get(n).getName());
            jniSet.addSources(flavorJniDirs);
            jniSets.add(jniSet);
        }

        // multiflavor specific overrides flavor
        if (mMultiFlavorSourceProvider != null) {
            Collection<File> variantJniDirs = mMultiFlavorSourceProvider.getJniLibsDirectories();
            jniSet = new AssetSet(getFlavorName());
            jniSet.addSources(variantJniDirs);
            jniSets.add(jniSet);
        }

        // build type overrides flavors
        if (mBuildTypeSourceProvider != null) {
            Collection<File> typeJniDirs = mBuildTypeSourceProvider.getJniLibsDirectories();
            jniSet = new AssetSet(mBuildType.getName());
            jniSet.addSources(typeJniDirs);
            jniSets.add(jniSet);
        }

        // variant specific overrides all
        if (mVariantSourceProvider != null) {
            Collection<File> variantJniDirs = mVariantSourceProvider.getJniLibsDirectories();
            jniSet = new AssetSet(getFullName());
            jniSet.addSources(variantJniDirs);
            jniSets.add(jniSet);
        }

        return jniSets;
    }

    /**
     * Returns the dynamic list of {@link AssetSet} based on the configuration, its dependencies,
     * as well as tested config if applicable (test of a library).
     *
     * The list is ordered in ascending order of importance, meaning the first set is meant to be
     * overridden by the 2nd one and so on. This is meant to facilitate usage of the list in a
     * {@link com.android.ide.common.res2.AssetMerger}.
     *
     * @return a list ResourceSet.
     */
    @NonNull
    public List<AssetSet> getShaderSets() {
        List<AssetSet> shaderSets = Lists.newArrayList();

        Collection<File> mainShaderDirs = mDefaultSourceProvider.getShadersDirectories();

        // the main + generated asset folders are in the same AssetSet
        AssetSet shaderSet = new AssetSet(BuilderConstants.MAIN);
        shaderSet.addSources(mainShaderDirs);
        shaderSets.add(shaderSet);

        // the list of flavor must be reversed to use the right overlay order.
        for (int n = mFlavorSourceProviders.size() - 1; n >= 0 ; n--) {
            SourceProvider sourceProvider = mFlavorSourceProviders.get(n);

            Collection<File> flavorJniDirs = sourceProvider.getShadersDirectories();
            // we need the same of the flavor config, but it's in a different list.
            // This is fine as both list are parallel collections with the same number of items.
            shaderSet = new AssetSet(mFlavors.get(n).getName());
            shaderSet.addSources(flavorJniDirs);
            shaderSets.add(shaderSet);
        }

        // multiflavor specific overrides flavor
        if (mMultiFlavorSourceProvider != null) {
            Collection<File> variantJniDirs = mMultiFlavorSourceProvider.getShadersDirectories();
            shaderSet = new AssetSet(getFlavorName());
            shaderSet.addSources(variantJniDirs);
            shaderSets.add(shaderSet);
        }

        // build type overrides flavors
        if (mBuildTypeSourceProvider != null) {
            Collection<File> typeJniDirs = mBuildTypeSourceProvider.getShadersDirectories();
            shaderSet = new AssetSet(mBuildType.getName());
            shaderSet.addSources(typeJniDirs);
            shaderSets.add(shaderSet);
        }

        // variant specific overrides all
        if (mVariantSourceProvider != null) {
            Collection<File> variantJniDirs = mVariantSourceProvider.getShadersDirectories();
            shaderSet = new AssetSet(getFullName());
            shaderSet.addSources(variantJniDirs);
            shaderSets.add(shaderSet);
        }

        return shaderSets;
    }

    public int getRenderscriptTarget() {
        ProductFlavor mergedFlavor = getMergedFlavor();

        int targetApi = mergedFlavor.getRenderscriptTargetApi() != null ?
                mergedFlavor.getRenderscriptTargetApi() : -1;
        ApiVersion apiVersion = getMinSdkVersion();
        int minSdk = apiVersion.getApiLevel();
        if (apiVersion.getCodename() != null) {
            minSdk = SdkVersionInfo.getApiByBuildCode(apiVersion.getCodename(), true);
        }

        return targetApi > minSdk ? targetApi : minSdk;
    }

    /**
     * Returns all the renderscript import folder that are outside of the current project.
     */
    @NonNull
    public List<File> getRenderscriptImports() {
        List<File> list = Lists.newArrayList();

        // use the package one to ignore the optional libs.
        for (AndroidLibrary lib : mFlatPackageDependencies.getAndroidDependencies()) {
            File rsLib = lib.getRenderscriptFolder();
            if (rsLib.isDirectory()) {
                list.add(rsLib);
            }
        }

        return list;
    }

    /**
     * Returns all the renderscript source folder from the main config, the flavors and the
     * build type.
     *
     * @return a list of folders.
     */
    @NonNull
    public List<File> getRenderscriptSourceList() {
        List<SourceProvider> providers = getSortedSourceProviders();

        List<File> sourceList = Lists.newArrayListWithExpectedSize(providers.size());

        for (SourceProvider provider : providers) {
            sourceList.addAll(provider.getRenderscriptDirectories());
        }

        return sourceList;
    }

    /**
     * Returns all the aidl import folder that are outside of the current project.
     */
    @NonNull
    public List<File> getAidlImports() {
        List<File> list = Lists.newArrayList();

        // use the package one to ignore the optional libs.
        for (AndroidLibrary lib : mFlatPackageDependencies.getAndroidDependencies()) {
            File aidlLib = lib.getAidlFolder();
            if (aidlLib.isDirectory()) {
                list.add(aidlLib);
            }
        }

        return list;
    }

    @NonNull
    public List<File> getAidlSourceList() {
        List<SourceProvider> providers = getSortedSourceProviders();

        List<File> sourceList = Lists.newArrayListWithExpectedSize(providers.size());

        for (SourceProvider provider : providers) {
            sourceList.addAll(provider.getAidlDirectories());
        }

        return sourceList;
    }

    @NonNull
    public List<File> getJniSourceList() {
        List<SourceProvider> providers = getSortedSourceProviders();

        List<File> sourceList = Lists.newArrayListWithExpectedSize(providers.size());

        for (SourceProvider provider : providers) {
            sourceList.addAll(provider.getCDirectories());
        }

        return sourceList;
    }

    /**
     * Returns the compile classpath for this config. If the config tests a library, this
     * will include the classpath of the tested config
     *
     * @return a non null, but possibly empty set.
     */
    @NonNull
    public Set<File> getCompileClasspath() {

        Set<File> classpath = Sets.newHashSetWithExpectedSize(
                mFlatCompileDependencies.getJarDependencies().size() +
                        mFlatCompileDependencies.getLocalDependencies().size() +
                        mFlatCompileDependencies.getAndroidDependencies().size());

        for (AndroidLibrary android : mFlatCompileDependencies.getAndroidDependencies()) {
            classpath.add(android.getJarFile());
            for (File jarFile : android.getLocalJars()) {
                classpath.add(jarFile);
            }
        }

        for (JavaLibrary javaLibrary : mFlatCompileDependencies.getJarDependencies()) {
            classpath.add(javaLibrary.getJarFile());
        }

        for (JavaLibrary javaLibrary : mFlatCompileDependencies.getLocalDependencies()) {
            classpath.add(javaLibrary.getJarFile());
        }

        return classpath;
    }

    /**
     * Returns the list of packaged jars for this config. If the config tests a library, this
     * will include the jars of the tested config
     *
     * @return a non null, but possibly empty list.
     */
    @NonNull
    public Set<File> getAllPackagedJars() {
        Set<File> localJars = getLocalPackagedJars();

        Set<File> jars = Sets.newHashSetWithExpectedSize(
                mFlatPackageDependencies.getJarDependencies().size() +
                        localJars.size() +
                        mFlatPackageDependencies.getAndroidDependencies().size());

        for (JavaLibrary javaLibrary : mFlatPackageDependencies.getJarDependencies()) {
            File jarFile = javaLibrary.getJarFile();
            if (jarFile.exists()) {
                jars.add(jarFile);
            }
        }

        jars.addAll(localJars);

        for (AndroidLibrary androidLibrary : mFlatPackageDependencies.getAndroidDependencies()) {
            File libJar = androidLibrary.getJarFile();
            if (libJar.exists()) {
                jars.add(libJar);
            }
            for (File jarFile : androidLibrary.getLocalJars()) {
                if (jarFile.isFile()) {
                    jars.add(jarFile);
                }
            }
        }

        return jars;
    }

    /**
     * Returns the list of packaged jars for this config that is not coming from subprojects..
     *
     * If the config tests a library, this will include the jars of the tested config
     *
     * @return a non null, but possibly empty list.
     */
    @NonNull
    public Set<File> getExternalPackagedJars() {
        Set<File> jars = Sets.newHashSetWithExpectedSize(
                mFlatPackageDependencies.getJarDependencies().size() +
                        mFlatPackageDependencies.getAndroidDependencies().size());

        for (JavaLibrary javaLibrary : mFlatPackageDependencies.getJarDependencies()) {
            // only take java libraries that are not coming from a module.
            if (javaLibrary.getProject() == null) {
                File jarFile = javaLibrary.getJarFile();
                if (jarFile.exists()) {
                    jars.add(jarFile);
                }
            }
        }

        for (AndroidLibrary androidLibrary : mFlatPackageDependencies.getAndroidDependencies()) {
            // only take android libraries that are not coming from a module.
            if (androidLibrary.getProject() == null) {
                File libJar = androidLibrary.getJarFile();
                if (libJar.exists()) {
                    jars.add(libJar);
                }

                // also grab their local jars
                for (File jarFile : androidLibrary.getLocalJars()) {
                    if (jarFile.isFile()) {
                        jars.add(jarFile);
                    }
                }
            }
        }

        return jars;
    }

    /**
     * Returns the list of external packaged jars for this config.
     *
     * @return a non null, but possibly empty list.
     */
    @NonNull
    public Set<File> getExternalPackagedJniJars() {
        Set<File> jars = Sets.newHashSetWithExpectedSize(
                mFlatPackageDependencies.getJarDependencies().size());

        for (JavaLibrary javaLibrary : mFlatPackageDependencies.getJarDependencies()) {
            if (javaLibrary.getProject() == null) {
                File jarFile = javaLibrary.getJarFile();
                if (jarFile.exists()) {
                    jars.add(jarFile);
                }
            }
        }

        return jars;
    }

    /**
     * Returns the packaged local Jars
     *
     * @return a non null, but possibly empty set.
     */
    @NonNull
    public Set<File> getLocalPackagedJars() {
        Set<File> jars = Sets.newHashSetWithExpectedSize(
                mFlatPackageDependencies.getLocalDependencies().size());

        for (JavaLibrary jar : mFlatPackageDependencies.getLocalDependencies()) {
            File jarFile = jar.getJarFile();
            if (jarFile.exists()) {
                jars.add(jarFile);
            }
        }

        return jars;
    }

    /**
     * Returns the packaged sub-project Jars, coming from Android or Java modules.
     *
     * @return a non null, but possibly empty set.
     */
    @NonNull
    public Set<File> getSubProjectPackagedJars() {
        Set<File> jars = Sets.newHashSetWithExpectedSize(
                mFlatPackageDependencies.getJarDependencies().size() +
                        mFlatPackageDependencies.getAndroidDependencies().size());

        for (AndroidLibrary androidLibrary : mFlatPackageDependencies.getAndroidDependencies()) {
            // only take the sub-project android libraries.
            if (androidLibrary.getProject() != null) {
                File libJar = androidLibrary.getJarFile();
                if (libJar.exists()) {
                    jars.add(libJar);
                }
            }
        }

        for (JavaLibrary javaLibrary : mFlatPackageDependencies.getJarDependencies()) {
            if (javaLibrary.getProject() != null) {
                File jarFile = javaLibrary.getJarFile();
                if (jarFile.exists()) {
                    jars.add(jarFile);
                }
            }
        }

        return jars;
    }

    /**
     * Returns the packaged sub-project local Jars
     *
     * @return a non null, but possibly empty immutable set.
     */
    @NonNull
    public Set<File> getSubProjectLocalPackagedJars() {
        Set<File> jars = Sets.newHashSetWithExpectedSize(
                mFlatPackageDependencies.getAndroidDependencies().size());

        for (AndroidLibrary androidLibrary : mFlatPackageDependencies.getAndroidDependencies()) {
            // only take the sub-project android libraries.
            if (androidLibrary.getProject() != null) {
                for (File jarFile : androidLibrary.getLocalJars()) {
                    if (jarFile.isFile()) {
                        jars.add(jarFile);
                    }
                }
            }
        }

        return jars;
    }

    /**
     * Returns the sub-project jni libs
     *
     * @return a non null, but possibly empty immutable set.
     */
    @NonNull
    public Set<File> getSubProjectJniLibFolders() {
        Set<File> jniDirectories = Sets.newHashSetWithExpectedSize(
                mFlatPackageDependencies.getAndroidDependencies().size());

        for (AndroidLibrary androidLibrary : mFlatPackageDependencies.getAndroidDependencies()) {
            // only take the sub-project android libraries.
            if (androidLibrary.getProject() != null) {
                File jniDir = androidLibrary.getJniFolder();
                if (jniDir.exists()) {
                    jniDirectories.add(jniDir);
                }
            }
        }

        return jniDirectories;
    }

    /**
     * Returns the list of sub-project packaged jars for this config.
     *
     * @return a non null, but possibly empty list.
     */
    @NonNull
    public Set<File> getSubProjectPackagedJniJars() {
        Set<File> jars = Sets.newHashSetWithExpectedSize(
                mFlatPackageDependencies.getJarDependencies().size());

        for (JavaLibrary ja : mFlatPackageDependencies.getJarDependencies()) {
            if (ja.getProject() != null) {
                File jarFile = ja.getJarFile();
                if (jarFile.exists()) {
                    jars.add(jarFile);
                }
            }
        }

        return jars;
    }

    /**
     * Returns the external jni lib folders
     *
     * @return a non null, but possibly empty immutable set.
     */
    @NonNull
    public Set<File> getExternalAarJniLibFolders() {
        Set<File> jniDirectories = Sets.newHashSetWithExpectedSize(
                mFlatPackageDependencies.getAndroidDependencies().size());

        for (AndroidLibrary androidLibrary : mFlatPackageDependencies.getAndroidDependencies()) {
            // only take the external android libraries.
            if (androidLibrary.getProject() == null) {
                File jniDir = androidLibrary.getJniFolder();
                if (jniDir.exists()) {
                    jniDirectories.add(jniDir);
                }
            }
        }

        return jniDirectories;
    }

    /**
     * Returns the list of provided-only jars for this config.
     *
     * @return a non null, but possibly empty list.
     */
    @NonNull
    public List<File> getProvidedOnlyJars() {
        Set<File> jars = Sets.newHashSetWithExpectedSize(
                mFlatPackageDependencies.getAndroidDependencies().size() +
                        mFlatPackageDependencies.getJarDependencies().size() +
                        mFlatPackageDependencies.getLocalDependencies().size());

        // TODO: we might want to cache this somehow, or precompute during dependency manager.
        Set<MavenCoordinates> packageArtifacts = Sets.newHashSet();
        for (JavaLibrary javaLibrary : mFlatPackageDependencies.getJarDependencies()) {
            packageArtifacts.add(javaLibrary.getResolvedCoordinates());
        }

        for (JavaLibrary javaLibrary : mFlatPackageDependencies.getLocalDependencies()) {
            packageArtifacts.add(javaLibrary.getResolvedCoordinates());
        }

        for (AndroidLibrary androidLibrary : mFlatPackageDependencies.getAndroidDependencies()) {
            packageArtifacts.add(androidLibrary.getResolvedCoordinates());
        }

        // now find provided only jars by filtering out packaged dependencies

        for (JavaLibrary javaLibrary : mFlatCompileDependencies.getJarDependencies()) {
            if (!packageArtifacts.contains(javaLibrary.getResolvedCoordinates())) {
                File jarFile = javaLibrary.getJarFile();
                if (jarFile.exists()) {
                    jars.add(jarFile);
                }
            }
        }

        for (JavaLibrary javaLibrary : mFlatCompileDependencies.getLocalDependencies()) {
            if (!packageArtifacts.contains(javaLibrary.getResolvedCoordinates())) {
                File jarFile = javaLibrary.getJarFile();
                if (jarFile.exists()) {
                    jars.add(jarFile);
                }
            }
        }

        for (AndroidLibrary androidLibrary : mFlatCompileDependencies.getAndroidDependencies()) {
            if (!packageArtifacts.contains(androidLibrary.getResolvedCoordinates())) {
                File libJar = androidLibrary.getJarFile();
                if (libJar.exists()) {
                    jars.add(libJar);
                }
                for (File jarFile : androidLibrary.getLocalJars()) {
                    if (jarFile.isFile()) {
                        jars.add(jarFile);
                    }
                }
            }
        }

        return Lists.newArrayList(jars);
    }

    /**
     * Adds a variant-specific BuildConfig field.
     * @param type the type of the field
     * @param name the name of the field
     * @param value the value of the field
     */
    public void addBuildConfigField(@NonNull String type, @NonNull String name, @NonNull String value) {
        ClassField classField = new ClassFieldImpl(type, name, value);
        mBuildConfigFields.put(name, classField);
    }

    /**
     * Adds a variant-specific res value.
     * @param type the type of the field
     * @param name the name of the field
     * @param value the value of the field
     */
    public void addResValue(@NonNull String type, @NonNull String name, @NonNull String value) {
        ClassField classField = new ClassFieldImpl(type, name, value);
        mResValues.put(name, classField);
    }

    /**
     * Returns a list of items for the BuildConfig class.
     *
     * Items can be either fields (instance of {@link com.android.builder.model.ClassField})
     * or comments (instance of String).
     *
     * @return a list of items.
     */
    @NonNull
    public List<Object> getBuildConfigItems() {
        List<Object> fullList = Lists.newArrayList();

        // keep track of the names already added. This is because we show where the items
        // come from so we cannot just put everything a map and let the new ones override the
        // old ones.
        Set<String> usedFieldNames = Sets.newHashSet();

        Collection<ClassField> list = mBuildConfigFields.values();
        if (!list.isEmpty()) {
            fullList.add("Fields from the variant");
            fillFieldList(fullList, usedFieldNames, list);
        }

        list = mBuildType.getBuildConfigFields().values();
        if (!list.isEmpty()) {
            fullList.add("Fields from build type: " + mBuildType.getName());
            fillFieldList(fullList, usedFieldNames, list);
        }

        for (F flavor : mFlavors) {
            list = flavor.getBuildConfigFields().values();
            if (!list.isEmpty()) {
                fullList.add("Fields from product flavor: " + flavor.getName());
                fillFieldList(fullList, usedFieldNames, list);
            }
        }

        list = mDefaultConfig.getBuildConfigFields().values();
        if (!list.isEmpty()) {
            fullList.add("Fields from default config.");
            fillFieldList(fullList, usedFieldNames, list);
        }

        return fullList;
    }

    /**
     * Return the merged build config fields for the variant.
     *
     * This is made of of the variant-specific fields overlaid on top of the build type ones,
     * the flavors ones, and the default config ones.
     *
     * @return a map of merged fields
     */
    @NonNull
    public Map<String, ClassField> getMergedBuildConfigFields() {
        Map<String, ClassField> mergedMap = Maps.newHashMap();

        // start from the lowest priority and just add it all. Higher priority fields
        // will replace lower priority ones.

        mergedMap.putAll(mDefaultConfig.getBuildConfigFields());
        for (int i = mFlavors.size() - 1; i >= 0 ; i--) {
            mergedMap.putAll(mFlavors.get(i).getBuildConfigFields());
        }

        mergedMap.putAll(mBuildType.getBuildConfigFields());
        mergedMap.putAll(mBuildConfigFields);

        return mergedMap;
    }

    /**
     * Return the merged res values for the variant.
     *
     * This is made of of the variant-specific fields overlaid on top of the build type ones,
     * the flavors ones, and the default config ones.
     *
     * @return a map of merged fields
     */
    @NonNull
    public Map<String, ClassField> getMergedResValues() {
        Map<String, ClassField> mergedMap = Maps.newHashMap();

        // start from the lowest priority and just add it all. Higher priority fields
        // will replace lower priority ones.

        mergedMap.putAll(mDefaultConfig.getResValues());
        for (int i = mFlavors.size() - 1; i >= 0 ; i--) {
            mergedMap.putAll(mFlavors.get(i).getResValues());
        }

        mergedMap.putAll(mBuildType.getResValues());
        mergedMap.putAll(mResValues);

        return mergedMap;
    }

    /**
     * Fills a list of Object from a given list of ClassField only if the name isn't in a set.
     * Each new item added adds its name to the list.
     * @param outList the out list
     * @param usedFieldNames the list of field names already in the list
     * @param list the list to copy items from
     */
    private static void fillFieldList(
            @NonNull List<Object> outList,
            @NonNull Set<String> usedFieldNames,
            @NonNull Collection<ClassField> list) {
        for (ClassField f : list) {
            String name = f.getName();
            if (!usedFieldNames.contains(name)) {
                usedFieldNames.add(f.getName());
                outList.add(f);
            }
        }
    }

    /**
     * Returns a list of generated resource values.
     *
     * Items can be either fields (instance of {@link com.android.builder.model.ClassField})
     * or comments (instance of String).
     *
     * @return a list of items.
     */
    @NonNull
    public List<Object> getResValues() {
        List<Object> fullList = Lists.newArrayList();

        // keep track of the names already added. This is because we show where the items
        // come from so we cannot just put everything a map and let the new ones override the
        // old ones.
        Set<String> usedFieldNames = Sets.newHashSet();

        Collection<ClassField> list = mResValues.values();
        if (!list.isEmpty()) {
            fullList.add("Values from the variant");
            fillFieldList(fullList, usedFieldNames, list);
        }

        list = mBuildType.getResValues().values();
        if (!list.isEmpty()) {
            fullList.add("Values from build type: " + mBuildType.getName());
            fillFieldList(fullList, usedFieldNames, list);
        }

        for (F flavor : mFlavors) {
            list = flavor.getResValues().values();
            if (!list.isEmpty()) {
                fullList.add("Values from product flavor: " + flavor.getName());
                fillFieldList(fullList, usedFieldNames, list);
            }
        }

        list = mDefaultConfig.getResValues().values();
        if (!list.isEmpty()) {
            fullList.add("Values from default config.");
            fillFieldList(fullList, usedFieldNames, list);
        }

        return fullList;
    }

    @Nullable
    public SigningConfig getSigningConfig() {
        if (mSigningConfigOverride != null) {
            return mSigningConfigOverride;
        }

        SigningConfig signingConfig = mBuildType.getSigningConfig();
        if (signingConfig != null) {
            return signingConfig;
        }
        return mMergedFlavor.getSigningConfig();
    }

    public boolean isSigningReady() {
        SigningConfig signingConfig = getSigningConfig();
        return signingConfig != null && signingConfig.isSigningReady();
    }

    /**
     * Returns the proguard config files coming from the project but also from the dependencies.
     *
     * Note that if the method is set to include config files coming from libraries, they will
     * only be included if the aars have already been unzipped.
     *
     * @param includeLibraries whether to include the library dependencies.
     * @return a non null list of proguard files.
     */
    @NonNull
    public Set<File> getProguardFiles(boolean includeLibraries, List<File> defaultProguardConfig) {
        Set<File> fullList = Sets.newHashSet();

        // add the config files from the build type, main config and flavors
        fullList.addAll(mDefaultConfig.getProguardFiles());
        fullList.addAll(mBuildType.getProguardFiles());

        for (F flavor : mFlavors) {
            fullList.addAll(flavor.getProguardFiles());
        }

        if (fullList.isEmpty()) {
            fullList.addAll(defaultProguardConfig);
        }

        // now add the one coming from the library dependencies
        if (includeLibraries) {
            for (AndroidLibrary androidLibrary : mFlatPackageDependencies.getAndroidDependencies()) {
                File proguardRules = androidLibrary.getProguardRules();
                if (proguardRules.exists()) {
                    fullList.add(proguardRules);
                }
            }
        }

        return fullList;
    }

    /**
     * Returns the proguard config files to be used for the test APK.
     */
    @NonNull
    public Set<File> getTestProguardFiles() {
        Set<File> fullList = Sets.newHashSet();

        // add the config files from the build type, main config and flavors
        fullList.addAll(mDefaultConfig.getTestProguardFiles());
        fullList.addAll(mBuildType.getTestProguardFiles());

        for (F flavor : mFlavors) {
            fullList.addAll(flavor.getTestProguardFiles());
        }

        return fullList;
    }

    @NonNull
    public List<Object> getConsumerProguardFiles() {
        List<Object> fullList = Lists.newArrayList();

        // add the config files from the build type, main config and flavors
        fullList.addAll(mDefaultConfig.getConsumerProguardFiles());
        fullList.addAll(mBuildType.getConsumerProguardFiles());

        for (F flavor : mFlavors) {
            fullList.addAll(flavor.getConsumerProguardFiles());
        }

        return fullList;
    }

    public boolean isTestCoverageEnabled() {
        return mBuildType.isTestCoverageEnabled();
    }

    /**
     * Returns the merged manifest placeholders. All product flavors are merged first, then build
     * type specific placeholders are added and potentially overrides product flavors values.
     * @return the merged manifest placeholders for a build variant.
     */
    @NonNull
    public Map<String, Object> getManifestPlaceholders() {
        Map<String, Object> mergedFlavorsPlaceholders = mMergedFlavor.getManifestPlaceholders();
        // so far, blindly override the build type placeholders
        mergedFlavorsPlaceholders.putAll(mBuildType.getManifestPlaceholders());
        return mergedFlavorsPlaceholders;
    }

    public boolean isMultiDexEnabled() {
        Boolean value = mBuildType.getMultiDexEnabled();
        if (value != null) {
            return value;
        }

        value = mMergedFlavor.getMultiDexEnabled();
        if (value != null) {
            return value;
        }

        return false;
    }

    public File getMultiDexKeepFile() {
        File value = mBuildType.getMultiDexKeepFile();
        if (value != null) {
            return value;
        }

        value = mMergedFlavor.getMultiDexKeepFile();
        if (value != null) {
            return value;
        }

        return null;
    }

    public File getMultiDexKeepProguard() {
        File value = mBuildType.getMultiDexKeepProguard();
        if (value != null) {
            return value;
        }

        value = mMergedFlavor.getMultiDexKeepProguard();
        if (value != null) {
            return value;
        }

        return null;
    }

    public boolean isLegacyMultiDexMode() {
        return isMultiDexEnabled() && getMinSdkVersion().getApiLevel() < 21;
    }

    /**
     * Returns the renderscript support mode.
     */
    public boolean getRenderscriptSupportModeEnabled() {
        Boolean value = mMergedFlavor.getRenderscriptSupportModeEnabled();
        if (value != null) {
            return value;
        }

        // default is false.
        return false;
    }

    /**
     * Returns the renderscript BLAS support mode.
     */
    public boolean getRenderscriptSupportModeBlasEnabled() {
        Boolean value = mMergedFlavor.getRenderscriptSupportModeBlasEnabled();
        if (value != null) {
            return value;
        }

        // default is false.
        return false;
    }

    /**
     * Returns the renderscript NDK mode.
     */
    public boolean getRenderscriptNdkModeEnabled() {
        Boolean value = mMergedFlavor.getRenderscriptNdkModeEnabled();
        if (value != null) {
            return value;
        }

        // default is false.
        return false;
    }

    /**
     * Returns true if the variant output is a bundle.
     */
    public boolean isBundled() {
        return mType == VariantType.LIBRARY;
    }

    @NonNull
    public Collection<File> getJarJarRuleFiles() {
        ImmutableList.Builder<File> jarjarRuleFiles = ImmutableList.builder();
        jarjarRuleFiles.addAll(getMergedFlavor().getJarJarRuleFiles());
        jarjarRuleFiles.addAll(mBuildType.getJarJarRuleFiles());
        return jarjarRuleFiles.build();
    }

    @NonNull
    private ManifestAttributeSupplier getManifestAttributeSupplier(){
        if (mManifestAttributeSupplier == null){
            mManifestAttributeSupplier =
                    new DefaultManifestParser(mDefaultSourceProvider.getManifestFile());
        }
        return mManifestAttributeSupplier;
    }
}
