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

package com.android.build.gradle.model

import com.android.build.gradle.AppExtension
import com.android.build.gradle.BasePlugin
import com.android.build.gradle.internal.TaskManager
import com.android.build.gradle.internal.variant.ApplicationVariantFactory
import com.android.build.gradle.internal.variant.VariantFactory
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.model.Model
import org.gradle.model.RuleSource

/**
 * Gradle component model plugin class for 'application' projects.
 */
public class AppComponentModelPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        project.plugins.apply(InitializationPlugin)
        project.plugins.apply(BaseComponentModelPlugin)
    }

    private static class InitializationPlugin implements Plugin<Project> {
        @Override
        void apply(Project project) {
        }

        @RuleSource
        static class Rules {

            @Model
            Boolean isApplication() {
                // TODO: Determine a better way to do this.
                return true
            }

            @Model
            VariantFactory createVariantFactory(BasePlugin plugin, TaskManager taskManager) {
                return new ApplicationVariantFactory(plugin, taskManager)
            }
        }
    }
}
