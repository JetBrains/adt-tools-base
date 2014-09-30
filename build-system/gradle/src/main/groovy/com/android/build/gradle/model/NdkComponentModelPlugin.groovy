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

import com.android.build.gradle.api.AndroidSourceDirectorySet
import com.android.build.gradle.internal.api.DefaultAndroidSourceDirectorySet
import com.android.build.gradle.ndk.NdkExtension
import com.android.build.gradle.ndk.internal.NdkConfigurationAction
import com.android.build.gradle.ndk.internal.NdkExtensionConventionAction
import com.android.build.gradle.ndk.internal.NdkHandler
import com.android.build.gradle.ndk.internal.ToolchainConfigurationAction
import com.android.builder.core.VariantConfiguration
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.internal.project.ProjectIdentifier
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.configuration.project.ProjectConfigurationActionContainer
import org.gradle.internal.reflect.Instantiator
import org.gradle.language.base.ProjectSourceSet
import org.gradle.model.Finalize
import org.gradle.model.Model
import org.gradle.model.Mutate
import org.gradle.model.RuleSource
import org.gradle.nativeplatform.BuildTypeContainer
import org.gradle.nativeplatform.NativeLibrarySpec
import org.gradle.nativeplatform.StaticLibraryBinary
import org.gradle.nativeplatform.internal.DefaultSharedLibraryBinarySpec
import org.gradle.nativeplatform.toolchain.ToolChainRegistry
import org.gradle.platform.base.ComponentSpecContainer
import org.gradle.platform.base.PlatformContainer

import javax.inject.Inject

/**
 * Plugin for Android NDK applications.
 */
class NdkComponentModelPlugin implements Plugin<Project> {

    protected Project project

    private NdkExtension extension

    private ProjectConfigurationActionContainer configurationActions

    protected Instantiator instantiator

    @Inject
    public NdkComponentModelPlugin(
            ProjectConfigurationActionContainer configurationActions,
            Instantiator instantiator) {
        this.configurationActions = configurationActions
        this.instantiator = instantiator
    }

    void apply(Project project) {
        this.project = project
        project.extensions.add("projectModel", project)

        def sourceSetContainers = project.container(AndroidSourceDirectorySet) { name ->
            instantiator.newInstance(DefaultAndroidSourceDirectorySet, name, project)
        }
        extension = instantiator.newInstance(NdkExtension, sourceSetContainers)
        project.extensions.add("android_ndk", extension)

        project.apply plugin: 'c'
        project.apply plugin: 'cpp'

        // Remove static library tasks from assemble
        project.binaries.withType(StaticLibraryBinary) {
            // TODO: Determine how to hide these task from task list.
            it.buildable = false
        }
    }

    @RuleSource
    static class Rules {
        @Model("android.ndk")
        NdkExtension createAndroidNdk(ExtensionContainer extensions) {
            return extensions.getByType(NdkExtension)
        }

        @Model
        NdkHandler ndkHandler(ProjectIdentifier projectId, NdkExtension extension) {
            while (projectId.parentIdentifier != null) {
                projectId = projectId.parentIdentifier
            }
            return new NdkHandler(projectId.projectDir, extension)
        }

        @Model
        Project projectModel(ExtensionContainer extensions) {
            return extensions.getByType(Project)
        }

        @Finalize
        void setDefaultNdkExtensionValue(NdkExtension extension) {
            NdkExtensionConventionAction.setExtensionDefault(extension)
        }

        @Mutate createAndroidPlatforms(PlatformContainer platforms, NdkHandler ndkHandler) {
            // Create android platforms.
            ToolchainConfigurationAction.configurePlatforms(platforms, ndkHandler)
        }

        @Mutate createToolchains(
                ToolChainRegistry toolchains,
                NdkExtension ndkExtension,
                NdkHandler ndkHandler) {
            // Create toolchain for each ABI.
            ToolchainConfigurationAction.configureToolchain(
                    toolchains,
                    ndkExtension.getToolchain(),
                    ndkExtension.getToolchainVersion(),
                    ndkHandler)
        }

        @Mutate
        void createNativeBuildTypes(BuildTypeContainer buildTypes) {
            NdkConfigurationAction.createBuildTypes(buildTypes)
        }

        @Mutate
        void createNativeLibrary(
                ComponentSpecContainer specs,
                NdkExtension extension,
                NdkHandler ndkHandler,
                Project project) {
            NativeLibrarySpec library = specs.create(extension.getModuleName(), NativeLibrarySpec)
            NdkConfigurationAction.configureProperties(library, project, extension, ndkHandler)
        }

        @Mutate
        void configureNativeSourceSet(ProjectSourceSet sources, NdkExtension extension) {
            NdkConfigurationAction.configureSources(sources, extension)
        }
    }

    /**
     * Return library binaries for a VariantConfiguration.
     */
    public Collection<DefaultSharedLibraryBinarySpec> getBinaries(
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
}

