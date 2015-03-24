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

package com.android.build.gradle.internal

import com.android.annotations.NonNull
import com.android.build.gradle.internal.api.DefaultAndroidSourceSet
import com.android.builder.core.BuilderConstants
import com.android.builder.core.DefaultProductFlavor
import com.android.builder.core.VariantType
import groovy.transform.CompileStatic
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration

/**
 * Class containing a ProductFlavor and associated data (sourcesets)
 */
@CompileStatic
public class ProductFlavorData<T extends DefaultProductFlavor> {

    public static class ConfigurationProviderImpl implements ConfigurationProvider {

        private final Project project
        private final DefaultAndroidSourceSet sourceSet

        ConfigurationProviderImpl(Project project, DefaultAndroidSourceSet sourceSet) {
            this.project = project
            this.sourceSet = sourceSet
        }

        @Override
        @NonNull
        public Configuration getCompileConfiguration() {
            return project.configurations.getByName(sourceSet.compileConfigurationName)
        }

        @Override
        @NonNull
        public Configuration getPackageConfiguration() {
            return project.configurations.getByName(sourceSet.packageConfigurationName)
        }

        @Override
        @NonNull
        Configuration getProvidedConfiguration() {
            return project.configurations.getByName(sourceSet.providedConfigurationName)
        }
    }

    final T productFlavor

    final DefaultAndroidSourceSet sourceSet
    private final DefaultAndroidSourceSet androidTestSourceSet
    private final DefaultAndroidSourceSet unitTestSourceSet

    final ConfigurationProvider mainProvider
    private final ConfigurationProvider androidTestProvider
    private final ConfigurationProvider unitTestProvider

    final Task assembleTask

    ProductFlavorData(
            @NonNull T productFlavor,
            @NonNull DefaultAndroidSourceSet sourceSet,
            @NonNull DefaultAndroidSourceSet androidTestSourceSet,
            @NonNull DefaultAndroidSourceSet unitTestSourceSet,
            @NonNull Project project) {
        this.productFlavor = productFlavor
        this.sourceSet = sourceSet
        this.androidTestSourceSet = androidTestSourceSet
        this.unitTestSourceSet = unitTestSourceSet
        mainProvider = new ConfigurationProviderImpl(project, sourceSet)
        androidTestProvider = new ConfigurationProviderImpl(project, androidTestSourceSet)
        unitTestProvider = new ConfigurationProviderImpl(project, unitTestSourceSet)

        if (!BuilderConstants.MAIN.equals(sourceSet.name)) {
            assembleTask = project.tasks.create("assemble${sourceSet.name.capitalize()}")
            assembleTask.description = "Assembles all ${sourceSet.name.capitalize()} builds."
            assembleTask.setGroup("Build")
        } else {
            assembleTask = null
        }
    }

    @NonNull
    DefaultAndroidSourceSet getTestSourceSet(@NonNull VariantType type) {
        switch (type) {
            case VariantType.ANDROID_TEST:
                return androidTestSourceSet;
            case VariantType.UNIT_TEST:
                return unitTestSourceSet;
            default:
                throw unknownTestType(type)
        }
    }

    ConfigurationProvider getTestConfigurationProvider(@NonNull VariantType type) {
        switch (type) {
            case VariantType.ANDROID_TEST:
                return androidTestProvider;
            case VariantType.UNIT_TEST:
                return unitTestProvider;
            default:
                throw unknownTestType(type)
        }
    }

    private static Throwable unknownTestType(VariantType type) {
        throw new IllegalArgumentException(
                String.format("Unknown test variant type %s", type));
    }
}
