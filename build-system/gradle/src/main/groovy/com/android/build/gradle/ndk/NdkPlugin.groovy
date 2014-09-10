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
package com.android.build.gradle.ndk

import com.android.build.gradle.AppPlugin
import com.android.build.gradle.BasePlugin
import com.android.build.gradle.LibraryPlugin
import com.android.build.gradle.api.AndroidSourceDirectorySet
import com.android.build.gradle.internal.api.DefaultAndroidSourceDirectorySet
import com.android.build.gradle.ndk.internal.ForwardNdkConfigurationAction
import com.android.build.gradle.ndk.internal.NdkConfigurationAction
import com.android.build.gradle.ndk.internal.NdkExtensionConventionAction
import com.android.build.gradle.ndk.internal.NdkHandler
import com.android.build.gradle.ndk.internal.ToolchainConfigurationAction
import com.android.builder.core.VariantConfiguration
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.specs.Spec
import org.gradle.configuration.project.ProjectConfigurationActionContainer
import org.gradle.internal.Actions
import org.gradle.internal.reflect.Instantiator
import org.gradle.nativebinaries.internal.DefaultSharedLibraryBinarySpec
import org.gradle.nativebinaries.internal.DefaultStaticLibraryBinarySpec

import javax.inject.Inject

/**
 * Plugin for Android NDK applications.
 */
class NdkPlugin implements Plugin<Project> {

    protected Project project

    private NdkExtension extension

    private NdkHandler ndkHandler

    private ProjectConfigurationActionContainer configurationActions

    protected Instantiator instantiator

    @Inject
    public NdkPlugin(
            ProjectConfigurationActionContainer configurationActions,
            Instantiator instantiator) {
        this.configurationActions = configurationActions
        this.instantiator = instantiator
    }

    public Instantiator getInstantiator() {
        instantiator
    }

    public NdkExtension getNdkExtension() {
        extension
    }

    void apply(Project project) {
        this.project = project

        def sourceSetContainers = project.container(AndroidSourceDirectorySet) { name ->
            instantiator.newInstance(DefaultAndroidSourceDirectorySet, name, project)
        }
        extension = instantiator.newInstance(NdkExtension, sourceSetContainers)

        if (project.extensions.findByName("android") == null) {
            project.extensions.add("android", extension)
        }
        ndkHandler = new NdkHandler(project, extension)

        project.apply plugin: 'c'
        project.apply plugin: 'cpp'

        configurationActions.add(Actions.filter(
                Actions.composite(
                        new WarnExperimental(),
                        new ForwardNdkConfigurationAction(),
                        new NdkExtensionConventionAction(),
                        new ToolchainConfigurationAction(ndkHandler, extension),
                        new NdkConfigurationAction(ndkHandler, extension)),
                new Spec<Project>() {
                    @Override
                    boolean isSatisfiedBy(Project p) {
                        BasePlugin androidPlugin = project.getPlugins().findPlugin(AppPlugin.class)
                        if (androidPlugin == null) {
                            androidPlugin = project.getPlugins().findPlugin(LibraryPlugin.class)
                        }
                        if (androidPlugin == null) {
                            return true
                        }
                        return androidPlugin.extension.useNewNativePlugin &&
                                extension.moduleName != null
                    }
                }))

        project.afterEvaluate {
            if (extension.moduleName != null) {
                hideUnwantedTasks()
            }
        }
    }

    private static class WarnExperimental implements Action<Project> {
        @Override
        public void execute(Project proj) {
            BasePlugin.displayWarning(
                    Logging.getLogger(NdkPlugin.class),
                    proj,
                    "NdkPlugin is an experimental plugin.  Future versions may not be backward " +
                    "compatible.")
        }
    }

    /**
     * Return library binaries for a VariantConfiguration.
     */
    public Collection<ProjectSharedLibraryBinary> getBinaries(
            VariantConfiguration variantConfig) {
        if (variantConfig.getType() == VariantConfiguration.Type.TEST) {
            // Do not return binaries for test variants as test source set is not supported at the
            // moment.
            return []
        }

        project.binaries.withType(DefaultSharedLibraryBinarySpec).matching { binary ->
            (binary.buildType.name.equals(variantConfig.getBuildType().getName())
                    && (binary.flavor.name.equals(variantConfig.getFlavorName())
                            || (binary.flavor.name.equals("default")
                                    && variantConfig.getFlavorName().isEmpty()))
                    && (variantConfig.getNdkConfig().getAbiFilters() == null
                            || variantConfig.getNdkConfig().getAbiFilters().contains(
                                    binary.targetPlatform.name)))
        }
    }

    /**
     * Return the output directory of the native binary tasks for a VariantConfiguration.
     */
    public Collection<File> getOutputDirectories(VariantConfiguration variantConfig) {
        // Return the parent's parent directory of the binaries' output.
        // A binary's output file is set to something in the form of
        // "/path/to/lib/platformName/libmodulename.so".  We want to return "/path/to/lib".
        (getBinaries(variantConfig)
                *.getPrimaryOutput()
                *.getParentFile()
                *.getParentFile()
                .unique())
    }

    /**
     * Remove unintended tasks created by Gradle native plugin from task list.
     *
     * Gradle native plugins creates static library tasks automatically.  This method removes them
     * to avoid cluttering the task list.
     */
    private void hideUnwantedTasks() {
        // Gradle do not support a way to remove created tasks.  The best workaround is to clear the
        // group of the task and have another task depends on it.  Therefore, we have to create
        // a dummy task to depend on all the tasks that we do not want to show up on the task
        // list. The dummy task dependsOn itself, effectively making it non-executable and
        // invisible unless the --all option is use.
        Task nonExecutableTask = project.task("nonExecutableTask")
        nonExecutableTask.dependsOn nonExecutableTask
        nonExecutableTask.description =
                "Dummy task to hide other unwanted tasks in the task list."

        project.libraries.getByName(ndkExtension.getModuleName()) {
            Iterable<Task> lifecycleTasks =
                    binaries.withType(DefaultStaticLibraryBinarySpec)*.getBuildTask()
            nonExecutableTask.dependsOn lifecycleTasks
            lifecycleTasks*.group = null
            lifecycleTasks*.enabled = false
            lifecycleTasks*.setDependsOn([])
        }
    }
}

