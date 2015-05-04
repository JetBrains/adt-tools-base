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
package com.android.build.gradle

import com.android.annotations.NonNull
import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.api.LibraryVariant
import com.android.build.gradle.internal.ExtraModelInfo
import com.android.build.gradle.internal.LoggingUtil
import com.android.build.gradle.internal.SdkHandler
import com.android.build.gradle.internal.dsl.BuildType
import com.android.build.gradle.internal.dsl.ProductFlavor
import com.android.build.gradle.internal.dsl.SigningConfig
import com.android.builder.core.AndroidBuilder
import groovy.transform.CompileStatic
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.internal.DefaultDomainObjectSet
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.internal.reflect.Instantiator

/**
 * Options for <code>com.android.library</code> projects.
 */
@CompileStatic
public class LibraryExtension extends TestedExtension {

    private final DefaultDomainObjectSet<LibraryVariant> libraryVariantList =
        new DefaultDomainObjectSet<LibraryVariant>(LibraryVariant.class)

    LibraryExtension(
            @NonNull ProjectInternal project,
            @NonNull Instantiator instantiator,
            @NonNull AndroidBuilder androidBuilder,
            @NonNull SdkHandler sdkHandler,
            @NonNull NamedDomainObjectContainer<BuildType> buildTypes,
            @NonNull NamedDomainObjectContainer<ProductFlavor> productFlavors,
            @NonNull NamedDomainObjectContainer<SigningConfig> signingConfigs,
            @NonNull ExtraModelInfo extraModelInfo,
            boolean isLibrary) {
        super(project, instantiator, androidBuilder, sdkHandler, buildTypes, productFlavors,
                signingConfigs, extraModelInfo, isLibrary)
    }

    /**
     * Returns the list of library variants. Since the collections is built after evaluation,
     * it should be used with Gradle's <code>all</code> iterator to process future items.
     */
    public DefaultDomainObjectSet<LibraryVariant> getLibraryVariants() {
        return libraryVariantList
    }

    @Override
    void addVariant(BaseVariant variant) {
        libraryVariantList.add((LibraryVariant) variant)
    }

    // ---------------
    // TEMP for compatibility
    // STOPSHIP Remove in 1.0

    private boolean packageBuildConfig = true

    public void packageBuildConfig(boolean value) {
        if (!value) {
            LoggingUtil.displayDeprecationWarning(logger, project, "Support for not packaging BuildConfig is deprecated and will be removed in 1.0")
        }

        packageBuildConfig = value
    }

    public void setPackageBuildConfig(boolean value) {
        packageBuildConfig(value)
    }

    boolean getPackageBuildConfig() {
        return packageBuildConfig
    }
}
