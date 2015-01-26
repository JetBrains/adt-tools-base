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

package com.android.build.gradle.internal

import com.android.annotations.NonNull
import com.android.annotations.Nullable
import com.android.build.OutputFile
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.api.AndroidSourceSet
import com.android.build.gradle.internal.core.GradleVariantConfiguration
import com.android.build.gradle.internal.coverage.JacocoInstrumentTask
import com.android.build.gradle.internal.coverage.JacocoPlugin
import com.android.build.gradle.internal.coverage.JacocoReportTask
import com.android.build.gradle.internal.dependency.LibraryDependencyImpl
import com.android.build.gradle.internal.dependency.ManifestDependencyImpl
import com.android.build.gradle.internal.dependency.SymbolFileProviderImpl
import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.build.gradle.internal.dsl.AbiSplitOptions
import com.android.build.gradle.internal.dsl.SigningConfig
import com.android.build.gradle.internal.publishing.ApkPublishArtifact
import com.android.build.gradle.internal.tasks.AndroidReportTask
import com.android.build.gradle.internal.tasks.CheckManifest
import com.android.build.gradle.internal.tasks.DependencyReportTask
import com.android.build.gradle.internal.tasks.DeviceProviderInstrumentTestLibraryTask
import com.android.build.gradle.internal.tasks.DeviceProviderInstrumentTestTask
import com.android.build.gradle.internal.tasks.GenerateApkDataTask
import com.android.build.gradle.internal.tasks.InstallVariantTask
import com.android.build.gradle.internal.tasks.MockableAndroidJarTask
import com.android.build.gradle.internal.tasks.OutputFileTask
import com.android.build.gradle.internal.tasks.PrepareDependenciesTask
import com.android.build.gradle.internal.tasks.SigningReportTask
import com.android.build.gradle.internal.tasks.TestServerTask
import com.android.build.gradle.internal.tasks.UninstallTask
import com.android.build.gradle.internal.tasks.ValidateSigningTask
import com.android.build.gradle.internal.tasks.multidex.CreateMainDexList
import com.android.build.gradle.internal.tasks.multidex.CreateManifestKeepList
import com.android.build.gradle.internal.tasks.multidex.JarMergingTask
import com.android.build.gradle.internal.tasks.multidex.RetraceMainDexList
import com.android.build.gradle.internal.test.report.ReportType
import com.android.build.gradle.internal.variant.ApkVariantData
import com.android.build.gradle.internal.variant.ApkVariantOutputData
import com.android.build.gradle.internal.variant.ApplicationVariantData
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.build.gradle.internal.variant.BaseVariantOutputData
import com.android.build.gradle.internal.variant.LibraryVariantData
import com.android.build.gradle.internal.variant.TestVariantData
import com.android.build.gradle.internal.variant.TestedVariantData
import com.android.build.gradle.tasks.AidlCompile
import com.android.build.gradle.tasks.CompatibleScreensManifest
import com.android.build.gradle.tasks.Dex
import com.android.build.gradle.tasks.GenerateBuildConfig
import com.android.build.gradle.tasks.GenerateResValues
import com.android.build.gradle.tasks.GenerateSplitAbiRes
import com.android.build.gradle.tasks.JackTask
import com.android.build.gradle.tasks.JillTask
import com.android.build.gradle.tasks.Lint
import com.android.build.gradle.tasks.MergeAssets
import com.android.build.gradle.tasks.MergeManifests
import com.android.build.gradle.tasks.MergeResources
import com.android.build.gradle.tasks.NdkCompile
import com.android.build.gradle.tasks.PackageApplication
import com.android.build.gradle.tasks.PackageSplitAbi
import com.android.build.gradle.tasks.PackageSplitRes
import com.android.build.gradle.tasks.PreDex
import com.android.build.gradle.tasks.ProcessAndroidResources
import com.android.build.gradle.tasks.ProcessManifest
import com.android.build.gradle.tasks.ProcessTestManifest
import com.android.build.gradle.tasks.RenderscriptCompile
import com.android.build.gradle.tasks.ShrinkResources
import com.android.build.gradle.tasks.SplitZipAlign
import com.android.build.gradle.tasks.ZipAlign
import com.android.builder.core.AndroidBuilder
import com.android.builder.core.VariantConfiguration
import com.android.builder.core.VariantType
import com.android.builder.dependency.LibraryDependency
import com.android.builder.internal.testing.SimpleTestCallable
import com.android.builder.model.ApiVersion
import com.android.builder.model.ProductFlavor
import com.android.builder.model.SourceProvider
import com.android.builder.testing.ConnectedDeviceProvider
import com.android.builder.testing.api.DeviceProvider
import com.android.builder.testing.api.TestServer
import com.android.sdklib.AndroidTargetHash
import com.android.sdklib.BuildToolInfo
import com.android.sdklib.IAndroidTarget
import com.android.sdklib.SdkVersionInfo
import com.google.common.collect.ImmutableSet
import com.google.common.collect.Lists
import com.google.common.collect.Sets
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.gradle.api.GradleException
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.execution.TaskExecutionGraph
import org.gradle.api.internal.ConventionMapping
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.plugins.BasePluginConvention
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.compile.AbstractCompile
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.language.jvm.tasks.ProcessResources
import org.gradle.tooling.BuildException
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry
import proguard.gradle.ProGuardTask

import static com.android.SdkConstants.FN_ANDROID_MANIFEST_XML
import static com.android.builder.core.BuilderConstants.CONNECTED
import static com.android.builder.core.BuilderConstants.DEVICE
import static com.android.builder.core.BuilderConstants.FD_ANDROID_RESULTS
import static com.android.builder.core.BuilderConstants.FD_ANDROID_TESTS
import static com.android.builder.core.BuilderConstants.FD_FLAVORS
import static com.android.builder.core.BuilderConstants.FD_FLAVORS_ALL
import static com.android.builder.core.BuilderConstants.FD_REPORTS
import static com.android.builder.core.VariantType.ANDROID_TEST
import static com.android.builder.core.VariantType.DEFAULT
import static com.android.builder.core.VariantType.UNIT_TEST
import static com.android.builder.model.AndroidProject.FD_GENERATED
import static com.android.builder.model.AndroidProject.FD_INTERMEDIATES
import static com.android.builder.model.AndroidProject.FD_OUTPUTS
import static com.android.builder.model.AndroidProject.PROPERTY_APK_LOCATION
import static com.android.sdklib.BuildToolInfo.PathId.ZIP_ALIGN
import static com.google.common.base.Preconditions.checkNotNull

/**
 * Manages tasks creation.
 */
@CompileStatic
abstract class TaskManager {

    public static final String FILE_JACOCO_AGENT = 'jacocoagent.jar'

    public static final String DEFAULT_PROGUARD_CONFIG_FILE = 'proguard-android.txt'

    public final static String DIR_BUNDLES = "bundles";

    // These need to be public for gradle-dev.
    public static final String INSTALL_GROUP = "Install"

    public static final String BUILD_GROUP = BasePlugin.BUILD_GROUP

    public static final String ANDROID_GROUP = "Android"

    final Map<SigningConfig, ValidateSigningTask> validateSigningTaskMap = [:]

    protected Project project

    private TaskContainer tasks

    protected AndroidBuilder androidBuilder

    private DependencyManager dependencyManager

    protected SdkHandler sdkHandler

    protected BaseExtension extension

    private ToolingModelBuilderRegistry toolingRegistry

    private Logger logger

    // Tasks
    private Task mainPreBuild

    private Task uninstallAll

    private Task assembleAndroidTest

    private Task deviceCheck

    private Task connectedCheck

    private Copy jacocoAgentTask

    public Task lintCompile

    protected Task lintAll

    public MockableAndroidJarTask createMockableJar

    public TaskManager(
            Project project,
            TaskContainer tasks,
            AndroidBuilder androidBuilder,
            BaseExtension extension,
            SdkHandler sdkHandler,
            DependencyManager dependencyManager,
            ToolingModelBuilderRegistry toolingRegistry) {
        this.project = project
        this.tasks = tasks
        this.androidBuilder = androidBuilder
        this.sdkHandler = sdkHandler
        this.extension = extension
        this.toolingRegistry = toolingRegistry
        this.dependencyManager = dependencyManager
        logger = Logging.getLogger(this.class)
    }

    private boolean isVerbose() {
        return project.logger.isEnabled(LogLevel.INFO)
    }

    private boolean isDebugLog() {
        return project.logger.isEnabled(LogLevel.DEBUG)
    }

    /**
     * Creates the tasks for a given BaseVariantData.
     * @param variantData the non-null BaseVariantData.
     * @param assembleTask an optional assembleTask to be used. If null, a new one is created.
     */
    abstract public void createTasksForVariantData(
            @NonNull BaseVariantData<? extends BaseVariantOutputData> variantData)

    private BaseExtension getExtension() {
        return extension
    }

    public void resolveDependencies(
            @NonNull VariantDependencies variantDeps,
            @Nullable VariantDependencies testedVariantDeps) {
        dependencyManager.resolveDependencies(variantDeps, testedVariantDeps)
    }

    public void createTasks() {
        uninstallAll = project.tasks.create("uninstallAll")
        uninstallAll.description = "Uninstall all applications."
        uninstallAll.group = INSTALL_GROUP

        deviceCheck = project.tasks.create("deviceCheck")
        deviceCheck.description = "Runs all device checks using Device Providers and Test Servers."
        deviceCheck.group = JavaBasePlugin.VERIFICATION_GROUP

        connectedCheck = project.tasks.create("connectedCheck")
        connectedCheck.description = "Runs all device checks on currently connected devices."
        connectedCheck.group = JavaBasePlugin.VERIFICATION_GROUP

        mainPreBuild = project.tasks.create("preBuild")
    }

    public void createAssembleAndroidTestTask() {
        assembleAndroidTest = project.getTasks().create("assembleAndroidTest");
        assembleAndroidTest.setGroup(BasePlugin.BUILD_GROUP);
        assembleAndroidTest.setDescription("Assembles all the Test applications.");
    }

    public void createMockableJarTask() {
        createMockableJar = project.tasks.create("mockableAndroidJar", MockableAndroidJarTask)
        createMockableJar.group = BUILD_GROUP
        createMockableJar.description = "Creates a version of android.jar that's suitable for unit tests."

        conventionMapping(createMockableJar).map("androidJar") {
            new File(androidBuilder.target.getPath(IAndroidTarget.ANDROID_JAR))
        }

        conventionMapping(createMockableJar).map("outputFile") {
            project.file(
                    "$project.buildDir/${FD_INTERMEDIATES}/mockable-${extension.compileSdkVersion}.jar")
        }

        conventionMapping(createMockableJar).map("returnDefaultValues") {
            extension.testOptions.unitTests.returnDefaultValues
        }
    }

    public void createMergeAppManifestsTask(
            @NonNull BaseVariantData<? extends BaseVariantOutputData> variantData) {

        VariantConfiguration config = variantData.variantConfiguration
        ProductFlavor mergedFlavor = config.mergedFlavor

        ApplicationVariantData appVariantData = variantData as ApplicationVariantData
        Set<String> screenSizes = appVariantData.getCompatibleScreens()

        // loop on all outputs. The only difference will be the name of the task, and location
        // of the generated manifest
        for (BaseVariantOutputData vod : variantData.outputs) {
            final CompatibleScreensManifest csmTask =
                    (vod.getMainOutputFile().getFilter(OutputFile.DENSITY) != null
                            && !screenSizes.isEmpty()) ?
                            createCompatibleScreensManifest(vod, screenSizes) :
                            null

            // create final var inside the loop to ensure the closures will work.
            final BaseVariantOutputData variantOutputData = vod

            String outputName = variantOutputData.fullName.capitalize()
            String outputDirName = variantOutputData.dirName

            def processManifestTask = project.tasks.create(
                    "process${outputName}Manifest",
                    MergeManifests)

            variantOutputData.manifestProcessorTask = processManifestTask

            processManifestTask.androidBuilder = androidBuilder

            processManifestTask.dependsOn variantData.prepareDependenciesTask
            if (variantData.generateApkDataTask != null) {
                processManifestTask.dependsOn variantData.generateApkDataTask
            }
            if (csmTask != null) {
                processManifestTask.dependsOn csmTask
            }

            processManifestTask.variantConfiguration = config
            if (variantOutputData instanceof ApkVariantOutputData) {
                processManifestTask.variantOutputData = variantOutputData as ApkVariantOutputData
            }

            conventionMapping(processManifestTask).map("libraries") {
                List<ManifestDependencyImpl> manifests =
                        getManifestDependencies(config.directLibraries)

                if (variantData.generateApkDataTask != null &&
                    variantData.getVariantConfiguration().getBuildType().isEmbedMicroApp()) {
                    manifests.add(new ManifestDependencyImpl(
                            variantData.generateApkDataTask.getManifestFile(), []))
                }

                if (csmTask != null) {
                    manifests.add(
                            new ManifestDependencyImpl(csmTask.getManifestFile(), []))
                }

                return manifests
            }

            conventionMapping(processManifestTask).map("minSdkVersion") {
                if (androidBuilder.isPreviewTarget()) {
                    return androidBuilder.getTargetCodename()
                }

                mergedFlavor.minSdkVersion?.apiString
            }

            conventionMapping(processManifestTask).map("targetSdkVersion") {
                if (androidBuilder.isPreviewTarget()) {
                    return androidBuilder.getTargetCodename()
                }

                return mergedFlavor.targetSdkVersion?.apiString
            }

            conventionMapping(processManifestTask).map("maxSdkVersion") {
                if (androidBuilder.isPreviewTarget()) {
                    return null
                }

                return mergedFlavor.maxSdkVersion
            }

            conventionMapping(processManifestTask).map("manifestOutputFile") {
                project.file(
                        "$project.buildDir/${FD_INTERMEDIATES}/manifests/full/" +
                                "${outputDirName}/AndroidManifest.xml")
            }

            conventionMapping(processManifestTask).map("reportFile") {
                project.file(
                        "$project.buildDir/${FD_OUTPUTS}/logs/manifest-merger-${config.baseName}-report.txt")
            }
        }
    }

    private CompatibleScreensManifest createCompatibleScreensManifest(
            @NonNull BaseVariantOutputData variantOutputData,
            @NonNull Set<String> screenSizes) {

        CompatibleScreensManifest csmTask = project.tasks.create(
                "create${variantOutputData.fullName.capitalize()}CompatibleScreensManifest",
                CompatibleScreensManifest)

        csmTask.screenDensity = variantOutputData.getMainOutputFile().getFilter(OutputFile.DENSITY)
        csmTask.screenSizes = screenSizes

        conventionMapping(csmTask).map("manifestFile") {
            project.file(
                    "$project.buildDir/${FD_INTERMEDIATES}/manifests/density/" +
                            "${variantOutputData.dirName}/AndroidManifest.xml")
        }

        return csmTask;
    }

    @CompileDynamic
    private static ConventionMapping conventionMapping(Task task) {
        task.conventionMapping
    }

    public void createMergeLibManifestsTask(
            @NonNull BaseVariantData<? extends BaseVariantOutputData> variantData,
            @NonNull String manifestOutDir) {
        VariantConfiguration config = variantData.variantConfiguration

        // get single output for now.
        BaseVariantOutputData variantOutputData = variantData.outputs.get(0)

        def processManifest = project.tasks.create(
                "process${variantData.variantConfiguration.fullName.capitalize()}Manifest",
                ProcessManifest)
        variantOutputData.manifestProcessorTask = processManifest
        processManifest.androidBuilder = androidBuilder

        processManifest.dependsOn variantData.prepareDependenciesTask
        processManifest.variantConfiguration = config

        ProductFlavor mergedFlavor = config.mergedFlavor

        conventionMapping(processManifest).map("minSdkVersion") {
            if (androidBuilder.isPreviewTarget()) {
                return androidBuilder.getTargetCodename()
            }
            return mergedFlavor.minSdkVersion?.apiString
        }

        conventionMapping(processManifest).map("targetSdkVersion") {
            if (androidBuilder.isPreviewTarget()) {
                return androidBuilder.getTargetCodename()
            }

            return mergedFlavor.targetSdkVersion?.apiString
        }

        conventionMapping(processManifest).map("maxSdkVersion") {
            if (androidBuilder.isPreviewTarget()) {
                return null
            }

            return mergedFlavor.maxSdkVersion
        }

        conventionMapping(processManifest).map("manifestOutputFile") {
            project.file(
                    "$project.buildDir/${FD_INTERMEDIATES}/${manifestOutDir}/" +
                            "${variantData.variantConfiguration.dirName}/AndroidManifest.xml")
        }

        conventionMapping(processManifest).map("aaptFriendlyManifestOutputFile") {
            project.file(
                    "$project.buildDir/${FD_INTERMEDIATES}/${manifestOutDir}/" +
                            "${variantData.variantConfiguration.dirName}/aapt/AndroidManifest.xml")
        }
    }

    protected void createProcessTestManifestTask(
            @NonNull BaseVariantData<? extends BaseVariantOutputData> variantData,
            @NonNull String manifestOurDir) {
        def processTestManifestTask;
        VariantConfiguration config = variantData.variantConfiguration
        processTestManifestTask = project.tasks.create(
                "process${variantData.variantConfiguration.fullName.capitalize()}Manifest",
                ProcessTestManifest)
        conventionMapping(processTestManifestTask).map("testManifestFile") {
            config.getMainManifest()
        }
        conventionMapping(processTestManifestTask).map("tmpDir") {
            project.file(
                    "$project.buildDir/${FD_INTERMEDIATES}/${manifestOurDir}/tmp")
        }

        // get single output for now.
        BaseVariantOutputData variantOutputData = variantData.outputs.get(0)

        variantOutputData.manifestProcessorTask = processTestManifestTask
        processTestManifestTask.dependsOn variantData.prepareDependenciesTask

        processTestManifestTask.androidBuilder = androidBuilder

        conventionMapping(processTestManifestTask).map("testApplicationId") {
            config.applicationId
        }
        conventionMapping(processTestManifestTask).map("minSdkVersion") {
            if (androidBuilder.isPreviewTarget()) {
                return androidBuilder.getTargetCodename()
            }

            config.minSdkVersion?.apiString
        }
        conventionMapping(processTestManifestTask).map("targetSdkVersion") {
            if (androidBuilder.isPreviewTarget()) {
                return androidBuilder.getTargetCodename()
            }

            return config.targetSdkVersion?.apiString
        }
        conventionMapping(processTestManifestTask).map("testedApplicationId") {
            config.testedApplicationId
        }
        conventionMapping(processTestManifestTask).map("instrumentationRunner") {
            config.instrumentationRunner
        }
        conventionMapping(processTestManifestTask).map("handleProfiling") {
            config.handleProfiling
        }
        conventionMapping(processTestManifestTask).map("functionalTest") {
            config.functionalTest
        }
        conventionMapping(processTestManifestTask).map("libraries") {
            getManifestDependencies(config.directLibraries)
        }
        conventionMapping(processTestManifestTask).map("manifestOutputFile") {
            project.file(
                    "$project.buildDir/${FD_INTERMEDIATES}/${manifestOurDir}/${variantData.variantConfiguration.dirName}/AndroidManifest.xml")
        }
    }

    public void createRenderscriptTask(
            @NonNull BaseVariantData<? extends BaseVariantOutputData> variantData) {
        GradleVariantConfiguration config = variantData.variantConfiguration

        // get single output for now.
        BaseVariantOutputData variantOutputData = variantData.outputs.get(0)

        def renderscriptTask = project.tasks.create(
                "compile${variantData.variantConfiguration.fullName.capitalize()}Renderscript",
                RenderscriptCompile)
        variantData.renderscriptCompileTask = renderscriptTask
        if (config.type.isForTesting()) {
            renderscriptTask.dependsOn variantOutputData.manifestProcessorTask
        } else {
            renderscriptTask.dependsOn variantData.checkManifestTask
        }

        ProductFlavor mergedFlavor = config.mergedFlavor
        boolean ndkMode = config.renderscriptNdkModeEnabled

        variantData.resourceGenTask.dependsOn renderscriptTask
        // only put this dependency if rs will generate Java code
        if (!ndkMode) {
            variantData.sourceGenTask.dependsOn renderscriptTask
        }

        renderscriptTask.dependsOn variantData.prepareDependenciesTask
        renderscriptTask.androidBuilder = androidBuilder

        conventionMapping(renderscriptTask).map("targetApi") {
            int targetApi = mergedFlavor.renderscriptTargetApi != null ?
                    mergedFlavor.renderscriptTargetApi : -1
            ApiVersion apiVersion = config.getMinSdkVersion()
            if (apiVersion != null) {
                int minSdk = apiVersion.apiLevel
                if (apiVersion.codename != null) {
                    minSdk = SdkVersionInfo.getApiByBuildCode(apiVersion.codename, true)
                }

                return targetApi > minSdk ? targetApi : minSdk
            }

            return targetApi
        }

        renderscriptTask.supportMode = config.renderscriptSupportModeEnabled
        renderscriptTask.ndkMode = ndkMode
        renderscriptTask.debugBuild = config.buildType.renderscriptDebuggable
        renderscriptTask.optimLevel = config.buildType.renderscriptOptimLevel

        conventionMapping(renderscriptTask).map("sourceDirs") { config.renderscriptSourceList }
        conventionMapping(renderscriptTask).map("importDirs") { config.renderscriptImports }

        conventionMapping(renderscriptTask).map("sourceOutputDir") {
            project.file(
                    "$project.buildDir/${FD_GENERATED}/source/rs/${variantData.variantConfiguration.dirName}")
        }
        conventionMapping(renderscriptTask).map("resOutputDir") {
            project.file(
                    "$project.buildDir/${FD_GENERATED}/res/rs/${variantData.variantConfiguration.dirName}")
        }
        conventionMapping(renderscriptTask).map("objOutputDir") {
            project.file(
                    "$project.buildDir/${FD_INTERMEDIATES}/rs/${variantData.variantConfiguration.dirName}/obj")
        }
        conventionMapping(renderscriptTask).map("libOutputDir") {
            project.file(
                    "$project.buildDir/${FD_INTERMEDIATES}/rs/${variantData.variantConfiguration.dirName}/lib")
        }
        conventionMapping(renderscriptTask).map("ndkConfig") { config.ndkConfig }
    }

    public void createMergeResourcesTask(
            @NonNull BaseVariantData<? extends BaseVariantOutputData> variantData,
            final boolean process9Patch) {
        MergeResources mergeResourcesTask = basicCreateMergeResourcesTask(
                variantData,
                "merge",
                "$project.buildDir/${FD_INTERMEDIATES}/res/${variantData.variantConfiguration.dirName}",
                true /*includeDependencies*/,
                process9Patch)
        variantData.mergeResourcesTask = mergeResourcesTask
    }

    public MergeResources basicCreateMergeResourcesTask(
            @NonNull BaseVariantData<? extends BaseVariantOutputData> variantData,
            @NonNull String taskNamePrefix,
            @NonNull String outputLocation,
            final boolean includeDependencies,
            final boolean process9Patch) {
        MergeResources mergeResourcesTask = project.tasks.create(
                "$taskNamePrefix${variantData.variantConfiguration.fullName.capitalize()}Resources",
                MergeResources)

        mergeResourcesTask.dependsOn variantData.prepareDependenciesTask,
                variantData.resourceGenTask
        mergeResourcesTask.androidBuilder = androidBuilder
        mergeResourcesTask.incrementalFolder = project.file(
                "$project.buildDir/${FD_INTERMEDIATES}/incremental/${taskNamePrefix}Resources/${variantData.variantConfiguration.dirName}")

        mergeResourcesTask.process9Patch = process9Patch
        mergeResourcesTask.crunchPng = extension.aaptOptions.getCruncherEnabled()

        conventionMapping(mergeResourcesTask).
                map("useNewCruncher") { getExtension().aaptOptions.useNewCruncher }

        conventionMapping(mergeResourcesTask).map("inputResourceSets") {
            List<File> generatedResFolders = Lists.newArrayList(
                    variantData.renderscriptCompileTask.getResOutputDir(),
                    variantData.generateResValuesTask.getResOutputDir())
            if (variantData.extraGeneratedResFolders != null) {
                generatedResFolders += variantData.extraGeneratedResFolders
            }
            if (variantData.generateApkDataTask != null &&
                    variantData.getVariantConfiguration().getBuildType().isEmbedMicroApp()) {
                generatedResFolders.add(variantData.generateApkDataTask.getResOutputDir())
            }
            variantData.variantConfiguration.getResourceSets(generatedResFolders,
                    includeDependencies)
        }

        conventionMapping(mergeResourcesTask).map("outputDir") { project.file(outputLocation) }

        return mergeResourcesTask
    }

    public void createMergeAssetsTask(
            @NonNull BaseVariantData<? extends BaseVariantOutputData> variantData,
            @Nullable String outputLocation,
            final boolean includeDependencies) {
        if (outputLocation == null) {
            outputLocation =
                    "$project.buildDir/${FD_INTERMEDIATES}/assets/${variantData.variantConfiguration.dirName}"
        }

        VariantConfiguration variantConfig = variantData.variantConfiguration

        def mergeAssetsTask = project.tasks.create(
                "merge${variantConfig.fullName.capitalize()}Assets",
                MergeAssets)
        variantData.mergeAssetsTask = mergeAssetsTask

        mergeAssetsTask.dependsOn variantData.prepareDependenciesTask, variantData.assetGenTask
        mergeAssetsTask.androidBuilder = androidBuilder
        mergeAssetsTask.incrementalFolder =
                project.file(
                        "$project.buildDir/${FD_INTERMEDIATES}/incremental/mergeAssets/${variantConfig.dirName}")

        conventionMapping(mergeAssetsTask).map("inputAssetSets") {
            def generatedAssets = []
            if (variantData.copyApkTask != null) {
                generatedAssets.add(variantData.copyApkTask.destinationDir)
            }
            variantConfig.getAssetSets(generatedAssets, includeDependencies)
        }
        conventionMapping(mergeAssetsTask).map("outputDir") { project.file(outputLocation) }
    }

    public void createBuildConfigTask(
            @NonNull BaseVariantData<? extends BaseVariantOutputData> variantData) {
        def generateBuildConfigTask = project.tasks.create(
                "generate${variantData.variantConfiguration.fullName.capitalize()}BuildConfig",
                GenerateBuildConfig)

        variantData.generateBuildConfigTask = generateBuildConfigTask

        VariantConfiguration variantConfiguration = variantData.variantConfiguration

        variantData.sourceGenTask.dependsOn generateBuildConfigTask
        if (variantConfiguration.type.isForTesting()) {
            // in case of a test project, the manifest is generated so we need to depend
            // on its creation.

            // For test apps there should be a single output, so we get it.
            BaseVariantOutputData variantOutputData = variantData.outputs.get(0)

            generateBuildConfigTask.dependsOn variantOutputData.manifestProcessorTask
        } else {
            generateBuildConfigTask.dependsOn variantData.checkManifestTask
        }

        generateBuildConfigTask.androidBuilder = androidBuilder

        conventionMapping(generateBuildConfigTask).map("buildConfigPackageName") {
            variantConfiguration.originalApplicationId
        }

        conventionMapping(generateBuildConfigTask).map("appPackageName") {
            variantConfiguration.applicationId
        }

        conventionMapping(generateBuildConfigTask).map("versionName") {
            variantConfiguration.versionName
        }

        conventionMapping(generateBuildConfigTask).map("versionCode") {
            variantConfiguration.versionCode
        }

        conventionMapping(generateBuildConfigTask).map("debuggable") {
            variantConfiguration.buildType.isDebuggable()
        }

        conventionMapping(generateBuildConfigTask).map("buildTypeName") {
            variantConfiguration.buildType.name
        }

        conventionMapping(generateBuildConfigTask).map("flavorName") {
            variantConfiguration.flavorName
        }

        conventionMapping(generateBuildConfigTask).map("flavorNamesWithDimensionNames") {
            variantConfiguration.flavorNamesWithDimensionNames
        }

        conventionMapping(generateBuildConfigTask).map("items") {
            variantConfiguration.buildConfigItems
        }

        conventionMapping(generateBuildConfigTask).map("sourceOutputDir") {
            project.file(
                    "$project.buildDir/${FD_GENERATED}/source/buildConfig/${variantData.variantConfiguration.dirName}")
        }
    }

    public void createGenerateResValuesTask(
            @NonNull BaseVariantData<? extends BaseVariantOutputData> variantData) {
        GenerateResValues generateResValuesTask = project.tasks.create(
                "generate${variantData.variantConfiguration.fullName.capitalize()}ResValues",
                GenerateResValues)
        variantData.generateResValuesTask = generateResValuesTask
        variantData.resourceGenTask.dependsOn generateResValuesTask

        VariantConfiguration variantConfiguration = variantData.variantConfiguration

        generateResValuesTask.androidBuilder = androidBuilder

        conventionMapping(generateResValuesTask).map("items") {
            variantConfiguration.resValues
        }

        conventionMapping(generateResValuesTask).map("resOutputDir") {
            project.file(
                    "$project.buildDir/${FD_GENERATED}/res/generated/${variantData.variantConfiguration.dirName}")
        }
    }

    public void createProcessResTask(
            @NonNull BaseVariantData<? extends BaseVariantOutputData> variantData,
            boolean generateResourcePackage) {
        createProcessResTask(variantData,
                "$project.buildDir/${FD_INTERMEDIATES}/symbols/${variantData.variantConfiguration.dirName}",
                generateResourcePackage)
    }

    public void createProcessResTask(
            @NonNull BaseVariantData<? extends BaseVariantOutputData> variantData,
            @NonNull final String symbolLocation,
            boolean generateResourcePackage) {

        VariantConfiguration config = variantData.variantConfiguration

        // loop on all outputs. The only difference will be the name of the task, and location
        // of the generated data.
        for (BaseVariantOutputData vod : variantData.outputs) {
            // create final var inside the loop to ensure the closures will work.
            final BaseVariantOutputData variantOutputData = vod

            String outputName = variantOutputData.fullName.capitalize()
            String outputBaseName = variantOutputData.baseName

            ProcessAndroidResources processResources = project.tasks.create(
                    "process${outputName}Resources",
                    ProcessAndroidResources)

            variantOutputData.processResourcesTask = processResources

            processResources.dependsOn variantOutputData.manifestProcessorTask,
                    variantData.mergeResourcesTask, variantData.mergeAssetsTask
            processResources.androidBuilder = androidBuilder

            if (variantData.getSplitHandlingPolicy() ==
                    BaseVariantData.SplitHandlingPolicy.RELEASE_21_AND_AFTER_POLICY) {

                Set<String> filters = new HashSet<String>(
                        getExtension().getSplits().getDensityFilters());
                filters.addAll(getExtension().getSplits().getLanguageFilters());
                filters = removeAllNullEntries(filters);
                processResources.splits = filters;
            }

            // only generate code if the density filter is null, and if we haven't generated
            // it yet (if you have abi + density splits, then several abi output will have no
            // densityFilter)
            if (variantOutputData.getMainOutputFile().getFilter(OutputFile.DENSITY) == null
                    && variantData.generateRClassTask == null) {
                variantData.generateRClassTask = processResources
                variantData.sourceGenTask.dependsOn processResources
                processResources.enforceUniquePackageName = getExtension().getEnforceUniquePackageName()

                conventionMapping(processResources).map("libraries") {
                    getTextSymbolDependencies(config.allLibraries)
                }
                conventionMapping(processResources).map("packageForR") {
                    config.originalApplicationId
                }

                // TODO: unify with generateBuilderConfig, compileAidl, and library packaging somehow?
                conventionMapping(processResources).map("sourceOutputDir") {
                    project.file(
                            "$project.buildDir/${FD_GENERATED}/source/r/${config.dirName}")
                }

                conventionMapping(processResources).map("textSymbolOutputDir") {
                    project.file(symbolLocation)
                }

                if (config.buildType.isMinifyEnabled()) {
                    if (config.buildType.shrinkResources && config.useJack) {
                        LoggingUtil.displayWarning(logger, project,
                                "shrinkResources does not yet work with useJack=true")
                    }
                    conventionMapping(processResources).map("proguardOutputFile") {
                        project.file(
                                "$project.buildDir/${FD_INTERMEDIATES}/proguard-rules/${config.dirName}/aapt_rules.txt")
                    }
                } else if (config.buildType.shrinkResources) {
                    LoggingUtil.displayWarning(logger, project,
                            "To shrink resources you must also enable ProGuard")
                }
            }

            conventionMapping(processResources).map("manifestFile") {
                variantOutputData.manifestProcessorTask.getOutputFile()
            }

            conventionMapping(processResources).map("resDir") {
                variantData.mergeResourcesTask.outputDir
            }

            conventionMapping(processResources).map("assetsDir") {
                variantData.mergeAssetsTask.outputDir
            }

            if (generateResourcePackage) {
                conventionMapping(processResources).map("packageOutputFile") {
                    project.file(
                            "$project.buildDir/${FD_INTERMEDIATES}/resources/resources-${outputBaseName}.ap_")
                }
            }

            conventionMapping(processResources).map("type") { config.type }
            conventionMapping(processResources).map("debuggable") { config.buildType.debuggable }
            conventionMapping(processResources).map("aaptOptions") { getExtension().aaptOptions }
            conventionMapping(processResources).
                    map("pseudoLocalesEnabled") { config.buildType.pseudoLocalesEnabled }

            conventionMapping(processResources).map("resourceConfigs") {
                return config.mergedFlavor.resourceConfigurations
            }
            conventionMapping(processResources).map("preferredDensity") {
                variantOutputData.getMainOutputFile().getFilter(OutputFile.DENSITY)
            }

        }
    }

    /**
     * Creates the split resources packages task if necessary. AAPT will produce split packages
     * for all --split provided parameters. These split packages should be signed and moved
     * unchanged to the APK build output directory.
     * @param variantData the variant configuration.
     */
    public void createSplitResourcesTasks(
            @NonNull BaseVariantData<? extends BaseVariantOutputData> variantData) {

        assert variantData.getSplitHandlingPolicy() ==
                BaseVariantData.SplitHandlingPolicy.RELEASE_21_AND_AFTER_POLICY;

        VariantConfiguration config = variantData.variantConfiguration
        Set<String> densityFilters =
                removeAllNullEntries(getExtension().getSplits().getDensityFilters());
        Set<String> abiFilters = removeAllNullEntries(getExtension().getSplits().getAbiFilters());
        Set<String> languageFilters =
                removeAllNullEntries(getExtension().getSplits().getLanguageFilters());

        def outputs = variantData.outputs;
        if (outputs.size() != 1) {
            throw new RuntimeException("In release 21 and later, there can be only one main APK, " +
                    "found " + outputs.size());
        }

        BaseVariantOutputData variantOutputData = outputs.get(0);
        variantOutputData.packageSplitResourcesTask =
                project.tasks.create("package${config.fullName.capitalize()}SplitResources",
                        PackageSplitRes);
        variantOutputData.packageSplitResourcesTask.inputDirectory =
                variantOutputData.processResourcesTask.packageOutputFile.getParentFile()
        variantOutputData.packageSplitResourcesTask.densitySplits = densityFilters
        variantOutputData.packageSplitResourcesTask.languageSplits = languageFilters
        variantOutputData.packageSplitResourcesTask.outputBaseName = config.baseName
        variantOutputData.packageSplitResourcesTask.signingConfig =
                (SigningConfig) config.signingConfig
        variantOutputData.packageSplitResourcesTask.outputDirectory =
                new File("$project.buildDir/${FD_INTERMEDIATES}/splits/${config.dirName}")
        variantOutputData.packageSplitResourcesTask.androidBuilder = androidBuilder
        variantOutputData.packageSplitResourcesTask.dependsOn variantOutputData.processResourcesTask

        SplitZipAlign zipAlign = project.tasks.
                create("zipAlign${config.fullName.capitalize()}SplitPackages", SplitZipAlign)
        conventionMapping(zipAlign).map("zipAlignExe") {
            String path = androidBuilder.targetInfo?.buildTools?.getPath(ZIP_ALIGN)
            if (path != null) {
                return new File(path)
            }

            return null
        }
        zipAlign.outputDirectory = new File("$project.buildDir/outputs/apk")
        zipAlign.inputFiles.addAll(variantOutputData.packageSplitResourcesTask.getOutputFiles())
        zipAlign.outputBaseName = config.baseName
        zipAlign.abiFilters = abiFilters
        zipAlign.languageFilters = languageFilters
        zipAlign.densityFilters = densityFilters
        ((ApkVariantOutputData) variantOutputData).splitZipAlign = zipAlign
        zipAlign.dependsOn(variantOutputData.packageSplitResourcesTask)
    }

    public void createSplitAbiTasks(
            @NonNull ApplicationVariantData variantData) {

        assert variantData.getSplitHandlingPolicy() ==
                BaseVariantData.SplitHandlingPolicy.RELEASE_21_AND_AFTER_POLICY;

        VariantConfiguration config = variantData.variantConfiguration
        Set<String> filters = AbiSplitOptions.getAbiFilters(
                getExtension().getSplits().getAbiFilters())
        if (filters.isEmpty()) {
            return;
        }
        def outputs = variantData.outputs;
        if (outputs.size() != 1) {
            throw new RuntimeException("In release 21 and later, there can be only one main APK, " +
                    "found " + outputs.size());
        }

        BaseVariantOutputData variantOutputData = outputs.get(0);
        // first create the split APK resources.
        GenerateSplitAbiRes generateSplitAbiRes = project.tasks.
                create("generate${config.fullName.capitalize()}SplitAbiRes",
                        GenerateSplitAbiRes)
        generateSplitAbiRes.androidBuilder = androidBuilder

        generateSplitAbiRes.outputDirectory =
                new File("$project.buildDir/${FD_INTERMEDIATES}/abi/${config.dirName}")
        generateSplitAbiRes.splits = filters
        generateSplitAbiRes.outputBaseName = config.baseName
        generateSplitAbiRes.applicationId = config.getApplicationId()
        generateSplitAbiRes.versionCode = config.getVersionCode()
        generateSplitAbiRes.versionName = config.getVersionName()
        generateSplitAbiRes.debuggable = {
            config.buildType.debuggable
        }
        conventionMapping(generateSplitAbiRes).map("aaptOptions") {
            getExtension().aaptOptions
        }
        generateSplitAbiRes.dependsOn variantOutputData.processResourcesTask

        // then package those resources witth the appropriate JNI libraries.
        variantOutputData.packageSplitAbiTask =
                project.tasks.create("package${config.fullName.capitalize()}SplitAbi",
                        PackageSplitAbi);
        variantOutputData.packageSplitAbiTask.inputFiles = generateSplitAbiRes.getOutputFiles()
        variantOutputData.packageSplitAbiTask.splits = filters
        variantOutputData.packageSplitAbiTask.outputBaseName = config.baseName
        variantOutputData.packageSplitAbiTask.signingConfig =
                (SigningConfig) config.signingConfig
        variantOutputData.packageSplitAbiTask.outputDirectory =
                new File("$project.buildDir/${FD_INTERMEDIATES}/splits/${config.dirName}")
        variantOutputData.packageSplitAbiTask.androidBuilder = androidBuilder
        variantOutputData.packageSplitAbiTask.dependsOn generateSplitAbiRes

        conventionMapping(variantOutputData.packageSplitAbiTask).map("jniFolders") {
            getJniFolders(variantData);
        }
        conventionMapping(variantOutputData.packageSplitAbiTask).
                map("jniDebuggable") { config.buildType.jniDebuggable }
        conventionMapping(variantOutputData.packageSplitAbiTask).
                map("packagingOptions") { getExtension().packagingOptions }

        ((ApkVariantOutputData) variantOutputData).splitZipAlign.inputFiles.addAll(
                variantOutputData.packageSplitAbiTask.getOutputFiles())

        ((ApkVariantOutputData) variantOutputData).splitZipAlign.dependsOn variantOutputData.packageSplitAbiTask
    }

    /**
     * Calculate the list of folders that can contain jni artifacts for this variant.
     * @param variantData the variant
     * @return a potentially empty list of directories that exist or not and that may contains
     * native resources.
     */
    @NonNull
    public Set<File> getJniFolders(@NonNull ApkVariantData variantData) {
        VariantConfiguration config = variantData.variantConfiguration
        // for now only the project's compilation output.
        Set<File> set = Sets.newHashSet()
        if (getExtension().getUseNewNativePlugin()) {
            throw new RuntimeException("useNewNativePlugin is currently not supported.")
        } else {
            set.addAll(variantData.ndkCompileTask.soFolder)
        }
        set.addAll(variantData.renderscriptCompileTask.libOutputDir)
        set.addAll(config.libraryJniFolders)
        set.addAll(config.jniLibsList)

        if (config.mergedFlavor.renderscriptSupportModeEnabled) {
            File rsLibs = androidBuilder.getSupportNativeLibFolder()
            if (rsLibs != null && rsLibs.isDirectory()) {
                set.add(rsLibs);
            }
        }

        return set
    }

    public void createProcessJavaResTask(
            @NonNull BaseVariantData<? extends BaseVariantOutputData> variantData) {
        VariantConfiguration variantConfiguration = variantData.variantConfiguration

        Copy processResources = project.tasks.create(
                "process${variantData.variantConfiguration.fullName.capitalize()}JavaRes",
                ProcessResources);
        variantData.processJavaResourcesTask = processResources

        // set the input
        processResources.from(((AndroidSourceSet) variantConfiguration.defaultSourceSet).resources.
                getSourceFiles())

        if (!variantConfiguration.type.isForTesting()) {
            processResources.from(
                    ((AndroidSourceSet) variantConfiguration.buildTypeSourceSet).resources.
                            getSourceFiles())
        }
        if (variantConfiguration.hasFlavors()) {
            for (SourceProvider flavorSourceSet : variantConfiguration.flavorSourceProviders) {
                processResources.
                        from(((AndroidSourceSet) flavorSourceSet).resources.getSourceFiles())
            }
        }

        conventionMapping(processResources).map("destinationDir") {
            project.file(
                    "$project.buildDir/${FD_INTERMEDIATES}/javaResources/${variantData.variantConfiguration.dirName}")
        }
    }

    public void createAidlTask(
            @NonNull BaseVariantData<? extends BaseVariantOutputData> variantData,
            @Nullable File parcelableDir) {
        VariantConfiguration variantConfiguration = variantData.variantConfiguration

        def compileTask = project.tasks.create(
                "compile${variantData.variantConfiguration.fullName.capitalize()}Aidl",
                AidlCompile)
        variantData.aidlCompileTask = compileTask

        variantData.sourceGenTask.dependsOn compileTask
        variantData.aidlCompileTask.dependsOn variantData.prepareDependenciesTask

        compileTask.androidBuilder = androidBuilder
        compileTask.incrementalFolder =
                project.file(
                        "$project.buildDir/${FD_INTERMEDIATES}/incremental/aidl/${variantData.variantConfiguration.dirName}")

        conventionMapping(compileTask).map("sourceDirs") { variantConfiguration.aidlSourceList }
        conventionMapping(compileTask).map("importDirs") { variantConfiguration.aidlImports }

        conventionMapping(compileTask).map("sourceOutputDir") {
            project.file(
                    "$project.buildDir/${FD_GENERATED}/source/aidl/${variantData.variantConfiguration.dirName}")
        }
        compileTask.aidlParcelableDir = parcelableDir
    }

    public void createCompileTask(
            @NonNull BaseVariantData<? extends BaseVariantOutputData> variantData,
            @Nullable BaseVariantData<? extends BaseVariantOutputData> testedVariantData) {
        def compileTask = project.tasks.create(
                "compile${variantData.variantConfiguration.fullName.capitalize()}Java",
                JavaCompile)
        variantData.javaCompileTask = compileTask
        variantData.compileTask.dependsOn variantData.javaCompileTask
        optionalDependsOn(variantData.javaCompileTask, variantData.sourceGenTask)

        compileTask.source = variantData.getJavaSources()

        VariantConfiguration config = variantData.variantConfiguration

        // if the tested variant is an app, add its classpath. For the libraries,
        // it's done automatically since the classpath includes the library output as a normal
        // dependency.
        if (testedVariantData instanceof ApplicationVariantData) {
            conventionMapping(compileTask).map("classpath") {
                project.files(androidBuilder.getCompileClasspath(config)) +
                        testedVariantData.javaCompileTask.classpath +
                        testedVariantData.javaCompileTask.outputs.files
            }
        } else {
            conventionMapping(compileTask).map("classpath") {
                project.files(androidBuilder.getCompileClasspath(config))
            }
        }

        // TODO - dependency information for the compile classpath is being lost.
        // Add a temporary approximation
        compileTask.dependsOn variantData.variantDependency.compileConfiguration.buildDependencies

        conventionMapping(compileTask).map("destinationDir") {
            project.file(
                    "$project.buildDir/${FD_INTERMEDIATES}/classes/${variantData.variantConfiguration.dirName}")
        }
        conventionMapping(compileTask).map("dependencyCacheDir") {
            project.file(
                    "$project.buildDir/${FD_INTERMEDIATES}/dependency-cache/${variantData.variantConfiguration.dirName}")
        }

        configureLanguageLevel(compileTask)
        compileTask.options.encoding = getExtension().compileOptions.encoding

        // setup the boot classpath just before the task actually runs since this will
        // force the sdk to be parsed.
        compileTask.doFirst {
            compileTask.options.bootClasspath =
                    androidBuilder.getBootClasspathAsStrings().join(File.pathSeparator)
        }
    }

    public void createGenerateMicroApkDataTask(
            @NonNull BaseVariantData<? extends BaseVariantOutputData> variantData,
            @NonNull Configuration config) {
        GenerateApkDataTask task = project.tasks.create(
                "handle${variantData.variantConfiguration.fullName.capitalize()}MicroApk",
                GenerateApkDataTask)

        variantData.generateApkDataTask = task

        task.androidBuilder = androidBuilder
        conventionMapping(task).map("resOutputDir") {
            project.file(
                    "$project.buildDir/${FD_GENERATED}/res/microapk/${variantData.variantConfiguration.dirName}")
        }
        conventionMapping(task).map("apkFile") {
            // only care about the first one. There shouldn't be more anyway.
            config.getFiles().iterator().next()
        }
        conventionMapping(task).map("manifestFile") {
            project.file(
                    "$project.buildDir/${FD_INTERMEDIATES}/${FD_GENERATED}/manifests/microapk/${variantData.variantConfiguration.dirName}/${FN_ANDROID_MANIFEST_XML}")
        }
        conventionMapping(task).map("mainPkgName") {
            variantData.variantConfiguration.getApplicationId()
        }

        conventionMapping(task).map("minSdkVersion") {
            variantData.variantConfiguration.getMinSdkVersion().apiLevel
        }

        conventionMapping(task).map("targetSdkVersion") {
            variantData.variantConfiguration.getTargetSdkVersion().apiLevel
        }

        task.dependsOn config

        // the merge res task will need to run after this one.
        variantData.resourceGenTask.dependsOn task
    }

    public void createNdkTasks(
            @NonNull BaseVariantData<? extends BaseVariantOutputData> variantData) {
        NdkCompile ndkCompile = project.tasks.create(
                "compile${variantData.variantConfiguration.fullName.capitalize()}Ndk",
                NdkCompile)

        ndkCompile.dependsOn variantData.preBuildTask

        ndkCompile.androidBuilder = androidBuilder
        ndkCompile.ndkDirectory = sdkHandler.getNdkFolder()
        variantData.ndkCompileTask = ndkCompile
        variantData.compileTask.dependsOn variantData.ndkCompileTask

        GradleVariantConfiguration variantConfig = variantData.variantConfiguration

        if (variantConfig.mergedFlavor.renderscriptNdkModeEnabled) {
            ndkCompile.ndkRenderScriptMode = true
            ndkCompile.dependsOn variantData.renderscriptCompileTask
        } else {
            ndkCompile.ndkRenderScriptMode = false
        }

        conventionMapping(ndkCompile).map("sourceFolders") {
            List<File> sourceList = variantConfig.jniSourceList
            if (variantConfig.mergedFlavor.renderscriptNdkModeEnabled) {
                sourceList.add(variantData.renderscriptCompileTask.sourceOutputDir)
            }

            return sourceList
        }

        conventionMapping(ndkCompile).map("generatedMakefile") {
            project.file(
                    "$project.buildDir/${FD_INTERMEDIATES}/ndk/${variantData.variantConfiguration.dirName}/Android.mk")
        }

        conventionMapping(ndkCompile).map("ndkConfig") { variantConfig.ndkConfig }

        conventionMapping(ndkCompile).map("debuggable") {
            variantConfig.buildType.jniDebuggable
        }

        conventionMapping(ndkCompile).map("objFolder") {
            project.file(
                    "$project.buildDir/${FD_INTERMEDIATES}/ndk/${variantData.variantConfiguration.dirName}/obj")
        }
        conventionMapping(ndkCompile).map("soFolder") {
            project.file(
                    "$project.buildDir/${FD_INTERMEDIATES}/ndk/${variantData.variantConfiguration.dirName}/lib")
        }
    }

    /**
     * Creates the tasks to build unit tests.
     *
     * @param variantData the test variant
     */
    void createUnitTestVariantTasks(@NonNull TestVariantData variantData) {
        BaseVariantData testedVariantData = variantData.getTestedVariantData() as BaseVariantData
        createCompileAnchorTask(variantData)
        createCompileTask(variantData, testedVariantData)
        variantData.assembleVariantTask.dependsOn variantData.compileTask
    }

    /**
     * Creates the tasks to build android tests.
     *
     * @param variantData the test variant
     */
    public void createAndroidTestVariantTasks(@NonNull TestVariantData variantData) {

        BaseVariantData<? extends BaseVariantOutputData> testedVariantData =
                variantData.
                        getTestedVariantData() as BaseVariantData<? extends BaseVariantOutputData>

        // get single output for now (though this may always be the case for tests).
        BaseVariantOutputData variantOutputData = variantData.outputs.get(0)
        BaseVariantOutputData testedVariantOutputData = testedVariantData.outputs.get(0)

        createAnchorTasks(variantData)

        // Add a task to process the manifest
        createProcessTestManifestTask(variantData, "manifests")

        // Add a task to create the res values
        createGenerateResValuesTask(variantData)

        // Add a task to compile renderscript files.
        createRenderscriptTask(variantData)

        // Add a task to merge the resource folders
        createMergeResourcesTask(variantData, true /*process9Patch*/)

        // Add a task to merge the assets folders
        createMergeAssetsTask(variantData, null /*default location*/, true /*includeDependencies*/)

        if (testedVariantData.variantConfiguration.type == VariantType.LIBRARY) {
            // in this case the tested library must be fully built before test can be built!
            if (testedVariantOutputData.assembleTask != null) {
                variantOutputData.manifestProcessorTask.dependsOn testedVariantOutputData.assembleTask
                variantData.mergeResourcesTask.dependsOn testedVariantOutputData.assembleTask
            }
        }

        // Add a task to create the BuildConfig class
        createBuildConfigTask(variantData)

        // Add a task to generate resource source files
        createProcessResTask(variantData, true /*generateResourcePackage*/)

        // process java resources
        createProcessJavaResTask(variantData)

        createAidlTask(variantData, null /*parcelableDir*/)

        // Add NDK tasks
        if (!getExtension().getUseNewNativePlugin()) {
            createNdkTasks(variantData)
        }

        // Add a task to compile the test application
        if (variantData.getVariantConfiguration().useJack) {
            createJackTask(variantData, testedVariantData);
        } else {
            createCompileTask(variantData, testedVariantData)
            createPostCompilationTasks(variantData);
        }

        createPackagingTask(variantData, false /*publishApk*/)

        assembleAndroidTest.dependsOn variantOutputData.assembleTask
    }

    // TODO - should compile src/lint/java from src/lint/java and jar it into build/lint/lint.jar
    public void createLintCompileTask() {
        lintCompile = project.tasks.create("compileLint", Task)
        File outputDir = new File("$project.buildDir/${FD_INTERMEDIATES}/lint")

        lintCompile.doFirst {
            // create the directory for lint output if it does not exist.
            if (!outputDir.exists()) {
                boolean mkdirs = outputDir.mkdirs();
                if (!mkdirs) {
                    throw new GradleException("Unable to create lint output directory.")
                }
            }
        }
    }

    /** Is the given variant relevant for lint? */
    private static boolean isLintVariant(
            @NonNull BaseVariantData<? extends BaseVariantOutputData> baseVariantData) {
        // Only create lint targets for variants like debug and release, not debugTest
        VariantConfiguration config = baseVariantData.variantConfiguration
        // TODO: re-enable with Jack when possible
        return !config.getType().isForTesting() && !config.useJack;
    }

    // Add tasks for running lint on individual variants. We've already added a
    // lint task earlier which runs on all variants.
    public void createLintTasks(
            List<BaseVariantData<? extends BaseVariantOutputData>> variantDataList) {
        Lint lint = project.tasks.create("lint", Lint)
        lint.description = "Runs lint on all variants."
        lint.group = JavaBasePlugin.VERIFICATION_GROUP
        lint.setLintOptions(getExtension().lintOptions)
        lint.setSdkHome(sdkHandler.getSdkFolder())
        lint.setToolingRegistry(toolingRegistry)
        project.tasks.getByName(JavaBasePlugin.CHECK_TASK_NAME).dependsOn lint
        lintAll = lint

        int count = variantDataList.size()
        for (int i = 0; i < count; i++) {
            final BaseVariantData<? extends BaseVariantOutputData> baseVariantData =
                    variantDataList.get(i)
            if (!isLintVariant(baseVariantData)) {
                continue;
            }

            // wire the main lint task dependency.
            lint.dependsOn lintCompile
            optionalDependsOn(lint, baseVariantData.javaCompileTask, baseVariantData.jackTask)

            String variantName = baseVariantData.variantConfiguration.fullName
            def capitalizedVariantName = variantName.capitalize()
            Lint variantLintCheck = project.tasks.create("lint" + capitalizedVariantName, Lint)
            variantLintCheck.dependsOn lintCompile
            optionalDependsOn(variantLintCheck, baseVariantData.javaCompileTask,
                    baseVariantData.jackTask)

            // Note that we don't do "lint.dependsOn lintCheck"; the "lint" target will
            // on its own run through all variants (and compare results), it doesn't delegate
            // to the individual tasks (since it needs to coordinate data collection and
            // reporting)
            variantLintCheck.setLintOptions(getExtension().lintOptions)
            variantLintCheck.setSdkHome(sdkHandler.getSdkFolder())
            variantLintCheck.setVariantName(variantName)
            variantLintCheck.setToolingRegistry(toolingRegistry)
            variantLintCheck.description = "Runs lint on the " + capitalizedVariantName + " build."
            variantLintCheck.group = JavaBasePlugin.VERIFICATION_GROUP
        }
    }

    private void createLintVitalTask(@NonNull ApkVariantData variantData) {
        assert getExtension().lintOptions.checkReleaseBuilds
        // TODO: re-enable with Jack when possible
        if (!variantData.variantConfiguration.buildType.debuggable &&
                !variantData.variantConfiguration.useJack) {
            String variantName = variantData.variantConfiguration.fullName
            def capitalizedVariantName = variantName.capitalize()
            def taskName = "lintVital" + capitalizedVariantName
            Lint lintReleaseCheck = project.tasks.create(taskName, Lint)
            // TODO: Make this task depend on lintCompile too (resolve initialization order first)
            optionalDependsOn(lintReleaseCheck, variantData.javaCompileTask)
            lintReleaseCheck.setLintOptions(getExtension().lintOptions)
            lintReleaseCheck.setSdkHome(sdkHandler.getSdkFolder())
            lintReleaseCheck.setVariantName(variantName)
            lintReleaseCheck.setToolingRegistry(toolingRegistry)
            lintReleaseCheck.setFatalOnly(true)
            lintReleaseCheck.description = "Runs lint on just the fatal issues in the " +
                    capitalizedVariantName + " build."
            variantData.assembleVariantTask.dependsOn lintReleaseCheck

            // If lint is being run, we do not need to run lint vital.
            project.gradle.taskGraph.whenReady { TaskExecutionGraph taskGraph ->
                if (taskGraph.hasTask(lintAll)) {
                    lintReleaseCheck.setEnabled(false)
                }
            }
        }
    }

    void createUnitTestTasks(
            List<BaseVariantData<? extends BaseVariantOutputData>> variantDataList) {
        Task topLevelTest = project.tasks.create(JavaPlugin.TEST_TASK_NAME)
        topLevelTest.group = JavaBasePlugin.VERIFICATION_GROUP
        topLevelTest.description = "Run unit tests for all variants."

        variantDataList.findAll { it.variantConfiguration.type == UNIT_TEST }.each {
            TestVariantData variantData = it as TestVariantData
            BaseVariantData testedVariantData = variantData.testedVariantData as BaseVariantData

            if (variantData.variantConfiguration.useJack
                    || testedVariantData.variantConfiguration.useJack) {
                // Don't create unit test tasks when using Jack.
                // TODO: Handle Jack somehow.
                return
            }

            Test runTestsTask = project.tasks.create(
                    UNIT_TEST.prefix +
                            testedVariantData.variantConfiguration.fullName.capitalize(),
                    Test)
            runTestsTask.group = JavaBasePlugin.VERIFICATION_GROUP
            runTestsTask.description = "Run unit tests for the " +
                    "$testedVariantData.variantConfiguration.fullName build."

            fixTestTaskSources(runTestsTask)

            runTestsTask.dependsOn variantData.assembleVariantTask
            JavaCompile testCompileTask = variantData.javaCompileTask
            runTestsTask.testClassesDir = testCompileTask.destinationDir

            conventionMapping(runTestsTask).map("classpath") {
                project.files(
                        testCompileTask.classpath,
                        testCompileTask.outputs.files,
                        androidBuilder.bootClasspath.findAll { it.name != "android.jar"},
                        createMockableJar.outputFile)
            }

            topLevelTest.dependsOn runTestsTask
            variantData.assembleVariantTask.dependsOn createMockableJar
        }

        Task check = project.tasks.getByName(JavaBasePlugin.CHECK_TASK_NAME)
        check.dependsOn topLevelTest
    }

    @CompileDynamic
    private static void fixTestTaskSources(@NonNull Test testTask) {
        // We are running in afterEvaluate, so the JavaBasePlugin has already added a
        // callback to add test classes to the list of source files of the newly created task.
        // The problem is that we haven't configured the test classes yet (JavaBasePlugin
        // assumes all Test tasks are fully configured at this point), so we have to remove the
        // "directory null" entry from source files and add the right value.
        //
        // This is an ugly hack, since we assume sourceFiles is an instance of
        // DefaultConfigurableFileCollection.
        testTask.inputs.sourceFiles.from.clear()
    }

    public void createConnectedCheckTasks(
            List<BaseVariantData<? extends BaseVariantOutputData>> variantDataList,
            boolean hasFlavors,
            boolean isLibraryTest) {
        List<AndroidReportTask> reportTasks = Lists.newArrayListWithExpectedSize(2)

        List<DeviceProvider> providers = getExtension().deviceProviders
        List<TestServer> servers = getExtension().testServers

        Task mainConnectedTask = connectedCheck
        String connectedRootName = "${CONNECTED}${ANDROID_TEST.suffix}"
        // if more than one flavor, create a report aggregator task and make this the parent
        // task for all new connected tasks.
        if (hasFlavors) {
            mainConnectedTask = project.tasks.create(connectedRootName, AndroidReportTask)
            mainConnectedTask.group = JavaBasePlugin.VERIFICATION_GROUP
            mainConnectedTask.description =
                    "Installs and runs instrumentation tests for all flavors on connected devices."
            mainConnectedTask.reportType = ReportType.MULTI_FLAVOR
            connectedCheck.dependsOn mainConnectedTask

            conventionMapping(mainConnectedTask).map("resultsDir") {
                String rootLocation = getExtension().testOptions.resultsDir != null ?
                        getExtension().testOptions.resultsDir :
                        "$project.buildDir/${FD_OUTPUTS}/$FD_ANDROID_RESULTS"

                project.file("$rootLocation/connected/$FD_FLAVORS_ALL")
            }
            conventionMapping(mainConnectedTask).map("reportsDir") {
                String rootLocation = getExtension().testOptions.reportDir != null ?
                        getExtension().testOptions.reportDir :
                        "$project.buildDir/${FD_OUTPUTS}/$FD_REPORTS/$FD_ANDROID_TESTS"

                project.file("$rootLocation/connected/$FD_FLAVORS_ALL")
            }

            reportTasks.add(mainConnectedTask)
        }

        Task mainProviderTask = deviceCheck
        // if more than one provider tasks, either because of several flavors, or because of
        // more than one providers, then create an aggregate report tasks for all of them.
        if (providers.size() > 1 || hasFlavors) {
            mainProviderTask = project.tasks.create("${DEVICE}${ANDROID_TEST.suffix}",
                    AndroidReportTask)
            mainProviderTask.group = JavaBasePlugin.VERIFICATION_GROUP
            mainProviderTask.description =
                    "Installs and runs instrumentation tests using all Device Providers."
            mainProviderTask.reportType = ReportType.MULTI_FLAVOR
            deviceCheck.dependsOn mainProviderTask

            conventionMapping(mainProviderTask).map("resultsDir") {
                String rootLocation = getExtension().testOptions.resultsDir != null ?
                        getExtension().testOptions.resultsDir :
                        "$project.buildDir/${FD_OUTPUTS}/$FD_ANDROID_RESULTS"

                project.file("$rootLocation/devices/$FD_FLAVORS_ALL")
            }
            conventionMapping(mainProviderTask).map("reportsDir") {
                String rootLocation = getExtension().testOptions.reportDir != null ?
                        getExtension().testOptions.reportDir :
                        "$project.buildDir/${FD_OUTPUTS}/$FD_REPORTS/$FD_ANDROID_TESTS"

                project.file("$rootLocation/devices/$FD_FLAVORS_ALL")
            }

            reportTasks.add(mainProviderTask)
        }

        // Now look for the tested variant and create the check tasks for them.
        // don't use an auto loop as we can't reuse baseVariantData or the closure lower
        // gets broken.
        int count = variantDataList.size();
        for (int i = 0; i < count; i++) {
            final BaseVariantData<? extends BaseVariantOutputData> baseVariantData = variantDataList.get(i);
            if (baseVariantData instanceof TestedVariantData) {
                final TestVariantData testVariantData = ((TestedVariantData) baseVariantData).
                        getTestVariantData(ANDROID_TEST)
                if (testVariantData == null) {
                    continue
                }

                // get single output for now
                BaseVariantOutputData variantOutputData = baseVariantData.outputs.get(0)
                BaseVariantOutputData testVariantOutputData = testVariantData.outputs.get(0)

                Class<? extends DeviceProviderInstrumentTestTask> taskClass = isLibraryTest ?
                        DeviceProviderInstrumentTestLibraryTask :
                        DeviceProviderInstrumentTestTask

                String connectedTaskName = hasFlavors ?
                        "${connectedRootName}${baseVariantData.variantConfiguration.fullName.capitalize()}" :
                        connectedRootName

                // create the check tasks for this test
                // first the connected one.
                DeviceProviderInstrumentTestTask connectedTask =
                        createDeviceProviderInstrumentTestTask(
                                connectedTaskName,
                                "Installs and runs the tests for ${baseVariantData.description} on connected devices.",
                                taskClass,
                                testVariantData,
                                baseVariantData as BaseVariantData,
                                new ConnectedDeviceProvider(sdkHandler.getSdkInfo().adb),
                                CONNECTED
                        )

                mainConnectedTask.dependsOn connectedTask
                testVariantData.connectedTestTask = connectedTask

                if (baseVariantData.variantConfiguration.buildType.isTestCoverageEnabled()) {
                    def reportTask = project.tasks.create(
                            "create${baseVariantData.variantConfiguration.fullName.capitalize()}CoverageReport",
                            JacocoReportTask)
                    reportTask.reportName = baseVariantData.variantConfiguration.fullName
                    conventionMapping(reportTask).map("jacocoClasspath") {
                        project.configurations[JacocoPlugin.ANT_CONFIGURATION_NAME]
                    }
                    conventionMapping(reportTask).map("coverageFile") {
                        new File(connectedTask.getCoverageDir(),
                                SimpleTestCallable.FILE_COVERAGE_EC)
                    }
                    conventionMapping(reportTask).map("classDir") {
                        if (baseVariantData.javaCompileTask != null) {
                            return baseVariantData.javaCompileTask.destinationDir
                        }

                        return baseVariantData.jackTask.destinationDir
                    }
                    conventionMapping(reportTask).
                            map("sourceDir") { baseVariantData.getJavaSourceFoldersForCoverage() }

                    conventionMapping(reportTask).map("reportDir") {
                        project.file(
                                "$project.buildDir/${FD_OUTPUTS}/$FD_REPORTS/coverage/${baseVariantData.variantConfiguration.dirName}")
                    }

                    reportTask.dependsOn connectedTask
                    mainConnectedTask.dependsOn reportTask
                }

                // now the providers.
                for (DeviceProvider deviceProvider : providers) {
                    DeviceProviderInstrumentTestTask providerTask =
                            createDeviceProviderInstrumentTestTask(
                                    hasFlavors ?
                                            "${deviceProvider.name}${ANDROID_TEST.suffix}${baseVariantData.variantConfiguration.fullName.capitalize()}" :
                                            "${deviceProvider.name}${ANDROID_TEST.suffix}",
                                    "Installs and runs the tests for Build '${baseVariantData.variantConfiguration.fullName}' using Provider '${deviceProvider.name.capitalize()}'.",
                                    taskClass,
                                    testVariantData,
                                    baseVariantData as BaseVariantData,
                                    deviceProvider,
                                    "$DEVICE/$deviceProvider.name"
                            )

                    mainProviderTask.dependsOn providerTask
                    testVariantData.providerTestTaskList.add(providerTask)

                    if (!deviceProvider.isConfigured()) {
                        providerTask.enabled = false;
                    }
                }

                // now the test servers
                // don't use an auto loop as it'll break the closure inside.
                for (TestServer testServer : servers) {
                    def serverTask = project.tasks.create(
                            hasFlavors ?
                                    "${testServer.name}${"upload".capitalize()}${baseVariantData.variantConfiguration.fullName}" :
                                    "${testServer.name}${"upload".capitalize()}",
                            TestServerTask)

                    serverTask.description =
                            "Uploads APKs for Build '${baseVariantData.variantConfiguration.fullName}' to Test Server '${testServer.name.capitalize()}'."
                    serverTask.group = JavaBasePlugin.VERIFICATION_GROUP
                    serverTask.dependsOn testVariantOutputData.assembleTask,
                            variantOutputData.assembleTask

                    serverTask.testServer = testServer

                    conventionMapping(serverTask).
                            map("testApk") { testVariantOutputData.outputFile }
                    if (!(baseVariantData instanceof LibraryVariantData)) {
                        conventionMapping(serverTask).
                                map("testedApk") { variantOutputData.outputFile }
                    }

                    conventionMapping(serverTask).
                            map("variantName") { baseVariantData.variantConfiguration.fullName }

                    deviceCheck.dependsOn serverTask

                    if (!testServer.isConfigured()) {
                        serverTask.enabled = false;
                    }
                }
            }
        }

        // If gradle is launched with --continue, we want to run all tests and generate an
        // aggregate report (to help with the fact that we may have several build variants, or
        // or several device providers).
        // To do that, the report tasks must run even if one of their dependent tasks (flavor
        // or specific provider tasks) fails, when --continue is used, and the report task is
        // meant to run (== is in the task graph).
        // To do this, we make the children tasks ignore their errors (ie they won't fail and
        // stop the build).
        if (!reportTasks.isEmpty() && project.gradle.startParameter.continueOnFailure) {
            project.gradle.taskGraph.whenReady { TaskExecutionGraph taskGraph ->
                for (AndroidReportTask reportTask : reportTasks) {
                    if (taskGraph.hasTask(reportTask)) {
                        reportTask.setWillRun()
                    }
                }
            }
        }
    }

    private DeviceProviderInstrumentTestTask createDeviceProviderInstrumentTestTask(
            @NonNull String taskName,
            @NonNull String description,
            @NonNull Class<? extends DeviceProviderInstrumentTestTask> taskClass,
            @NonNull TestVariantData testVariantData,
            @NonNull BaseVariantData<? extends BaseVariantOutputData> testedVariantData,
            @NonNull DeviceProvider deviceProvider,
            @NonNull String subFolder) {

        // get single output for now for the test.
        BaseVariantOutputData testVariantOutputData = testVariantData.outputs.get(0)

        DeviceProviderInstrumentTestTask testTask = project.tasks.create(
                taskName,
                taskClass as Class<DeviceProviderInstrumentTestTask>)

        testTask.description = description
        testTask.group = JavaBasePlugin.VERIFICATION_GROUP
        testTask.dependsOn testVariantOutputData.assembleTask, testedVariantData.assembleVariantTask

        testTask.androidBuilder = androidBuilder
        testTask.testVariantData = testVariantData
        testTask.flavorName = testVariantData.variantConfiguration.flavorName.capitalize()
        testTask.deviceProvider = deviceProvider

        conventionMapping(testTask).map("resultsDir") {
            String rootLocation = getExtension().testOptions.resultsDir != null ?
                    getExtension().testOptions.resultsDir :
                    "$project.buildDir/${FD_OUTPUTS}/$FD_ANDROID_RESULTS"

            String flavorFolder = testVariantData.variantConfiguration.flavorName
            if (!flavorFolder.isEmpty()) {
                flavorFolder = "$FD_FLAVORS/" + flavorFolder
            }

            project.file("$rootLocation/$subFolder/$flavorFolder")
        }

        conventionMapping(testTask).map("adbExec") {
            return sdkHandler.getSdkInfo().getAdb()
        }

        conventionMapping(testTask).map("reportsDir") {
            String rootLocation = getExtension().testOptions.reportDir != null ?
                    getExtension().testOptions.reportDir :
                    "$project.buildDir/${FD_OUTPUTS}/$FD_REPORTS/$FD_ANDROID_TESTS"

            String flavorFolder = testVariantData.variantConfiguration.flavorName
            if (!flavorFolder.isEmpty()) {
                flavorFolder = "$FD_FLAVORS/" + flavorFolder
            }

            project.file("$rootLocation/$subFolder/$flavorFolder")
        }
        conventionMapping(testTask).map("coverageDir") {
            String rootLocation = "$project.buildDir/${FD_OUTPUTS}/code-coverage"

            String flavorFolder = testVariantData.variantConfiguration.flavorName
            if (!flavorFolder.isEmpty()) {
                flavorFolder = "$FD_FLAVORS/" + flavorFolder
            }

            project.file("$rootLocation/$subFolder/$flavorFolder")
        }

        return testTask
    }

    /**
     * Class to hold data to setup the many optional
     * post-compilation steps.
     */
    public static class PostCompilationData {

        List<?> classGeneratingTask

        List<?> libraryGeneratingTask

        Closure<List<File>> inputFiles

        Closure<File> inputDir

        Closure<List<File>> inputLibraries
    }

    /**
     * Creates the post-compilation tasks for the given Variant.
     *
     * These tasks create the dex file from the .class files, plus optional intermediary steps
     * like proguard and jacoco
     *
     * @param variantData the variant data.
     */
    public void createPostCompilationTasks(@NonNull ApkVariantData variantData) {
        GradleVariantConfiguration config = variantData.variantConfiguration

        boolean isTestForApp = config.type.isForTesting() &&
                (variantData as TestVariantData).testedVariantData.variantConfiguration.type ==
                DEFAULT

        boolean isMinifyEnabled = config.isMinifyEnabled()
        boolean isMultiDexEnabled = config.isMultiDexEnabled() && !isTestForApp
        boolean isLegacyMultiDexMode = config.isLegacyMultiDexMode()
        File multiDexKeepProguard = config.getMultiDexKeepProguard()
        File multiDexKeepFile = config.getMultiDexKeepFile()

        boolean isTestCoverageEnabled = config.buildType.isTestCoverageEnabled() &&
                !config.type.isForTesting()

        // common dex task configuration
        String dexTaskName = "dex${config.fullName.capitalize()}"
        Dex dexTask = project.tasks.create(dexTaskName, Dex)
        variantData.dexTask = dexTask
        dexTask.androidBuilder = androidBuilder
        conventionMapping(dexTask).map("outputFolder") {
            project.file("${project.buildDir}/${FD_INTERMEDIATES}/dex/${config.dirName}")
        }
        dexTask.tmpFolder =
                project.file("$project.buildDir/${FD_INTERMEDIATES}/tmp/dex/${config.dirName}")
        dexTask.dexOptions = getExtension().dexOptions
        dexTask.multiDexEnabled = isMultiDexEnabled
        dexTask.legacyMultiDexMode = isLegacyMultiDexMode
        // dx doesn't work with receving --no-optimize in debug so we disable it for now.
        dexTask.optimize = true //!variantData.variantConfiguration.buildType.debuggable


        // data holding dependencies and input for the dex. This gets updated as new
        // post-compilation steps are inserted between the compilation and dx.
        PostCompilationData pcData = new PostCompilationData()
        pcData.classGeneratingTask = [variantData.javaCompileTask]
        pcData.libraryGeneratingTask =
                [variantData.variantDependency.packageConfiguration.buildDependencies]
        pcData.inputFiles = {
            variantData.javaCompileTask.outputs.files.files as List
        }
        pcData.inputDir = {
            variantData.javaCompileTask.destinationDir
        }
        pcData.inputLibraries = {
            androidBuilder.getPackagedJars(config) as List
        }

        // ---- Code Coverage first -----
        if (isTestCoverageEnabled) {
            pcData = createJacocoTask(config, variantData, pcData)
        }

        // ----- Minify next ----

        if (isMinifyEnabled) {
            // first proguard task.
            BaseVariantData<? extends BaseVariantOutputData> testedVariantData =
                    (variantData instanceof TestVariantData ? variantData.testedVariantData :
                            null) as BaseVariantData
            createProguardTasks(variantData, testedVariantData, pcData)

        } else if ((getExtension().dexOptions.preDexLibraries && !isMultiDexEnabled) ||
                (isMultiDexEnabled && !isLegacyMultiDexMode)) {
            def preDexTaskName = "preDex${config.fullName.capitalize()}"
            PreDex preDexTask = project.tasks.create(preDexTaskName, PreDex)

            variantData.preDexTask = preDexTask
            preDexTask.androidBuilder = androidBuilder
            preDexTask.dexOptions = getExtension().dexOptions
            preDexTask.multiDex = isMultiDexEnabled

            conventionMapping(preDexTask).map("inputFiles", pcData.inputLibraries)
            conventionMapping(preDexTask).map("outputFolder") {
                project.file(
                        "${project.buildDir}/${FD_INTERMEDIATES}/pre-dexed/${config.dirName}")
            }

            // update dependency.
            optionalDependsOn(preDexTask, pcData.libraryGeneratingTask)
            pcData.libraryGeneratingTask = [preDexTask] as List<Object>

            // update inputs
            if (isMultiDexEnabled) {
                pcData.inputLibraries = { [] }

            } else {
                pcData.inputLibraries = {
                    project.fileTree(preDexTask.outputFolder).files as List
                }
            }
        }

        // ----- Multi-Dex support
        if (isMultiDexEnabled && isLegacyMultiDexMode) {
            if (!isMinifyEnabled) {
                // create a task that will convert the output of the compilation
                // into a jar. This is needed by the multi-dex input.
                JarMergingTask jarMergingTask = project.tasks.create(
                        "packageAll${config.fullName.capitalize()}ClassesForMultiDex",
                        JarMergingTask)
                conventionMapping(jarMergingTask).map("inputJars", pcData.inputLibraries)
                conventionMapping(jarMergingTask).map("inputDir", pcData.inputDir)

                jarMergingTask.jarFile = project.file(
                        "$project.buildDir/${FD_INTERMEDIATES}/multi-dex/${config.dirName}/allclasses.jar")

                // update dependencies
                optionalDependsOn(jarMergingTask, pcData.classGeneratingTask)
                optionalDependsOn(jarMergingTask, pcData.libraryGeneratingTask)
                pcData.libraryGeneratingTask = [jarMergingTask]
                pcData.classGeneratingTask = [jarMergingTask]

                // Update the inputs
                pcData.inputFiles = { [jarMergingTask.jarFile] }
                pcData.inputDir = null
                pcData.inputLibraries = { [] }
            }

            // ----------
            // Create a task to collect the list of manifest entry points which are
            // needed in the primary dex
            CreateManifestKeepList manifestKeepListTask = project.tasks.create(
                    "collect${config.fullName.capitalize()}MultiDexComponents",
                    CreateManifestKeepList)

            // since all the output have the same manifest, besides the versionCode,
            // we can take any of the output and use that.
            final BaseVariantOutputData output = variantData.outputs.get(0)
            manifestKeepListTask.dependsOn output.manifestProcessorTask
            conventionMapping(manifestKeepListTask).map("manifest") {
                output.manifestProcessorTask.getOutputFile()
            }

            manifestKeepListTask.proguardFile = multiDexKeepProguard
            manifestKeepListTask.outputFile = project.file(
                    "${project.buildDir}/${FD_INTERMEDIATES}/multi-dex/${config.dirName}/manifest_keep.txt")

            //variant.ext.collectMultiDexComponents = manifestKeepListTask

            // ----------
            // Create a proguard task to shrink the classes to manifest components
            ProGuardTask proguardComponentsTask = createShrinkingProGuardTask(project,
                    "shrink${config.fullName.capitalize()}MultiDexComponents")

            proguardComponentsTask.configuration(manifestKeepListTask.outputFile)

            proguardComponentsTask.libraryjars({
                checkNotNull(androidBuilder.getTargetInfo())
                File shrinkedAndroid = new File(androidBuilder.getTargetInfo().buildTools.location,
                        "lib${File.separatorChar}shrinkedAndroid.jar")

                // TODO remove in 1.0
                // STOPSHIP
                if (!shrinkedAndroid.isFile()) {
                    shrinkedAndroid = new File(androidBuilder.getTargetInfo().buildTools.location,
                            "multidex${File.separatorChar}shrinkedAndroid.jar")
                }
                return shrinkedAndroid
            })

            proguardComponentsTask.injars(pcData.inputFiles.call().iterator().next())

            File componentsJarFile = project.file(
                    "${project.buildDir}/${FD_INTERMEDIATES}/multi-dex/${config.dirName}/componentClasses.jar")
            proguardComponentsTask.outjars(componentsJarFile)

            proguardComponentsTask.printconfiguration(
                    "${project.buildDir}/${FD_INTERMEDIATES}/multi-dex/${config.dirName}/components.flags")

            // update dependencies
            proguardComponentsTask.dependsOn manifestKeepListTask
            optionalDependsOn(proguardComponentsTask, pcData.classGeneratingTask)
            optionalDependsOn(proguardComponentsTask, pcData.libraryGeneratingTask)

            // ----------
            // Compute the full list of classes for the main dex file
            CreateMainDexList createMainDexListTask = project.tasks.create(
                    "create${config.fullName.capitalize()}MainDexClassList",
                    CreateMainDexList)
            createMainDexListTask.androidBuilder = androidBuilder
            createMainDexListTask.dependsOn proguardComponentsTask
            //createMainDexListTask.dependsOn { proguardMainDexTask }

            def files = pcData.inputFiles
            createMainDexListTask.allClassesJarFile = files().first()
            conventionMapping(createMainDexListTask).map("componentsJarFile") { componentsJarFile }
            // conventionMapping(createMainDexListTask).map("includeInMainDexJarFile") { mainDexJarFile }
            createMainDexListTask.mainDexListFile = multiDexKeepFile
            createMainDexListTask.outputFile = project.file(
                    "${project.buildDir}/${FD_INTERMEDIATES}/multi-dex/${config.dirName}/maindexlist.txt")

            // update dependencies
            dexTask.dependsOn createMainDexListTask

            // ----------
            // If proguard is on create a de-obfuscated list to aid debugging.
            if (isMinifyEnabled) {
                RetraceMainDexList retraceTask = project.tasks.create(
                        "retrace${config.fullName.capitalize()}MainDexClassList",
                        RetraceMainDexList)
                retraceTask.dependsOn variantData.obfuscationTask, createMainDexListTask

                conventionMapping(retraceTask).
                        map("mainDexListFile") { createMainDexListTask.outputFile }
                conventionMapping(retraceTask).map("mappingFile") { variantData.mappingFile }
                retraceTask.outputFile = project.file(
                        "${project.buildDir}/${FD_INTERMEDIATES}/multi-dex/${config.dirName}/maindexlist_deobfuscated.txt")
                dexTask.dependsOn retraceTask
            }

            // configure the dex task to receive the generated class list.
            conventionMapping(dexTask).map("mainDexListFile") { createMainDexListTask.outputFile }
        }

        // ----- Dex Task ----

        // dependencies, some of these could be null
        optionalDependsOn(dexTask, pcData.classGeneratingTask)
        optionalDependsOn(dexTask, pcData.libraryGeneratingTask,)

        // inputs
        if (pcData.inputDir != null) {
            conventionMapping(dexTask).map("inputDir", pcData.inputDir)
        } else {
            conventionMapping(dexTask).map("inputFiles", pcData.inputFiles)
        }
        conventionMapping(dexTask).map("libraries", pcData.inputLibraries)
    }

    public PostCompilationData createJacocoTask(
            @NonNull GradleVariantConfiguration config,
            @NonNull BaseVariantData variantData,
            @NonNull final PostCompilationData pcData) {
        final JacocoInstrumentTask jacocoTask = project.tasks.create(
                "instrument${config.fullName.capitalize()}", JacocoInstrumentTask)
        conventionMapping(jacocoTask).map("jacocoClasspath") {
            project.configurations[JacocoPlugin.ANT_CONFIGURATION_NAME]
        }
        // can't directly use the existing inputFiles closure as we need the dir instead :\
        conventionMapping(jacocoTask).map("inputDir", pcData.inputDir)
        conventionMapping(jacocoTask).map("outputDir") {
            project.file(
                    "${project.buildDir}/${FD_INTERMEDIATES}/coverage-instrumented-classes/${config.dirName}")
        }
        variantData.jacocoInstrumentTask = jacocoTask

        Copy agentTask = getJacocoAgentTask()
        jacocoTask.dependsOn agentTask

        // update dependency.
        PostCompilationData pcData2 = new PostCompilationData()
        optionalDependsOn(jacocoTask, pcData.classGeneratingTask)
        pcData2.classGeneratingTask = [jacocoTask]
        pcData2.libraryGeneratingTask = [pcData.libraryGeneratingTask, agentTask]

        // update inputs
        pcData2.inputFiles = {
            project.files(jacocoTask.getOutputDir()).files as List
        }
        pcData2.inputDir = {
            jacocoTask.getOutputDir()
        }
        pcData2.inputLibraries = {
            [pcData.inputLibraries.call(), [new File(agentTask.destinationDir, FILE_JACOCO_AGENT)]].
                    flatten() as List
        }

        return pcData2
    }

    private static ProGuardTask createShrinkingProGuardTask(
            @NonNull Project project,
            @NonNull String name) {
        ProGuardTask task = project.tasks.create(name, ProGuardTask)

        task.dontobfuscate()
        task.dontoptimize()
        task.dontpreverify()
        task.dontwarn()
        task.forceprocessing()

        return task;
    }

    public void createJackTask(
            @NonNull BaseVariantData<? extends BaseVariantOutputData> variantData,
            @Nullable BaseVariantData<? extends BaseVariantOutputData> testedVariantData) {

        GradleVariantConfiguration config = variantData.variantConfiguration

        // ----- Create Jill tasks -----
        JillTask jillRuntimeTask = project.tasks.create(
                "jill${config.fullName.capitalize()}RuntimeLibraries",
                JillTask)

        jillRuntimeTask.androidBuilder = androidBuilder
        jillRuntimeTask.dexOptions = getExtension().dexOptions

        conventionMapping(jillRuntimeTask).map("inputLibs") {
            androidBuilder.getBootClasspath()
        }
        conventionMapping(jillRuntimeTask).map("outputFolder") {
            project.file(
                    "${project.buildDir}/${FD_INTERMEDIATES}/jill/${config.dirName}/runtime")
        }

        // ----

        JillTask jillPackagedTask = project.tasks.create(
                "jill${config.fullName.capitalize()}PackagedLibraries",
                JillTask)

        jillPackagedTask.dependsOn variantData.variantDependency.packageConfiguration.buildDependencies
        jillPackagedTask.androidBuilder = androidBuilder
        jillPackagedTask.dexOptions = getExtension().dexOptions

        conventionMapping(jillPackagedTask).map("inputLibs") {
            androidBuilder.getPackagedJars(config)
        }
        conventionMapping(jillPackagedTask).map("outputFolder") {
            project.file(
                    "${project.buildDir}/${FD_INTERMEDIATES}/jill/${config.dirName}/packaged")
        }

        // ----- Create Jack Task -----
        JackTask compileTask = project.tasks.create(
                "compile${config.fullName.capitalize()}Java",
                JackTask)
        compileTask.isVerbose = isVerbose()
        compileTask.isDebugLog = isDebugLog()

        variantData.jackTask = compileTask
        variantData.jackTask.dependsOn variantData.sourceGenTask, jillRuntimeTask, jillPackagedTask
        variantData.compileTask.dependsOn variantData.jackTask
        // TODO - dependency information for the compile classpath is being lost.
        // Add a temporary approximation
        compileTask.dependsOn variantData.variantDependency.compileConfiguration.buildDependencies

        compileTask.androidBuilder = androidBuilder
        conventionMapping(compileTask).
                map("javaMaxHeapSize") { getExtension().dexOptions.getJavaMaxHeapSize() }

        compileTask.source = variantData.getJavaSources()

        compileTask.multiDexEnabled = config.isMultiDexEnabled()
        compileTask.minSdkVersion = config.minSdkVersion.apiLevel

        // if the tested variant is an app, add its classpath. For the libraries,
        // it's done automatically since the classpath includes the library output as a normal
        // dependency.
        if (testedVariantData instanceof ApplicationVariantData) {
            conventionMapping(compileTask).map("classpath") {
                project.fileTree(jillRuntimeTask.outputFolder) +
                        testedVariantData.jackTask.classpath +
                        project.fileTree(testedVariantData.jackTask.jackFile)
            }
        } else {
            conventionMapping(compileTask).map("classpath") {
                project.fileTree(jillRuntimeTask.outputFolder)
            }
        }

        conventionMapping(compileTask).map("packagedLibraries") {
            project.fileTree(jillPackagedTask.outputFolder).files
        }

        conventionMapping(compileTask).map("destinationDir") {
            project.file("$project.buildDir/${FD_INTERMEDIATES}/dex/${config.dirName}")
        }

        conventionMapping(compileTask).map("jackFile") {
            project.file(
                    "$project.buildDir/${FD_INTERMEDIATES}/classes/${config.dirName}/classes.zip")
        }

        conventionMapping(compileTask).map("tempFolder") {
            project.file("$project.buildDir/${FD_INTERMEDIATES}/tmp/jack/${config.dirName}")
        }
        if (config.isMinifyEnabled()) {
            conventionMapping(compileTask).map("proguardFiles") {
                // since all the output use the same resources, we can use the first output
                // to query for a proguard file.
                BaseVariantOutputData variantOutputData = variantData.outputs.get(0)

                List<File> proguardFiles = config.getProguardFiles(true /*includeLibs*/,
                        [getExtension().getDefaultProguardFile(DEFAULT_PROGUARD_CONFIG_FILE)])
                File proguardResFile = variantOutputData.processResourcesTask.proguardOutputFile
                if (proguardResFile != null) {
                    proguardFiles.add(proguardResFile)
                }
                // for tested app, we only care about their aapt config since the base
                // configs are the same files anyway.
                if (testedVariantData != null) {
                    // use single output for now.
                    proguardResFile =
                            testedVariantData.outputs.get(0).processResourcesTask.proguardOutputFile
                    if (proguardResFile != null) {
                        proguardFiles.add(proguardResFile)
                    }
                }

                return proguardFiles
            }

            compileTask.mappingFile = variantData.mappingFile = project.file(
                    "${project.buildDir}/${FD_OUTPUTS}/mapping/${variantData.variantConfiguration.dirName}/mapping.txt")
        }

        configureLanguageLevel(compileTask)
    }

    /**
     * Configures the source and target language level of a compile task. If the user has set it
     * explicitly, we obey the setting. Otherwise we change the default language level based on the
     * compile SDK version.
     *
     * <p>This method modifies getExtension().compileOptions, to propagate the language level to Studio.
     */
    private void configureLanguageLevel(AbstractCompile compileTask) {
        def compileOptions = getExtension().compileOptions
        JavaVersion javaVersionToUse

        Integer compileSdkLevel =
                AndroidTargetHash.getVersionFromHash(getExtension().compileSdkVersion)?.apiLevel
        switch (compileSdkLevel) {
            case null:  // Default to 1.6 if we fail to parse compile SDK version.
            case 0..20:
                javaVersionToUse = JavaVersion.VERSION_1_6
                break
            default:
                javaVersionToUse = JavaVersion.VERSION_1_7
                break
        }

        def jdkVersion = JavaVersion.toVersion(System.getProperty("java.specification.version"))
        if (jdkVersion < javaVersionToUse) {
            logger.info(
                    "Default language level for 'compileSdkVersion $compileSdkLevel' is " +
                            "$javaVersionToUse, but the JDK used is $jdkVersion, so the JDK " +
                            "language level will be used.")
            javaVersionToUse = jdkVersion
        }

        compileOptions.defaultJavaVersion = javaVersionToUse

        conventionMapping(compileTask).map("sourceCompatibility") {
            compileOptions.sourceCompatibility.toString()
        }
        conventionMapping(compileTask).map("targetCompatibility") {
            compileOptions.targetCompatibility.toString()
        }
    }

    /**
     * Creates the final packaging task, and optionally the zipalign task (if the variant is signed)
     * @param variantData
     * @param assembleTask an optional assembleTask to be used. If null a new one is created. The
     *                assembleTask is always set in the Variant.
     * @param publishApk if true the generated APK gets published.
     */
    public void createPackagingTask(@NonNull ApkVariantData variantData, boolean publishApk) {
        GradleVariantConfiguration config = variantData.variantConfiguration

        boolean signedApk = variantData.isSigned()
        BasePluginConvention convention = project.convention.findPlugin(BasePluginConvention)
        String projectBaseName = convention.archivesBaseName
        String defaultLocation = "$project.buildDir/${FD_OUTPUTS}/apk"
        String apkLocation = defaultLocation
        if (project.hasProperty(PROPERTY_APK_LOCATION)) {
            apkLocation = project.getProperties().get(PROPERTY_APK_LOCATION)
        }
        SigningConfig sc = (SigningConfig) config.signingConfig

        boolean multiOutput = variantData.outputs.size() > 1

        // loop on all outputs. The only difference will be the name of the task, and location
        // of the generated data.
        for (ApkVariantOutputData vod : variantData.outputs) {
            // create final var inside the loop to ensure the closures will work.
            final ApkVariantOutputData variantOutputData = vod

            String outputName = variantOutputData.fullName
            String outputBaseName = variantOutputData.baseName

            // Add a task to generate application package
            PackageApplication packageApp = project.tasks.
                    create("package${outputName.capitalize()}",
                            PackageApplication)
            variantOutputData.packageApplicationTask = packageApp
            packageApp.dependsOn variantOutputData.processResourcesTask,
                    variantData.processJavaResourcesTask

            optionalDependsOn(packageApp, variantData.dexTask, variantData.jackTask)

            if (variantOutputData.packageSplitResourcesTask != null) {
                packageApp.dependsOn variantOutputData.packageSplitResourcesTask
            }
            if (variantOutputData.packageSplitAbiTask != null) {
                packageApp.dependsOn variantOutputData.packageSplitAbiTask
            }

            // Add dependencies on NDK tasks if NDK plugin is applied.
            if (getExtension().getUseNewNativePlugin()) {
                throw new RuntimeException("useNewNativePlugin is currently not supported.")
            } else {
                packageApp.dependsOn variantData.ndkCompileTask
            }

            packageApp.androidBuilder = androidBuilder

            if (config.minifyEnabled && config.buildType.shrinkResources && !config.useJack) {
                def shrinkTask = createShrinkResourcesTask(vod)

                // When shrinking resources, rather than having the packaging task
                // directly map to the packageOutputFile of ProcessAndroidResources,
                // we insert the ShrinkResources task into the chain, such that its
                // input is the ProcessAndroidResources packageOutputFile, and its
                // output is what the PackageApplication task reads.
                packageApp.dependsOn shrinkTask
                conventionMapping(packageApp).map("resourceFile") {
                    shrinkTask.compressedResources
                }
            } else {
                conventionMapping(packageApp).map("resourceFile") {
                    variantOutputData.processResourcesTask.packageOutputFile
                }
            }
            conventionMapping(packageApp).map("dexFolder") {
                if (variantData.dexTask != null) {
                    return variantData.dexTask.outputFolder
                }

                if (variantData.jackTask != null) {
                    return variantData.jackTask.getDestinationDir()
                }

                return null
            }
            conventionMapping(packageApp).map("dexedLibraries") {
                if (config.isMultiDexEnabled() &&
                        !config.isLegacyMultiDexMode() &&
                        variantData.preDexTask != null) {
                    return project.fileTree(variantData.preDexTask.outputFolder).files
                }

                return Collections.<File> emptyList()
            }
            conventionMapping(packageApp).
                    map("packagedJars") { androidBuilder.getPackagedJars(config) }
            conventionMapping(packageApp).map("javaResourceDir") {
                getOptionalDir(variantData.processJavaResourcesTask.destinationDir)
            }
            conventionMapping(packageApp).map("jniFolders") {
                Set<String> filters = AbiSplitOptions.getAbiFilters(
                        getExtension().getSplits().getAbiFilters())
                return filters.isEmpty() ? getJniFolders(variantData) : Collections.emptySet();
            }
            conventionMapping(packageApp).map("abiFilters") {
                if (variantOutputData.getMainOutputFile().getFilter(OutputFile.ABI) != null) {
                    return ImmutableSet.
                            of(variantOutputData.getMainOutputFile().getFilter(OutputFile.ABI))
                }
                return config.supportedAbis
            }
            conventionMapping(packageApp).map("jniDebugBuild") { config.buildType.jniDebuggable }

            conventionMapping(packageApp).map("signingConfig") { sc }
            if (sc != null) {
                ValidateSigningTask validateSigningTask = validateSigningTaskMap.get(sc)
                if (validateSigningTask == null) {
                    validateSigningTask =
                            project.tasks.create("validate${sc.name.capitalize()}Signing",
                                    ValidateSigningTask)
                    validateSigningTask.androidBuilder = androidBuilder
                    validateSigningTask.signingConfig = sc

                    validateSigningTaskMap.put(sc, validateSigningTask)
                }

                packageApp.dependsOn validateSigningTask
            }

            String apkName = signedApk ?
                    "$projectBaseName-${outputBaseName}-unaligned.apk" :
                    "$projectBaseName-${outputBaseName}-unsigned.apk"

            conventionMapping(packageApp).map("packagingOptions") { getExtension().packagingOptions }

            conventionMapping(packageApp).map("outputFile") {
                // if this is the final task then the location is
                // the potentially overridden one.
                if (!signedApk || !variantData.zipAlignEnabled) {
                    project.file("$apkLocation/${apkName}")
                } else {
                    // otherwise default one.
                    project.file("$defaultLocation/${apkName}")
                }
            }

            Task appTask = packageApp
            OutputFileTask outputFileTask = packageApp

            if (signedApk) {
                if (variantData.zipAlignEnabled) {
                    // Add a task to zip align application package
                    def zipAlignTask = project.tasks.create(
                            "zipalign${outputName.capitalize()}",
                            ZipAlign)
                    variantOutputData.zipAlignTask = zipAlignTask

                    zipAlignTask.dependsOn packageApp
                    conventionMapping(zipAlignTask).map("inputFile") { packageApp.outputFile }
                    conventionMapping(zipAlignTask).map("outputFile") {
                        project.file(
                                "$apkLocation/$projectBaseName-${outputBaseName}.apk")
                    }
                    conventionMapping(zipAlignTask).map("zipAlignExe") {
                        String path = androidBuilder.targetInfo?.buildTools?.getPath(ZIP_ALIGN)
                        if (path != null) {
                            return new File(path)
                        }

                        return null
                    }
                    if (variantOutputData.splitZipAlign != null) {
                        zipAlignTask.dependsOn variantOutputData.splitZipAlign
                    }

                    appTask = zipAlignTask

                    outputFileTask = zipAlignTask
                }
            }

            assert variantData.assembleVariantTask != null

            // Add an assemble task
            if (multiOutput) {
                // create a task for this output
                variantOutputData.assembleTask = createAssembleTask(variantOutputData)

                // variant assemble task depends on each output assemble task.
                variantData.assembleVariantTask.dependsOn variantOutputData.assembleTask
            } else {
                // single output
                variantOutputData.assembleTask = variantData.assembleVariantTask
            }

            if (!signedApk && variantOutputData.packageSplitResourcesTask != null) {
                // in case we are not signing the resulting APKs and we have some pure splits
                // we should manually copy them from the intermediate location to the final
                // apk location unmodified.
                Copy copyTask = project.tasks.create(
                        "copySplit${outputName.capitalize()}",
                        Copy)
                copyTask.destinationDir = new File(apkLocation as String);
                copyTask.from(variantOutputData.packageSplitResourcesTask.getOutputDirectory())
                variantOutputData.assembleTask.dependsOn(copyTask)
                copyTask.mustRunAfter(appTask)
            }

            variantOutputData.assembleTask.dependsOn appTask

            if (publishApk) {
                if (getExtension().defaultPublishConfig.equals(outputName)) {
                    // add the artifact that will be published
                    project.artifacts.add("default", new ApkPublishArtifact(
                            projectBaseName,
                            null,
                            outputFileTask))
                }

                // also publish the artifact with its full config name
                if (getExtension().publishNonDefault) {
                    // classifier cannot just be the publishing config as we need
                    // to add the filters if needed.
                    String classifier = variantData.variantDependency.publishConfiguration.name
                    if (variantOutputData.getMainOutputFile().getFilter(OutputFile.DENSITY) != null) {
                        classifier =
                                "${classifier}-${variantOutputData.getMainOutputFile().getFilter(OutputFile.DENSITY)}"
                    }
                    if (variantOutputData.getMainOutputFile().getFilter(OutputFile.ABI) != null) {
                        classifier =
                                "${classifier}-${variantOutputData.getMainOutputFile().getFilter(OutputFile.ABI)}"
                    }

                    project.artifacts.add(variantData.variantDependency.publishConfiguration.name,
                            new ApkPublishArtifact(
                                    projectBaseName,
                                    classifier,
                                    outputFileTask))
                }
            }
        }

        // create install task for the variant Data. This will deal with finding the
        // right output if there are more than one.
        // Add a task to install the application package
        if (signedApk) {
            InstallVariantTask installTask = project.tasks.
                    create("install${config.fullName.capitalize()}",
                            InstallVariantTask)
            installTask.description = "Installs the ${variantData.description}."
            installTask.group = INSTALL_GROUP
            installTask.projectName = project.name
            installTask.variantData = variantData
            installTask.timeOutInMs = getExtension().getAdbOptions().getTimeOutInMs();
            installTask.processExecutor = androidBuilder.getProcessExecutor()
            conventionMapping(installTask).map("adbExe") { sdkHandler.sdkInfo?.adb }
            conventionMapping(installTask).map("splitSelectExe") {
                String path = androidBuilder.targetInfo?.buildTools?.getPath(
                        BuildToolInfo.PathId.SPLIT_SELECT);
                path != null ? new File(path) : null;
            }
            installTask.dependsOn variantData.assembleVariantTask
            variantData.installTask = installTask
        }


        if (getExtension().lintOptions.checkReleaseBuilds) {
            createLintVitalTask(variantData)
        }

        // add an uninstall task
        def uninstallTask = project.tasks.create(
                "uninstall${variantData.variantConfiguration.fullName.capitalize()}",
                UninstallTask)
        uninstallTask.description = "Uninstalls the ${variantData.description}."
        uninstallTask.group = INSTALL_GROUP
        uninstallTask.variant = variantData
        conventionMapping(uninstallTask).map("adbExe") { sdkHandler.getSdkInfo()?.adb }

        variantData.uninstallTask = uninstallTask
        uninstallAll.dependsOn uninstallTask
    }

    public Task createAssembleTask(
            @NonNull BaseVariantOutputData variantOutputData) {
        Task assembleTask = project.tasks.
                create("assemble${variantOutputData.fullName.capitalize()}")
        return assembleTask
    }

    public Task createAssembleTask(
            @NonNull BaseVariantData<? extends BaseVariantOutputData> variantData) {
        Task assembleTask = project.tasks.
                create("assemble${variantData.variantConfiguration.fullName.capitalize()}")
        assembleTask.description = "Assembles the ${variantData.description}."
        assembleTask.group = BUILD_GROUP
        return assembleTask
    }

    public Copy getJacocoAgentTask() {
        if (jacocoAgentTask == null) {
            jacocoAgentTask = project.tasks.create("unzipJacocoAgent", Copy)
            jacocoAgentTask.from {
                project.configurations.getByName(JacocoPlugin.AGENT_CONFIGURATION_NAME).
                        collect { project.zipTree(it) }
            }
            jacocoAgentTask.include FILE_JACOCO_AGENT
            jacocoAgentTask.into "$project.buildDir/${FD_INTERMEDIATES}/jacoco"
        }

        return jacocoAgentTask
    }

    /**
     * creates a zip align. This does not use convention mapping,
     * and is meant to let other plugin create zip align tasks.
     *
     * @param name the name of the task
     * @param inputFile the input file
     * @param outputFile the output file
     *
     * @return the task
     */
    @NonNull
    ZipAlign createZipAlignTask(
            @NonNull String name,
            @NonNull File inputFile,
            @NonNull File outputFile) {
        // Add a task to zip align application package
        def zipAlignTask = project.tasks.create(name, ZipAlign)

        zipAlignTask.inputFile = inputFile
        zipAlignTask.outputFile = outputFile
        conventionMapping(zipAlignTask).map("zipAlignExe") {
            String path = androidBuilder.targetInfo?.buildTools?.getPath(ZIP_ALIGN)
            if (path != null) {
                return new File(path)
            }

            return null
        }

        return zipAlignTask
    }

    /**
     * Creates the proguarding task for the given Variant.
     * @param variantData the variant data.
     * @param testedVariantData optional. variant data representing the tested variant, null if the
     *                          variant is not a test variant
     * @return outFile file outputted by proguard
     */
    @NonNull
    public void createProguardTasks(
            final @NonNull BaseVariantData<? extends BaseVariantOutputData> variantData,
            final @Nullable BaseVariantData<? extends BaseVariantOutputData> testedVariantData,
            final @NonNull PostCompilationData pcData) {
        final VariantConfiguration variantConfig = variantData.variantConfiguration

        // use single output for now.
        final BaseVariantOutputData variantOutputData = variantData.outputs.get(0)

        def proguardTask = project.tasks.create(
                "proguard${variantData.variantConfiguration.fullName.capitalize()}",
                ProGuardTask)

        if (testedVariantData != null) {
            proguardTask.dependsOn testedVariantData.obfuscationTask
        }

        variantData.obfuscationTask = proguardTask

        // --- Output File ---

        final File outFile = variantData instanceof LibraryVariantData ?
            project.file(
                    "${project.buildDir}/${FD_INTERMEDIATES}/$DIR_BUNDLES/${variantData.variantConfiguration.dirName}/classes.jar") :
            project.file(
                    "${project.buildDir}/${FD_INTERMEDIATES}/classes-proguard/${variantData.variantConfiguration.dirName}/classes.jar")
        variantData.obfuscatedClassesJar = outFile

        // --- Proguard Config ---

        if (testedVariantData != null) {
            // Don't remove any code in tested app.
            proguardTask.dontshrink()
            proguardTask.dontoptimize()

            // We can't call dontobfuscate, since that would make ProGuard ignore the mapping file.
            proguardTask.keep("class * {*;}")
            proguardTask.keep("interface * {*;}")
            proguardTask.keep("enum * {*;}")

            // Input the mapping from the tested app so that we can deal with obfuscated code.
            proguardTask.applymapping("${project.buildDir}/${FD_OUTPUTS}/mapping/${testedVariantData.variantConfiguration.dirName}/mapping.txt")
        } else {
            if (variantConfig.isTestCoverageEnabled()) {
                // when collecting coverage, don't remove the JaCoCo runtime
                proguardTask.keep("class com.vladium.** {*;}")
                proguardTask.keep("class org.jacoco.** {*;}")
                proguardTask.keep("interface org.jacoco.** {*;}")
                proguardTask.dontwarn("org.jacoco.**")
            }

            proguardTask.configuration {
                List<File> proguardFiles = variantConfig.getProguardFiles(true /*includeLibs*/,
                        [extension.getDefaultProguardFile(DEFAULT_PROGUARD_CONFIG_FILE)])

                proguardFiles + [variantOutputData.processResourcesTask.proguardOutputFile]
            }
        }

        // --- InJars / LibraryJars ---

        if (variantData instanceof LibraryVariantData) {
            String packageName = variantConfig.getPackageFromManifest()
            if (packageName == null) {
                throw new BuildException("Failed to read manifest", null)
            }
            packageName = packageName.replace('.', '/');

            // injar: the compilation output
            // exclude R files and such from output
            String exclude = '!' + packageName + "/R.class"
            exclude += (', !' + packageName + "/R\$*.class")
            if (!((LibraryExtension) getExtension()).packageBuildConfig) {
                exclude += (', !' + packageName + "/Manifest.class")
                exclude += (', !' + packageName + "/Manifest\$*.class")
                exclude += (', !' + packageName + "/BuildConfig.class")
            }
            proguardTask.injars(pcData.inputDir, filter: exclude)

            // include R files and such for compilation
            String include = exclude.replace('!', '')
            proguardTask.libraryjars(pcData.inputDir, filter: include)

            // injar: the local dependencies
            Closure inJars = {
                DependencyManager.getPackagedLocalJarFileList(variantData.variantDependency)
            }

            proguardTask.injars(inJars, filter: '!META-INF/MANIFEST.MF')

            // libjar: the library dependencies. In this case we take all the compile-scope
            // dependencies
            Closure libJars = {
                // get all the compiled jar.
                Set<File> compiledJars = androidBuilder.getCompileClasspath(variantConfig)
                // and remove local jar that are also packaged
                List<File> localJars =
                        DependencyManager.getPackagedLocalJarFileList(variantData.variantDependency)

                compiledJars.findAll({ !localJars.contains(it) })
            }

            proguardTask.libraryjars(libJars, filter: '!META-INF/MANIFEST.MF')

            // ensure local jars keep their package names
            proguardTask.keeppackagenames()
        } else {
            // injar: the compilation output
            proguardTask.injars(pcData.inputDir)

            // injar: the packaged dependencies
            proguardTask.injars(pcData.inputLibraries, filter: '!META-INF/MANIFEST.MF')

            // the provided-only jars as libraries.
            Closure libJars = {
                variantData.variantConfiguration.providedOnlyJars
            }

            proguardTask.libraryjars(libJars)
        }

        // libraryJars: the runtime jars. Do this in doFirst since the boot classpath isn't
        // available until the SDK is loaded in the prebuild task
        proguardTask.doFirst {
            for (String runtimeJar : androidBuilder.getBootClasspathAsStrings()) {
                proguardTask.libraryjars(runtimeJar)
            }
        }

        if (testedVariantData != null) {
            // input the tested app as library
            proguardTask.libraryjars(testedVariantData.javaCompileTask.destinationDir)
            // including its dependencies
            Closure testedPackagedJars = {
                androidBuilder.getPackagedJars(testedVariantData.variantConfiguration)
            }

            proguardTask.libraryjars(testedPackagedJars, filter: '!META-INF/MANIFEST.MF')
        }

        // --- Out files ---

        proguardTask.outjars(outFile)

        final File proguardOut = project.file(
                "${project.buildDir}/${FD_OUTPUTS}/mapping/${variantData.variantConfiguration.dirName}")

        proguardTask.dump(new File(proguardOut, "dump.txt"))
        proguardTask.printseeds(new File(proguardOut, "seeds.txt"))
        proguardTask.printusage(new File(proguardOut, "usage.txt"))
        proguardTask.printmapping(variantData.mappingFile = new File(proguardOut, "mapping.txt"))

        // proguard doesn't verify that the seed/mapping/usage folders exist and will fail
        // if they don't so create them.
        proguardTask.doFirst {
            proguardOut.mkdirs()
        }

        // update dependency.
        optionalDependsOn(proguardTask, pcData.classGeneratingTask)
        optionalDependsOn(proguardTask, pcData.libraryGeneratingTask)
        pcData.libraryGeneratingTask = [proguardTask]
        pcData.classGeneratingTask = [proguardTask]

        // Update the inputs
        pcData.inputFiles = { [outFile] }
        pcData.inputDir = null
        pcData.inputLibraries = { [] }
    }

    private ShrinkResources createShrinkResourcesTask(ApkVariantOutputData variantOutputData) {
        def variantData = variantOutputData.variantData
        def task = project.tasks.create(
                "shrink${variantOutputData.fullName.capitalize()}Resources",
                ShrinkResources)
        task.androidBuilder = androidBuilder
        task.variantOutputData = variantOutputData

        String outputBaseName = variantOutputData.baseName
        conventionMapping(task).map("compressedResources") {
            project.file("$project.buildDir/${FD_INTERMEDIATES}/resources/" +
                            "resources-${outputBaseName}-stripped.ap_")
        }

        conventionMapping(task).map("uncompressedResources") {
            variantOutputData.processResourcesTask.packageOutputFile
        }

        task.dependsOn variantData.obfuscationTask, variantOutputData.manifestProcessorTask,
                variantOutputData.processResourcesTask

        return task
    }

    public void createReportTasks(
            List<BaseVariantData<? extends BaseVariantOutputData>> variantDataList) {
        def dependencyReportTask = project.tasks.create("androidDependencies", DependencyReportTask)
        dependencyReportTask.setDescription("Displays the Android dependencies of the project.")
        dependencyReportTask.setVariants(variantDataList)
        dependencyReportTask.setGroup(ANDROID_GROUP)

        def signingReportTask = project.tasks.create("signingReport", SigningReportTask)
        signingReportTask.setDescription("Displays the signing info for each variant.")
        signingReportTask.setVariants(variantDataList)
        signingReportTask.setGroup(ANDROID_GROUP)
    }

    public void createAnchorTasks(
            @NonNull BaseVariantData<? extends BaseVariantOutputData> variantData) {
        variantData.preBuildTask = project.tasks.create(
                "pre${variantData.variantConfiguration.fullName.capitalize()}Build")
        variantData.preBuildTask.dependsOn mainPreBuild

        def prepareDependenciesTask = project.tasks.create(
                "prepare${variantData.variantConfiguration.fullName.capitalize()}Dependencies",
                PrepareDependenciesTask)

        variantData.prepareDependenciesTask = prepareDependenciesTask
        prepareDependenciesTask.dependsOn variantData.preBuildTask

        prepareDependenciesTask.androidBuilder = androidBuilder
        prepareDependenciesTask.variant = variantData

        // for all libraries required by the configurations of this variant, make this task
        // depend on all the tasks preparing these libraries.
        VariantDependencies configurationDependencies = variantData.variantDependency
        prepareDependenciesTask.addChecker(configurationDependencies.checker)

        for (LibraryDependencyImpl lib : configurationDependencies.libraries) {
            dependencyManager.addDependencyToPrepareTask(variantData, prepareDependenciesTask, lib)
        }

        // also create sourceGenTask
        variantData.sourceGenTask = project.tasks.create(
                "generate${variantData.variantConfiguration.fullName.capitalize()}Sources")
        // and resGenTask
        variantData.resourceGenTask = project.tasks.create(
                "generate${variantData.variantConfiguration.fullName.capitalize()}Resources")
        variantData.assetGenTask = project.tasks.create(
                "generate${variantData.variantConfiguration.fullName.capitalize()}Assets")
        // and compile task
        createCompileAnchorTask(variantData)
    }

    private void createCompileAnchorTask(
            BaseVariantData<? extends BaseVariantOutputData> variantData) {
        variantData.compileTask = project.tasks.create(
                "compile${variantData.variantConfiguration.fullName.capitalize()}Sources")
        variantData.compileTask.group = BUILD_GROUP
    }

    public void createCheckManifestTask(
            @NonNull BaseVariantData<? extends BaseVariantOutputData> variantData) {
        String name = variantData.variantConfiguration.fullName
        variantData.checkManifestTask = project.tasks.create(
                "check${name.capitalize()}Manifest",
                CheckManifest)
        variantData.checkManifestTask.dependsOn variantData.preBuildTask

        variantData.prepareDependenciesTask.dependsOn variantData.checkManifestTask

        variantData.checkManifestTask.variantName = name
        conventionMapping(variantData.checkManifestTask).map("manifest") {
            variantData.variantConfiguration.getDefaultSourceSet().manifestFile
        }
    }

    public static void optionalDependsOn(@NonNull Task main, Task... dependencies) {
        for (Task dependency : dependencies) {
            if (dependency != null) {
                main.dependsOn dependency
            }
        }
    }

    public static void optionalDependsOn(@NonNull Task main, @NonNull List<?> dependencies) {
        for (Object dependency : dependencies) {
            if (dependency != null) {
                main.dependsOn dependency
            }
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
    private List<ManifestDependencyImpl> getManifestDependencies(
            List<LibraryDependency> libraries) {

        List<ManifestDependencyImpl> list = Lists.newArrayListWithCapacity(libraries.size())

        for (LibraryDependency lib : libraries) {
            // get the dependencies
            List<ManifestDependencyImpl> children = getManifestDependencies(lib.dependencies)
            list.add(new ManifestDependencyImpl(lib.getName(), lib.manifest, children))
        }

        return list
    }

    @NonNull
    protected static List<SymbolFileProviderImpl> getTextSymbolDependencies(
            List<LibraryDependency> libraries) {

        List<SymbolFileProviderImpl> list = Lists.newArrayListWithCapacity(libraries.size())

        for (LibraryDependency lib : libraries) {
            list.add(new SymbolFileProviderImpl(lib.manifest, lib.symbolFile))
        }

        return list
    }

    protected static File getOptionalDir(File dir) {
        if (dir.isDirectory()) {
            return dir
        }

        return null
    }
}
