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

import com.android.annotations.NonNull
import com.android.annotations.Nullable
import com.android.build.gradle.api.AndroidSourceSet
import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.internal.BadPluginException
import com.android.build.gradle.internal.ConfigurationDependencies
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.ProductFlavorData
import com.android.build.gradle.internal.Sdk
import com.android.build.gradle.internal.api.DefaultAndroidSourceSet
import com.android.build.gradle.internal.dependency.DependencyChecker
import com.android.build.gradle.internal.dependency.LibraryDependencyImpl
import com.android.build.gradle.internal.dependency.ManifestDependencyImpl
import com.android.build.gradle.internal.dependency.SymbolFileProviderImpl
import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.build.gradle.internal.dsl.SigningConfigDsl
import com.android.build.gradle.internal.model.ArtifactMetaDataImpl
import com.android.build.gradle.internal.model.JavaArtifactImpl
import com.android.build.gradle.internal.model.ModelBuilder
import com.android.build.gradle.internal.tasks.AndroidReportTask
import com.android.build.gradle.internal.tasks.CheckManifest
import com.android.build.gradle.internal.tasks.DependencyReportTask
import com.android.build.gradle.internal.tasks.DeviceProviderInstrumentTestLibraryTask
import com.android.build.gradle.internal.tasks.DeviceProviderInstrumentTestTask
import com.android.build.gradle.internal.tasks.InstallTask
import com.android.build.gradle.internal.tasks.OutputFileTask
import com.android.build.gradle.internal.tasks.PrepareDependenciesTask
import com.android.build.gradle.internal.tasks.PrepareLibraryTask
import com.android.build.gradle.internal.tasks.SigningReportTask
import com.android.build.gradle.internal.tasks.TestServerTask
import com.android.build.gradle.internal.tasks.UninstallTask
import com.android.build.gradle.internal.tasks.ValidateSigningTask
import com.android.build.gradle.internal.test.report.ReportType
import com.android.build.gradle.internal.variant.ApkVariantData
import com.android.build.gradle.internal.variant.ApplicationVariantData
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.build.gradle.internal.variant.DefaultSourceProviderContainer
import com.android.build.gradle.internal.variant.LibraryVariantData
import com.android.build.gradle.internal.variant.TestVariantData
import com.android.build.gradle.internal.variant.TestedVariantData
import com.android.build.gradle.tasks.AidlCompile
import com.android.build.gradle.tasks.Dex
import com.android.build.gradle.tasks.GenerateBuildConfig
import com.android.build.gradle.tasks.Lint
import com.android.build.gradle.tasks.MergeAssets
import com.android.build.gradle.tasks.MergeResources
import com.android.build.gradle.tasks.NdkCompile
import com.android.build.gradle.tasks.PackageApplication
import com.android.build.gradle.tasks.PreDex
import com.android.build.gradle.tasks.ProcessAndroidResources
import com.android.build.gradle.tasks.ProcessAppManifest
import com.android.build.gradle.tasks.ProcessTestManifest
import com.android.build.gradle.tasks.RenderscriptCompile
import com.android.build.gradle.tasks.ZipAlign
import com.android.builder.AndroidBuilder
import com.android.builder.DefaultProductFlavor
import com.android.builder.SdkParser
import com.android.builder.VariantConfiguration
import com.android.builder.dependency.JarDependency
import com.android.builder.dependency.LibraryDependency
import com.android.builder.model.AndroidArtifact
import com.android.builder.model.AndroidProject
import com.android.builder.model.ArtifactMetaData
import com.android.builder.model.BuildType
import com.android.builder.model.JavaArtifact
import com.android.builder.model.ProductFlavor
import com.android.builder.model.SigningConfig
import com.android.builder.model.SourceProvider
import com.android.builder.model.SourceProviderContainer
import com.android.builder.testing.ConnectedDeviceProvider
import com.android.builder.testing.api.DeviceProvider
import com.android.builder.testing.api.TestServer
import com.android.utils.ILogger
import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.ListMultimap
import com.google.common.collect.Lists
import com.google.common.collect.Maps
import com.google.common.collect.Multimap
import com.google.common.collect.Sets
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.SelfResolvingDependency
import org.gradle.api.artifacts.result.DependencyResult
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.artifacts.result.UnresolvedDependencyResult
import org.gradle.api.logging.LogLevel
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.specs.Specs
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.internal.reflect.Instantiator
import org.gradle.language.jvm.tasks.ProcessResources
import org.gradle.tooling.BuildException
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry
import org.gradle.util.GUtil
import proguard.gradle.ProGuardTask

import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.jar.Attributes
import java.util.jar.Manifest

import static com.android.builder.BuilderConstants.CONNECTED
import static com.android.builder.BuilderConstants.DEVICE
import static com.android.builder.BuilderConstants.EXT_LIB_ARCHIVE
import static com.android.builder.BuilderConstants.FD_FLAVORS
import static com.android.builder.BuilderConstants.FD_FLAVORS_ALL
import static com.android.builder.BuilderConstants.FD_INSTRUMENT_RESULTS
import static com.android.builder.BuilderConstants.FD_INSTRUMENT_TESTS
import static com.android.builder.BuilderConstants.FD_REPORTS
import static com.android.builder.BuilderConstants.INSTRUMENT_TEST
import static com.android.builder.VariantConfiguration.Type.TEST
import static java.io.File.separator

/**
 * Base class for all Android plugins
 */
public abstract class BasePlugin {
    protected final static String DIR_BUNDLES = "bundles";

    public static final String GRADLE_MIN_VERSION = "1.10"
    public static final String[] GRADLE_SUPPORTED_VERSIONS = [ GRADLE_MIN_VERSION ]

    public static final String INSTALL_GROUP = "Install"

    public static File TEST_SDK_DIR;

    protected Instantiator instantiator
    private ToolingModelBuilderRegistry registry

    private final Map<Object, AndroidBuilder> builders = Maps.newIdentityHashMap()

    final List<BaseVariantData> variantDataList = []
    final Map<LibraryDependencyImpl, PrepareLibraryTask> prepareTaskMap = [:]
    final Map<SigningConfig, ValidateSigningTask> validateSigningTaskMap = [:]

    protected Project project
    private LoggerWrapper loggerWrapper
    private Sdk sdk
    private String creator

    private boolean hasCreatedTasks = false

    private ProductFlavorData<DefaultProductFlavor> defaultConfigData
    private final Collection<String> unresolvedDependencies = Sets.newHashSet();

    protected DefaultAndroidSourceSet mainSourceSet
    protected DefaultAndroidSourceSet testSourceSet

    protected Task mainPreBuild
    protected Task uninstallAll
    protected Task assembleTest
    protected Task deviceCheck
    protected Task connectedCheck
    protected Task lintCompile
    protected Task lintAll
    protected Task lintVital

    protected BasePlugin(Instantiator instantiator, ToolingModelBuilderRegistry registry) {
        this.instantiator = instantiator
        this.registry = registry
        String pluginVersion = getLocalVersion()
        if (pluginVersion != null) {
            creator = "Android Gradle " + pluginVersion
        } else  {
            creator = "Android Gradle"
        }
    }

    public abstract BaseExtension getExtension()
    protected abstract void doCreateAndroidTasks()

    protected void apply(Project project) {
        this.project = project

        checkGradleVersion()
        sdk = new Sdk(project, logger)

        project.apply plugin: JavaBasePlugin

        // Register a builder for the custom tooling model
        registry.register(new ModelBuilder());

        project.tasks.assemble.description =
            "Assembles all variants of all applications and secondary packages."

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

        project.afterEvaluate {
            createAndroidTasks(false)
        }
    }

    protected void setBaseExtension(@NonNull BaseExtension extension) {
        sdk.setExtension(extension)
        mainSourceSet = (DefaultAndroidSourceSet) extension.sourceSets.create(extension.defaultConfig.name)
        testSourceSet = (DefaultAndroidSourceSet) extension.sourceSets.create(INSTRUMENT_TEST)

        defaultConfigData = new ProductFlavorData<DefaultProductFlavor>(
                extension.defaultConfig, mainSourceSet,
                testSourceSet, project)
    }

    private void checkGradleVersion() {
        boolean foundMatch = false
        for (String version : GRADLE_SUPPORTED_VERSIONS) {
            if (project.getGradle().gradleVersion.startsWith(version)) {
                foundMatch = true
                break
            }
        }

        if (!foundMatch) {
            File file = new File("gradle" + separator + "wrapper" + separator +
                    "gradle-wrapper.properties");
            throw new BuildException(
                String.format(
                    "Gradle version %s is required. Current version is %s. " +
                    "If using the gradle wrapper, try editing the distributionUrl in %s " +
                    "to gradle-%s-all.zip",
                    GRADLE_MIN_VERSION, project.getGradle().gradleVersion, file.getAbsolutePath(),
                    GRADLE_MIN_VERSION), null);

        }
    }

    final void createAndroidTasks(boolean force) {
        // get current plugins and look for the default Java plugin.
        if (project.plugins.hasPlugin(JavaPlugin.class)) {
            throw new BadPluginException(
                    "The 'java' plugin has been applied, but it is not compatible with the Android plugins.")
        }

        // don't do anything if the project was not initialized.
        // Unless TEST_SDK_DIR is set in which case this is unit tests and we don't return.
        // This is because project don't get evaluated in the unit test setup.
        // See AppPluginDslTest
        if (!force && (!project.state.executed || project.state.failure != null) && TEST_SDK_DIR == null) {
            return
        }

        if (hasCreatedTasks) {
            return
        }
        hasCreatedTasks = true

        doCreateAndroidTasks()
        createReportTasks()

        if (lintVital != null) {
            project.gradle.taskGraph.whenReady { taskGraph ->
                if (taskGraph.hasTask(lintAll)) {
                    lintVital.setEnabled(false)
                }
            }
        }
    }

    void checkTasksAlreadyCreated() {
        if (hasCreatedTasks) {
            throw new GradleException(
                    "Android tasks have already been created.\n" +
                    "This happens when calling android.applicationVariants,\n" +
                    "android.libraryVariants or android.testVariants.\n" +
                    "Once these methods are called, it is not possible to\n" +
                    "continue configuring the model.")
        }
    }


    ProductFlavorData getDefaultConfigData() {
        return defaultConfigData
    }

    Collection<String> getUnresolvedDependencies() {
        return unresolvedDependencies
    }

    SdkParser getSdkParser() {
        return sdk.parser
    }

    SdkParser getLoadedSdkParser() {
        return sdk.loadParser()
    }

    File getSdkDirectory() {
        return sdk.sdkDirectory
    }

    File getNdkDirectory() {
        return sdk.ndkDirectory
    }

    ILogger getLogger() {
        if (loggerWrapper == null) {
            loggerWrapper = new LoggerWrapper(project.logger)
        }

        return loggerWrapper
    }

    boolean isVerbose() {
        return project.logger.isEnabled(LogLevel.DEBUG)
    }

    AndroidBuilder getAndroidBuilder(BaseVariantData variantData) {
        AndroidBuilder androidBuilder = builders.get(variantData)

        if (androidBuilder == null) {
            SdkParser parser = getLoadedSdkParser()
            androidBuilder = new AndroidBuilder(parser, creator, logger, verbose)
            if (this instanceof LibraryPlugin) {
                androidBuilder.setBuildingLibrary(true);
            }

            builders.put(variantData, androidBuilder)
        }

        return androidBuilder
    }


    protected String getRuntimeJars() {
        return runtimeJarList.join(File.pathSeparator)
    }

    public List<String> getRuntimeJarList() {
        SdkParser sdkParser = getLoadedSdkParser()
        return AndroidBuilder.getBootClasspath(sdkParser);
    }

    protected void createProcessManifestTask(BaseVariantData variantData,
                                             String manifestOurDir) {
        VariantConfiguration config = variantData.variantConfiguration

        def processManifestTask = project.tasks.create(
                "process${variantData.variantConfiguration.fullName.capitalize()}Manifest",
                ProcessAppManifest)
        variantData.processManifestTask = processManifestTask
        processManifestTask.dependsOn variantData.prepareDependenciesTask
        if (config.type != TEST) {
            processManifestTask.dependsOn variantData.checkManifestTask
        }

        processManifestTask.plugin = this
        processManifestTask.variant = variantData

        ProductFlavor mergedFlavor = config.mergedFlavor

        processManifestTask.conventionMapping.mainManifest = {
            config.mainManifest
        }
        processManifestTask.conventionMapping.manifestOverlays = {
            config.manifestOverlays
        }
        processManifestTask.conventionMapping.packageNameOverride = {
            config.packageOverride
        }
        processManifestTask.conventionMapping.versionName = {
            config.versionName
        }
        processManifestTask.conventionMapping.libraries = {
            getManifestDependencies(config.directLibraries)
        }
        processManifestTask.conventionMapping.versionCode = {
            config.versionCode
        }
        processManifestTask.conventionMapping.minSdkVersion = {
            mergedFlavor.minSdkVersion
        }
        processManifestTask.conventionMapping.targetSdkVersion = {
            mergedFlavor.targetSdkVersion
        }
        processManifestTask.conventionMapping.manifestOutputFile = {
            project.file(
                    "$project.buildDir/${manifestOurDir}/${variantData.variantConfiguration.dirName}/AndroidManifest.xml")
        }
    }

    protected void createProcessTestManifestTask(BaseVariantData variantData,
                                                 String manifestOurDir) {
        def processTestManifestTask = project.tasks.create(
                "process${variantData.variantConfiguration.fullName.capitalize()}Manifest",
                ProcessTestManifest)
        variantData.processManifestTask = processTestManifestTask
        processTestManifestTask.dependsOn variantData.prepareDependenciesTask

        processTestManifestTask.plugin = this
        processTestManifestTask.variant = variantData

        VariantConfiguration config = variantData.variantConfiguration

        processTestManifestTask.conventionMapping.testPackageName = {
            config.packageName
        }
        processTestManifestTask.conventionMapping.minSdkVersion = {
            config.minSdkVersion
        }
        processTestManifestTask.conventionMapping.targetSdkVersion = {
            config.targetSdkVersion
        }
        processTestManifestTask.conventionMapping.testedPackageName = {
            config.testedPackageName
        }
        processTestManifestTask.conventionMapping.instrumentationRunner = {
            config.instrumentationRunner
        }
        processTestManifestTask.conventionMapping.handleProfiling = {
            config.handleProfiling
        }
        processTestManifestTask.conventionMapping.functionalTest = {
            config.functionalTest
        }
        processTestManifestTask.conventionMapping.libraries = {
            getManifestDependencies(config.directLibraries)
        }
        processTestManifestTask.conventionMapping.manifestOutputFile = {
            project.file(
                    "$project.buildDir/${manifestOurDir}/${variantData.variantConfiguration.dirName}/AndroidManifest.xml")
        }
    }

    protected void createRenderscriptTask(BaseVariantData variantData) {
        VariantConfiguration config = variantData.variantConfiguration

        def renderscriptTask = project.tasks.create(
                "compile${variantData.variantConfiguration.fullName.capitalize()}Renderscript",
                RenderscriptCompile)
        variantData.renderscriptCompileTask = renderscriptTask
        if (config.type == TEST) {
            renderscriptTask.dependsOn variantData.processManifestTask
        } else {
            renderscriptTask.dependsOn variantData.checkManifestTask
        }

        ProductFlavor mergedFlavor = config.mergedFlavor
        boolean ndkMode = mergedFlavor.renderscriptNdkMode

        // only put this dependency if rs will generate Java code
        if (!ndkMode) {
            variantData.sourceGenTask.dependsOn renderscriptTask
        }

        renderscriptTask.dependsOn variantData.prepareDependenciesTask
        renderscriptTask.plugin = this
        renderscriptTask.variant = variantData

        renderscriptTask.conventionMapping.targetApi = {
            int targetApi = mergedFlavor.renderscriptTargetApi
            int minSdk = config.getMinSdkVersion()
            targetApi > minSdk ? targetApi : minSdk
        }

        renderscriptTask.supportMode = mergedFlavor.renderscriptSupportMode
        renderscriptTask.ndkMode = ndkMode
        renderscriptTask.debugBuild = config.buildType.renderscriptDebugBuild
        renderscriptTask.optimLevel = config.buildType.renderscriptOptimLevel

        renderscriptTask.conventionMapping.sourceDirs = { config.renderscriptSourceList }
        renderscriptTask.conventionMapping.importDirs = { config.renderscriptImports }

        renderscriptTask.conventionMapping.sourceOutputDir = {
            project.file("$project.buildDir/source/rs/${variantData.variantConfiguration.dirName}")
        }
        renderscriptTask.conventionMapping.resOutputDir = {
            project.file("$project.buildDir/res/rs/${variantData.variantConfiguration.dirName}")
        }
        renderscriptTask.conventionMapping.objOutputDir = {
            project.file("$project.buildDir/rs/${variantData.variantConfiguration.dirName}/obj")
        }
        renderscriptTask.conventionMapping.libOutputDir = {
            project.file("$project.buildDir/rs/${variantData.variantConfiguration.dirName}/lib")
        }
        renderscriptTask.conventionMapping.ndkConfig = { config.ndkConfig }
    }

    protected void createMergeResourcesTask(@NonNull BaseVariantData variantData,
                                            final boolean process9Patch) {
        MergeResources mergeResourcesTask = basicCreateMergeResourcesTask(
                variantData,
                "merge",
                "$project.buildDir/res/all/${variantData.variantConfiguration.dirName}",
                true /*includeDependencies*/,
                process9Patch)
        variantData.mergeResourcesTask = mergeResourcesTask
    }

    protected MergeResources basicCreateMergeResourcesTask(
            @NonNull BaseVariantData variantData,
            @NonNull String taskNamePrefix,
            @NonNull String outputLocation,
            final boolean includeDependencies,
            final boolean process9Patch) {
        MergeResources mergeResourcesTask = project.tasks.create(
                "$taskNamePrefix${variantData.variantConfiguration.fullName.capitalize()}Resources",
                MergeResources)

        mergeResourcesTask.dependsOn variantData.prepareDependenciesTask, variantData.renderscriptCompileTask
        mergeResourcesTask.plugin = this
        mergeResourcesTask.variant = variantData
        mergeResourcesTask.incrementalFolder = project.file(
                "$project.buildDir/incremental/${taskNamePrefix}Resources/${variantData.variantConfiguration.dirName}")

        mergeResourcesTask.process9Patch = process9Patch

        mergeResourcesTask.conventionMapping.inputResourceSets = {
            variantData.variantConfiguration.getResourceSets(
                    variantData.renderscriptCompileTask.getResOutputDir(),
                    includeDependencies)
        }

        mergeResourcesTask.conventionMapping.outputDir = { project.file(outputLocation) }

        return mergeResourcesTask
    }

    protected void createMergeAssetsTask(@NonNull BaseVariantData variantData,
                                         @Nullable String outputLocation,
                                         final boolean includeDependencies) {
        if (outputLocation == null) {
            outputLocation = "$project.buildDir/assets/${variantData.variantConfiguration.dirName}"
        }

        def mergeAssetsTask = project.tasks.create(
                "merge${variantData.variantConfiguration.fullName.capitalize()}Assets",
                MergeAssets)
        variantData.mergeAssetsTask = mergeAssetsTask

        mergeAssetsTask.dependsOn variantData.prepareDependenciesTask
        mergeAssetsTask.plugin = this
        mergeAssetsTask.variant = variantData
        mergeAssetsTask.incrementalFolder =
                project.file("$project.buildDir/incremental/mergeAssets/${variantData.variantConfiguration.dirName}")

        mergeAssetsTask.conventionMapping.inputAssetSets = {
            variantData.variantConfiguration.getAssetSets(includeDependencies)
        }
        mergeAssetsTask.conventionMapping.outputDir = { project.file(outputLocation) }
    }

    protected void createBuildConfigTask(BaseVariantData variantData) {
        def generateBuildConfigTask = project.tasks.create(
                "generate${variantData.variantConfiguration.fullName.capitalize()}BuildConfig",
                GenerateBuildConfig)
        variantData.generateBuildConfigTask = generateBuildConfigTask

        VariantConfiguration variantConfiguration = variantData.variantConfiguration

        variantData.sourceGenTask.dependsOn generateBuildConfigTask
        if (variantConfiguration.type == TEST) {
            // in case of a test project, the manifest is generated so we need to depend
            // on its creation.
            generateBuildConfigTask.dependsOn variantData.processManifestTask
        } else {
            generateBuildConfigTask.dependsOn variantData.checkManifestTask
        }

        generateBuildConfigTask.plugin = this
        generateBuildConfigTask.variant = variantData

        generateBuildConfigTask.conventionMapping.buildConfigPackageName = {
            variantConfiguration.originalPackageName
        }

        generateBuildConfigTask.conventionMapping.appPackageName = {
            variantConfiguration.packageName
        }

        generateBuildConfigTask.conventionMapping.versionName = {
            variantConfiguration.versionName
        }

        generateBuildConfigTask.conventionMapping.versionCode = {
            variantConfiguration.versionCode
        }

        generateBuildConfigTask.conventionMapping.debuggable = {
            variantConfiguration.buildType.isDebuggable()
        }

        generateBuildConfigTask.conventionMapping.buildTypeName = {
            variantConfiguration.buildType.name
        }

        generateBuildConfigTask.conventionMapping.flavorName = {
            variantConfiguration.flavorName
        }

        generateBuildConfigTask.conventionMapping.flavorNamesWithDimensionNames = {
            variantConfiguration.flavorNamesWithDimensionNames
        }

        generateBuildConfigTask.conventionMapping.items = {
            variantConfiguration.buildConfigItems
        }

        generateBuildConfigTask.conventionMapping.sourceOutputDir = {
            project.file("$project.buildDir/source/buildConfig/${variantData.variantConfiguration.dirName}")
        }
    }

    protected void createProcessResTask(BaseVariantData variantData) {
        createProcessResTask(variantData, "$project.buildDir/symbols/${variantData.variantConfiguration.dirName}")
    }

    protected void createProcessResTask(BaseVariantData variantData, final String symbolLocation) {
        def processResources = project.tasks.create(
                "process${variantData.variantConfiguration.fullName.capitalize()}Resources",
                ProcessAndroidResources)
        variantData.processResourcesTask = processResources

        variantData.sourceGenTask.dependsOn processResources
        processResources.dependsOn variantData.processManifestTask, variantData.mergeResourcesTask, variantData.mergeAssetsTask

        processResources.plugin = this
        processResources.variant = variantData

        VariantConfiguration variantConfiguration = variantData.variantConfiguration

        processResources.conventionMapping.manifestFile = {
            variantData.processManifestTask.manifestOutputFile
        }

        processResources.conventionMapping.resDir = {
            variantData.mergeResourcesTask.outputDir
        }

        processResources.conventionMapping.assetsDir =  {
            variantData.mergeAssetsTask.outputDir
        }

        processResources.conventionMapping.libraries = {
            getTextSymbolDependencies(variantConfiguration.allLibraries)
        }
        processResources.conventionMapping.packageForR = {
            variantConfiguration.originalPackageName
        }

        // TODO: unify with generateBuilderConfig, compileAidl, and library packaging somehow?
        processResources.conventionMapping.sourceOutputDir = {
            project.file("$project.buildDir/source/r/${variantData.variantConfiguration.dirName}")
        }
        processResources.conventionMapping.textSymbolOutputDir = {
            project.file(symbolLocation)
        }
        processResources.conventionMapping.packageOutputFile = {
            project.file(
                    "$project.buildDir/libs/${project.archivesBaseName}-${variantData.variantConfiguration.baseName}.ap_")
        }
        if (variantConfiguration.buildType.runProguard) {
            processResources.conventionMapping.proguardOutputFile = {
                project.file("$project.buildDir/proguard/${variantData.variantConfiguration.dirName}/aapt_rules.txt")
            }
        }

        processResources.conventionMapping.type = { variantConfiguration.type }
        processResources.conventionMapping.debuggable = { variantConfiguration.buildType.debuggable }
        processResources.conventionMapping.aaptOptions = { extension.aaptOptions }
        processResources.conventionMapping.resourceConfigs = { variantConfiguration.mergedFlavor.resourceConfigurations }
    }

    protected void createProcessJavaResTask(BaseVariantData variantData) {
        VariantConfiguration variantConfiguration = variantData.variantConfiguration

        Copy processResources = project.tasks.create(
                "process${variantData.variantConfiguration.fullName.capitalize()}JavaRes",
                ProcessResources);
        variantData.processJavaResourcesTask = processResources

        // set the input
        processResources.from(((AndroidSourceSet) variantConfiguration.defaultSourceSet).resources)

        if (variantConfiguration.type != TEST) {
            processResources.from(
                    ((AndroidSourceSet) variantConfiguration.buildTypeSourceSet).resources)
        }
        if (variantConfiguration.hasFlavors()) {
            for (SourceProvider flavorSourceSet : variantConfiguration.flavorSourceProviders) {
                processResources.from(((AndroidSourceSet) flavorSourceSet).resources)
            }
        }

        processResources.conventionMapping.destinationDir = {
            project.file("$project.buildDir/javaResources/${variantData.variantConfiguration.dirName}")
        }
    }

    protected void createAidlTask(BaseVariantData variantData) {
        VariantConfiguration variantConfiguration = variantData.variantConfiguration

        def compileTask = project.tasks.create(
                "compile${variantData.variantConfiguration.fullName.capitalize()}Aidl",
                AidlCompile)
        variantData.aidlCompileTask = compileTask

        variantData.sourceGenTask.dependsOn compileTask
        variantData.aidlCompileTask.dependsOn variantData.prepareDependenciesTask

        compileTask.plugin = this
        compileTask.variant = variantData
        compileTask.incrementalFolder =
                project.file("$project.buildDir/incremental/aidl/${variantData.variantConfiguration.dirName}")

        compileTask.conventionMapping.sourceDirs = { variantConfiguration.aidlSourceList }
        compileTask.conventionMapping.importDirs = { variantConfiguration.aidlImports }

        compileTask.conventionMapping.sourceOutputDir = {
            project.file("$project.buildDir/source/aidl/${variantData.variantConfiguration.dirName}")
        }
    }

    protected void createCompileTask(BaseVariantData variantData,
                                     BaseVariantData testedVariantData) {
        def compileTask = project.tasks.create(
                "compile${variantData.variantConfiguration.fullName.capitalize()}Java",
                JavaCompile)
        variantData.javaCompileTask = compileTask
        compileTask.dependsOn variantData.sourceGenTask, variantData.variantDependency.compileConfiguration

        VariantConfiguration config = variantData.variantConfiguration

        List<Object> sourceList = Lists.newArrayList()
        sourceList.add(((AndroidSourceSet) config.defaultSourceSet).java)
        sourceList.add({ variantData.processResourcesTask.sourceOutputDir })
        sourceList.add({ variantData.generateBuildConfigTask.sourceOutputDir })
        sourceList.add({ variantData.aidlCompileTask.sourceOutputDir })
        if (!config.mergedFlavor.renderscriptNdkMode) {
            sourceList.add({ variantData.renderscriptCompileTask.sourceOutputDir })
        }

        if (config.getType() != TEST) {
            sourceList.add(((AndroidSourceSet) config.buildTypeSourceSet).java)
        }
        if (config.hasFlavors()) {
            for (SourceProvider flavorSourceProvider : config.flavorSourceProviders) {
                sourceList.add(((AndroidSourceSet) flavorSourceProvider).java)
            }
        }
        compileTask.source = sourceList.toArray()

        // if the tested variant is an app, add its classpath. For the libraries,
        // it's done automatically since the classpath includes the library output as a normal
        // dependency.
        if (testedVariantData instanceof ApplicationVariantData) {
            compileTask.conventionMapping.classpath =  {
                project.files(getAndroidBuilder(variantData).getCompileClasspath(config)) + testedVariantData.javaCompileTask.classpath + testedVariantData.javaCompileTask.outputs.files
            }
        } else {
            compileTask.conventionMapping.classpath =  {
                project.files(getAndroidBuilder(variantData).getCompileClasspath(config))
            }
        }

        // TODO - dependency information for the compile classpath is being lost.
        // Add a temporary approximation
        compileTask.dependsOn project.configurations.compile.buildDependencies

        compileTask.conventionMapping.destinationDir = {
            project.file("$project.buildDir/classes/${variantData.variantConfiguration.dirName}")
        }
        compileTask.conventionMapping.dependencyCacheDir = {
            project.file("$project.buildDir/dependency-cache/${variantData.variantConfiguration.dirName}")
        }

        // set source/target compatibility
        compileTask.conventionMapping.sourceCompatibility = {
            extension.compileOptions.sourceCompatibility.toString()
        }
        compileTask.conventionMapping.targetCompatibility = {
            extension.compileOptions.targetCompatibility.toString()
        }
        compileTask.options.encoding = extension.compileOptions.encoding

        // setup the boot classpath just before the task actually runs since this will
        // force the sdk to be parsed.
        compileTask.doFirst {
            compileTask.options.bootClasspath = getRuntimeJars()
        }
    }

    protected void createNdkTasks(@NonNull BaseVariantData variantData) {
        NdkCompile ndkCompile = project.tasks.create(
                "compile${variantData.variantConfiguration.fullName.capitalize()}Ndk",
                NdkCompile)

        ndkCompile.plugin = this
        ndkCompile.variant = variantData
        variantData.ndkCompileTask = ndkCompile

        VariantConfiguration variantConfig = variantData.variantConfiguration

        if (variantConfig.mergedFlavor.renderscriptNdkMode) {
            ndkCompile.ndkRenderScriptMode = true
            ndkCompile.dependsOn variantData.renderscriptCompileTask
        } else {
            ndkCompile.ndkRenderScriptMode = false
        }

        ndkCompile.conventionMapping.sourceFolders = {
            List<File> sourceList = variantConfig.jniSourceList
            if (variantConfig.mergedFlavor.renderscriptNdkMode) {
                sourceList.add(variantData.renderscriptCompileTask.sourceOutputDir)
            }

            return sourceList
        }

        ndkCompile.conventionMapping.generatedMakefile = {
            project.file("$project.buildDir/ndk/${variantData.variantConfiguration.dirName}/Android.mk")
        }

        ndkCompile.conventionMapping.ndkConfig = { variantConfig.ndkConfig }

        ndkCompile.conventionMapping.debuggable = {
            variantConfig.buildType.jniDebugBuild
        }

        ndkCompile.conventionMapping.objFolder = {
            project.file("$project.buildDir/ndk/${variantData.variantConfiguration.dirName}/obj")
        }
        ndkCompile.conventionMapping.soFolder = {
            project.file("$project.buildDir/ndk/${variantData.variantConfiguration.dirName}/lib")
        }
    }

    /**
     * Creates the tasks to build the test apk.
     *
     * @param variant the test variant
     * @param testedVariant the tested variant
     * @param configDependencies the list of config dependencies
     */
    protected void createTestApkTasks(@NonNull TestVariantData variantData,
                                      @NonNull BaseVariantData testedVariantData) {
        createAnchorTasks(variantData)

        // Add a task to process the manifest
        createProcessTestManifestTask(variantData, "manifests")

        // Add a task to compile renderscript files.
        createRenderscriptTask(variantData)

        // Add a task to merge the resource folders
        createMergeResourcesTask(variantData, true /*process9Patch*/)

        // Add a task to merge the assets folders
        createMergeAssetsTask(variantData, null /*default location*/, true /*includeDependencies*/)

        if (testedVariantData.variantConfiguration.type == VariantConfiguration.Type.LIBRARY) {
            // in this case the tested library must be fully built before test can be built!
            if (testedVariantData.assembleTask != null) {
                variantData.processManifestTask.dependsOn testedVariantData.assembleTask
                variantData.mergeResourcesTask.dependsOn testedVariantData.assembleTask
            }
        }

        // Add a task to create the BuildConfig class
        createBuildConfigTask(variantData)

        // Add a task to generate resource source files
        createProcessResTask(variantData)

        // process java resources
        createProcessJavaResTask(variantData)

        createAidlTask(variantData)

        // Add a task to compile the test application
        createCompileTask(variantData, testedVariantData)

        // Add NDK tasks
        createNdkTasks(variantData)

        addPackageTasks(variantData, null)

        if (assembleTest != null) {
            assembleTest.dependsOn variantData.assembleTask
        }
    }

    // TODO - should compile src/lint/java from src/lint/java and jar it into build/lint/lint.jar
    protected void createLintCompileTask() {
        lintCompile = project.tasks.create("compileLint", Task)
        File outputDir = new File("$project.buildDir/lint")

        lintCompile.doFirst{
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
    private static boolean isLintVariant(@NonNull BaseVariantData baseVariantData) {
        // Only create lint targets for variants like debug and release, not debugTest
        VariantConfiguration config = baseVariantData.variantConfiguration
        return config.getType() != TEST;
    }

    // Add tasks for running lint on individual variants. We've already added a
    // lint task earlier which runs on all variants.
    protected void createLintTasks() {
        Lint lint = project.tasks.create("lint", Lint)
        lint.description = "Runs lint on all variants."
        lint.group = JavaBasePlugin.VERIFICATION_GROUP
        lint.setPlugin(this)
        project.tasks.check.dependsOn lint
        lintAll = lint

        int count = variantDataList.size()
        for (int i = 0 ; i < count ; i++) {
            final BaseVariantData baseVariantData = variantDataList.get(i)
            if (!isLintVariant(baseVariantData)) {
                continue;
            }

            // wire the main lint task dependency.
            lint.dependsOn baseVariantData.javaCompileTask, lintCompile

            String variantName = baseVariantData.variantConfiguration.fullName
            def capitalizedVariantName = variantName.capitalize()
            Lint variantLintCheck = project.tasks.create("lint" + capitalizedVariantName, Lint)
            variantLintCheck.dependsOn baseVariantData.javaCompileTask, lintCompile
            // Note that we don't do "lint.dependsOn lintCheck"; the "lint" target will
            // on its own run through all variants (and compare results), it doesn't delegate
            // to the individual tasks (since it needs to coordinate data collection and
            // reporting)
            variantLintCheck.setPlugin(this)
            variantLintCheck.setVariantName(variantName)
            variantLintCheck.description = "Runs lint on the " + capitalizedVariantName + " build"
            variantLintCheck.group = JavaBasePlugin.VERIFICATION_GROUP
        }
    }

    private void createLintVitalTask(@NonNull ApkVariantData variantData) {
        assert extension.lintOptions.checkReleaseBuilds
        if (!variantData.variantConfiguration.buildType.debuggable) {
            String variantName = variantData.variantConfiguration.fullName
            def capitalizedVariantName = variantName.capitalize()
            def taskName = "lintVital" + capitalizedVariantName
            Lint lintReleaseCheck = project.tasks.create(taskName, Lint)
            // TODO: Make this task depend on lintCompile too (resolve initialization order first)
            lintReleaseCheck.dependsOn variantData.javaCompileTask
            lintReleaseCheck.setPlugin(this)
            lintReleaseCheck.setVariantName(variantName)
            lintReleaseCheck.setFatalOnly(true)
            lintReleaseCheck.description = "Runs lint on just the fatal issues in the " +
                    capitalizedVariantName + " build"
            variantData.assembleTask.dependsOn lintReleaseCheck
            lintVital = lintReleaseCheck
        }
    }

    protected void createCheckTasks(boolean hasFlavors, boolean isLibraryTest) {
        List<AndroidReportTask> reportTasks = Lists.newArrayListWithExpectedSize(2)

        List<DeviceProvider> providers = extension.deviceProviders
        List<TestServer> servers = extension.testServers

        Task mainConnectedTask = connectedCheck
        String connectedRootName = "${CONNECTED}${INSTRUMENT_TEST.capitalize()}"
        // if more than one flavor, create a report aggregator task and make this the parent
        // task for all new connected tasks.
        if (hasFlavors) {
            mainConnectedTask = project.tasks.create(connectedRootName, AndroidReportTask)
            mainConnectedTask.group = JavaBasePlugin.VERIFICATION_GROUP
            mainConnectedTask.description = "Installs and runs instrumentation tests for all flavors on connected devices."
            mainConnectedTask.reportType = ReportType.MULTI_FLAVOR
            connectedCheck.dependsOn mainConnectedTask

            mainConnectedTask.conventionMapping.resultsDir = {
                String rootLocation = extension.testOptions.resultsDir != null ?
                    extension.testOptions.resultsDir : "$project.buildDir/$FD_INSTRUMENT_RESULTS"

                project.file("$rootLocation/connected/$FD_FLAVORS_ALL")
            }
            mainConnectedTask.conventionMapping.reportsDir = {
                String rootLocation = extension.testOptions.reportDir != null ?
                    extension.testOptions.reportDir :
                    "$project.buildDir/$FD_REPORTS/$FD_INSTRUMENT_TESTS"

                project.file("$rootLocation/connected/$FD_FLAVORS_ALL")
            }

            reportTasks.add(mainConnectedTask)
        }

        Task mainProviderTask = deviceCheck
        // if more than one provider tasks, either because of several flavors, or because of
        // more than one providers, then create an aggregate report tasks for all of them.
        if (providers.size() > 1 || hasFlavors) {
            mainProviderTask = project.tasks.create("${DEVICE}${INSTRUMENT_TEST.capitalize()}",
                    AndroidReportTask)
            mainProviderTask.group = JavaBasePlugin.VERIFICATION_GROUP
            mainProviderTask.description = "Installs and runs instrumentation tests using all Device Providers."
            mainProviderTask.reportType = ReportType.MULTI_FLAVOR
            deviceCheck.dependsOn mainProviderTask

            mainProviderTask.conventionMapping.resultsDir = {
                String rootLocation = extension.testOptions.resultsDir != null ?
                    extension.testOptions.resultsDir : "$project.buildDir/$FD_INSTRUMENT_RESULTS"

                project.file("$rootLocation/devices/$FD_FLAVORS_ALL")
            }
            mainProviderTask.conventionMapping.reportsDir = {
                String rootLocation = extension.testOptions.reportDir != null ?
                    extension.testOptions.reportDir :
                    "$project.buildDir/$FD_REPORTS/$FD_INSTRUMENT_TESTS"

                project.file("$rootLocation/devices/$FD_FLAVORS_ALL")
            }

            reportTasks.add(mainProviderTask)
        }

        // now look for the testedvariant and create the check tasks for them.
        // don't use an auto loop as we can't reuse baseVariantData or the closure lower
        // gets broken.
        int count = variantDataList.size();
        for (int i = 0 ; i < count ; i++) {
            final BaseVariantData baseVariantData = variantDataList.get(i);
            if (baseVariantData instanceof TestedVariantData) {
                final TestVariantData testVariantData = ((TestedVariantData) baseVariantData).testVariantData
                if (testVariantData == null) {
                    continue
                }

                // create the check tasks for this test

                // first the connected one.
                def connectedTask = createDeviceProviderInstrumentTestTask(
                        hasFlavors ?
                            "${connectedRootName}${baseVariantData.variantConfiguration.fullName.capitalize()}" : connectedRootName,
                        "Installs and runs the tests for Build '${baseVariantData.variantConfiguration.fullName}' on connected devices.",
                        isLibraryTest ?
                            DeviceProviderInstrumentTestLibraryTask :
                            DeviceProviderInstrumentTestTask,
                        testVariantData,
                        baseVariantData,
                        new ConnectedDeviceProvider(getSdkParser()),
                        CONNECTED
                )

                mainConnectedTask.dependsOn connectedTask
                testVariantData.connectedTestTask = connectedTask

                // now the providers.
                for (DeviceProvider deviceProvider : providers) {
                    DefaultTask providerTask = createDeviceProviderInstrumentTestTask(
                            hasFlavors ?
                                "${deviceProvider.name}${INSTRUMENT_TEST.capitalize()}${baseVariantData.variantConfiguration.fullName.capitalize()}" :
                                "${deviceProvider.name}${INSTRUMENT_TEST.capitalize()}",
                            "Installs and runs the tests for Build '${baseVariantData.variantConfiguration.fullName}' using Provider '${deviceProvider.name.capitalize()}'.",
                            isLibraryTest ?
                                DeviceProviderInstrumentTestLibraryTask :
                                DeviceProviderInstrumentTestTask,
                            testVariantData,
                            baseVariantData,
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
                    DefaultTask serverTask = project.tasks.create(
                            hasFlavors ?
                                "${testServer.name}${"upload".capitalize()}${baseVariantData.variantConfiguration.fullName}" :
                                "${testServer.name}${"upload".capitalize()}",
                            TestServerTask)
                    serverTask.description = "Uploads APKs for Build '${baseVariantData.variantConfiguration.fullName}' to Test Server '${testServer.name.capitalize()}'."
                    serverTask.group = JavaBasePlugin.VERIFICATION_GROUP
                    serverTask.dependsOn testVariantData.assembleTask, baseVariantData.assembleTask

                    serverTask.testServer = testServer

                    serverTask.conventionMapping.testApk = { testVariantData.outputFile }
                    if (!(baseVariantData instanceof LibraryVariantData)) {
                        serverTask.conventionMapping.testedApk = { baseVariantData.outputFile }
                    }

                    serverTask.conventionMapping.variantName = { baseVariantData.variantConfiguration.fullName }

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
            project.gradle.taskGraph.whenReady { taskGraph ->
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
            @NonNull TestVariantData variantData,
            @NonNull BaseVariantData testedVariantData,
            @NonNull DeviceProvider deviceProvider,
            @NonNull String subFolder) {

        def testTask = project.tasks.create(taskName, taskClass)
        testTask.description = description
        testTask.group = JavaBasePlugin.VERIFICATION_GROUP
        testTask.dependsOn testedVariantData.assembleTask, variantData.assembleTask

        testTask.plugin = this
        testTask.variant = variantData
        testTask.flavorName = variantData.variantConfiguration.flavorName.capitalize()
        testTask.deviceProvider = deviceProvider

        testTask.conventionMapping.testApp = { variantData.outputFile }
        if (testedVariantData.variantConfiguration.type != VariantConfiguration.Type.LIBRARY) {
            testTask.conventionMapping.testedApp = { testedVariantData.outputFile }
        }

        testTask.conventionMapping.resultsDir = {
            String rootLocation = extension.testOptions.resultsDir != null ?
                extension.testOptions.resultsDir :
                "$project.buildDir/$FD_INSTRUMENT_RESULTS"

            String flavorFolder = variantData.variantConfiguration.flavorName
            if (!flavorFolder.isEmpty()) {
                flavorFolder = "$FD_FLAVORS/" + flavorFolder
            }

            project.file("$rootLocation/$subFolder/$flavorFolder")
        }
        testTask.conventionMapping.reportsDir = {
            String rootLocation = extension.testOptions.reportDir != null ?
                extension.testOptions.reportDir :
                "$project.buildDir/$FD_REPORTS/$FD_INSTRUMENT_TESTS"

            String flavorFolder = variantData.variantConfiguration.flavorName
            if (!flavorFolder.isEmpty()) {
                flavorFolder = "$FD_FLAVORS/" + flavorFolder
            }

            project.file("$rootLocation/$subFolder/$flavorFolder")
        }

        return testTask
    }

    /**
     * Creates the packaging tasks for the given Variant.
     * @param variantData the variant data.
     * @param assembleTask an optional assembleTask to be used. If null a new one is created. The
     *                assembleTask is always set in the Variant.
     */
    protected void addPackageTasks(@NonNull ApkVariantData variantData,
                                   @Nullable Task assembleTask) {
        VariantConfiguration variantConfig = variantData.variantConfiguration

        boolean runProguard = variantConfig.buildType.runProguard &&
                (variantConfig.type != TEST ||
                        (variantConfig.type == TEST &&
                                variantConfig.testedConfig.type != VariantConfiguration.Type.LIBRARY))

        // common dex task configuration
        String dexTaskName = "dex${variantData.variantConfiguration.fullName.capitalize()}"
        Dex dexTask = project.tasks.create(dexTaskName, Dex)
        variantData.dexTask = dexTask

        dexTask.plugin = this
        dexTask.variant = variantData

        dexTask.conventionMapping.outputFile = {
            project.file(
                    "${project.buildDir}/libs/${project.archivesBaseName}-${variantData.variantConfiguration.baseName}.dex")
        }
        dexTask.dexOptions = extension.dexOptions

        if (runProguard) {

            // first proguard task.
            BaseVariantData testedVariantData = variantData instanceof TestVariantData ? variantData.testedVariantData : null as BaseVariantData
            File outFile = createProguardTasks(variantData, testedVariantData)

            // then dexing task
            dexTask.dependsOn variantData.proguardTask
            dexTask.conventionMapping.inputFiles = { project.files(outFile) }
            dexTask.conventionMapping.preDexedLibraries = { Collections.emptyList() }

        } else {

            // if required, pre-dexing task.
            PreDex preDexTask = null;
            boolean runPreDex = extension.dexOptions.preDexLibraries
            if (runPreDex) {
                def preDexTaskName = "preDex${variantData.variantConfiguration.fullName.capitalize()}"
                preDexTask = project.tasks.create(preDexTaskName, PreDex)

                preDexTask.dependsOn variantData.javaCompileTask, variantData.variantDependency.packageConfiguration
                preDexTask.plugin = this
                preDexTask.dexOptions = extension.dexOptions

                preDexTask.conventionMapping.inputFiles = {
                    project.files(getAndroidBuilder(variantData).getPackagedJars(variantConfig))
                }
                preDexTask.conventionMapping.outputFolder = {
                    project.file(
                            "${project.buildDir}/pre-dexed/${variantData.variantConfiguration.dirName}")
                }
            }

            // then dexing task
            dexTask.dependsOn variantData.javaCompileTask
            if (runPreDex) {
                dexTask.dependsOn preDexTask
            }

            dexTask.conventionMapping.inputFiles = { variantData.javaCompileTask.outputs.files }
            if (runPreDex) {
                dexTask.conventionMapping.preDexedLibraries = {
                    project.fileTree(preDexTask.outputFolder).files
                }
            } else {
                dexTask.conventionMapping.preDexedLibraries = {
                    project.files(getAndroidBuilder(variantData).getPackagedJars(variantConfig))
                }
            }
        }

        // Add a task to generate application package
        PackageApplication packageApp = project.tasks.create(
                "package${variantData.variantConfiguration.fullName.capitalize()}",
                PackageApplication)
        variantData.packageApplicationTask = packageApp
        packageApp.dependsOn variantData.processResourcesTask, dexTask, variantData.processJavaResourcesTask, variantData.ndkCompileTask

        packageApp.plugin = this
        packageApp.variant = variantData

        packageApp.conventionMapping.resourceFile = {
            variantData.processResourcesTask.packageOutputFile
        }
        packageApp.conventionMapping.dexFile = { dexTask.outputFile }
        packageApp.conventionMapping.packagedJars = { getAndroidBuilder(variantData).getPackagedJars(variantConfig) }
        packageApp.conventionMapping.javaResourceDir = {
            getOptionalDir(variantData.processJavaResourcesTask.destinationDir)
        }
        packageApp.conventionMapping.jniFolders = {
            // for now only the project's compilation output.
            Set<File> set = Sets.newHashSet()
            set.addAll(variantData.ndkCompileTask.soFolder)
            set.addAll(variantData.renderscriptCompileTask.libOutputDir)
            set.addAll(variantConfig.libraryJniFolders)
            set.addAll(variantConfig.jniLibsList)

            if (variantConfig.mergedFlavor.renderscriptSupportMode) {
                File rsLibs = getAndroidBuilder(variantData).getSupportNativeLibFolder()
                if (rsLibs.isDirectory()) {
                    set.add(rsLibs);
                }
            }

            return set
        }
        packageApp.conventionMapping.abiFilters = { variantConfig.supportedAbis }
        packageApp.conventionMapping.jniDebugBuild = { variantConfig.buildType.jniDebugBuild }

        SigningConfigDsl sc = (SigningConfigDsl) variantConfig.signingConfig
        packageApp.conventionMapping.signingConfig = { sc }
        if (sc != null) {
            ValidateSigningTask validateSigningTask = validateSigningTaskMap.get(sc)
            if (validateSigningTask == null) {
                validateSigningTask = project.tasks.create("validate${sc.name.capitalize()}Signing",
                    ValidateSigningTask)
                validateSigningTask.plugin = this
                validateSigningTask.signingConfig = sc

                validateSigningTaskMap.put(sc, validateSigningTask)
            }

            packageApp.dependsOn validateSigningTask
        }

        def signedApk = variantData.isSigned()
        def apkName = signedApk ?
            "${project.archivesBaseName}-${variantData.variantConfiguration.baseName}-unaligned.apk" :
            "${project.archivesBaseName}-${variantData.variantConfiguration.baseName}-unsigned.apk"

        packageApp.conventionMapping.packagingOptions = { extension.packagingOptions }

        packageApp.conventionMapping.outputFile = {
            project.file("$project.buildDir/apk/${apkName}")
        }

        Task appTask = packageApp
        OutputFileTask outputFileTask = packageApp

        if (signedApk) {
            if (variantData.zipAlign) {
                // Add a task to zip align application package
                def zipAlignTask = project.tasks.create(
                        "zipalign${variantData.variantConfiguration.fullName.capitalize()}",
                        ZipAlign)
                variantData.zipAlignTask = zipAlignTask

                zipAlignTask.dependsOn packageApp
                zipAlignTask.conventionMapping.inputFile = { packageApp.outputFile }
                zipAlignTask.conventionMapping.outputFile = {
                    project.file(
                            "$project.buildDir/apk/${project.archivesBaseName}-${variantData.variantConfiguration.baseName}.apk")
                }
                zipAlignTask.conventionMapping.zipAlignExe = { getSdkParser().zipAlign }

                appTask = zipAlignTask
                outputFileTask = zipAlignTask
                variantData.outputFile = project.file(
                        "$project.buildDir/apk/${project.archivesBaseName}-${variantData.variantConfiguration.baseName}.apk")
            }

            // Add a task to install the application package
            def installTask = project.tasks.create(
                    "install${variantData.variantConfiguration.fullName.capitalize()}",
                    InstallTask)
            installTask.description = "Installs the " + variantData.description
            installTask.group = INSTALL_GROUP
            installTask.dependsOn appTask
            installTask.conventionMapping.packageFile = { outputFileTask.outputFile }
            installTask.conventionMapping.adbExe = { getSdkParser().adb }

            variantData.installTask = installTask
        }

        // Add an assemble task
        if (assembleTask == null) {
            assembleTask = project.tasks.create("assemble${variantData.variantConfiguration.fullName.capitalize()}")
            assembleTask.description = "Assembles the " + variantData.description
            assembleTask.group = org.gradle.api.plugins.BasePlugin.BUILD_GROUP
        }
        assembleTask.dependsOn appTask
        variantData.assembleTask = assembleTask
        if (extension.lintOptions.checkReleaseBuilds) {
            createLintVitalTask(variantData)
        }

        variantData.outputFile = { outputFileTask.outputFile }

        // add an uninstall task
        def uninstallTask = project.tasks.create(
                "uninstall${variantData.variantConfiguration.fullName.capitalize()}",
                UninstallTask)
        uninstallTask.description = "Uninstalls the " + variantData.description
        uninstallTask.group = INSTALL_GROUP
        uninstallTask.variant = variantData
        uninstallTask.conventionMapping.adbExe = { getSdkParser().adb }

        variantData.uninstallTask = uninstallTask
        uninstallAll.dependsOn uninstallTask
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
        zipAlignTask.conventionMapping.zipAlignExe = { getSdkParser().zipAlign }

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
    protected File createProguardTasks(@NonNull BaseVariantData variantData,
                                       @Nullable BaseVariantData testedVariantData) {
        VariantConfiguration variantConfig = variantData.variantConfiguration

        def proguardTask = project.tasks.create(
                "proguard${variantData.variantConfiguration.fullName.capitalize()}",
                ProGuardTask)
        proguardTask.dependsOn variantData.javaCompileTask, variantData.variantDependency.packageConfiguration

        if (testedVariantData != null) {
            proguardTask.dependsOn testedVariantData.proguardTask
        }

        variantData.proguardTask = proguardTask

        // --- Output File ---

        File outFile;
        if (variantData instanceof LibraryVariantData) {
            outFile = project.file(
                    "${project.buildDir}/$DIR_BUNDLES/${variantData.variantConfiguration.dirName}/classes.jar")
        } else {
            outFile = project.file(
                    "${project.buildDir}/classes-proguard/${variantData.variantConfiguration.dirName}/classes.jar")
        }

        // --- Proguard Config ---

        if (testedVariantData != null) {
            // don't remove any code in tested app
            proguardTask.dontshrink()
            proguardTask.keepnames("class * extends junit.framework.TestCase")
            proguardTask.keepclassmembers("class * extends junit.framework.TestCase {\n" +
                    "    void test*(...);\n" +
                    "}")

            // input the mapping from the tested app so that we can deal with obfuscated code
            proguardTask.applymapping("${project.buildDir}/proguard/${testedVariantData.variantConfiguration.dirName}/mapping.txt")

            // for tested app, we only care about their aapt config since the base
            // configs are the same files anyway.
            proguardTask.configuration(testedVariantData.processResourcesTask.proguardOutputFile)
        }

        // all the config files coming from build type, product flavors.
        List<Object> proguardFiles = variantConfig.getProguardFiles(true /*includeLibs*/)
        for (Object proguardFile : proguardFiles) {
            proguardTask.configuration(proguardFile)
        }

        // also the config file output by aapt
        proguardTask.configuration(variantData.processResourcesTask.proguardOutputFile)

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
            exclude += (', !' + packageName + "/Manifest.class")
            exclude += (', !' + packageName + "/Manifest\$*.class")
            exclude += (', !' + packageName + "/BuildConfig.class")
            proguardTask.injars(variantData.javaCompileTask.destinationDir, filter: exclude)

            // include R files and such for compilation
            String include = exclude.replace('!', '')
            proguardTask.libraryjars(variantData.javaCompileTask.destinationDir, filter: include)

            proguardTask.doFirst {
                // we need to query the list of packaged Jar just before the task
                // runs to make sure all the created jars are present.
                // (This is mostly for aar that needs expanding during the build, and their
                // local jar files which aren't even known during evaluation/task creation).
                List<File> packagedJars = getAndroidBuilder(variantData).getPackagedJars(variantConfig)

                // injar: the local dependencies, filter out local jars from packagedJars
                Object[] jars = LibraryPlugin.getLocalJarFileList(variantData.variantDependency)
                for (Object inJar : jars) {
                    if (packagedJars.contains(inJar)) {
                        packagedJars.remove(inJar);
                    }
                    proguardTask.injars((File) inJar, filter: '!META-INF/MANIFEST.MF')
                }

                // libjar: the library dependencies
                for (File libJar : packagedJars) {
                    proguardTask.libraryjars(libJar, filter: '!META-INF/MANIFEST.MF')
                }

                // Now reset the input count for the only output present in the proguard task.
                // This is to go around the fact that proguard wants to know about the input
                // before the output is given.
                resetProguardInJarCounts(proguardTask)
            }

            // ensure local jars keep their package names
            proguardTask.keeppackagenames()
        } else {
            // injar: the compilation output
            proguardTask.injars(variantData.javaCompileTask.destinationDir)

            proguardTask.doFirst {
                // we need to query the list of packaged Jar just before the task
                // runs to make sure all the created jars are present.
                // (This is mostly for aar that needs expanding during the build, and their
                // local jar files which aren't even known during evaluation/task creation).
                List<File> packagedJars = getAndroidBuilder(variantData).getPackagedJars(variantConfig)

                // injar: the dependencies
                for (File inJar : packagedJars) {
                    proguardTask.injars(inJar, filter: '!META-INF/MANIFEST.MF')
                }

                // now add the provided jars
                for (File libJar : variantData.variantConfiguration.providedJars) {
                    proguardTask.libraryjars(libJar)
                }

                // Now reset the input count for the only output present in the proguard task.
                // This is to go around the fact that proguard wants to know about the input
                // before the output is given.
                resetProguardInJarCounts(proguardTask)
            }
        }

        // libraryJars: the runtime jars
        for (String runtimeJar : getRuntimeJarList()) {
            proguardTask.libraryjars(runtimeJar)
        }

        if (testedVariantData != null) {
            // input the tested app as library
            proguardTask.libraryjars(testedVariantData.javaCompileTask.destinationDir)
            // including its dependencies
            List<File> testedPackagedJars = getAndroidBuilder(testedVariantData).getPackagedJars(testedVariantData.variantConfiguration)
            for (File inJar : testedPackagedJars) {
                proguardTask.libraryjars(inJar, filter: '!META-INF/MANIFEST.MF')
            }
        }

        // --- Out files ---

        proguardTask.outjars(outFile)

        proguardTask.dump("${project.buildDir}/proguard/${variantData.variantConfiguration.dirName}/dump.txt")
        proguardTask.printseeds(
                "${project.buildDir}/proguard/${variantData.variantConfiguration.dirName}/seeds.txt")
        proguardTask.printusage(
                "${project.buildDir}/proguard/${variantData.variantConfiguration.dirName}/usage.txt")
        proguardTask.printmapping(
                "${project.buildDir}/proguard/${variantData.variantConfiguration.dirName}/mapping.txt")

        return outFile
    }

    protected static void resetProguardInJarCounts(@NonNull ProGuardTask proguardTask) {
        // we're basically doing:
        //     proguardTask.inJarCounts.clear()
        //     proguardTask.inJarCounts.add(Integer.valueOf(proguardTask.inJarFiles.size()));

        try {
            Field inJarCountsField = ProGuardTask.getDeclaredField("inJarCounts")
            inJarCountsField.setAccessible(true)
            Field inJarFilesField = ProGuardTask.getDeclaredField("inJarFiles")
            inJarFilesField.setAccessible(true)

            Method clearMethod = ArrayList.class.getMethod("clear")
            Method sizeMethod = ArrayList.class.getMethod("size")
            Method addMethod = ArrayList.class.getMethod("add", Object.class)

            Object inJarCountsInstance = inJarCountsField.get(proguardTask)
            Object inJarFilesInstance = inJarFilesField.get(proguardTask)

            clearMethod.invoke(inJarCountsInstance, null)
            Object sizeValue = sizeMethod.invoke(inJarFilesInstance, null)

            addMethod.invoke(inJarCountsInstance, sizeValue)
        } catch (Throwable t) {
            throw new RuntimeException(
                    "Failed to change some proguard internals. There is a mismatch in the version of used.",
                    t)
        }
    }

    private void createReportTasks() {
        def dependencyReportTask = project.tasks.create("androidDependencies", DependencyReportTask)
        dependencyReportTask.setDescription("Displays the Android dependencies of the project")
        dependencyReportTask.setVariants(variantDataList)
        dependencyReportTask.setGroup("Android")

        def signingReportTask = project.tasks.create("signingReport", SigningReportTask)
        signingReportTask.setDescription("Displays the signing info for each variant")
        signingReportTask.setVariants(variantDataList)
        signingReportTask.setGroup("Android")
    }

    protected void createAnchorTasks(@NonNull BaseVariantData variantData) {
        variantData.preBuildTask = project.tasks.create(
                "pre${variantData.variantConfiguration.fullName.capitalize()}Build")
        variantData.preBuildTask.dependsOn mainPreBuild

        def prepareDependenciesTask = project.tasks.create(
                "prepare${variantData.variantConfiguration.fullName.capitalize()}Dependencies",
                PrepareDependenciesTask)

        variantData.prepareDependenciesTask = prepareDependenciesTask
        prepareDependenciesTask.dependsOn variantData.preBuildTask

        prepareDependenciesTask.plugin = this
        prepareDependenciesTask.variant = variantData

        // for all libraries required by the configurations of this variant, make this task
        // depend on all the tasks preparing these libraries.
        VariantDependencies configurationDependencies = variantData.variantDependency
        prepareDependenciesTask.addChecker(configurationDependencies.checker)

        for (LibraryDependencyImpl lib : configurationDependencies.libraries) {
            addDependencyToPrepareTask(variantData, prepareDependenciesTask, lib)
        }

        // also create sourceGenTask
        variantData.sourceGenTask = project.tasks.create(
                "generate${variantData.variantConfiguration.fullName.capitalize()}Sources")
    }

    protected void createCheckManifestTask(@NonNull BaseVariantData variantData) {
        String name = variantData.variantConfiguration.fullName
        variantData.checkManifestTask = project.tasks.create(
                "check${name.capitalize()}Manifest",
                CheckManifest)
        variantData.checkManifestTask.dependsOn variantData.preBuildTask

        variantData.prepareDependenciesTask.dependsOn variantData.checkManifestTask

        variantData.checkManifestTask.variantName = name
        variantData.checkManifestTask.conventionMapping.manifest = {
            variantData.variantConfiguration.getDefaultSourceSet().manifestFile
        }
    }

    private final Map<String, ArtifactMetaData> extraArtifactMap = Maps.newHashMap()
    private final ListMultimap<String, AndroidArtifact> extraAndroidArtifacts = ArrayListMultimap.create()
    private final ListMultimap<String, JavaArtifact> extraJavaArtifacts = ArrayListMultimap.create()
    private final ListMultimap<String, SourceProviderContainer> extraVariantSourceProviders = ArrayListMultimap.create()
    private final ListMultimap<String, SourceProviderContainer> extraBuildTypeSourceProviders = ArrayListMultimap.create()
    private final ListMultimap<String, SourceProviderContainer> extraProductFlavorSourceProviders = ArrayListMultimap.create()
    private final ListMultimap<String, SourceProviderContainer> extraMultiFlavorSourceProviders = ArrayListMultimap.create()


    public Collection<ArtifactMetaData> getExtraArtifacts() {
        return extraArtifactMap.values()
    }

    public Collection<AndroidArtifact> getExtraAndroidArtifacts(@NonNull String variantName) {
        return extraAndroidArtifacts.get(variantName)
    }

    public Collection<JavaArtifact> getExtraJavaArtifacts(@NonNull String variantName) {
        return extraJavaArtifacts.get(variantName)
    }

    public Collection<SourceProviderContainer> getExtraVariantSourceProviders(@NonNull String variantName) {
        return extraVariantSourceProviders.get(variantName)
    }

    public Collection<SourceProviderContainer> getExtraFlavorSourceProviders(@NonNull String flavorName) {
        return extraProductFlavorSourceProviders.get(flavorName)
    }

    public Collection<SourceProviderContainer> getExtraBuildTypeSourceProviders(@NonNull String buildTypeName) {
        return extraBuildTypeSourceProviders.get(buildTypeName)
    }

    public void registerArtifactType(@NonNull String name,
                                     boolean isTest,
                                     int artifactType) {

        if (extraArtifactMap.get(name) != null) {
            throw new IllegalArgumentException("Artifact with name $name already registered.")
        }

        extraArtifactMap.put(name, new ArtifactMetaDataImpl(name, isTest, artifactType))
    }

    public void registerBuildTypeSourceProvider(@NonNull String name,
                                                @NonNull BuildType buildType,
                                                @NonNull SourceProvider sourceProvider) {
        if (extraArtifactMap.get(name) == null) {
            throw new IllegalArgumentException(
                    "Artifact with name $name is not yet registered. Use registerArtifactType()")
        }

        extraBuildTypeSourceProviders.put(buildType.name,
                new DefaultSourceProviderContainer(name, sourceProvider))

    }

    public void registerProductFlavorSourceProvider(@NonNull String name,
                                                    @NonNull ProductFlavor productFlavor,
                                                    @NonNull SourceProvider sourceProvider) {
        if (extraArtifactMap.get(name) == null) {
            throw new IllegalArgumentException(
                    "Artifact with name $name is not yet registered. Use registerArtifactType()")
        }

        extraProductFlavorSourceProviders.put(productFlavor.name,
                new DefaultSourceProviderContainer(name, sourceProvider))

    }

    public void registerMultiFlavorSourceProvider(@NonNull String name,
                                                  @NonNull String flavorName,
                                                  @NonNull SourceProvider sourceProvider) {
        if (extraArtifactMap.get(name) == null) {
            throw new IllegalArgumentException(
                    "Artifact with name $name is not yet registered. Use registerArtifactType()")
        }

        extraMultiFlavorSourceProviders.put(flavorName,
                new DefaultSourceProviderContainer(name, sourceProvider))
    }

    public void registerJavaArtifact(
            @NonNull String name,
            @NonNull BaseVariant variant,
            @NonNull String assembleTaskName,
            @NonNull String javaCompileTaskName,
            @NonNull Configuration configuration,
            @NonNull File classesFolder,
            @Nullable SourceProvider sourceProvider) {
        ArtifactMetaData artifactMetaData = extraArtifactMap.get(name)
        if (artifactMetaData == null) {
            throw new IllegalArgumentException(
                    "Artifact with name $name is not yet registered. Use registerArtifactType()")
        }
        if (artifactMetaData.type != ArtifactMetaData.TYPE_JAVA) {
            throw new IllegalArgumentException(
                    "Artifact with name $name is not of type JAVA")
        }

        JavaArtifact artifact = new JavaArtifactImpl(
                name, assembleTaskName, javaCompileTaskName, classesFolder,
                new ConfigurationDependencies(configuration),
                sourceProvider, null)
        extraJavaArtifacts.put(variant.name, artifact)
    }

    //----------------------------------------------------------------------------------------------
    //------------------------------ START DEPENDENCY STUFF ----------------------------------------
    //----------------------------------------------------------------------------------------------

    def addDependencyToPrepareTask(@NonNull BaseVariantData variantData,
                                   @NonNull PrepareDependenciesTask prepareDependenciesTask,
                                   @NonNull LibraryDependencyImpl lib) {
        def prepareLibTask = prepareTaskMap.get(lib)
        if (prepareLibTask != null) {
            prepareDependenciesTask.dependsOn prepareLibTask
            prepareLibTask.dependsOn variantData.preBuildTask
        }

        for (LibraryDependencyImpl childLib : lib.dependencies) {
            addDependencyToPrepareTask(variantData, prepareDependenciesTask, childLib)
        }
    }

    def resolveDependencies(VariantDependencies variantDeps) {
        Map<ModuleVersionIdentifier, List<LibraryDependencyImpl>> modules = [:]
        Map<ModuleVersionIdentifier, List<ResolvedArtifact>> artifacts = [:]
        Multimap<LibraryDependency, VariantDependencies> reverseMap = ArrayListMultimap.create()

        resolveDependencyForConfig(variantDeps, modules, artifacts, reverseMap)

        modules.values().each { List list ->

            if (!list.isEmpty()) {
                // get the first item only
                LibraryDependencyImpl androidDependency = (LibraryDependencyImpl) list.get(0)

                String bundleName = GUtil.toCamelCase(androidDependency.name.replaceAll("\\:", " "))

                def prepareLibraryTask = prepareTaskMap.get(androidDependency)
                if (prepareLibraryTask == null) {
                    prepareLibraryTask = project.tasks.create("prepare${bundleName}Library",
                            PrepareLibraryTask)
                    prepareLibraryTask.description = "Prepare ${androidDependency.name}"
                    prepareLibraryTask.bundle = androidDependency.bundle
                    prepareLibraryTask.explodedDir = androidDependency.bundleFolder

                    prepareTaskMap.put(androidDependency, prepareLibraryTask)
                }

                // Use the reverse map to find all the configurations that included this android
                // library so that we can make sure they are built.
                List<VariantDependencies> configDepList = reverseMap.get(androidDependency)
                if (configDepList != null && !configDepList.isEmpty()) {
                    for (VariantDependencies configDependencies: configDepList) {
                        prepareLibraryTask.dependsOn configDependencies.compileConfiguration.buildDependencies
                    }
                }
            }
        }
    }

    def resolveDependencyForConfig(
            VariantDependencies variantDeps,
            Map<ModuleVersionIdentifier, List<LibraryDependencyImpl>> modules,
            Map<ModuleVersionIdentifier, List<ResolvedArtifact>> artifacts,
            Multimap<LibraryDependency, VariantDependencies> reverseMap) {

        Configuration compileClasspath = variantDeps.compileConfiguration
        Configuration packageClasspath = variantDeps.packageConfiguration
        Configuration providedClasspath = variantDeps.providedConfiguration

        // TODO - shouldn't need to do this - fix this in Gradle
        ensureConfigured(compileClasspath)
        ensureConfigured(packageClasspath)

        variantDeps.checker = new DependencyChecker(variantDeps, logger)

        Set<String> currentUnresolvedDependencies = Sets.newHashSet()

        // TODO - defer downloading until required -- This is hard to do as we need the info to build the variant config.
        List<LibraryDependencyImpl> bundles = []
        Map<File, JarDependency> jars = [:]
        Map<File, JarDependency> localJars = [:]
        collectArtifacts(compileClasspath, artifacts)
        def dependencies = compileClasspath.incoming.resolutionResult.root.dependencies
        dependencies.each { DependencyResult dep ->
            if (dep instanceof ResolvedDependencyResult) {
                addDependency(dep.selected, variantDeps, bundles, jars, modules, artifacts, reverseMap)
            } else if (dep instanceof UnresolvedDependencyResult) {
                def attempted = dep.attempted;
                if (attempted != null) {
                    currentUnresolvedDependencies.add(attempted.toString())
                }
            }
        }

        // also need to process local jar files, as they are not processed by the
        // resolvedConfiguration result. This only includes the local jar files for this project.
        compileClasspath.allDependencies.each { dep ->
            if (dep instanceof SelfResolvingDependency &&
                    !(dep instanceof ProjectDependency)) {
                Set<File> files = ((SelfResolvingDependency) dep).resolve()
                for (File f : files) {
                    localJars.put(f, new JarDependency(f, true /*compiled*/, false /*packaged*/))
                }
            }
        }

        if (!compileClasspath.resolvedConfiguration.hasError()) {
            // handle package dependencies. We'll refuse aar libs only in package but not
            // in compile and remove all dependencies already in compile to get package-only jar
            // files.

            Set<File> compileFiles = compileClasspath.files
            Set<File> packageFiles = packageClasspath.files

            for (File f : packageFiles) {
                if (compileFiles.contains(f)) {
                    // if also in compile
                    JarDependency jarDep = jars.get(f);
                    if (jarDep != null) {
                        jarDep.setPackaged(true)
                    }
                    continue
                }

                if (f.getName().toLowerCase().endsWith(".jar")) {
                    jars.put(f, new JarDependency(f, false /*compiled*/, true /*packaged*/))
                } else {
                    throw new RuntimeException("Package-only dependency '" +
                            f.absolutePath +
                            "' is not supported in project " + project.name)
                }
            }
        } else if (!currentUnresolvedDependencies.isEmpty()) {
            unresolvedDependencies.addAll(currentUnresolvedDependencies)
        }

        variantDeps.addLibraries(bundles)
        variantDeps.addJars(jars.values())
        variantDeps.addLocalJars(localJars.values())

        // TODO - filter bundles out of source set classpath

        configureBuild(variantDeps)
    }

    def ensureConfigured(Configuration config) {
        config.allDependencies.withType(ProjectDependency).each { dep ->
            project.evaluationDependsOn(dep.dependencyProject.path)
            ensureConfigured(dep.projectConfiguration)
        }
    }

    static def collectArtifacts(Configuration configuration, Map<ModuleVersionIdentifier,
                         List<ResolvedArtifact>> artifacts) {
        boolean buildModelOnly = Boolean.getBoolean(AndroidProject.BUILD_MODEL_ONLY_SYSTEM_PROPERTY);
        def allArtifacts
        if (buildModelOnly) {
            allArtifacts = configuration.resolvedConfiguration.lenientConfiguration.getArtifacts(Specs.satisfyAll())
        } else {
            allArtifacts = configuration.resolvedConfiguration.resolvedArtifacts
        }

        allArtifacts.each { ResolvedArtifact artifact ->
            def id = artifact.moduleVersion.id
            List<ResolvedArtifact> moduleArtifacts = artifacts[id]
            if (moduleArtifacts == null) {
                moduleArtifacts = []
                artifacts[id] = moduleArtifacts
            }
            moduleArtifacts << artifact
        }
    }

    def addDependency(ResolvedComponentResult moduleVersion,
                      VariantDependencies configDependencies,
                      Collection<LibraryDependencyImpl> bundles,
                      Map<File, JarDependency> jars,
                      Map<ModuleVersionIdentifier, List<LibraryDependencyImpl>> modules,
                      Map<ModuleVersionIdentifier, List<ResolvedArtifact>> artifacts,
                      Multimap<LibraryDependency, VariantDependencies> reverseMap) {
        ModuleVersionIdentifier id = moduleVersion.moduleVersion
        if (configDependencies.checker.excluded(id)) {
            return
        }

        List<LibraryDependencyImpl> bundlesForThisModule = modules[id]
        if (bundlesForThisModule == null) {
            bundlesForThisModule = []
            modules[id] = bundlesForThisModule

            def nestedBundles = []
            def dependencies = moduleVersion.dependencies
            dependencies.each { DependencyResult dep ->
                if (dep instanceof ResolvedDependencyResult) {
                    addDependency(dep.selected, configDependencies, nestedBundles,
                            jars, modules, artifacts, reverseMap)
                }
            }

            def moduleArtifacts = artifacts[id]
            moduleArtifacts?.each { artifact ->
                if (artifact.type == EXT_LIB_ARCHIVE) {
                    String bundleName = GUtil.toCamelCase(id.group + " " + id.name + " " + id.version)
                    def explodedDir = project.file(
                            "$project.buildDir/exploded-bundles/${bundleName}.aar")
                    LibraryDependencyImpl adep = new LibraryDependencyImpl(
                            artifact.file, explodedDir, nestedBundles,
                            id.group + ":" + id.name + ":" + id.version)
                    bundlesForThisModule << adep
                    reverseMap.put(adep, configDependencies)
                } else {
                    jars.put(artifact.file,
                            new JarDependency(artifact.file, true /*compiled*/, false /*packaged*/))
                }
            }

            if (bundlesForThisModule.empty && !nestedBundles.empty) {
                throw new GradleException("Module version $id depends on libraries but is not a library itself")
            }
        } else {
            for (LibraryDependency adep : bundlesForThisModule) {
                reverseMap.put(adep, configDependencies)
            }
        }

        bundles.addAll(bundlesForThisModule)
    }

    private void configureBuild(VariantDependencies configurationDependencies) {
        addDependsOnTaskInOtherProjects(
                project.getTasks().getByName(JavaBasePlugin.BUILD_NEEDED_TASK_NAME), true,
                JavaBasePlugin.BUILD_NEEDED_TASK_NAME, "compile");
        addDependsOnTaskInOtherProjects(
                project.getTasks().getByName(JavaBasePlugin.BUILD_DEPENDENTS_TASK_NAME), false,
                JavaBasePlugin.BUILD_DEPENDENTS_TASK_NAME, "compile");
    }

    /**
     * Adds a dependency on tasks with the specified name in other projects.  The other projects
     * are determined from project lib dependencies using the specified configuration name.
     * These may be projects this project depends on or projects that depend on this project
     * based on the useDependOn argument.
     *
     * @param task Task to add dependencies to
     * @param useDependedOn if true, add tasks from projects this project depends on, otherwise
     * use projects that depend on this one.
     * @param otherProjectTaskName name of task in other projects
     * @param configurationName name of configuration to use to find the other projects
     */
    private static void addDependsOnTaskInOtherProjects(final Task task, boolean useDependedOn,
                                                 String otherProjectTaskName,
                                                 String configurationName) {
        Project project = task.getProject();
        final Configuration configuration = project.getConfigurations().getByName(
                configurationName);
        task.dependsOn(configuration.getTaskDependencyFromProjectDependency(
                useDependedOn, otherProjectTaskName));
    }

    //----------------------------------------------------------------------------------------------
    //------------------------------- END DEPENDENCY STUFF -----------------------------------------
    //----------------------------------------------------------------------------------------------

    protected static File getOptionalDir(File dir) {
        if (dir.isDirectory()) {
            return dir
        }

        return null
    }

    @NonNull
    protected List<ManifestDependencyImpl> getManifestDependencies(
            List<LibraryDependency> libraries) {

        List<ManifestDependencyImpl> list = Lists.newArrayListWithCapacity(libraries.size())

        for (LibraryDependency lib : libraries) {
            // get the dependencies
            List<ManifestDependencyImpl> children = getManifestDependencies(lib.dependencies)
            list.add(new ManifestDependencyImpl(lib.manifest, children))
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

    private static String getLocalVersion() {
        try {
            Class clazz = BasePlugin.class
            String className = clazz.getSimpleName() + ".class"
            String classPath = clazz.getResource(className).toString()
            if (!classPath.startsWith("jar")) {
                // Class not from JAR, unlikely
                return null
            }
            String manifestPath = classPath.substring(0, classPath.lastIndexOf("!") + 1) +
                    "/META-INF/MANIFEST.MF";
            Manifest manifest = new Manifest(new URL(manifestPath).openStream());
            Attributes attr = manifest.getMainAttributes();
            return attr.getValue("Plugin-Version");
        } catch (Throwable t) {
            return null;
        }
    }

    public Project getProject() {
        return project
    }
}
