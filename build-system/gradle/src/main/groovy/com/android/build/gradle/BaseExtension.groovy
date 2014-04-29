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
import com.android.build.gradle.internal.dsl.DexOptionsImpl
import com.android.build.gradle.internal.dsl.LintOptionsImpl
import com.android.build.gradle.internal.dsl.PackagingOptionsImpl
import com.android.build.gradle.internal.dsl.ProductFlavorDsl
import com.android.build.gradle.internal.test.TestOptions
import com.android.builder.BuilderConstants
import com.android.builder.DefaultBuildType
import com.android.builder.DefaultProductFlavor
import com.android.builder.model.BuildType
import com.android.builder.model.ProductFlavor
import com.android.builder.model.SigningConfig
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

    final DefaultProductFlavor defaultConfig
    final AaptOptionsImpl aaptOptions
    final LintOptionsImpl lintOptions
    final DexOptionsImpl dexOptions
    final TestOptions testOptions
    final CompileOptions compileOptions
    final PackagingOptionsImpl packagingOptions
    final JacocoExtension jacoco

    final NamedDomainObjectContainer<DefaultProductFlavor> productFlavors
    final NamedDomainObjectContainer<DefaultBuildType> buildTypes
    final NamedDomainObjectContainer<SigningConfig> signingConfigs

    String resourcePrefix

    List<String> flavorDimensionList
    String testBuildType = "debug"
    // for now, use the old manifest merger.
    boolean useOldManifestMerger = true;

    private Closure<Void> variantFilter

    private final DefaultDomainObjectSet<TestVariant> testVariantList =
        new DefaultDomainObjectSet<TestVariant>(TestVariant.class)

    private final List<DeviceProvider> deviceProviderList = Lists.newArrayList();
    private final List<TestServer> testServerList = Lists.newArrayList();

    protected final BasePlugin plugin

    /**
     * The source sets container.
     */
    final NamedDomainObjectContainer<AndroidSourceSet> sourceSetsContainer

    BaseExtension(
            @NonNull BasePlugin plugin,
            @NonNull ProjectInternal project,
            @NonNull Instantiator instantiator,
            @NonNull NamedDomainObjectContainer<DefaultBuildType> buildTypes,
            @NonNull NamedDomainObjectContainer<DefaultProductFlavor> productFlavors,
            @NonNull NamedDomainObjectContainer<SigningConfig> signingConfigs,
                     final boolean isLibrary) {
        this.plugin = plugin
        this.buildTypes = buildTypes
        this.productFlavors = productFlavors
        this.signingConfigs = signingConfigs

        defaultConfig = instantiator.newInstance(ProductFlavorDsl.class, BuilderConstants.MAIN,
                project.fileResolver, instantiator, project.getLogger())

        aaptOptions = instantiator.newInstance(AaptOptionsImpl.class)
        dexOptions = instantiator.newInstance(DexOptionsImpl.class)
        lintOptions = instantiator.newInstance(LintOptionsImpl.class)
        testOptions = instantiator.newInstance(TestOptions.class)
        compileOptions = instantiator.newInstance(CompileOptions.class)
        packagingOptions = instantiator.newInstance(PackagingOptionsImpl.class)
        jacoco = instantiator.newInstance(JacocoExtension.class)

        sourceSetsContainer = project.container(AndroidSourceSet,
                new AndroidSourceSetFactory(instantiator, project.fileResolver, isLibrary))

        sourceSetsContainer.whenObjectAdded { AndroidSourceSet sourceSet ->
            ConfigurationContainer configurations = project.getConfigurations()

            Configuration compileConfiguration = configurations.findByName(
                    sourceSet.getCompileConfigurationName())
            if (compileConfiguration == null) {
                compileConfiguration = configurations.create(sourceSet.getCompileConfigurationName())
            }
            compileConfiguration.setVisible(false);
            compileConfiguration.setDescription(
                    String.format("Classpath for compiling the %s sources.", sourceSet.getName()))

            Configuration packageConfiguration = configurations.findByName(
                    sourceSet.getPackageConfigurationName())
            if (packageConfiguration == null) {
                packageConfiguration = configurations.create(sourceSet.getPackageConfigurationName())
            }
            packageConfiguration.setVisible(false)
            if (isLibrary) {
                packageConfiguration.setDescription(
                        String.format("Classpath only used for publishing.",
                                sourceSet.getName()));
            } else {
                packageConfiguration.setDescription(
                        String.format("Classpath packaged with the compiled %s classes.",
                                sourceSet.getName()));
            }

            Configuration providedConfiguration = configurations.findByName(
                    sourceSet.getProvidedConfigurationName())
            if (providedConfiguration == null) {
                providedConfiguration = configurations.create(sourceSet.getProvidedConfigurationName())
            }
            providedConfiguration.setVisible(false);
            providedConfiguration.setDescription(
                    String.format("Classpath for only compiling the %s sources.", sourceSet.getName()))

            sourceSet.setRoot(String.format("src/%s", sourceSet.getName()))
        }
    }

    void compileSdkVersion(int apiLevel) {
        plugin.checkTasksAlreadyCreated()
        this.target = "android-" + apiLevel
    }

    void setCompileSdkVersion(int apiLevel) {
        plugin.checkTasksAlreadyCreated()
        compileSdkVersion(apiLevel)
    }

    void compileSdkVersion(String target) {
        plugin.checkTasksAlreadyCreated()
        this.target = target
    }

    void setCompileSdkVersion(String target) {
        plugin.checkTasksAlreadyCreated()
        compileSdkVersion(target)
    }

    void useOldManifestMerger(boolean flag) {
        this.useOldManifestMerger = flag;
    }

    void buildToolsVersion(String version) {
        plugin.checkTasksAlreadyCreated()
        buildToolsRevision = FullRevision.parseRevision(version)
    }

    void setBuildToolsVersion(String version) {
        plugin.checkTasksAlreadyCreated()
        buildToolsVersion(version)
    }

    void buildTypes(Action<? super NamedDomainObjectContainer<DefaultBuildType>> action) {
        plugin.checkTasksAlreadyCreated()
        action.execute(buildTypes)
    }

    void productFlavors(Action<? super NamedDomainObjectContainer<DefaultProductFlavor>> action) {
        plugin.checkTasksAlreadyCreated()
        action.execute(productFlavors)
    }

    void signingConfigs(Action<? super NamedDomainObjectContainer<SigningConfig>> action) {
        plugin.checkTasksAlreadyCreated()
        action.execute(signingConfigs)
    }

    public void flavorDimensions(String... dimensions) {
        plugin.checkTasksAlreadyCreated()
        flavorDimensionList = Arrays.asList(dimensions)
    }

    void sourceSets(Action<NamedDomainObjectContainer<AndroidSourceSet>> action) {
        plugin.checkTasksAlreadyCreated()
        action.execute(sourceSetsContainer)
    }

    NamedDomainObjectContainer<AndroidSourceSet> getSourceSets() {
        sourceSetsContainer
    }

    void defaultConfig(Action<DefaultProductFlavor> action) {
        plugin.checkTasksAlreadyCreated()
        action.execute(defaultConfig)
    }

    void aaptOptions(Action<AaptOptionsImpl> action) {
        plugin.checkTasksAlreadyCreated()
        action.execute(aaptOptions)
    }

    void dexOptions(Action<DexOptionsImpl> action) {
        plugin.checkTasksAlreadyCreated()
        action.execute(dexOptions)
    }

    void lintOptions(Action<LintOptionsImpl> action) {
        plugin.checkTasksAlreadyCreated()
        action.execute(lintOptions)
    }

    void testOptions(Action<TestOptions> action) {
        plugin.checkTasksAlreadyCreated()
        action.execute(testOptions)
    }

    void compileOptions(Action<CompileOptions> action) {
        plugin.checkTasksAlreadyCreated()
        action.execute(compileOptions)
    }

    void packagingOptions(Action<PackagingOptionsImpl> action) {
        plugin.checkTasksAlreadyCreated()
        action.execute(packagingOptions)
    }

    void jacoco(Action<JacocoExtension> action) {
        plugin.checkTasksAlreadyCreated()
        action.execute(jacoco)
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

    public String getCompileSdkVersion() {
        return target
    }

    public FullRevision getBuildToolsRevision() {
        return buildToolsRevision
    }

    public File getAdbExe() {
        return plugin.sdkParser.adb
    }

    public ILogger getLogger() {
        return plugin.logger
    }

    public File getDefaultProguardFile(String name) {
        return new File(plugin.sdkDirectory,
                SdkConstants.FD_TOOLS + File.separatorChar
                        + SdkConstants.FD_PROGUARD + File.separatorChar
                        + name);
    }

    // ---------------
    // TEMP for compatibility
    // STOPSHIP Remove in 1.0

    private boolean enforceUniquePackageName = true

    public void enforceUniquePackageName(boolean value) {
        if (!value) {
            logger.warning("WARNING: support for libraries with same package name is deprecated and will be removed in 1.0")
        }
        enforceUniquePackageName = value
    }

    public void setEnforceUniquePackageName(boolean value) {
        enforceUniquePackageName(value)
    }

    public getEnforceUniquePackageName() {
        return enforceUniquePackageName
    }

    public void flavorGroups(String... groups) {
        logger.warning("WARNING: flavorGroups has been renamed flavorDimensions. It will be removed in 1.0")
        flavorDimensions(groups);
    }
}
