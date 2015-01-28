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

import com.android.build.gradle.BaseExtension
import com.android.build.gradle.BasePlugin
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.internal.LibraryTaskManager
import com.android.build.gradle.internal.DependencyManager
import com.android.build.gradle.internal.TaskManager
import com.android.build.gradle.internal.variant.ApplicationVariantFactory
import com.android.build.gradle.internal.variant.LibraryVariantFactory
import com.android.build.gradle.internal.variant.VariantFactory
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.service.ServiceRegistry
import org.gradle.model.Model
import org.gradle.model.RuleSource
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry

/**
 * Gradle component model plugin class for 'application' projects.
 */
public class LibraryComponentModelPlugin implements Plugin<Project> {
    /**
     * Default assemble task for the default-published artifact. this is needed for
     * the prepare task on the consuming project.
     */
    Task assembleDefault

    @Override
    void apply(Project project) {
        project.plugins.apply(InitializationPlugin)
        project.plugins.apply(BaseComponentModelPlugin)

        assembleDefault = project.tasks.create("assembleDefault")
    }

    private static class InitializationPlugin implements Plugin<Project> {
        @Override
        void apply(Project project) {
        }

        @RuleSource
        static class Rules {

            @Model
            Boolean isApplication() {
                return false
            }

            @Model
            TaskManager createTaskManager(
                    BaseExtension androidExtension,
                    Project project,
                    BasePlugin plugin,
                    ToolingModelBuilderRegistry toolingRegistry) {
                DependencyManager dependencyManager = new DependencyManager(project, plugin.extraModelInfo)

                return new LibraryTaskManager(
                        project,
                        project.tasks,
                        plugin.androidBuilder,
                        androidExtension,
                        plugin.sdkHandler,
                        dependencyManager,
                        toolingRegistry)
            }

            @Model
            VariantFactory createVariantFactory(
                    ServiceRegistry serviceRegistry,
                    BasePlugin plugin,
                    BaseExtension extension) {
                Instantiator instantiator = serviceRegistry.get(Instantiator.class);
                return new LibraryVariantFactory(
                        instantiator,
                        plugin.androidBuilder,
                        (LibraryExtension) extension);
            }
        }
    }
}
