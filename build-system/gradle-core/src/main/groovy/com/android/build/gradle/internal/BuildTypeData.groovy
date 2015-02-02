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
import com.android.annotations.Nullable
import com.android.build.gradle.internal.api.DefaultAndroidSourceSet
import com.android.build.gradle.internal.dsl.BuildType
import com.android.builder.core.VariantType
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
/**
 * Class containing a BuildType and associated data (Sourceset for instance).
 */
class BuildTypeData implements ConfigurationProvider {

    final BuildType buildType
    final DefaultAndroidSourceSet sourceSet
    private final DefaultAndroidSourceSet unitTestSourceSet
    private final Project project

    final Task assembleTask

    BuildTypeData(
            @NonNull BuildType buildType,
            @NonNull Project project,
            @NonNull DefaultAndroidSourceSet sourceSet,
            @NonNull DefaultAndroidSourceSet unitTestSourceSet) {
        this.buildType = buildType
        this.sourceSet = sourceSet
        this.project = project
        this.unitTestSourceSet = unitTestSourceSet

        assembleTask = project.tasks.create("assemble${buildType.name.capitalize()}")
        assembleTask.description = "Assembles all ${buildType.name.capitalize()} builds."
        assembleTask.group = "Build"
    }

    @Override
    @NonNull
    Configuration getCompileConfiguration() {
        return project.configurations.getByName(sourceSet.compileConfigurationName)
    }

    @Override
    @NonNull
    Configuration getPackageConfiguration() {
        return project.configurations.getByName(sourceSet.packageConfigurationName)
    }

    @NonNull
    Configuration getProvidedConfiguration() {
        return project.configurations.getByName(sourceSet.providedConfigurationName)
    }

    @Nullable
    DefaultAndroidSourceSet getTestSourceSet(VariantType type) {
        switch (type) {
            case VariantType.UNIT_TEST:
                return unitTestSourceSet
            case VariantType.ANDROID_TEST:
                // There are no per-build-type sources for android tests.
                return null
            default:
                throw new IllegalArgumentException("Unknown test variant type $type.")
        }
    }
}
