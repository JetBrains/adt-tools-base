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
import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.internal.BuildTypeData
import com.android.build.gradle.internal.ConfigurationProvider
import com.android.build.gradle.internal.ProductFlavorData
import com.android.build.gradle.internal.api.ApplicationVariantImpl
import com.android.build.gradle.internal.api.DefaultAndroidSourceSet
import com.android.build.gradle.internal.api.TestVariantImpl
import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.build.gradle.internal.dsl.BuildTypeDsl
import com.android.build.gradle.internal.dsl.BuildTypeFactory
import com.android.build.gradle.internal.dsl.GroupableProductFlavorDsl
import com.android.build.gradle.internal.dsl.GroupableProductFlavorFactory
import com.android.build.gradle.internal.dsl.SigningConfigDsl
import com.android.build.gradle.internal.dsl.SigningConfigFactory
import com.android.build.gradle.internal.test.PluginHolder
import com.android.build.gradle.internal.variant.ApplicationVariantData
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.build.gradle.internal.variant.TestVariantData
import com.android.builder.DefaultBuildType
import com.android.builder.VariantConfiguration
import com.android.builder.model.SigningConfig
import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.ListMultimap
import com.google.common.collect.Maps
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.plugins.BasePlugin
import org.gradle.internal.reflect.Instantiator
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry

import javax.inject.Inject

import static com.android.builder.BuilderConstants.DEBUG
import static com.android.builder.BuilderConstants.INSTRUMENT_TEST
import static com.android.builder.BuilderConstants.LINT
import static com.android.builder.BuilderConstants.RELEASE
import static com.android.builder.BuilderConstants.UI_TEST
/**
 * Gradle plugin class for 'application' projects.
 */
class AppPlugin extends com.android.build.gradle.BasePlugin implements Plugin<Project> {
    static PluginHolder pluginHolder;

    final Map<String, BuildTypeData> buildTypes = [:]
    final Map<String, ProductFlavorData<GroupableProductFlavorDsl>> productFlavors = [:]
    final Map<String, SigningConfig> signingConfigs = [:]

    AppExtension extension

    @Inject
    public AppPlugin(Instantiator instantiator, ToolingModelBuilderRegistry registry) {
        super(instantiator, registry)
    }

    @Override
    public AppExtension getExtension() {
        return extension
    }

    @Override
    void apply(Project project) {
        super.apply(project)

        // This is for testing.
        if (pluginHolder != null) {
            pluginHolder.plugin = this;
        }

        def buildTypeContainer = project.container(DefaultBuildType,
                new BuildTypeFactory(instantiator,  project.fileResolver, project.getLogger()))
        def productFlavorContainer = project.container(GroupableProductFlavorDsl,
                new GroupableProductFlavorFactory(instantiator, project.fileResolver, project.getLogger()))
        def signingConfigContainer = project.container(SigningConfig,
                new SigningConfigFactory(instantiator))

        extension = project.extensions.create('android', AppExtension,
                this, (ProjectInternal) project, instantiator,
                buildTypeContainer, productFlavorContainer, signingConfigContainer)
        setBaseExtension(extension)

        // map the whenObjectAdded callbacks on the containers.
        signingConfigContainer.whenObjectAdded { SigningConfig signingConfig ->
            SigningConfigDsl signingConfigDsl = (SigningConfigDsl) signingConfig
            signingConfigs[signingConfigDsl.name] = signingConfig
        }

        buildTypeContainer.whenObjectAdded { DefaultBuildType buildType ->
            ((BuildTypeDsl)buildType).init(signingConfigContainer.getByName(DEBUG))
            addBuildType(buildType)
        }

        productFlavorContainer.whenObjectAdded { GroupableProductFlavorDsl productFlavor ->
            addProductFlavor(productFlavor)
        }

        // create default Objects, signingConfig first as its used by the BuildTypes.
        signingConfigContainer.create(DEBUG)
        buildTypeContainer.create(DEBUG)
        buildTypeContainer.create(RELEASE)

        // map whenObjectRemoved on the containers to throw an exception.
        signingConfigContainer.whenObjectRemoved {
            throw new UnsupportedOperationException("Removing signingConfigs is not supported.")
        }
        buildTypeContainer.whenObjectRemoved {
            throw new UnsupportedOperationException("Removing build types is not supported.")
        }
        productFlavorContainer.whenObjectRemoved {
            throw new UnsupportedOperationException("Removing product flavors is not supported.")
        }
    }

    /**
     * Adds new BuildType, creating a BuildTypeData, and the associated source set,
     * and adding it to the map.
     * @param buildType the build type.
     */
    private void addBuildType(DefaultBuildType buildType) {
        String name = buildType.name
        checkName(name, "BuildType")

        if (productFlavors.containsKey(name)) {
            throw new RuntimeException("BuildType names cannot collide with ProductFlavor names")
        }

        def sourceSet = extension.sourceSetsContainer.maybeCreate(name)

        BuildTypeData buildTypeData = new BuildTypeData(buildType, sourceSet, project)
        project.tasks.assemble.dependsOn buildTypeData.assembleTask

        buildTypes[name] = buildTypeData
    }

    /**
     * Adds a new ProductFlavor, creating a ProductFlavorData and associated source sets,
     * and adding it to the map.
     *
     * @param productFlavor the product flavor
     */
    private void addProductFlavor(GroupableProductFlavorDsl productFlavor) {
        String name = productFlavor.name
        checkName(name, "ProductFlavor")

        if (buildTypes.containsKey(name)) {
            throw new RuntimeException("ProductFlavor names cannot collide with BuildType names")
        }

        def mainSourceSet = (DefaultAndroidSourceSet) extension.sourceSetsContainer.maybeCreate(productFlavor.name)
        String testName = "${INSTRUMENT_TEST}${productFlavor.name.capitalize()}"
        def testSourceSet = (DefaultAndroidSourceSet) extension.sourceSetsContainer.maybeCreate(testName)

        ProductFlavorData<GroupableProductFlavorDsl> productFlavorData =
                new ProductFlavorData<GroupableProductFlavorDsl>(
                        productFlavor, mainSourceSet, testSourceSet, project)

        productFlavors[productFlavor.name] = productFlavorData
    }

    private static void checkName(String name, String displayName) {
        if (name.startsWith(INSTRUMENT_TEST)) {
            throw new RuntimeException(
                    "${displayName} names cannot start with '${INSTRUMENT_TEST}'")
        }

        if (name.startsWith(UI_TEST)) {
            throw new RuntimeException(
                    "${displayName} names cannot start with '${UI_TEST}'")
        }

        if (LINT.equals(name)) {
            throw new RuntimeException("${displayName} names cannot be ${LINT}")
        }
    }

    /**
     * Task creation entry point.
     */
    @Override
    protected void doCreateAndroidTasks() {
        if (productFlavors.isEmpty()) {
            createTasksForDefaultBuild()
        } else {
            // there'll be more than one test app, so we need a top level assembleTest
            assembleTest = project.tasks.create("assembleTest")
            assembleTest.group = BasePlugin.BUILD_GROUP
            assembleTest.description = "Assembles all the Test applications"

            // check whether we have multi flavor builds
            if (extension.flavorGroupList == null || extension.flavorGroupList.size() < 2) {
                productFlavors.values().each { ProductFlavorData productFlavorData ->
                    createTasksForFlavoredBuild(productFlavorData)
                }
            } else {
                // need to group the flavor per group.
                // First a map of group -> list(ProductFlavor)
                ArrayListMultimap<String, ProductFlavorData<GroupableProductFlavorDsl>> map = ArrayListMultimap.create();
                productFlavors.values().each { ProductFlavorData<GroupableProductFlavorDsl> productFlavorData ->
                    def flavor = productFlavorData.productFlavor
                    if (flavor.flavorGroup == null) {
                        throw new RuntimeException(
                                "Flavor ${flavor.name} has no flavor group.")
                    }
                    if (!extension.flavorGroupList.contains(flavor.flavorGroup)) {
                        throw new RuntimeException(
                                "Flavor ${flavor.name} has unknown group ${flavor.flavorGroup}.")
                    }

                    map.put(flavor.flavorGroup, productFlavorData)
                }

                // now we use the flavor groups to generate an ordered array of flavor to use
                ProductFlavorData[] array = new ProductFlavorData[extension.flavorGroupList.size()]
                createTasksForMultiFlavoredBuilds(array, 0, map)
            }
        }

        // Add a compile lint task
        createLintCompileTask()

        // create the lint tasks.
        createLintTasks()

        // create the test tasks.
        createCheckTasks(!productFlavors.isEmpty(), false /*isLibrary*/)

        // Create the variant API objects after the tasks have been created!
        createApiObjects()
    }

    /**
     * Creates the tasks for multi-flavor builds.
     *
     * This recursively fills the array of ProductFlavorData (in the order defined
     * in extension.flavorGroupList), creating all possible combination.
     *
     * @param datas the arrays to fill
     * @param i the current index to fill
     * @param map the map of group -> list(ProductFlavor)
     * @return
     */
    private createTasksForMultiFlavoredBuilds(ProductFlavorData[] datas, int i,
                                              ListMultimap<String, ? extends ProductFlavorData> map) {
        if (i == datas.length) {
            createTasksForFlavoredBuild(datas)
            return
        }

        // fill the array at the current index.
        // get the group name that matches the index we are filling.
        def group = extension.flavorGroupList.get(i)

        // from our map, get all the possible flavors in that group.
        def flavorList = map.get(group)

        // loop on all the flavors to add them to the current index and recursively fill the next
        // indices.
        for (ProductFlavorData flavor : flavorList) {
            datas[i] = flavor
            createTasksForMultiFlavoredBuilds(datas, (int) i + 1, map)
        }
    }

    /**
     * Creates Tasks for non-flavored build. This means assembleDebug, assembleRelease, and other
     * assemble<Type> are directly building the <type> build instead of all build of the given
     * <type>.
     */
    private createTasksForDefaultBuild() {
        BuildTypeData testData = buildTypes[extension.testBuildType]
        if (testData == null) {
            throw new RuntimeException("Test Build Type '$extension.testBuildType' does not exist.")
        }

        ApplicationVariantData testedVariantData = null

        ProductFlavorData defaultConfigData = getDefaultConfigData();

        for (BuildTypeData buildTypeData : buildTypes.values()) {
            def variantConfig = new VariantConfiguration(
                    defaultConfigData.productFlavor,
                    defaultConfigData.sourceSet,
                    buildTypeData.buildType,
                    buildTypeData.sourceSet)

            // create the variant and get its internal storage object.
            ApplicationVariantData appVariantData = new ApplicationVariantData(variantConfig)
            VariantDependencies variantDep = VariantDependencies.compute(
                    project, appVariantData.variantConfiguration.fullName,
                    buildTypeData, defaultConfigData.mainProvider)
            appVariantData.setVariantDependency(variantDep)

            variantDataList.add(appVariantData)

            if (buildTypeData == testData) {
                testedVariantData = appVariantData
            }
        }

        assert testedVariantData != null

        // handle the test variant
        def testVariantConfig = new VariantConfiguration(
                defaultConfigData.productFlavor,
                defaultConfigData.testSourceSet,
                testData.buildType,
                null,
                VariantConfiguration.Type.TEST, testedVariantData.variantConfiguration)

        // create the internal storage for this variant.
        def testVariantData = new TestVariantData(testVariantConfig, testedVariantData)
        variantDataList.add(testVariantData)
        // link the testVariant to the tested variant in the other direction
        testedVariantData.setTestVariantData(testVariantData);

        // dependencies for the test variant
        VariantDependencies variantDep = VariantDependencies.compute(
                project, testVariantData.variantConfiguration.fullName,
                defaultConfigData.testProvider)
        testVariantData.setVariantDependency(variantDep)

        // now loop on the VariantDependency and resolve them, and create the tasks
        // for each variant
        for (BaseVariantData variantData : variantDataList) {
            resolveDependencies(variantData.variantDependency)
            variantData.variantConfiguration.setDependencies(variantData.variantDependency)

            if (variantData instanceof ApplicationVariantData) {
                createApplicationVariant(
                        (ApplicationVariantData) variantData,
                        buildTypes[variantData.variantConfiguration.buildType.name].assembleTask)

            } else if (variantData instanceof TestVariantData) {
                testVariantData = (TestVariantData) variantData
                createTestApkTasks(testVariantData,
                        (BaseVariantData) testVariantData.testedVariantData)
            }
        }
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

            if (variantData instanceof ApplicationVariantData) {
                ApplicationVariantData appVariantData = (ApplicationVariantData) variantData
                createVariantApiObjects(map, appVariantData, appVariantData.testVariantData)

            } else if (variantData instanceof TestVariantData) {
                TestVariantData testVariantData = (TestVariantData) variantData
                createVariantApiObjects(map,
                        (ApplicationVariantData) testVariantData.testedVariantData,
                        testVariantData)
            }
        }
    }

    private void createVariantApiObjects(@NonNull Map<BaseVariantData, BaseVariant> map,
                                         @NonNull ApplicationVariantData appVariantData,
                                         @Nullable TestVariantData testVariantData) {
        ApplicationVariantImpl appVariant = instantiator.newInstance(
                ApplicationVariantImpl.class, appVariantData, this)

        TestVariantImpl testVariant = null;
        if (testVariantData != null) {
            testVariant = instantiator.newInstance(TestVariantImpl.class, testVariantData, this)
        }

        if (appVariant != null && testVariant != null) {
            appVariant.setTestVariant(testVariant)
            testVariant.setTestedVariant(appVariant)
        }

        extension.addApplicationVariant(appVariant)
        map.put(appVariantData, appVariant)

        if (testVariant != null) {
            extension.addTestVariant(testVariant)
            map.put(testVariantData, testVariant)
        }
    }

    /**
     * Creates Task for a given flavor. This will create tasks for all build types for the given
     * flavor.
     * @param flavorDataList the flavor(s) to build.
     */
    private createTasksForFlavoredBuild(ProductFlavorData... flavorDataList) {

        BuildTypeData testData = buildTypes[extension.testBuildType]
        if (testData == null) {
            throw new RuntimeException("Test Build Type '$extension.testBuildType' does not exist.")
        }

        // because this method is called multiple times, we need to keep track
        // of the variantData only for this call.
        final List<BaseVariantData> localVariantDataList = []

        ApplicationVariantData testedVariantData = null

        // assembleTask for this flavor(group)
        def assembleTask = createAssembleTask(flavorDataList)
        project.tasks.assemble.dependsOn assembleTask

        for (BuildTypeData buildTypeData : buildTypes.values()) {
            /// add the container of dependencies
            // the order of the libraries is important. In descending order:
            // build types, flavors, defaultConfig.
            List<ConfigurationProvider> variantProviders = []
            variantProviders.add(buildTypeData)

            VariantConfiguration variantConfig = new VariantConfiguration(
                    extension.defaultConfig,
                    getDefaultConfigData().sourceSet,
                    buildTypeData.buildType,
                    buildTypeData.sourceSet)

            for (ProductFlavorData data : flavorDataList) {
                String dimensionName = "";
                if (data.productFlavor instanceof GroupableProductFlavorDsl) {
                    dimensionName = ((GroupableProductFlavorDsl) data.productFlavor).flavorGroup
                }
                variantConfig.addProductFlavor(
                        data.productFlavor,
                        data.sourceSet,
                        dimensionName
                )
                variantProviders.add(data.mainProvider)
            }

            // now add the defaultConfig
            variantProviders.add(defaultConfigData.mainProvider)

            // create the variant and get its internal storage object.
            ApplicationVariantData appVariantData = new ApplicationVariantData(variantConfig)

            DefaultAndroidSourceSet variantSourceSet = (DefaultAndroidSourceSet) extension.sourceSetsContainer.maybeCreate(variantConfig.fullName)
            variantConfig.setVariantSourceProvider(variantSourceSet)
            // TODO: hmm this won't work
            //variantProviders.add(new ConfigurationProviderImpl(project, variantSourceSet))

            if (flavorDataList.size() > 1) {
                DefaultAndroidSourceSet multiFlavorSourceSet = (DefaultAndroidSourceSet) extension.sourceSetsContainer.maybeCreate(variantConfig.flavorName)
                variantConfig.setMultiFlavorSourceProvider(multiFlavorSourceSet)
                // TODO: hmm this won't work
                //variantProviders.add(new ConfigurationProviderImpl(project, multiFlavorSourceSet))
            }

            VariantDependencies variantDep = VariantDependencies.compute(
                    project, appVariantData.variantConfiguration.fullName,
                    variantProviders.toArray(new ConfigurationProvider[variantProviders.size()]))
            appVariantData.setVariantDependency(variantDep)

            localVariantDataList.add(appVariantData)

            if (buildTypeData == testData) {
                testedVariantData = appVariantData
            }
        }

        assert testedVariantData != null

        // handle test variant
        VariantConfiguration testVariantConfig = new VariantConfiguration(
                extension.defaultConfig,
                getDefaultConfigData().testSourceSet,
                testData.buildType,
                null,
                VariantConfiguration.Type.TEST,
                testedVariantData.variantConfiguration)

        /// add the container of dependencies
        // the order of the libraries is important. In descending order:
        // flavors, defaultConfig. No build type for tests
        List<ConfigurationProvider> testVariantProviders = []

        for (ProductFlavorData data : flavorDataList) {
            String dimensionName = "";
            if (data.productFlavor instanceof GroupableProductFlavorDsl) {
                dimensionName = ((GroupableProductFlavorDsl) data.productFlavor).flavorGroup
            }
            testVariantConfig.addProductFlavor(
                    data.productFlavor,
                    data.testSourceSet,
                    dimensionName)
            testVariantProviders.add(data.testProvider)
        }

        // now add the default config
        testVariantProviders.add(defaultConfigData.testProvider)

        // create the internal storage for this variant.
        TestVariantData testVariantData = new TestVariantData(testVariantConfig, testedVariantData)
        localVariantDataList.add(testVariantData)
        // link the testVariant to the tested variant in the other direction
        testedVariantData.setTestVariantData(testVariantData);

        // dependencies for the test variant
        VariantDependencies variantDep = VariantDependencies.compute(
                project, testVariantData.variantConfiguration.fullName,
                testVariantProviders.toArray(new ConfigurationProvider[testVariantProviders.size()]))
        testVariantData.setVariantDependency(variantDep)

        // now loop on the VariantDependency and resolve them, and create the tasks
        // for each variant
        for (BaseVariantData variantData : localVariantDataList) {
            resolveDependencies(variantData.variantDependency)
            variantData.variantConfiguration.setDependencies(variantData.variantDependency)

            if (variantData instanceof ApplicationVariantData) {
                BuildTypeData buildTypeData = buildTypes[variantData.variantConfiguration.buildType.name]
                createApplicationVariant((ApplicationVariantData) variantData, null)

                buildTypeData.assembleTask.dependsOn variantData.assembleTask
                assembleTask.dependsOn variantData.assembleTask

            } else if (variantData instanceof TestVariantData) {
                testVariantData = (TestVariantData) variantData
                createTestApkTasks(testVariantData,
                        (BaseVariantData) testVariantData.testedVariantData)
            }

            variantDataList.add(variantData)
        }
    }

    private Task createAssembleTask(ProductFlavorData[] flavorDataList) {
        String name = ProductFlavorData.getFlavoredName(flavorDataList, true)

        def assembleTask = project.tasks.create("assemble${name}")
        assembleTask.description = "Assembles all builds for flavor ${name}"
        assembleTask.group = "Build"

        return assembleTask
    }

    /**
     * Creates an ApplicationVariantData and its tasks for a given variant configuration.
     * @param variantConfig the non-null variant configuration.
     * @param assembleTask an optional assembleTask to be used. If null, a new one is created.
     * @param configDependencies a non null list of dependencies for this variant.
     * @return
     */
    @NonNull
    private void createApplicationVariant(
            @NonNull ApplicationVariantData variant,
            @Nullable Task assembleTask) {

        createAnchorTasks(variant)

        createCheckManifestTask(variant)

        // Add a task to process the manifest(s)
        createProcessManifestTask(variant, "manifests")

        // Add a task to compile renderscript files.
        createRenderscriptTask(variant)

        // Add a task to merge the resource folders
        createMergeResourcesTask(variant, true /*process9Patch*/)

        // Add a task to merge the asset folders
        createMergeAssetsTask(variant, null /*default location*/, true /*includeDependencies*/)

        // Add a task to create the BuildConfig class
        createBuildConfigTask(variant)

        // Add a task to generate resource source files
        createProcessResTask(variant)

        // Add a task to process the java resources
        createProcessJavaResTask(variant)

        createAidlTask(variant)

        // Add a compile task
        createCompileTask(variant, null/*testedVariant*/)

        // Add NDK tasks
        createNdkTasks(variant)

        addPackageTasks(variant, assembleTask)
    }
}
