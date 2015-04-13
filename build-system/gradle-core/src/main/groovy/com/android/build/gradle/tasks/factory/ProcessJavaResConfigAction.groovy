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

package com.android.build.gradle.tasks.factory

import com.android.build.gradle.api.AndroidSourceSet
import com.android.build.gradle.internal.scope.ConventionMappingHelper
import com.android.build.gradle.internal.scope.TaskConfigAction
import com.android.build.gradle.internal.scope.VariantScope
import com.android.builder.core.BuilderConstants
import com.android.builder.core.VariantType
import com.android.builder.model.AndroidProject
import com.android.builder.model.SourceProvider
import org.gradle.api.tasks.Copy

import static com.android.builder.core.VariantType.ANDROID_TEST
import static com.android.builder.model.AndroidProject.FD_INTERMEDIATES

/**
 * Configuration Action for a ProcessJavaRes task.
 */
class ProcessJavaResConfigAction implements TaskConfigAction<Copy> {

    VariantScope scope;

    ProcessJavaResConfigAction(VariantScope scope) {
        this.scope = scope
    }

    @Override
    String getName() {
        return scope.getTaskName("process", "JavaRes");
    }

    @Override
    Class<Copy> getType() {
        return Copy.class
    }

    @Override
    void execute(Copy processResources) {
        scope.variantData.processJavaResourcesTask = processResources

        // set the input
        processResources.from(((AndroidSourceSet) scope.variantConfiguration.defaultSourceSet).resources.
                getSourceFiles())

        if (scope.variantConfiguration.type != ANDROID_TEST) {
            processResources.from(
                    ((AndroidSourceSet) scope.variantConfiguration.buildTypeSourceSet).resources.
                            getSourceFiles())
        }
        if (scope.variantConfiguration.hasFlavors()) {
            for (SourceProvider flavorSourceSet : scope.variantConfiguration.flavorSourceProviders) {
                processResources.
                        from(((AndroidSourceSet) flavorSourceSet).resources.getSourceFiles())
            }
        }

        ConventionMappingHelper.map(processResources, "destinationDir") {
            scope.getJavaResourcesDestinationDir()
        }

    }
}
