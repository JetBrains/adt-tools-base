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

package com.android.build.gradle.internal.variant
import com.android.SdkConstants
import com.android.annotations.NonNull
import com.android.annotations.Nullable
import com.android.build.gradle.BasePlugin
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.internal.api.LibraryVariantImpl
import com.android.build.gradle.internal.tasks.MergeFileTask
import com.android.build.gradle.tasks.MergeResources
import com.android.builder.BuilderConstants
import com.android.builder.DefaultBuildType
import com.android.builder.VariantConfiguration
import com.android.builder.dependency.LibraryBundle
import com.android.builder.dependency.LibraryDependency
import com.android.builder.dependency.ManifestDependency
import com.android.builder.model.AndroidLibrary
import com.google.common.collect.Sets
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.plugins.MavenPlugin
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.bundling.Zip
import org.gradle.tooling.BuildException

import static com.android.SdkConstants.LIBS_FOLDER
import static com.android.build.gradle.BasePlugin.DIR_BUNDLES
/**
 */
public class LibraryVariantFactory implements VariantFactory {

    @NonNull
    private final BasePlugin basePlugin
    @NonNull
    private final LibraryExtension extension

    public LibraryVariantFactory(@NonNull BasePlugin basePlugin,
            @NonNull LibraryExtension extension) {
        this.extension = extension
        this.basePlugin = basePlugin
    }

    @Override
    @NonNull
    public BaseVariantData createVariantData(@NonNull VariantConfiguration variantConfiguration) {
        return new LibraryVariantData(variantConfiguration)
    }

    @Override
    @NonNull
    public BaseVariant createVariantApi(@NonNull BaseVariantData variantData) {
        return basePlugin.getInstantiator().newInstance(LibraryVariantImpl.class, variantData)
    }

    @NonNull
    @Override
    public VariantConfiguration.Type getVariantConfigurationType() {
        return VariantConfiguration.Type.LIBRARY
    }

    @Override
    boolean isVariantPublished() {
        return true
    }

    @Override
    boolean isLibrary() {
        return true
    }

    @Override
    public void createTasks(@NonNull BaseVariantData variantData, @Nullable Task assembleTask) {
        LibraryVariantData libVariantData = variantData as LibraryVariantData
        VariantConfiguration variantConfig = variantData.variantConfiguration
        DefaultBuildType buildType = variantConfig.buildType

        String fullName = variantConfig.fullName
        String dirName = variantConfig.dirName
        Project project = basePlugin.project

        basePlugin.createAnchorTasks(variantData)

        basePlugin.createCheckManifestTask(variantData)

        // Add a task to create the res values
        basePlugin.createGenerateResValuesTask(variantData)

        // Add a task to process the manifest(s)
        basePlugin.createProcessManifestTask(variantData, DIR_BUNDLES)

        // Add a task to compile renderscript files.
        basePlugin.createRenderscriptTask(variantData)

        // Create a merge task to only merge the resources from this library and not
        // the dependencies. This is what gets packaged in the aar.
        MergeResources packageRes = basePlugin.basicCreateMergeResourcesTask(variantData,
                "package",
                "$project.buildDir/$DIR_BUNDLES/${dirName}/res",
                false /*includeDependencies*/,
                false /*process9Patch*/)

        if (variantData.variantDependency.androidDependencies.isEmpty()) {
            // if there is no android dependencies, then we should use the packageRes task above
            // as the only res merging task.
            variantData.mergeResourcesTask = packageRes
        } else {
            // Add a task to merge the resource folders, including the libraries, in order to
            // generate the R.txt file with all the symbols, including the ones from
            // the dependencies.
            basePlugin.createMergeResourcesTask(variantData, false /*process9Patch*/)
        }

        // Add a task to merge the assets folders
        basePlugin.createMergeAssetsTask(variantData,
                "$project.buildDir/$DIR_BUNDLES/${dirName}/assets",
                false /*includeDependencies*/)

        // Add a task to create the BuildConfig class
        basePlugin.createBuildConfigTask(variantData)

        // Add a task to generate resource source files, directing the location
        // of the r.txt file to be directly in the bundle.
        basePlugin.createProcessResTask(variantData,
                "$project.buildDir/$DIR_BUNDLES/${dirName}",
                false /*generateResourcePackage*/)

        // process java resources
        basePlugin.createProcessJavaResTask(variantData)

        basePlugin.createAidlTask(variantData)

        // Add a compile task
        basePlugin.createCompileTask(variantData, null/*testedVariant*/)

        // Add NDK tasks
        basePlugin.createNdkTasks(variantData);

        // package the prebuilt native libs into the bundle folder
        Sync packageJniLibs = project.tasks.create(
                "package${fullName.capitalize()}JniLibs",
                Sync)
        packageJniLibs.dependsOn variantData.ndkCompileTask
        // package from 3 sources.
        packageJniLibs.from(variantConfig.jniLibsList).include("**/*.so")
        packageJniLibs.from(variantData.ndkCompileTask.soFolder).include("**/*.so")
        packageJniLibs.into(project.file(
                "$project.buildDir/$DIR_BUNDLES/${dirName}/jni"))

        // package the aidl files into the bundle folder
        Sync packageAidl = basePlugin.project.tasks.create(
                "package${fullName.capitalize()}Aidl",
                Sync)
        // packageAidl from 3 sources. the order is important to make sure the override works well.
        packageAidl.from(variantConfig.aidlSourceList).include("**/*.aidl")
        packageAidl.into(basePlugin.project.file(
                "$basePlugin.project.buildDir/$DIR_BUNDLES/${dirName}/$SdkConstants.FD_AIDL"))

        // package the renderscript header files files into the bundle folder
        Sync packageRenderscript = project.tasks.create(
                "package${fullName.capitalize()}Renderscript",
                Sync)
        // package from 3 sources. the order is important to make sure the override works well.
        packageRenderscript.from(variantConfig.renderscriptSourceList).include("**/*.rsh")
        packageRenderscript.into(project.file(
                "$project.buildDir/$DIR_BUNDLES/${dirName}/$SdkConstants.FD_RENDERSCRIPT"))

        // merge consumer proguard files from different build types and flavors
        MergeFileTask mergeProGuardFileTask = project.tasks.create(
                "merge${fullName.capitalize()}ProguardFiles",
                MergeFileTask)
        mergeProGuardFileTask.conventionMapping.inputFiles = {
            project.files(variantConfig.getConsumerProguardFiles()).files }
        mergeProGuardFileTask.conventionMapping.outputFile = {
            project.file(
                    "$project.buildDir/$DIR_BUNDLES/${dirName}/$LibraryBundle.FN_PROGUARD_TXT")
        }

        // copy lint.jar into the bundle folder
        Copy lintCopy = project.tasks.create(
                "copy${fullName.capitalize()}Lint",
                Copy)
        lintCopy.dependsOn basePlugin.lintCompile
        lintCopy.from("$project.buildDir/lint/lint.jar")
        lintCopy.into("$project.buildDir/$DIR_BUNDLES/$dirName")

        Zip bundle = project.tasks.create(
                "bundle${fullName.capitalize()}",
                Zip)

        if (variantConfig.buildType.runProguard) {
            // run proguard on output of compile task
            basePlugin.createProguardTasks(variantData, null)

            // hack since bundle can't depend on variantData.proguardTask
            mergeProGuardFileTask.dependsOn variantData.proguardTask

            bundle.dependsOn packageRes, packageAidl, packageRenderscript, mergeProGuardFileTask, lintCopy, packageJniLibs
        } else {
            Sync packageLocalJar = project.tasks.create(
                    "package${fullName.capitalize()}LocalJar",
                    Sync)
            packageLocalJar.from(BasePlugin.getLocalJarFileList(variantData.variantDependency))
            packageLocalJar.into(project.file(
                    "$project.buildDir/$DIR_BUNDLES/${dirName}/$LIBS_FOLDER"))

            // jar the classes.
            Jar jar = project.tasks.create("package${fullName.capitalize()}Jar", Jar);
            jar.dependsOn variantData.javaCompileTask, variantData.processJavaResourcesTask
            jar.from(variantData.javaCompileTask.outputs);
            jar.from(variantData.processJavaResourcesTask.destinationDir)

            jar.destinationDir = project.file(
                    "$project.buildDir/$DIR_BUNDLES/${dirName}")
            jar.archiveName = "classes.jar"

            String packageName = variantConfig.getPackageFromManifest()
            if (packageName == null) {
                throw new BuildException("Failed to read manifest", null)
            }
            packageName = packageName.replace('.', '/');

            jar.exclude(packageName + "/R.class")
            jar.exclude(packageName + "/R\$*.class")
            jar.exclude(packageName + "/Manifest.class")
            jar.exclude(packageName + "/Manifest\$*.class")
            jar.exclude(packageName + "/BuildConfig.class")

            bundle.dependsOn packageRes, jar, packageAidl, packageRenderscript, packageLocalJar,
                    mergeProGuardFileTask, lintCopy, packageJniLibs
        }

        bundle.setDescription("Assembles a bundle containing the library in ${fullName.capitalize()}.");
        bundle.destinationDir = project.file("$project.buildDir/libs")
        bundle.extension = BuilderConstants.EXT_LIB_ARCHIVE
        bundle.from(project.file("$project.buildDir/$DIR_BUNDLES/${dirName}"))

        libVariantData.packageLibTask = bundle
        variantData.outputFile = bundle.archivePath

        if (extension.defaultPublishConfig.equals(fullName)) {
            setupDefaultConfig(project, variantData.variantDependency.packageConfiguration)

            // add the artifact that will be published
            project.artifacts.add("default", bundle)
        }

        // also publish the artifact with its full config name
        if (extension.publishNonDefault) {
            project.artifacts.add(variantData.variantDependency.publishConfiguration.name, bundle)
            bundle.classifier = variantData.variantDependency.publishConfiguration.name
        }

        if (assembleTask == null) {
            assembleTask = basePlugin.createAssembleTask(variantData)
        }
        assembleTask.dependsOn bundle
        variantData.assembleTask = assembleTask

        // configure the variant to be testable.
        variantConfig.output = new LibraryBundle(
                bundle.archivePath,
                project.file("$project.buildDir/$DIR_BUNDLES/${dirName}"),
                variantData.getName()) {

            @Override
            @Nullable
            String getProject() {
                return project.path
            }

            @Override
            @Nullable
            String getProjectVariant() {
                return variantData.getName()
            }

            @NonNull
            @Override
            List<LibraryDependency> getDependencies() {
                return variantConfig.directLibraries
            }

            @NonNull
            @Override
            List<? extends AndroidLibrary> getLibraryDependencies() {
                return variantConfig.directLibraries
            }

            @NonNull
            @Override
            List<ManifestDependency> getManifestDependencies() {
                return variantConfig.directLibraries
            }
        };
    }

    private static void setupDefaultConfig(@NonNull Project project, @NonNull Configuration configuration) {
        // The library artifact is published (inter-project( for the "default" configuration so
        // we make sure "default" extends from the actual configuration used for building.
        Configuration defaultConfig = project.configurations["default"]
        defaultConfig.setExtendsFrom(Collections.singleton(configuration))

        // for the maven publication (for now), we need to manually include all the configuration
        // object in a special mapping.
        // It's not possible to put the top level config object as extended from config won't
        // be included.
        Set<Configuration> flattenedConfigs = flattenConfigurations(configuration)

        project.plugins.withType(MavenPlugin) {
            for (Configuration config : flattenedConfigs) {
                project.conf2ScopeMappings.addMapping(300,
                        project.configurations[config.name],
                        "compile")
            }
        }
    }

    /**
     * Build a set of configuration containing all the Configuration object that a given
     * configuration extends from, directly or transitively.
     *
     * @param configuration the configuration
     * @return a set of config.
     */
    private static Set<Configuration> flattenConfigurations(@NonNull Configuration configuration) {
        Set<Configuration> configs = Sets.newHashSet()
        configs.add(configuration)

        for (Configuration extend : configuration.getExtendsFrom()) {
            configs.addAll(flattenConfigurations(extend))
        }

        return configs
    }
}
