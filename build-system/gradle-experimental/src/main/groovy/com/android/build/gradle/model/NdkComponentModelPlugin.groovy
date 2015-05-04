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

import com.android.build.gradle.internal.ProductFlavorCombo
import com.android.build.gradle.managed.BuildType
import com.android.build.gradle.ndk.managed.NdkConfig
import com.android.build.gradle.ndk.internal.NdkConfiguration
import com.android.build.gradle.ndk.internal.NdkExtensionConvention
import com.android.build.gradle.ndk.internal.NdkHandler
import com.android.build.gradle.ndk.internal.ToolchainConfiguration
import com.android.builder.core.VariantConfiguration
import com.android.builder.model.ProductFlavor
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.internal.project.ProjectIdentifier
import org.gradle.api.tasks.TaskContainer
import org.gradle.language.c.CSourceSet
import org.gradle.language.cpp.CppSourceSet
import org.gradle.model.Finalize
import org.gradle.model.Model
import org.gradle.model.Mutate
import org.gradle.model.Path
import org.gradle.model.RuleSource
import org.gradle.model.collection.ManagedSet
import org.gradle.nativeplatform.BuildTypeContainer
import org.gradle.nativeplatform.FlavorContainer
import org.gradle.nativeplatform.NativeLibraryBinarySpec
import org.gradle.nativeplatform.NativeLibrarySpec
import org.gradle.nativeplatform.SharedLibraryBinarySpec
import org.gradle.nativeplatform.StaticLibraryBinary
import org.gradle.nativeplatform.toolchain.NativeToolChainRegistry
import org.gradle.platform.base.BinaryContainer
import org.gradle.platform.base.BinarySpec
import org.gradle.platform.base.ComponentSpecContainer
import org.gradle.platform.base.PlatformContainer

/**
 * Plugin for Android NDK applications.
 */
class NdkComponentModelPlugin implements Plugin<Project> {
    private Project project

    void apply(Project project) {
        this.project = project

        project.apply plugin: AndroidComponentModelPlugin

        project.apply plugin: 'c'
        project.apply plugin: 'cpp'

        // Remove static library tasks from assemble
        project.binaries.withType(StaticLibraryBinary) {
            it.buildable = false
        }
    }

    @SuppressWarnings("GrMethodMayBeStatic")
    static class Rules extends RuleSource {
        @Mutate
        void initializeNdkConfig(@Path("android.ndk") NdkConfig ndk) {
            ndk.moduleName = ""
            ndk.compileSdkVersion = ""
            ndk.toolchain = ""
            ndk.toolchainVersion = ""
            ndk.CFlags = ""
            ndk.cppFlags = ""
            ndk.stl = ""
            ndk.renderscriptNdkMode = false
        }

        @Finalize
        void setDefaultNdkExtensionValue(@Path("android.ndk") NdkConfig ndkConfig) {
            NdkExtensionConvention.setExtensionDefault(ndkConfig)
        }

        @Mutate
        void addDefaultNativeSourceSet(@Path("android.sources") AndroidComponentModelSourceSet sources) {
            sources.addDefaultSourceSet("c", CSourceSet.class);
            sources.addDefaultSourceSet("cpp", CppSourceSet.class);
        }

        @Model
        NdkHandler ndkHandler(
                ProjectIdentifier projectId,
                @Path("android.ndk") NdkConfig ndkConfig) {
            while (projectId.parentIdentifier != null) {
                projectId = projectId.parentIdentifier
            }
            return new NdkHandler(projectId.projectDir, ndkConfig)
        }

        @Mutate
        void createAndroidPlatforms(PlatformContainer platforms, NdkHandler ndkHandler) {
            // Create android platforms.
            ToolchainConfiguration.configurePlatforms(platforms, ndkHandler)
        }

        @Mutate
        void createToolchains(
                NativeToolChainRegistry toolchains,
                @Path("android.ndk") NdkConfig ndkConfig,
                NdkHandler ndkHandler) {
            // Create toolchain for each ABI.
            ToolchainConfiguration.configureToolchain(
                    toolchains,
                    ndkConfig.getToolchain(),
                    ndkConfig.getToolchainVersion(),
                    ndkHandler)
        }

        @Mutate
        void createNativeBuildTypes(
                BuildTypeContainer nativeBuildTypes,
                @Path("android.buildTypes") ManagedSet<BuildType> androidBuildTypes) {
            for (def buildType : androidBuildTypes) {
                nativeBuildTypes.maybeCreate(buildType.name)
            }
        }

        @Mutate
        void createNativeFlavors(
                FlavorContainer nativeFlavors,
                List<ProductFlavorCombo> androidFlavorGroups) {
            for (def group : androidFlavorGroups) {
                nativeFlavors.maybeCreate(group.name)
            }
        }

        @Mutate
        void createNativeLibrary(
                ComponentSpecContainer specs,
                @Path("android.ndk") NdkConfig ndkConfig,
                NdkHandler ndkHandler,
                @Path("android.sources") AndroidComponentModelSourceSet sources,
                @Path("buildDir") File buildDir) {
            if (!ndkConfig.moduleName.isEmpty()) {
                NativeLibrarySpec library = specs.create(ndkConfig.moduleName, NativeLibrarySpec)
                specs.withType(DefaultAndroidComponentSpec) { androidSpec ->
                    androidSpec.nativeLibrary = library
                    NdkConfiguration.configureProperties(
                            library, sources, buildDir, ndkConfig, ndkHandler)
                }
            }
        }
        @Mutate
        void createAdditionalTasksForNatives(
                TaskContainer tasks,
                ComponentSpecContainer specs,
                @Path("android.ndk") NdkConfig ndkConfig,
                NdkHandler ndkHandler,
                @Path("buildDir") File buildDir) {
            specs.withType(DefaultAndroidComponentSpec) { androidSpec ->
                if (androidSpec.nativeLibrary != null) {
                    androidSpec.nativeLibrary.binaries.withType(SharedLibraryBinarySpec) { binary ->
                        NdkConfiguration.createTasks(
                                tasks, binary, buildDir, ndkConfig, ndkHandler)
                    }
                }
            }
        }

        @Mutate
        void attachNativeBinaryToAndroid(
                BinaryContainer binaries,
                ComponentSpecContainer specs,
                AndroidComponentSpec androidSpec,
                @Path("android.ndk") NdkConfig ndkConfig) {
            if (!ndkConfig.moduleName.isEmpty()) {
                NativeLibrarySpec library =
                        specs.withType(NativeLibrarySpec).getByName(ndkConfig.moduleName);
                binaries.withType(DefaultAndroidBinary).each { binary ->
                    def nativeBinaries = getNativeBinaries(library, binary.buildType, binary.productFlavors)
                    binary.getNativeBinaries().addAll(nativeBinaries)
                }
            }
        }

        @Finalize
        void attachNativeTasksToAssembleTasks(BinaryContainer binaries) {
            binaries.withType(DefaultAndroidBinary) { binary ->
                if (binary.targetAbi.isEmpty())  {
                    binary.builtBy(binary.nativeBinaries)
                } else {
                    for (NativeLibraryBinarySpec nativeBinary : binary.nativeBinaries) {
                        if (binary.targetAbi.contains(nativeBinary.targetPlatform.name)) {
                            binary.builtBy(nativeBinary)
                        }
                    }
                }
            }
        }

        /**
         * Remove unintended tasks created by Gradle native plugin from task list.
         *
         * Gradle native plugins creates static library tasks automatically.  This method removes them
         * to avoid cluttering the task list.
         */
        @Mutate
        void hideNativeTasks(TaskContainer tasks, BinaryContainer binaries) {
            // Gradle do not support a way to remove created tasks.  The best workaround is to clear the
            // group of the task and have another task depends on it.  Therefore, we have to create
            // a dummy task to depend on all the tasks that we do not want to show up on the task
            // list. The dummy task dependsOn itself, effectively making it non-executable and
            // invisible unless the --all option is use.
            Task nonExecutableTask = tasks.create("nonExecutableTask")
            nonExecutableTask.dependsOn nonExecutableTask
            nonExecutableTask.description =
                    "Dummy task to hide other unwanted tasks in the task list."

            binaries.withType(NativeLibraryBinarySpec) { binary ->
                Task buildTask = binary.getBuildTask()
                nonExecutableTask.dependsOn buildTask
                buildTask.group = null
            }
        }
    }

    private static Collection<SharedLibraryBinarySpec> getNativeBinaries(
            NativeLibrarySpec library,
            com.android.builder.model.BuildType buildType,
            List<? extends ProductFlavor> productFlavors) {
        ProductFlavorCombo flavorGroup = new ProductFlavorCombo(productFlavors);
        library.binaries.withType(SharedLibraryBinarySpec).matching { binary ->
            (binary.buildType.name.equals(buildType.name)
                    && ((productFlavors.isEmpty() && binary.flavor.name.equals("default"))
                        || binary.flavor.name.equals(flavorGroup.name)))
        }
    }

    /**
     * Return library binaries for a VariantConfiguration.
     */
    public Collection<BinarySpec> getBinaries(VariantConfiguration variantConfig) {
        if (variantConfig.getType().isForTesting()) {
            // Do not return binaries for test variants as test source set is not supported at the
            // moment.
            return []
        }

        project.binaries.withType(SharedLibraryBinarySpec).matching { binary ->
            (binary.buildType.name.equals(variantConfig.getBuildType().getName())
                    && (binary.flavor.name.equals(variantConfig.getFlavorName())
                            || (binary.flavor.name.equals("default")
                                    && variantConfig.getFlavorName().isEmpty()))
                    && (variantConfig.getNdkConfig().getAbiFilters() == null
                            || variantConfig.getNdkConfig().getAbiFilters().isEmpty()
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

