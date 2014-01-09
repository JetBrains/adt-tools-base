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
package com.android.build.gradle
import com.android.SdkConstants
import com.android.annotations.NonNull
import com.android.annotations.Nullable
import com.android.build.gradle.api.AndroidSourceSet
import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.internal.BuildTypeData
import com.android.build.gradle.internal.ProductFlavorData
import com.android.build.gradle.internal.api.DefaultAndroidSourceSet
import com.android.build.gradle.internal.api.LibraryVariantImpl
import com.android.build.gradle.internal.api.TestVariantImpl
import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.build.gradle.internal.tasks.MergeFileTask
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.build.gradle.internal.variant.LibraryVariantData
import com.android.build.gradle.internal.variant.TestVariantData
import com.android.build.gradle.tasks.MergeResources
import com.android.builder.BuilderConstants
import com.android.builder.DefaultBuildType
import com.android.builder.VariantConfiguration
import com.android.builder.dependency.DependencyContainer
import com.android.builder.dependency.JarDependency
import com.android.builder.dependency.LibraryBundle
import com.android.builder.dependency.LibraryDependency
import com.android.builder.dependency.ManifestDependency
import com.android.builder.model.AndroidLibrary
import com.google.common.collect.Maps
import com.google.common.collect.Sets
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.plugins.MavenPlugin
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.bundling.Zip
import org.gradle.internal.reflect.Instantiator
import org.gradle.tooling.BuildException
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry

import javax.inject.Inject
/**
 * Gradle plugin class for 'library' projects.
 */
public class LibraryPlugin extends BasePlugin implements Plugin<Project> {

    LibraryExtension extension
    BuildTypeData debugBuildTypeData
    BuildTypeData releaseBuildTypeData

    @Inject
    public LibraryPlugin(Instantiator instantiator, ToolingModelBuilderRegistry registry) {
        super(instantiator, registry)
    }

    @Override
    public LibraryExtension getExtension() {
        return extension
    }

    @Override
    void apply(Project project) {
        super.apply(project)

        extension = project.extensions.create('android', LibraryExtension,
                this, (ProjectInternal) project, instantiator)
        setBaseExtension(extension);

        // create the source sets for the build type.
        // the ones for the main product flavors are handled by the base plugin.
        DefaultAndroidSourceSet debugSourceSet =
            (DefaultAndroidSourceSet) extension.sourceSetsContainer.maybeCreate(BuilderConstants.DEBUG)
        DefaultAndroidSourceSet releaseSourceSet =
            (DefaultAndroidSourceSet) extension.sourceSetsContainer.maybeCreate(BuilderConstants.RELEASE)

        debugBuildTypeData = new BuildTypeData(extension.debug, debugSourceSet, project)
        releaseBuildTypeData = new BuildTypeData(extension.release, releaseSourceSet, project)
        project.tasks.assemble.dependsOn debugBuildTypeData.assembleTask
        project.tasks.assemble.dependsOn releaseBuildTypeData.assembleTask

        createConfigurations(releaseSourceSet)
    }

    void createConfigurations(AndroidSourceSet releaseSourceSet) {
        // The library artifact is published for the "default" configuration so we make
        // sure "default" extends from the actual configuration used for building.
        project.configurations["default"].extendsFrom(
                project.configurations[mainSourceSet.getPackageConfigurationName()])
        project.configurations["default"].extendsFrom(
                project.configurations[releaseSourceSet.getPackageConfigurationName()])

        project.plugins.withType(MavenPlugin) {
            project.conf2ScopeMappings.addMapping(300,
                    project.configurations[mainSourceSet.getCompileConfigurationName()],
                    "compile")
            project.conf2ScopeMappings.addMapping(300,
                    project.configurations[releaseSourceSet.getCompileConfigurationName()],
                    "compile")
            // TODO -- figure out the package configuration
//            project.conf2ScopeMappings.addMapping(300,
//                    project.configurations[mainSourceSet.getPackageConfigurationName()],
//                    "runtime")
//            project.conf2ScopeMappings.addMapping(300,
//                    project.configurations[releaseSourceSet.getPackageConfigurationName()],
//                    "runtime")
        }
    }

    @Override
    protected void doCreateAndroidTasks() {
        ProductFlavorData defaultConfigData = getDefaultConfigData()
        VariantDependencies variantDep

        LibraryVariantData debugVariantData = createLibVariant(defaultConfigData,
                debugBuildTypeData)
        LibraryVariantData releaseVariantData = createLibVariant(defaultConfigData,
                releaseBuildTypeData)

        // Add a compile lint task before library is bundled
        createLintCompileTask()

        // Need to create the tasks for these before doing the test variant as it
        // references the debug variant and its output
        createLibraryVariant(debugVariantData, false)
        createLibraryVariant(releaseVariantData, true)

        VariantConfiguration testVariantConfig = new VariantConfiguration(
                defaultConfigData.productFlavor,
                defaultConfigData.testSourceSet,
                debugBuildTypeData.buildType,
                null,
                VariantConfiguration.Type.TEST,
                debugVariantData.variantConfiguration)

        TestVariantData testVariantData = new TestVariantData(testVariantConfig, debugVariantData)
        // link the testVariant to the tested variant in the other direction
        debugVariantData.setTestVariantData(testVariantData);

        // dependencies for the test variant
        variantDep = VariantDependencies.compute(project,
                testVariantData.variantConfiguration.fullName,
                defaultConfigData.testProvider, debugVariantData.variantDependency)
        testVariantData.setVariantDependency(variantDep)

        variantDataList.add(testVariantData)

        createTestVariant(testVariantData, debugVariantData)

        // create the lint tasks.
        createLintTasks()

        // create the test tasks.
        createCheckTasks(false /*hasFlavors*/, true /*isLibrary*/)

        // Create the variant API objects after the tasks have been created!
        createApiObjects()
    }

    protected LibraryVariantData createLibVariant(@NonNull ProductFlavorData configData,
                                                  @NonNull BuildTypeData buildTypeData) {
        VariantConfiguration variantConfig = new VariantConfiguration(
                configData.productFlavor,
                configData.sourceSet,
                buildTypeData.buildType,
                buildTypeData.sourceSet,
                VariantConfiguration.Type.LIBRARY)

        LibraryVariantData variantData = new LibraryVariantData(variantConfig)

        VariantDependencies debugVariantDep = VariantDependencies.compute(
                project, variantData.variantConfiguration.fullName,
                buildTypeData, configData.mainProvider)
        variantData.setVariantDependency(debugVariantDep)

        variantDataList.add(variantData)

        return variantData
    }

    private void createLibraryVariant(@NonNull LibraryVariantData variantData,
                                      boolean publishArtifact) {
        resolveDependencies(variantData.variantDependency)
        variantData.variantConfiguration.setDependencies(variantData.variantDependency)

        VariantConfiguration variantConfig = variantData.variantConfiguration
        DefaultBuildType buildType = variantConfig.buildType

        createAnchorTasks(variantData)

        createCheckManifestTask(variantData)

        // Add a task to process the manifest(s)
        createProcessManifestTask(variantData, DIR_BUNDLES)

        // Add a task to compile renderscript files.
        createRenderscriptTask(variantData)

        // Add a task to merge the resource folders, including the libraries, in order to
        // generate the R.txt file with all the symbols, including the ones from the dependencies.
        createMergeResourcesTask(variantData, false /*process9Patch*/)

        // Create another merge task to only merge the resources from this libraries and not
        // the dependencies. This is what gets packaged in the aar.
        MergeResources packageRes = basicCreateMergeResourcesTask(variantData,
                "package",
                "$project.buildDir/$DIR_BUNDLES/${variantData.variantConfiguration.dirName}/res",
                false /*includeDependencies*/,
                false /*process9Patch*/)

        // Add a task to merge the assets folders
        createMergeAssetsTask(variantData,
                "$project.buildDir/$DIR_BUNDLES/${variantData.variantConfiguration.dirName}/assets",
                false /*includeDependencies*/)

        // Add a task to create the BuildConfig class
        createBuildConfigTask(variantData)

        // Add a task to generate resource source files, directing the location
        // of the r.txt file to be directly in the bundle.
        createProcessResTask(variantData,
                "$project.buildDir/$DIR_BUNDLES/${variantData.variantConfiguration.dirName}")

        // process java resources
        createProcessJavaResTask(variantData)

        createAidlTask(variantData)

        // Add a compile task
        createCompileTask(variantData, null/*testedVariant*/)

        // Add NDK tasks
        createNdkTasks(variantData);

        // package the prebuilt native libs into the bundle folder
        Sync packageJniLibs = project.tasks.create(
                "package${variantData.variantConfiguration.fullName.capitalize()}JniLibs",
                Sync)
        packageJniLibs.dependsOn variantData.ndkCompileTask
        // package from 3 sources.
        packageJniLibs.from(variantConfig.jniLibsList).include("**/*.so")
        packageJniLibs.from(variantData.ndkCompileTask.soFolder).include("**/*.so")
        packageJniLibs.into(project.file(
                "$project.buildDir/$DIR_BUNDLES/${variantData.variantConfiguration.dirName}/jni"))

        // package the aidl files into the bundle folder
        Sync packageAidl = project.tasks.create(
                "package${variantData.variantConfiguration.fullName.capitalize()}Aidl",
                Sync)
        // packageAidl from 3 sources. the order is important to make sure the override works well.
        packageAidl.from(variantConfig.aidlSourceList).include("**/*.aidl")
        packageAidl.into(project.file(
                "$project.buildDir/$DIR_BUNDLES/${variantData.variantConfiguration.dirName}/$SdkConstants.FD_AIDL"))

        // package the renderscript header files files into the bundle folder
        Sync packageRenderscript = project.tasks.create(
                "package${variantData.variantConfiguration.fullName.capitalize()}Renderscript",
                Sync)
        // package from 3 sources. the order is important to make sure the override works well.
        packageRenderscript.from(variantConfig.renderscriptSourceList).include("**/*.rsh")
        packageRenderscript.into(project.file(
                "$project.buildDir/$DIR_BUNDLES/${variantData.variantConfiguration.dirName}/$SdkConstants.FD_RENDERSCRIPT"))

        // merge consumer proguard files from different build types and flavors
        MergeFileTask mergeProGuardFileTask = project.tasks.create(
                "merge${variantData.variantConfiguration.fullName.capitalize()}ProguardFiles",
                MergeFileTask)
        mergeProGuardFileTask.conventionMapping.inputFiles = {
            project.files(variantConfig.getConsumerProguardFiles()).files }
        mergeProGuardFileTask.conventionMapping.outputFile = {
            project.file(
                    "$project.buildDir/$DIR_BUNDLES/${variantData.variantConfiguration.dirName}/$LibraryBundle.FN_PROGUARD_TXT")
        }

        // copy lint.jar into the bundle folder
        Copy lintCopy = project.tasks.create(
                "copy${variantData.variantConfiguration.fullName.capitalize()}Lint",
                Copy)
        lintCopy.dependsOn lintCompile
        lintCopy.from("$project.buildDir/lint/lint.jar")
        lintCopy.into("$project.buildDir/$DIR_BUNDLES/$variantData.variantConfiguration.dirName")

        Zip bundle = project.tasks.create(
                "bundle${variantData.variantConfiguration.fullName.capitalize()}",
                Zip)

        if (variantConfig.buildType.runProguard) {
            // run proguard on output of compile task
            createProguardTasks(variantData, null)

            // hack since bundle can't depend on variantData.proguardTask
            mergeProGuardFileTask.dependsOn variantData.proguardTask

            bundle.dependsOn packageRes, packageAidl, packageRenderscript, mergeProGuardFileTask, lintCopy, packageJniLibs
        } else {
            Sync packageLocalJar = project.tasks.create(
                    "package${variantData.variantConfiguration.fullName.capitalize()}LocalJar",
                    Sync)
            packageLocalJar.from(getLocalJarFileList(variantData.variantDependency))
            packageLocalJar.into(project.file(
                    "$project.buildDir/$DIR_BUNDLES/${variantData.variantConfiguration.dirName}/$SdkConstants.LIBS_FOLDER"))

            // jar the classes.
            Jar jar = project.tasks.create("package${buildType.name.capitalize()}Jar", Jar);
            jar.dependsOn variantData.javaCompileTask, variantData.processJavaResourcesTask
            jar.from(variantData.javaCompileTask.outputs);
            jar.from(variantData.processJavaResourcesTask.destinationDir)

            jar.destinationDir = project.file(
                    "$project.buildDir/$DIR_BUNDLES/${variantData.variantConfiguration.dirName}")
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

        bundle.setDescription("Assembles a bundle containing the library in ${variantData.variantConfiguration.fullName.capitalize()}.");
        bundle.destinationDir = project.file("$project.buildDir/libs")
        bundle.extension = BuilderConstants.EXT_LIB_ARCHIVE
        if (variantData.variantConfiguration.baseName != BuilderConstants.RELEASE) {
            bundle.classifier = variantData.variantConfiguration.baseName
        }
        bundle.from(project.file("$project.buildDir/$DIR_BUNDLES/${variantData.variantConfiguration.dirName}"))

        variantData.packageLibTask = bundle
        variantData.outputFile = bundle.archivePath

        if (publishArtifact) {
            // add the artifact that will be published
            project.artifacts.add("default", bundle)
            releaseBuildTypeData.assembleTask.dependsOn bundle
        } else {
            debugBuildTypeData.assembleTask.dependsOn bundle
        }

        variantData.assembleTask = bundle

        // configure the variant to be testable.
        variantConfig.output = new LibraryBundle(
                bundle.archivePath,
                project.file("$project.buildDir/$DIR_BUNDLES/${variantData.variantConfiguration.dirName}"),
                variantData.getName()) {

            @Nullable
            @Override
            String getProject() {
                return LibraryPlugin.this.project.path
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

    static Object[] getLocalJarFileList(DependencyContainer dependencyContainer) {
        Set<File> files = Sets.newHashSet()
        for (JarDependency jarDependency : dependencyContainer.localDependencies) {
            files.add(jarDependency.jarFile)
        }

        return files.toArray()
    }

    private void createTestVariant(@NonNull TestVariantData testVariantData,
                                   @NonNull LibraryVariantData testedVariantData) {

        resolveDependencies(testVariantData.variantDependency)
        testVariantData.variantConfiguration.setDependencies(testVariantData.variantDependency)

        createTestApkTasks(testVariantData, testedVariantData)
    }

    protected void createApiObjects() {
        // we always want to have the test/tested objects created at the same time
        // so that dynamic closure call on add can have referenced objects created.
        // This means some objects are created before they are processed from the loop,
        // so we store whether we have processed them or not.
        Map<BaseVariantData, BaseVariant> map = Maps.newHashMap()
        for (BaseVariantData variantData : variantDataList) {
            if (map.get(variantData) != null) {
                continue
            }

            if (variantData instanceof LibraryVariantData) {
                LibraryVariantData libVariantData = (LibraryVariantData) variantData
                createVariantApiObjects(map, libVariantData, libVariantData.testVariantData)

            } else if (variantData instanceof TestVariantData) {
                TestVariantData testVariantData = (TestVariantData) variantData
                createVariantApiObjects(map,
                        (LibraryVariantData) testVariantData.testedVariantData,
                        testVariantData)
            }
        }
    }

    private void createVariantApiObjects(@NonNull Map<BaseVariantData, BaseVariant> map,
                                         @NonNull LibraryVariantData libVariantData,
                                         @Nullable TestVariantData testVariantData) {
        LibraryVariantImpl libVariant = instantiator.newInstance(
                LibraryVariantImpl.class, libVariantData)

        TestVariantImpl testVariant = null;
        if (testVariantData != null) {
            testVariant = instantiator.newInstance(TestVariantImpl.class, testVariantData, this)
        }

        if (libVariant != null && testVariant != null) {
            libVariant.setTestVariant(testVariant)
            testVariant.setTestedVariant(libVariant)
        }

        extension.addLibraryVariant(libVariant)
        map.put(libVariantData, libVariant)

        if (testVariant != null) {
            extension.addTestVariant(testVariant)
            map.put(testVariantData, testVariant)
        }
    }
}
