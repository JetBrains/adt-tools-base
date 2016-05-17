/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.builder.internal.aapt;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.core.VariantType;
import com.android.builder.model.AaptOptions;
import com.android.builder.model.AndroidLibrary;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.IAndroidTarget;
import com.android.utils.ILogger;
import com.google.common.base.Verify;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Configuration for an {@code aapt} packaging operation. This implementation provides getters
 * for all properties to configure packaging.
 *
 * <p>To create a packaging configuration, use the provided builder, {@link Builder}.
 */
public class AaptPackageConfig implements Cloneable {

    /**
     * The manifest file (see {@link Builder#setManifestFile(File)}).
     */
    @Nullable
    private File mManifestFile;

    /**
     * The {@code aapt} options set (see {@link Builder#setOptions(AaptOptions)}).
     */
    @Nullable
    private AaptOptions mAaptOptions;

    /**
     * The directory where to output source files (see
     * {@link Builder#setSourceOutputDir(File)}).
     */
    @Nullable
    private File mSourceOutputDir;

    /**
     * The output directory for resources (see
     * {@link Builder#setResourceOutputApk(File)}}.
     */
    @Nullable
    private File mResourceOutputApk;

    /**
     * All libraries added (see {@link Builder#setLibraries(List)}).
     */
    @NonNull
    private ImmutableList<AndroidLibrary> mLibraries;

    /**
     * Symbol output directory (see {@link Builder#setSymbolOutputDir(File)}).
     */
    @Nullable
    private File mSymbolOutputDir;

    /**
     * Are we requesting verbose output?
     */
    private boolean mVerbose;

    /**
     * The resource directory (see {@link Builder#setResourceDir(File)}).
     */
    private File mResourceDir;

    /**
     * The assets directory (see {@link Builder#setAssetsDir(File)}).
     */
    private File mAssetsDir;

    /**
     * Where to write proguard output (see {@link Builder#setProguardOutputFile(File)}).
     */
    private File mProguardOutputFile;

    /**
     * Where to write the main dex list proguard output file (see
     * {@link Builder#setMainDexListProguardOutputFile(File)}).
     */
    private File mMainDexListProguardOutputFile;

    /**
     * Collection of splits, if any (see {@link Builder#setSplits(Collection)}).
     */
    @Nullable
    private ImmutableCollection<String> mSplits;

    /**
     * Is the package debuggable? (See {@link Builder#setDebuggable(boolean)}.)
     */
    private boolean mDebuggable;

    /**
     * Package to use for {@code R} (see {@link Builder#setCustomPackageForR(String)}).
     */
    @Nullable
    private String mPackageForR;

    /**
     * Should resources for pseudo-locales be generated? (See
     * {@link Builder#setPseudoLocalize(boolean)}.)
     */
    private boolean mPseudoLocalize;

    /**
     * The preferred density.
     */
    @Nullable
    private String mPreferredDensity;

    /**
     * Build tools information.
     */
    @Nullable
    private BuildToolInfo mBuildToolInfo;

    /**
     * The Android target.
     */
    @Nullable
    private IAndroidTarget mAndroidTarget;

    /**
     * Logger to use.
     */
    @Nullable
    private ILogger mLogger;

    /**
     * Resource configs.
     */
    @NonNull
    private ImmutableSet<String> mResourceConfigs;

    /**
     * Variant type.
     */
    @Nullable
    private VariantType mVariantType;

    /**
     * Creates a new instance of the the package configuration with default values.
     */
    private AaptPackageConfig() {
        mLibraries = ImmutableList.of();
        mVerbose = false;
        mResourceConfigs = ImmutableSet.of();
    }

    @Override
    @NonNull
    public AaptPackageConfig clone() {
        try {
            return (AaptPackageConfig) super.clone();
        } catch (CloneNotSupportedException e) {
            /*
             * Never thrown. Return line should fool the compiler :)
             */
            Verify.verify(false);
            return new AaptPackageConfig();
        }
    }

    /**
     * Obtains the currently set manifest file. See package description for details on field rules.
     *
     * @return the manifest file, {@code null} if not yet set
     */
    @Nullable
    public File getManifestFile() {
        return mManifestFile;
    }

    /**
     * Obtains the currently set options.
     *
     * @return the options, {@code null} it not ye set
     */
    @Nullable
    public AaptOptions getOptions() {
        return mAaptOptions;
    }

    /**
     * Obtains the currently set source output directory.
     *
     * @return the source output directory, {@code null} if not defined
     */
    @Nullable
    public File getSourceOutputDir() {
        return mSourceOutputDir;
    }

    /**
     * Obtains the currently set resource output APK.
     *
     * @return the resource output APK, {@code null} if not defined
     */
    @Nullable
    public File getResourceOutputApk() {
        return mResourceOutputApk;
    }

    /**
     * Obtains all libraries added.
     *
     * @return all libraries; returns an empty list if no libraries were added
     */
    @NonNull
    public List<? extends AndroidLibrary> getLibraries() {
        return Collections.unmodifiableList(mLibraries);
    }

    /**
     * Obtains the currently set symbol output directory.
     *
     * @return the symbol output directory, {@code null} if not set
     */
    @Nullable
    public File getSymbolOutputDir() {
        return mSymbolOutputDir;
    }

    /**
     * Obtains if verbose output is requested.
     *
     * @return is verbose output requested?
     */
    public boolean isVerbose() {
        return mVerbose;
    }

    /**
     * Obtains the currently set resource directory.
     *
     * @return the resource directory, {@code null} if not defined
     */
    @Nullable
    public File getResourceDir() {
        return mResourceDir;
    }

    /**
     * Obtains the currently set assets directory.
     *
     * @return the assets directory, {@code null} if not set
     */
    @Nullable
    public File getAssetsDir() {
        return mAssetsDir;
    }

    /**
     * Obtains the currently set proguard output file.
     *
     * @return the file, {@code null} if not set
     */
    @Nullable
    public File getProguardOutputFile() {
        return mProguardOutputFile;
    }

    /**
     * Obtains the currently set main dex list proguard output file.
     *
     * @return the file, {@code null} if not set
     */
    @Nullable
    public File getMainDexListProguardOutputFile() {
        return mMainDexListProguardOutputFile;
    }

    /**
     * Obtains the currently set collection of splits.
     *
     * @return the splits, {@code null} if not set
     */
    @Nullable
    public Collection<String> getSplits() {
        if (mSplits == null) {
            return null;
        } else {
            return Collections.unmodifiableCollection(mSplits);
        }
    }

    /**
     * Obtains if the a debuggable package was requested.
     *
     * @return should be package be debuggable?
     */
    public boolean isDebuggable() {
        return mDebuggable;
    }

    /**
     * Obtains the custom package for {@code R}.
     *
     * @return the custom package, {@code null} if not set
     */
    @Nullable
    public String getCustomPackageForR() {
        return mPackageForR;
    }

    /**
     * Obtains whether resources for pseudo-locales should be generated.
     *
     * @return generate resources for pseudo-locales?
     */
    public boolean isPseudoLocalize() {
        return mPseudoLocalize;
    }

    /**
     * Obtains the preferred density.
     *
     * @return the preferred density, {@code null} if not set
     */
    @Nullable
    public String getPreferredDensity() {
        return mPreferredDensity;
    }

    /**
     * Obtains the build tool information.
     *
     * @return the build tool information, {@code null} if not set
     */
    @Nullable
    public BuildToolInfo getBuildToolInfo() {
        return mBuildToolInfo;
    }

    /**
     * Obtains the Android target.
     *
     * @return the Android target, {@code null} if not set
     */
    @Nullable
    public IAndroidTarget getAndroidTarget() {
        return mAndroidTarget;
    }

    /**
     * Obtains the logger.
     *
     * @return the logger, {@code null} if not set
     */
    @Nullable
    public ILogger getLogger() {
        return mLogger;
    }

    /**
     * Obtains the resource configs.
     *
     * @return the resource configs
     */
    @NonNull
    public Set<String> getResourceConfigs() {
        return Collections.unmodifiableSet(mResourceConfigs);
    }

    /**
     * Obtains the variant type.
     *
     * @return the variant type, {@code null} if not set
     */
    @Nullable
    public VariantType getVariantType() {
        return mVariantType;
    }

    /**
     * Builder used to create a {@link AaptPackageConfig}.
     */
    public static class Builder {

        /**
         * Current configuration being built.
         */
        @NonNull
        private final AaptPackageConfig mConfig;

        /**
         * Creates a new builder.
         */
        public Builder() {
            mConfig = new AaptPackageConfig();
        }

        /**
         * Creates a new {@link AaptPackageConfig} from the data already placed in the builder.
         *
         * @return the new config
         */
        @NonNull
        public AaptPackageConfig build() {
            return mConfig.clone();
        }

        /**
         * Sets the manifest file. See {@link AbstractAapt#validatePackageConfig(AaptPackageConfig)}
         * for the details on field rules.
         *
         * @param manifestFile the manifest file
         * @return {@code this}
         */
        @NonNull
        public Builder setManifestFile(@NonNull File manifestFile) {
            if (!manifestFile.isFile()) {
                throw new IllegalArgumentException("Manifest file '{0}' is not a readable file."
                        + manifestFile.getAbsolutePath());
            }

            mConfig.mManifestFile = manifestFile;
            return this;
        }

        /**
         * Sets the aapt options. See {@link AbstractAapt#validatePackageConfig(AaptPackageConfig)}
         * for details on field rules.
         *
         * @param options the options
         * @return {@code this}
         */
        @NonNull
        public Builder setOptions(@NonNull AaptOptions options) {
            mConfig.mAaptOptions = options;
            return this;
        }

        /**
         * Sets the source output directory. See
         * {@link AbstractAapt#validatePackageConfig(AaptPackageConfig)} for details on field rules.
         *
         * @param sourceOutputDir the source output directory, may be {@code null}; if not
         * {@code null}, the directory may not exist
         * @return {@code this}
         */
        @NonNull
        public Builder setSourceOutputDir(@Nullable File sourceOutputDir) {
            mConfig.mSourceOutputDir = sourceOutputDir;
            return this;
        }

        /**
         * Sets the symbol output directory. See
         * {@link AbstractAapt#validatePackageConfig(AaptPackageConfig)} for details on field rules.
         *
         * @param symbolOutputDir the symbol output directory, if not {@code null}, doesn't need to
         * exist
         * @return {@code this}
         */
        @NonNull
        public Builder setSymbolOutputDir(@Nullable File symbolOutputDir) {
            mConfig.mSymbolOutputDir = symbolOutputDir;
            return this;
        }

        /**
         * Sets the libraries in the package configuration. See
         * {@link AbstractAapt#validatePackageConfig(AaptPackageConfig)} for details on field rules.
         *
         * @param libraries the list of libraries to add, {@code null} is treated as an empty set
         * @return {@code this}
         */
        @NonNull
        public Builder setLibraries(@Nullable List<? extends AndroidLibrary> libraries) {
            if (libraries == null) {
                mConfig.mLibraries = ImmutableList.of();
            } else {
                mConfig.mLibraries = ImmutableList.copyOf(libraries);
            }

            return this;
        }

        /**
         * Sets the resource output APK. See
         * {@link AbstractAapt#validatePackageConfig(AaptPackageConfig)} for details on field
         * rules.
         *
         * @param resourceOutputApk the resource output APK, the file, if not {@code null}, may not
         * exist
         * @return {@code this}
         */
        @NonNull
        public Builder setResourceOutputApk(@Nullable File resourceOutputApk) {
            mConfig.mResourceOutputApk = resourceOutputApk;
            return this;
        }

        /**
         * Sets the resources folder. See
         * {@link AbstractAapt#validatePackageConfig(AaptPackageConfig)} for details on field rules.
         *
         * @param resourceDir the resource folder; if it exists, it must be a valid directory
         * @return {@code this}
         */
        @NonNull
        public Builder setResourceDir(@Nullable File resourceDir) {
            if (resourceDir != null && !resourceDir.isDirectory()) {
                throw new IllegalArgumentException("Path '" + resourceDir.getAbsolutePath()
                        + "' is not a readable directory.");
            }

            mConfig.mResourceDir = resourceDir;
            return this;
        }

        /**
         * Sets the assets folder. See
         * {@link AbstractAapt#validatePackageConfig(AaptPackageConfig)} for details on field rules.
         *
         * @param assetsDir the assets folder; if not {@code null}, it must be a valid directory
         * @return {@code this}
         */
        @NonNull
        public Builder setAssetsDir(@Nullable File assetsDir) {
            if (assetsDir != null && !assetsDir.isDirectory()) {
                throw new IllegalArgumentException("Path '" + assetsDir.getAbsolutePath()
                        + "' is not a readable directory.");
            }

            mConfig.mAssetsDir = assetsDir;
            return this;
        }

        /**
         * Should verbose output be set? See
         * {@link AbstractAapt#validatePackageConfig(AaptPackageConfig)} for details on field rules.
         *
         * @param verbose should verbose output be set?
         * @return {@code this}
         */
        @NonNull
        public Builder setVerbose(boolean verbose) {
            mConfig.mVerbose = verbose;
            return this;
        }

        /**
         * Sets the proguard output file. See
         * {@link AbstractAapt#validatePackageConfig(AaptPackageConfig)} for details on field rules.
         *
         * @param proguardOutputFile the proguard output file
         * @return {@code this}
         */
        @NonNull
        public Builder setProguardOutputFile(@Nullable File proguardOutputFile) {
            mConfig.mProguardOutputFile = proguardOutputFile;
            return this;
        }

        /**
         * Sets the main dex proguard output file. See
         * {@link AbstractAapt#validatePackageConfig(AaptPackageConfig)} for details on field rules.
         *
         * @param mainDexListProguardOutputFile the proguard output file
         * @return {@code this}
         */
        @NonNull
        public Builder setMainDexListProguardOutputFile(@Nullable File mainDexListProguardOutputFile) {
            mConfig.mMainDexListProguardOutputFile = mainDexListProguardOutputFile;
            return this;
        }

        /**
         * Sets the splits. This will be used by aapt to generate the corresponding pure split apks.
         * See {@link AbstractAapt#validatePackageConfig(AaptPackageConfig)} for details on field
         * rules.
         *
         * @param splits optional list of split dimensions values (like a density or an abi)
         * @return {@code this}
         */
        @NonNull
        public Builder setSplits(@Nullable Collection<String> splits) {
            if (splits == null) {
                mConfig.mSplits = null;
            } else {
                mConfig.mSplits = ImmutableList.copyOf(splits);
            }

            return this;
        }

        /**
         * Should the resulting package be debuggable? See
         * {@link AbstractAapt#validatePackageConfig(AaptPackageConfig)} for details on field rules.
         *
         * @param debuggable should the resulting package be debuggable?
         * @return {@code this}
         */
        @NonNull
        public Builder setDebuggable(boolean debuggable) {
            mConfig.mDebuggable = debuggable;
            return this;
        }

        /**
         * Should resources for pseudo-locales be generated? See
         * {@link AbstractAapt#validatePackageConfig(AaptPackageConfig)} for details on field rules.
         *
         * @param pseudoLocalize should resources for pseudo-locales be generated?
         * @return {@code this}
         */
        @NonNull
        public Builder setPseudoLocalize(boolean pseudoLocalize) {
            mConfig.mPseudoLocalize = pseudoLocalize;
            return this;
        }

        /**
         * Sets the preferred density. See
         * {@link AbstractAapt#validatePackageConfig(AaptPackageConfig)} for details on field rules.
         *
         * @param preferredDensity the preferred density
         * @return {@code this}
         */
        @NonNull
        public Builder setPreferredDensity(@Nullable String preferredDensity) {
            mConfig.mPreferredDensity = preferredDensity;
            return this;
        }

        /**
         * Sets the build tool information. See
         * {@link AbstractAapt#validatePackageConfig(AaptPackageConfig)} for details on field rules.
         *
         * @param buildToolInfo the build tool information
         * @return {@code this}
         */
        @NonNull
        public Builder setBuildToolInfo(@Nullable BuildToolInfo buildToolInfo) {
            mConfig.mBuildToolInfo = buildToolInfo;
            return this;
        }

        /**
         * Sets the android target. See
         * {@link AbstractAapt#validatePackageConfig(AaptPackageConfig)} for details on field rules.
         *
         * @param androidTarget the android target
         * @return {@code this}
         */
        @NonNull
        public Builder setAndroidTarget(@Nullable IAndroidTarget androidTarget) {
            mConfig.mAndroidTarget = androidTarget;
            return this;
        }

        /**
         * Sets the logger. See
         * {@link AbstractAapt#validatePackageConfig(AaptPackageConfig)} for details on field rules.
         *
         * @param logger the logger
         * @return {@code this}
         */
        @NonNull
        public Builder setLogger(@Nullable ILogger logger) {
            mConfig.mLogger = logger;
            return this;
        }

        /**
         * Sets the resource configs. See
         * {@link AbstractAapt#validatePackageConfig(AaptPackageConfig)} for details on field rules.
         *
         * @param resourceConfigs the resource configs
         * @return {@code this}
         */
        @NonNull
        public Builder setResourceConfigs(@NonNull Collection<String> resourceConfigs) {
            mConfig.mResourceConfigs = ImmutableSet.copyOf(resourceConfigs);
            return this;
        }

        /**
         * Sets the variant type. See
         * {@link AbstractAapt#validatePackageConfig(AaptPackageConfig)} for details on field rules.
         *
         * @param variantType the variant type
         * @return {@code this}
         */
        @NonNull
        public Builder setVariantType(@NonNull VariantType variantType) {
            mConfig.mVariantType = variantType;
            return this;
        }

        /**
         * Sets the custom package the {@code R} file should be placed in.
         *
         * @param packageForR the custom package, if {@code null} then no package it set
         * @return {@code this}
         */
        @NonNull
        public Builder setCustomPackageForR(@Nullable String packageForR) {
            mConfig.mPackageForR = packageForR;
            return this;
        }
    }
}
