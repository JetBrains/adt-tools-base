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
package com.android.build.gradle.tasks

import com.android.annotations.NonNull
import com.android.build.gradle.internal.LoggingUtil
import com.android.build.gradle.internal.dependency.SymbolFileProviderImpl
import com.android.build.gradle.internal.dsl.AaptOptions
import com.android.build.gradle.internal.scope.ConventionMappingHelper
import com.android.build.gradle.internal.scope.TaskConfigAction
import com.android.build.gradle.internal.scope.VariantOutputScope
import com.android.build.gradle.internal.tasks.IncrementalTask
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.build.gradle.internal.variant.BaseVariantOutputData
import com.android.builder.core.AaptPackageProcessBuilder
import com.android.builder.core.VariantConfiguration
import com.android.builder.core.VariantType
import com.android.builder.dependency.LibraryDependency
import com.google.common.collect.Iterators
import com.google.common.collect.Lists
import org.gradle.api.logging.Logging
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.ParallelizableTask

import static com.android.builder.model.AndroidProject.FD_GENERATED
import static com.android.builder.model.AndroidProject.FD_INTERMEDIATES

@ParallelizableTask
public class ProcessAndroidResources extends IncrementalTask {

    // ----- PUBLIC TASK API -----

    @InputFile
    File manifestFile

    @InputDirectory
    File resDir

    @InputDirectory @Optional
    File assetsDir

    @OutputDirectory @Optional
    File sourceOutputDir

    @OutputDirectory @Optional
    File textSymbolOutputDir

    @OutputFile @Optional
    File packageOutputFile

    @OutputFile @Optional
    File proguardOutputFile

    @Input
    Collection<String> resourceConfigs

    @Input @Optional
    String preferredDensity

    // ----- PRIVATE TASK API -----
    @Input
    String getBuildToolsVersion() {
        getBuildTools().getRevision()
    }

    @Nested @Optional
    List<SymbolFileProviderImpl> libraries

    @Input @Optional
    String packageForR

    @Nested @Optional
    Collection<String> splits

    @Input
    boolean enforceUniquePackageName

    // this doesn't change from one build to another, so no need to annotate
    VariantType type

    @Input
    boolean debuggable

    @Input
    boolean pseudoLocalesEnabled

    @Nested
    AaptOptions aaptOptions

    private boolean isSplitPackage(File file, File resBaseName) {
        if (file.getName().startsWith(resBaseName.getName())) {
            for (String split : splits) {
                if (file.getName().contains(split)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    protected void doFullTaskAction() {
        // we have to clean the source folder output in case the package name changed.
        File srcOut = getSourceOutputDir()
        if (srcOut != null) {
            emptyFolder(srcOut)
        }

        File resOutBaseNameFile = getPackageOutputFile()

        // we have to check the resource output folder in case some splits were removed, we should
        // manually remove them.
        File packageOutputFolder = getResDir()
        if (resOutBaseNameFile != null) {
            for (File file : packageOutputFolder.listFiles()) {
                if (!isSplitPackage(file, resOutBaseNameFile)) {
                    file.delete();
                }
            }
        }

        AaptPackageProcessBuilder aaptPackageCommandBuilder =
                new AaptPackageProcessBuilder(getManifestFile(), getAaptOptions())
                    .setAssetsFolder(getAssetsDir())
                    .setResFolder(getResDir())
                    .setLibraries(getLibraries())
                    .setPackageForR(getPackageForR())
                    .setSourceOutputDir(srcOut?.absolutePath)
                    .setSymbolOutputDir(getTextSymbolOutputDir()?.absolutePath)
                    .setResPackageOutput(resOutBaseNameFile?.absolutePath)
                    .setProguardOutput(getProguardOutputFile()?.absolutePath)
                    .setType(getType())
                    .setDebuggable(getDebuggable())
                    .setPseudoLocalesEnabled(getPseudoLocalesEnabled())
                    .setResourceConfigs(getResourceConfigs())
                    .setSplits(getSplits())
                    .setPreferredDensity(getPreferredDensity())

        getBuilder().processResources(
                aaptPackageCommandBuilder,
                getEnforceUniquePackageName())
    }

    public static class ConfigAction implements TaskConfigAction<ProcessAndroidResources> {

        VariantOutputScope scope;
        File symbolLocation;
        boolean generateResourcePackage;

        ConfigAction(VariantOutputScope scope, File symbolLocation, generateResourcePackage) {
            this.scope = scope
            this.symbolLocation = symbolLocation
            this.generateResourcePackage = generateResourcePackage
        }

        @Override
        String getName() {
            return scope.getTaskName("process", "Resources")
        }

        @Override
        Class<ProcessAndroidResources> getType() {
            return ProcessAndroidResources.class;
        }

        @Override
        void execute(ProcessAndroidResources processResources) {
            BaseVariantOutputData variantOutputData = scope.variantOutputData
            BaseVariantData variantData = scope.variantScope.variantData
            variantOutputData.processResourcesTask = processResources
            VariantConfiguration config = variantData.getVariantConfiguration()

            processResources.androidBuilder = scope.globalScope.androidBuilder

            if (variantData.getSplitHandlingPolicy() ==
                    BaseVariantData.SplitHandlingPolicy.RELEASE_21_AND_AFTER_POLICY) {
                Set<String> allFilters = new HashSet<>();
                allFilters.addAll(variantData.getFilters(com.android.build.OutputFile.FilterType.DENSITY))
                allFilters.addAll(variantData.getFilters(com.android.build.OutputFile.FilterType.LANGUAGE))
                processResources.splits = allFilters;
            }

            // only generate code if the density filter is null, and if we haven't generated
            // it yet (if you have abi + density splits, then several abi output will have no
            // densityFilter)
            if (variantOutputData.getMainOutputFile().getFilter(com.android.build.OutputFile.DENSITY) == null
                    && variantData.generateRClassTask == null) {
                variantData.generateRClassTask = processResources
                processResources.enforceUniquePackageName = scope.globalScope.getExtension().getEnforceUniquePackageName()

                ConventionMappingHelper.map(processResources, "libraries") {
                    getTextSymbolDependencies(config.allLibraries)
                }
                ConventionMappingHelper.map(processResources, "packageForR") {
                    config.originalApplicationId
                }

                // TODO: unify with generateBuilderConfig, compileAidl, and library packaging somehow?
                ConventionMappingHelper.map(processResources, "sourceOutputDir") {
                    scope.getVariantScope().getRClassSourceOutputDir();
                }

                ConventionMappingHelper.map(processResources, "textSymbolOutputDir") {
                    symbolLocation
                }

                if (config.buildType.isMinifyEnabled()) {
                    if (config.buildType.shrinkResources && config.useJack) {
                        LoggingUtil.displayWarning(Logging.getLogger(this.class), scope.globalScope.project,
                                "shrinkResources does not yet work with useJack=true")
                    }
                    ConventionMappingHelper.map(processResources, "proguardOutputFile") {
                        new File(
                                "$scope.globalScope.buildDir/${FD_INTERMEDIATES}/proguard-rules/${config.dirName}/aapt_rules.txt")
                    }
                } else if (config.buildType.shrinkResources) {
                    LoggingUtil.displayWarning(Logging.getLogger(this.class), scope.globalScope.project,
                            "To shrink resources you must also enable ProGuard")
                }
            }

            ConventionMappingHelper.map(processResources, "manifestFile") {
                variantOutputData.manifestProcessorTask.getOutputFile()
            }

            ConventionMappingHelper.map(processResources, "resDir") {
                variantData.finalResourcesDir
            }

            ConventionMappingHelper.map(processResources, "assetsDir") {
                variantData.mergeAssetsTask.outputDir
            }

            if (generateResourcePackage) {
                ConventionMappingHelper.map(processResources, "packageOutputFile") {
                    scope.getProcessResourcePackageOutputFile()
                }
            }

            ConventionMappingHelper.map(processResources, "type") { config.type }
            ConventionMappingHelper.map(processResources, "debuggable") { config.buildType.debuggable }
            ConventionMappingHelper.map(processResources, "aaptOptions") { scope.globalScope.getExtension().aaptOptions }
            ConventionMappingHelper.map(processResources, "pseudoLocalesEnabled") { config.buildType.pseudoLocalesEnabled }

            ConventionMappingHelper.map(processResources, "resourceConfigs") {
                Collection<String> resConfigs = config.mergedFlavor.resourceConfigurations;
                if (resConfigs != null && resConfigs.size() == 1
                        && Iterators.getOnlyElement(resConfigs.iterator()).equals("auto")) {

                    return variantData.discoverListOfResourceConfigs();
                }
                return config.mergedFlavor.resourceConfigurations
            }

            ConventionMappingHelper.map(processResources, "preferredDensity") {
                variantOutputData.getMainOutputFile().getFilter(com.android.build.OutputFile.DENSITY)
            }

        }

        private static <T> Set<T> removeAllNullEntries(Set<T> input) {
            HashSet<T> output = new HashSet<T>();
            for (T element : input) {
                if (element != null) {
                    output.add(element);
                }
            }
            return output;
        }

        @NonNull
        private static List<SymbolFileProviderImpl> getTextSymbolDependencies(
                List<LibraryDependency> libraries) {

            List<SymbolFileProviderImpl> list = Lists.newArrayListWithCapacity(libraries.size())

            for (LibraryDependency lib : libraries) {
                list.add(new SymbolFileProviderImpl(lib.manifest, lib.symbolFile))
            }

            return list
        }
    }
}
