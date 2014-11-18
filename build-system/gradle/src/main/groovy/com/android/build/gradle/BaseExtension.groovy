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
import com.android.build.gradle.api.TestVariant
import com.android.build.gradle.internal.CompileOptions
import com.android.build.gradle.internal.SourceSetSourceProviderWrapper
import com.android.build.gradle.internal.coverage.JacocoExtension
import com.android.build.gradle.internal.dsl.AaptOptionsImpl
import com.android.build.gradle.internal.dsl.AndroidSourceSetFactory
import com.android.build.gradle.internal.dsl.BuildTypeDsl
import com.android.build.gradle.internal.dsl.DexOptionsImpl
import com.android.build.gradle.internal.dsl.GroupableProductFlavorDsl
import com.android.build.gradle.internal.dsl.LintOptionsImpl
import com.android.build.gradle.internal.dsl.PackagingOptionsImpl
import com.android.build.gradle.internal.dsl.ProductFlavorDsl
import com.android.build.gradle.internal.dsl.SigningConfigDsl
import com.android.build.gradle.internal.dsl.Splits
import com.android.build.gradle.internal.test.TestOptions
import com.android.builder.core.BuilderConstants
import com.android.builder.model.BuildType
import com.android.builder.model.ProductFlavor
import com.android.builder.model.SourceProvider
import com.android.builder.testing.api.DeviceProvider
import com.android.builder.testing.api.TestServer
import com.android.sdklib.repository.FullRevision
import com.android.utils.ILogger
import com.google.common.collect.Lists
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.internal.DefaultDomainObjectSet
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.tasks.SourceSet
import org.gradle.internal.reflect.Instantiator
/**
 * Base android extension for all android plugins.
 */
public abstract class BaseExtension {

    private String target
    private FullRevision buildToolsRevision

    /** Default config, shared by all flavors. */
    final ProductFlavorDsl defaultConfig

    /** Options for aapt, tool for packaging resources. */
    final AaptOptionsImpl aaptOptions

    /** Lint options. */
    final LintOptionsImpl lintOptions

    /** Dex options. */
    final DexOptionsImpl dexOptions

    /** Options for running tests. */
    final TestOptions testOptions

    /** Compile options */
    final CompileOptions compileOptions

    /** Packaging options. */
    final PackagingOptionsImpl packagingOptions

    /** JaCoCo options. */
    final JacocoExtension jacoco

    /** APK splits */
    final Splits splits

    /** All product flavors used by this project. */
    final NamedDomainObjectContainer<GroupableProductFlavorDsl> productFlavors

    /** Build types used by this project. */
    final NamedDomainObjectContainer<BuildTypeDsl> buildTypes

    /** Signing configs used by this project. */
    final NamedDomainObjectContainer<SigningConfigDsl> signingConfigs

    String resourcePrefix

    List<String> flavorDimensionList
    String testBuildType = "debug"

    private String defaultPublishConfig = "release"
    private boolean publishNonDefault = false
    private boolean useNewNativePlugin = false

    private Closure<Void> variantFilter

    private final DefaultDomainObjectSet<TestVariant> testVariantList =
        new DefaultDomainObjectSet<TestVariant>(TestVariant.class)

    private final List<DeviceProvider> deviceProviderList = Lists.newArrayList();
    private final List<TestServer> testServerList = Lists.newArrayList();

    private final BasePlugin plugin

    /**
     * The source sets container.
     */
    final NamedDomainObjectContainer<AndroidSourceSet> sourceSetsContainer

    BaseExtension(
            @NonNull BasePlugin plugin,
            @NonNull ProjectInternal project,
            @NonNull Instantiator instantiator,
            @NonNull NamedDomainObjectContainer<BuildTypeDsl> buildTypes,
            @NonNull NamedDomainObjectContainer<GroupableProductFlavorDsl> productFlavors,
            @NonNull NamedDomainObjectContainer<SigningConfigDsl> signingConfigs,
            boolean isLibrary) {
        this.plugin = plugin
        this.buildTypes = buildTypes
        this.productFlavors = productFlavors
        this.signingConfigs = signingConfigs

        defaultConfig = instantiator.newInstance(ProductFlavorDsl, BuilderConstants.MAIN,
                project, instantiator, project.getLogger())

        aaptOptions = instantiator.newInstance(AaptOptionsImpl)
        dexOptions = instantiator.newInstance(DexOptionsImpl)
        lintOptions = instantiator.newInstance(LintOptionsImpl)
        testOptions = instantiator.newInstance(TestOptions)
        compileOptions = instantiator.newInstance(CompileOptions)
        packagingOptions = instantiator.newInstance(PackagingOptionsImpl)
        jacoco = instantiator.newInstance(JacocoExtension)
        splits = instantiator.newInstance(Splits, instantiator)

        sourceSetsContainer = project.container(AndroidSourceSet,
                new AndroidSourceSetFactory(instantiator, project, isLibrary))

        sourceSetsContainer.whenObjectAdded { AndroidSourceSet sourceSet ->
            ConfigurationContainer configurations = project.getConfigurations()

            createConfiguration(
                    configurations,
                    sourceSet.getCompileConfigurationName(),
                    "Classpath for compiling the ${sourceSet.name} sources.")

            String packageConfigDescription
            if (isLibrary) {
                packageConfigDescription = "Classpath only used when publishing '${sourceSet.name}'."
            } else {
                packageConfigDescription = "Classpath packaged with the compiled '${sourceSet.name}' classes."
            }
            createConfiguration(
                    configurations,
                    sourceSet.getPackageConfigurationName(),
                    packageConfigDescription)

            createConfiguration(
                    configurations,
                    sourceSet.getProvidedConfigurationName(),
                    "Classpath for only compiling the ${sourceSet.name} sources.")

            createConfiguration(
                    configurations,
                    sourceSet.getWearAppConfigurationName(),
                    "Link to a wear app to embed for object '${sourceSet.name}'.")

            sourceSet.setRoot(String.format("src/%s", sourceSet.getName()))
        }
    }

    protected static void createConfiguration(
            @NonNull ConfigurationContainer configurations,
            @NonNull String configurationName,
            @NonNull String configurationDescription) {
        Configuration configuration = configurations.findByName(configurationName)
        if (configuration == null) {
            configuration = configurations.create(configurationName)
        }
        configuration.setVisible(false);
        configuration.setDescription(configurationDescription)
    }

    /**
     * Sets the compile SDK version, based on full SDK version string, e.g.
     * <code>android-21</code> for Lollipop.
     */
    void compileSdkVersion(String version) {
        plugin.checkTasksAlreadyCreated()
        this.target = version
    }

    /**
     * Sets the compile SDK version, based on API level, e.g. 21 for Lollipop.
     */
    void compileSdkVersion(int apiLevel) {
        compileSdkVersion("android-" + apiLevel)
    }

    void setCompileSdkVersion(int apiLevel) {
        compileSdkVersion(apiLevel)
    }

    void setCompileSdkVersion(String target) {
        compileSdkVersion(target)
    }

    /** Sets the build tools version. */
    void buildToolsVersion(String version) {
        plugin.checkTasksAlreadyCreated()
        buildToolsRevision = FullRevision.parseRevision(version)
    }

    void setBuildToolsVersion(String version) {
        buildToolsVersion(version)
    }

    /**
     * Configures the build types.
     */
    void buildTypes(Action<? super NamedDomainObjectContainer<BuildTypeDsl>> action) {
        plugin.checkTasksAlreadyCreated()
        action.execute(buildTypes)
    }

    /**
     * Configures the product flavors.
     */
    void productFlavors(Action<? super NamedDomainObjectContainer<GroupableProductFlavorDsl>> action) {
        plugin.checkTasksAlreadyCreated()
        action.execute(productFlavors)
    }

    /**
     * Configures the signing configs.
     */
    void signingConfigs(Action<? super NamedDomainObjectContainer<SigningConfigDsl>> action) {
        plugin.checkTasksAlreadyCreated()
        action.execute(signingConfigs)
    }

    public void flavorDimensions(String... dimensions) {
        plugin.checkTasksAlreadyCreated()
        flavorDimensionList = Arrays.asList(dimensions)
    }

    /**
     * Configures the source sets. Note that the Android plugin uses its own implementation of
     * source sets, {@link AndroidSourceSet}.
     */
    void sourceSets(Action<NamedDomainObjectContainer<AndroidSourceSet>> action) {
        plugin.checkTasksAlreadyCreated()
        action.execute(sourceSetsContainer)
    }

    /**
     * All source sets. Note that the Android plugin uses its own implementation of
     * source sets, {@link AndroidSourceSet}.
     */
    NamedDomainObjectContainer<AndroidSourceSet> getSourceSets() {
        sourceSetsContainer
    }

    /**
     * The default configuration, inherited by all build flavors (if any are defined).
     */
    void defaultConfig(Action<GroupableProductFlavorDsl> action) {
        plugin.checkTasksAlreadyCreated()
        action.execute(defaultConfig)
    }

    /**
     * Configures aapt options.
     */
    void aaptOptions(Action<AaptOptionsImpl> action) {
        plugin.checkTasksAlreadyCreated()
        action.execute(aaptOptions)
    }

    /**
     * Configures dex options.
     * @param action
     */
    void dexOptions(Action<DexOptionsImpl> action) {
        plugin.checkTasksAlreadyCreated()
        action.execute(dexOptions)
    }

    /**
     * Configure lint options.
     */
    void lintOptions(Action<LintOptionsImpl> action) {
        plugin.checkTasksAlreadyCreated()
        action.execute(lintOptions)
    }

    /** Configures the test options. */
    void testOptions(Action<TestOptions> action) {
        plugin.checkTasksAlreadyCreated()
        action.execute(testOptions)
    }

    /**
     * Configures compile options.
     */
    void compileOptions(Action<CompileOptions> action) {
        plugin.checkTasksAlreadyCreated()
        action.execute(compileOptions)
    }

    /**
     * Configures packaging options.
     */
    void packagingOptions(Action<PackagingOptionsImpl> action) {
        plugin.checkTasksAlreadyCreated()
        action.execute(packagingOptions)
    }

    /**
     * Configures JaCoCo options.
     */
    void jacoco(Action<JacocoExtension> action) {
        plugin.checkTasksAlreadyCreated()
        action.execute(jacoco)
    }

    /**
     * Configures APK splits.
     */
    void splits(Action<Splits> action) {
        plugin.checkTasksAlreadyCreated()
        action.execute(splits)
    }

    void deviceProvider(DeviceProvider deviceProvider) {
        plugin.checkTasksAlreadyCreated()
        deviceProviderList.add(deviceProvider)
    }

    @NonNull
    List<DeviceProvider> getDeviceProviders() {
        return deviceProviderList
    }

    void testServer(TestServer testServer) {
        plugin.checkTasksAlreadyCreated()
        testServerList.add(testServer)
    }

    @NonNull
    List<TestServer> getTestServers() {
        return testServerList
    }

    public void defaultPublishConfig(String value) {
        setDefaultPublishConfig(value)
    }

    public void publishNonDefault(boolean value) {
        publishNonDefault = value
    }

    /**
     * Name of the configuration used to build the default artifact of this project.
     *
     * <p>See <a href="http://tools.android.com/tech-docs/new-build-system/user-guide#TOC-Referencing-a-Library">
     * Referencing a Library</a>
     */
    public String getDefaultPublishConfig() {
        return defaultPublishConfig
    }

    public void setDefaultPublishConfig(String value) {
        defaultPublishConfig = value
    }

    /**
     * Whether to publish artifacts for all configurations, not just the default one.
     *
     * <p>See <a href="http://tools.android.com/tech-docs/new-build-system/user-guide#TOC-Referencing-a-Library">
     * Referencing a Library</a>
     */
    public boolean getPublishNonDefault() {
        return publishNonDefault
    }

    void variantFilter(Closure<Void> filter) {
        variantFilter = filter
    }

    public Closure<Void> getVariantFilter() {
        return variantFilter;
    }

    void resourcePrefix(String prefix) {
        resourcePrefix = prefix
    }

    @NonNull
    public DefaultDomainObjectSet<TestVariant> getTestVariants() {
        return testVariantList
    }

    abstract void addVariant(BaseVariant variant)

    void addTestVariant(TestVariant testVariant) {
        testVariantList.add(testVariant)
    }

    public void registerArtifactType(@NonNull String name,
                                     boolean isTest,
                                     int artifactType) {
        plugin.registerArtifactType(name, isTest, artifactType)
    }

    public void registerBuildTypeSourceProvider(
            @NonNull String name,
            @NonNull BuildType buildType,
            @NonNull SourceProvider sourceProvider) {
        plugin.registerBuildTypeSourceProvider(name, buildType, sourceProvider)
    }

    public void registerProductFlavorSourceProvider(
            @NonNull String name,
            @NonNull ProductFlavor productFlavor,
            @NonNull SourceProvider sourceProvider) {
        plugin.registerProductFlavorSourceProvider(name, productFlavor, sourceProvider)
    }

    public void registerJavaArtifact(
            @NonNull String name,
            @NonNull BaseVariant variant,
            @NonNull String assembleTaskName,
            @NonNull String javaCompileTaskName,
            @NonNull Configuration configuration,
            @NonNull File classesFolder,
            @Nullable SourceProvider sourceProvider) {
        plugin.registerJavaArtifact(name, variant, assembleTaskName, javaCompileTaskName,
                configuration, classesFolder, sourceProvider)
    }

    public void registerMultiFlavorSourceProvider(
            @NonNull String name,
            @NonNull String flavorName,
            @NonNull SourceProvider sourceProvider) {
        plugin.registerMultiFlavorSourceProvider(name, flavorName, sourceProvider)
    }

    @NonNull
    public SourceProvider wrapJavaSourceSet(@NonNull SourceSet sourceSet) {
        return new SourceSetSourceProviderWrapper(sourceSet)
    }

    /** Compile SDK version. */
    public String getCompileSdkVersion() {
        return target
    }

    public FullRevision getBuildToolsRevision() {
        return buildToolsRevision
    }

    public File getSdkDirectory() {
        return plugin.getSdkFolder()
    }

    public List<File> getBootClasspath() {
        return plugin.getBootClasspath()
    }

    public File getAdbExe() {
        return plugin.getSdkInfo().adb
    }

    public ILogger getLogger() {
        return plugin.logger
    }

    protected getPlugin() {
        return plugin
    }

    public File getDefaultProguardFile(String name) {
        File sdkDir = plugin.sdkHandler.getAndCheckSdkFolder()
        return new File(sdkDir,
                SdkConstants.FD_TOOLS + File.separatorChar
                        + SdkConstants.FD_PROGUARD + File.separatorChar
                        + name);
    }

    // ---------------
    // TEMP for compatibility
    // STOPSHIP Remove in 1.0

    // by default, we do not generate pure splits
    boolean generatePureSplits = false;

    void generatePureSplits(boolean flag) {
        if (flag) {
            plugin.getLogger().warning("Pure splits are not supported by PlayStore yet.")
        }
        this.generatePureSplits = flag;
    }

    private boolean enforceUniquePackageName = true

    public void enforceUniquePackageName(boolean value) {
        if (!value) {
            plugin.displayDeprecationWarning("Support for libraries with same package name is deprecated and will be removed in 1.0")
        }
        enforceUniquePackageName = value
    }

    public void setEnforceUniquePackageName(boolean value) {
        enforceUniquePackageName(value)
    }

    public getEnforceUniquePackageName() {
        return enforceUniquePackageName
    }

    public boolean getUseNewNativePlugin() {
        return useNewNativePlugin
    }

    public void setUseNewNativePlugin(boolean value) {
        useNewNativePlugin = value
    }
}
