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

package com.android.builder.model;

import com.android.annotations.NonNull;

import java.io.File;
import java.util.Collection;

/**
 * Entry point for the model of the Android Projects. This models a single module, whether
 * the module is an app project or a library project.
 */
public interface AndroidProject {
    String BUILD_MODEL_ONLY_SYSTEM_PROPERTY =  "android.build.model.only";

    public static final String ARTIFACT_MAIN = "_main_";
    public static final String ARTIFACT_INSTRUMENT_TEST = "_instrument_test_";

    /**
     * Returns the model version. This is a string in the format X.Y.Z
     *
     * @return a string containing the model version.
     */
    @NonNull
    String getModelVersion();

    /**
     * Returns the name of the module.
     *
     * @return the name of the module.
     */
    @NonNull
    String getName();

    /**
     * Returns whether this is a library.
     * @return true for a library module.
     */
    boolean isLibrary();

    /**
     * Returns the {@link ProductFlavorContainer} for the 'main' default config.
     *
     * @return the product flavor.
     */
    @NonNull
    ProductFlavorContainer getDefaultConfig();

    /**
     * Returns a list of all the {@link BuildType} in their container.
     *
     * @return a list of build type containers.
     */
    @NonNull
    Collection<BuildTypeContainer> getBuildTypes();

    /**
     * Returns a list of all the {@link ProductFlavor} in their container.
     *
     * @return a list of product flavor containers.
     */
    @NonNull
    Collection<ProductFlavorContainer> getProductFlavors();

    /**
     * Returns a list of all the variants.
     *
     * This does not include test variant. Test variants are additional artifacts in their
     * respective variant info.
     *
     * @return a list of the variants.
     */
    @NonNull
    Collection<Variant> getVariants();

    /**
     * Returns a list of extra artifacts meta data. This does not include the main artifact.
     *
     * @return a list of extra artifacts
     */
    @NonNull
    Collection<ArtifactMetaData> getExtraArtifacts();

    /**
     * Returns the compilation target as a string. This is the full extended target hash string.
     * (see com.android.sdklib.IAndroidTarget#hashString())
     *
     * @return the target hash string
     */
    @NonNull
    String getCompileTarget();

    /**
     * Returns the boot classpath matching the compile target. This is typically android.jar plus
     * other optional libraries.
     *
     * @return a list of jar files.
     */
    @NonNull
    Collection<String> getBootClasspath();

    /**
     * Returns a list of folders or jar files that contains the framework source code.
     * @return a list of folders or jar files that contains the framework source code.
     */
    @NonNull
    Collection<File> getFrameworkSources();

    /**
     * Returns a list of {@link SigningConfig}.
     *
     * @return a map of signing config
     */
    @NonNull
    Collection<SigningConfig> getSigningConfigs();

    /**
     * Returns the aapt options.
     *
     * @return the aapt options.
     */
    @NonNull
    AaptOptions getAaptOptions();

    /**
     * Returns the lint options.
     *
     * @return the lint options.
     */
    @NonNull
    LintOptions getLintOptions();

    /**
     * Returns the dependencies that were not successfully resolved. The returned list gets
     * populated only if the system property {@link #BUILD_MODEL_ONLY_SYSTEM_PROPERTY} has been
     * set to {@code true}.
     * <p>
     * Each value of the collection has the format group:name:version, for example:
     * com.google.guava:guava:15.0.2
     *
     * @return the dependencies that were not successfully resolved.
     */
    @NonNull
    Collection<String> getUnresolvedDependencies();

    /**
     * @return the compile options for Java code.
     */
    @NonNull
    JavaCompileOptions getJavaCompileOptions();
}
