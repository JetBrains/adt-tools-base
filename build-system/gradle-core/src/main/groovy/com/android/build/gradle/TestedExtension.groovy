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
package com.android.build.gradle
import com.android.annotations.NonNull
import com.android.build.gradle.api.TestVariant
import com.android.build.gradle.api.UnitTestVariant
import com.android.build.gradle.internal.ExtraModelInfo
import com.android.build.gradle.internal.SdkHandler
import com.android.build.gradle.internal.dsl.BuildType
import com.android.build.gradle.internal.dsl.GroupableProductFlavor
import com.android.build.gradle.internal.dsl.SigningConfig
import com.android.builder.core.AndroidBuilder
import groovy.transform.CompileStatic
import org.gradle.api.DomainObjectSet
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.internal.DefaultDomainObjectSet
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.internal.reflect.Instantiator

import static com.android.builder.core.VariantType.ANDROID_TEST
import static com.android.builder.core.VariantType.UNIT_TEST
/**
 * base 'android' extension for plugins that have a test component.
 */
@CompileStatic
public abstract class TestedExtension extends BaseExtension {

    private final DomainObjectSet<TestVariant> testVariantList =
            new DefaultDomainObjectSet<TestVariant>(TestVariant.class)

    private final DomainObjectSet<UnitTestVariant> unitTestVariantList =
            new DefaultDomainObjectSet<UnitTestVariant>(UnitTestVariant.class)

    String testBuildType = "debug"

    TestedExtension(
            @NonNull ProjectInternal project,
            @NonNull Instantiator instantiator,
            @NonNull AndroidBuilder androidBuilder,
            @NonNull SdkHandler sdkHandler,
            @NonNull NamedDomainObjectContainer<BuildType> buildTypes,
            @NonNull NamedDomainObjectContainer<GroupableProductFlavor> productFlavors,
            @NonNull NamedDomainObjectContainer<SigningConfig> signingConfigs,
            @NonNull ExtraModelInfo extraModelInfo,
            boolean isLibrary) {
        super(project, instantiator, androidBuilder, sdkHandler, buildTypes, productFlavors,
                signingConfigs, extraModelInfo, isLibrary)

        sourceSetsContainer.create(ANDROID_TEST.prefix)
        sourceSetsContainer.create(UNIT_TEST.prefix)
    }

    /**
     * Returns the list of (Android) test variants. Since the collections is built after evaluation,
     * it should be used with Gradle's <code>all</code> iterator to process future items.
     */
    @NonNull
    public DomainObjectSet<TestVariant> getTestVariants() {
        return testVariantList
    }

    void addTestVariant(TestVariant testVariant) {
        testVariantList.add(testVariant)
    }

    /**
     * Returns the list of (Android) test variants. Since the collections is built after evaluation,
     * it should be used with Gradle's <code>all</code> iterator to process future items.
     */
    @NonNull
    public DomainObjectSet<UnitTestVariant> getUnitTestVariants() {
        return unitTestVariantList
    }

    void addUnitTestVariant(UnitTestVariant testVariant) {
        unitTestVariantList.add(testVariant)
    }
}
