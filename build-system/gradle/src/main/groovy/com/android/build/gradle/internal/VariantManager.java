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

package com.android.build.gradle.internal;

import static com.android.builder.BuilderConstants.DEBUG;
import static com.android.builder.BuilderConstants.INSTRUMENT_TEST;
import static com.android.builder.BuilderConstants.LINT;
import static com.android.builder.BuilderConstants.UI_TEST;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.AppExtension;
import com.android.build.gradle.BasePlugin;
import com.android.build.gradle.api.AndroidSourceSet;
import com.android.build.gradle.api.BaseVariant;
import com.android.build.gradle.internal.api.ApplicationVariantImpl;
import com.android.build.gradle.internal.api.DefaultAndroidSourceSet;
import com.android.build.gradle.internal.api.TestVariantImpl;
import com.android.build.gradle.internal.dependency.VariantDependencies;
import com.android.build.gradle.internal.dsl.BuildTypeDsl;
import com.android.build.gradle.internal.dsl.GroupableProductFlavorDsl;
import com.android.build.gradle.internal.dsl.SigningConfigDsl;
import com.android.build.gradle.internal.variant.ApplicationVariantData;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.TestVariantData;
import com.android.builder.DefaultProductFlavor;
import com.android.builder.VariantConfiguration;
import com.android.builder.model.SigningConfig;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.Task;

import java.util.List;
import java.util.Map;

/**
 * Class to create, manage variants.
 */
public class VariantManager {

    @NonNull
    private final Project project;
    @NonNull
    private final BasePlugin basePlugin;
    @NonNull
    private final AppExtension appExtension;

    private final Map<String, BuildTypeData> buildTypes = Maps.newHashMap();
    private final Map<String, ProductFlavorData<GroupableProductFlavorDsl>> productFlavors = Maps.newHashMap();
    private final Map<String, SigningConfig> signingConfigs = Maps.newHashMap();

    public VariantManager(
            @NonNull Project project,
            @NonNull BasePlugin basePlugin,
            @NonNull AppExtension appExtension) {
        this.appExtension = appExtension;
        this.basePlugin = basePlugin;
        this.project = project;
    }

    @NonNull
    public Map<String, BuildTypeData> getBuildTypes() {
        return buildTypes;
    }

    @NonNull
    public Map<String, ProductFlavorData<GroupableProductFlavorDsl>> getProductFlavors() {
        return productFlavors;
    }

    @NonNull
    public Map<String, SigningConfig> getSigningConfigs() {
        return signingConfigs;
    }

    public void addSigningConfig(@NonNull SigningConfigDsl signingConfigDsl) {
        signingConfigs.put(signingConfigDsl.getName(), signingConfigDsl);
    }

    /**
     * Adds new BuildType, creating a BuildTypeData, and the associated source set,
     * and adding it to the map.
     * @param buildType the build type.
     */
    public void addBuildType(@NonNull BuildTypeDsl buildType) {
        buildType.init(signingConfigs.get(DEBUG));

        String name = buildType.getName();
        checkName(name, "BuildType");

        if (productFlavors.containsKey(name)) {
            throw new RuntimeException("BuildType names cannot collide with ProductFlavor names");
        }

        DefaultAndroidSourceSet sourceSet = (DefaultAndroidSourceSet) appExtension.getSourceSetsContainer().maybeCreate(name);

        BuildTypeData buildTypeData = new BuildTypeData(buildType, sourceSet, project);
        project.getTasks().getByName("assemble").dependsOn(buildTypeData.getAssembleTask());

        buildTypes.put(name, buildTypeData);
    }

    /**
     * Adds a new ProductFlavor, creating a ProductFlavorData and associated source sets,
     * and adding it to the map.
     *
     * @param productFlavor the product flavor
     */
    public void addProductFlavor(@NonNull GroupableProductFlavorDsl productFlavor) {
        String name = productFlavor.getName();
        checkName(name, "ProductFlavor");

        if (buildTypes.containsKey(name)) {
            throw new RuntimeException("ProductFlavor names cannot collide with BuildType names");
        }

        DefaultAndroidSourceSet mainSourceSet = (DefaultAndroidSourceSet) appExtension.getSourceSetsContainer().maybeCreate(
                productFlavor.getName());
        String testName = INSTRUMENT_TEST + StringHelper.capitalize(productFlavor.getName());
        DefaultAndroidSourceSet testSourceSet = (DefaultAndroidSourceSet) appExtension.getSourceSetsContainer().maybeCreate(
                testName);

        ProductFlavorData<GroupableProductFlavorDsl> productFlavorData =
                new ProductFlavorData<GroupableProductFlavorDsl>(
                        productFlavor, mainSourceSet, testSourceSet, project);

        productFlavors.put(productFlavor.getName(), productFlavorData);
    }

    /**
     * Task creation entry point.
     */
    public void createAndroidTasks() {
        if (productFlavors.isEmpty()) {
            createTasksForDefaultBuild();
        } else {
            // there'll be more than one test app, so we need a top level assembleTest
            Task assembleTest = project.getTasks().create("assembleTest");
            assembleTest.setGroup(org.gradle.api.plugins.BasePlugin.BUILD_GROUP);
            assembleTest.setDescription("Assembles all the Test applications");
            basePlugin.setAssembleTest(assembleTest);

            // check whether we have multi flavor builds
            List<String> flavorGroupList = appExtension.getFlavorGroupList();
            if (flavorGroupList == null || flavorGroupList.size() < 2) {
                for (ProductFlavorData productFlavorData : productFlavors.values()) {
                    createTasksForFlavoredBuild(productFlavorData);
                }
            } else {
                // need to group the flavor per group.
                // First a map of group -> list(ProductFlavor)
                ArrayListMultimap<String, ProductFlavorData<GroupableProductFlavorDsl>> map = ArrayListMultimap.create();
                for (ProductFlavorData<GroupableProductFlavorDsl> productFlavorData : productFlavors.values()) {

                    GroupableProductFlavorDsl flavor = productFlavorData.getProductFlavor();
                    String flavorGroup = flavor.getFlavorGroup();

                    if (flavorGroup == null) {
                        throw new RuntimeException(String.format(
                                "Flavor '%1$s' has no flavor group.", flavor.getName()));
                    }
                    if (!flavorGroupList.contains(flavorGroup)) {
                        throw new RuntimeException(String.format(
                                "Flavor '%1$s' has unknown group '%2$s'.", flavor.getName(), flavor.getFlavorGroup()));
                    }

                    map.put(flavorGroup, productFlavorData);
                }

                // now we use the flavor groups to generate an ordered array of flavor to use
                ProductFlavorData[] array = new ProductFlavorData[flavorGroupList.size()];
                createTasksForMultiFlavoredBuilds(array, 0, map);
            }
        }

        // Add a compile lint task
        basePlugin.createLintCompileTask();

        // create the lint tasks.
        basePlugin.createLintTasks();

        // create the test tasks.
        basePlugin.createCheckTasks(!productFlavors.isEmpty(), false /*isLibrary*/);

        // Create the variant API objects after the tasks have been created!
        createApiObjects();
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
    private void createTasksForMultiFlavoredBuilds(
            ProductFlavorData[] datas,
            int i,
            ListMultimap<String, ? extends ProductFlavorData> map) {
        if (i == datas.length) {
            createTasksForFlavoredBuild(datas);
            return;
        }

        // fill the array at the current index.
        // get the group name that matches the index we are filling.
        String group = appExtension.getFlavorGroupList().get(i);

        // from our map, get all the possible flavors in that group.
        List<? extends ProductFlavorData> flavorList = map.get(group);

        // loop on all the flavors to add them to the current index and recursively fill the next
        // indices.
        for (ProductFlavorData flavor : flavorList) {
            datas[i] = flavor;
            createTasksForMultiFlavoredBuilds(datas, (int) i + 1, map);
        }
    }

    /**
     * Creates Tasks for non-flavored build. This means assembleDebug, assembleRelease, and other
     * assemble<Type> are directly building the <type> build instead of all build of the given
     * <type>.
     */
    private void createTasksForDefaultBuild() {
        BuildTypeData testData = buildTypes.get(appExtension.getTestBuildType());
        if (testData == null) {
            throw new RuntimeException("Test Build Type '$appExtension.testBuildType' does not exist.");
        }

        ApplicationVariantData testedVariantData = null;

        ProductFlavorData defaultConfigData = basePlugin.getDefaultConfigData();

        for (BuildTypeData buildTypeData : buildTypes.values()) {
            VariantConfiguration variantConfig = new VariantConfiguration(
                    defaultConfigData.getProductFlavor(),
                    defaultConfigData.getSourceSet(),
                    buildTypeData.getBuildType(),
                    buildTypeData.getSourceSet());

            // create the variant and get its internal storage object.
            ApplicationVariantData appVariantData = new ApplicationVariantData(variantConfig);
            VariantDependencies variantDep = VariantDependencies.compute(
                    project, variantConfig.getFullName(),
                    buildTypeData, defaultConfigData.getMainProvider());
            appVariantData.setVariantDependency(variantDep);

            basePlugin.getVariantDataList().add(appVariantData);

            if (buildTypeData == testData) {
                testedVariantData = appVariantData;
            }
        }

        assert testedVariantData != null;

        // handle the test variant
        VariantConfiguration testVariantConfig = new VariantConfiguration(
                defaultConfigData.getProductFlavor(),
                defaultConfigData.getTestSourceSet(),
                testData.getBuildType(),
                null,
                VariantConfiguration.Type.TEST, testedVariantData.getVariantConfiguration());

        // create the internal storage for this variant.
        TestVariantData testVariantData = new TestVariantData(testVariantConfig, testedVariantData);
        basePlugin.getVariantDataList().add(testVariantData);
        // link the testVariant to the tested variant in the other direction
        testedVariantData.setTestVariantData(testVariantData);

        // dependencies for the test variant
        VariantDependencies variantDep = VariantDependencies.compute(
                project, testVariantConfig.getFullName(),
                defaultConfigData.getTestProvider());
        testVariantData.setVariantDependency(variantDep);

        // now loop on the VariantDependency and resolve them, and create the tasks
        // for each variant
        for (BaseVariantData variantData : basePlugin.getVariantDataList()) {
            VariantConfiguration variantConfig = variantData.getVariantConfiguration();
            VariantDependencies variantDeps = variantData.getVariantDependency();

            basePlugin.resolveDependencies(variantDeps);
            variantConfig.setDependencies(variantDeps);

            if (variantData instanceof ApplicationVariantData) {
                createApplicationVariant(
                        (ApplicationVariantData) variantData,
                        buildTypes.get(variantConfig.getBuildType().getName()).getAssembleTask());

            } else if (variantData instanceof TestVariantData) {
                testVariantData = (TestVariantData) variantData;
                basePlugin.createTestApkTasks(testVariantData,
                        (BaseVariantData) testVariantData.getTestedVariantData());
            }
        }
    }

    /**
     * Creates Task for a given flavor. This will create tasks for all build types for the given
     * flavor.
     * @param flavorDataList the flavor(s) to build.
     */
    private void createTasksForFlavoredBuild(@NonNull ProductFlavorData... flavorDataList) {

        BuildTypeData testData = buildTypes.get(appExtension.getTestBuildType());
        if (testData == null) {
            throw new RuntimeException("Test Build Type '$appExtension.testBuildType' does not exist.");
        }

        // because this method is called multiple times, we need to keep track
        // of the variantData only for this call.
        final List<BaseVariantData> localVariantDataList = Lists.newArrayListWithCapacity(buildTypes.size());

        ApplicationVariantData testedVariantData = null;

        // assembleTask for this flavor(group)
        Task assembleTask = createAssembleTask(flavorDataList);
        project.getTasks().getByName("assemble").dependsOn(assembleTask);

        for (BuildTypeData buildTypeData : buildTypes.values()) {
            /// add the container of dependencies
            // the order of the libraries is important. In descending order:
            // build types, flavors, defaultConfig.
            List<ConfigurationProvider> variantProviders = Lists.newArrayList();
            variantProviders.add(buildTypeData);

            VariantConfiguration variantConfig = new VariantConfiguration(
                    appExtension.getDefaultConfig(),
                    basePlugin.getDefaultConfigData().getSourceSet(),
                    buildTypeData.getBuildType(),
                    buildTypeData.getSourceSet());

            for (ProductFlavorData data : flavorDataList) {
                String dimensionName = "";
                DefaultProductFlavor productFlavor = data.getProductFlavor();

                if (productFlavor instanceof GroupableProductFlavorDsl) {
                    dimensionName = ((GroupableProductFlavorDsl) productFlavor).getFlavorGroup();
                }
                variantConfig.addProductFlavor(
                        productFlavor,
                        data.getSourceSet(),
                        dimensionName
                );
                variantProviders.add(data.getMainProvider());
            }

            // now add the defaultConfig
            variantProviders.add(basePlugin.getDefaultConfigData().getMainProvider());

            // create the variant and get its internal storage object.
            ApplicationVariantData appVariantData = new ApplicationVariantData(variantConfig);

            NamedDomainObjectContainer<AndroidSourceSet> sourceSetsContainer = appExtension
                    .getSourceSetsContainer();

            DefaultAndroidSourceSet variantSourceSet = (DefaultAndroidSourceSet) sourceSetsContainer.maybeCreate(
                    variantConfig.getFullName());
            variantConfig.setVariantSourceProvider(variantSourceSet);
            // TODO: hmm this won't work
            //variantProviders.add(new ConfigurationProviderImpl(project, variantSourceSet))

            if (flavorDataList.length > 1) {
                DefaultAndroidSourceSet multiFlavorSourceSet = (DefaultAndroidSourceSet) sourceSetsContainer.maybeCreate(variantConfig.getFlavorName());
                variantConfig.setMultiFlavorSourceProvider(multiFlavorSourceSet);
                // TODO: hmm this won't work
                //variantProviders.add(new ConfigurationProviderImpl(project, multiFlavorSourceSet))
            }

            VariantDependencies variantDep = VariantDependencies.compute(
                    project, variantConfig.getFullName(),
                    variantProviders.toArray(new ConfigurationProvider[variantProviders.size()]));
            appVariantData.setVariantDependency(variantDep);

            localVariantDataList.add(appVariantData);

            if (buildTypeData == testData) {
                testedVariantData = appVariantData;
            }
        }

        assert testedVariantData != null;

        // handle test variant
        VariantConfiguration testVariantConfig = new VariantConfiguration(
                appExtension.getDefaultConfig(),
                basePlugin.getDefaultConfigData().getTestSourceSet(),
                testData.getBuildType(),
                null,
                VariantConfiguration.Type.TEST,
                testedVariantData.getVariantConfiguration());

        /// add the container of dependencies
        // the order of the libraries is important. In descending order:
        // flavors, defaultConfig. No build type for tests
        List<ConfigurationProvider> testVariantProviders = Lists.newArrayListWithExpectedSize(1 + flavorDataList.length);

        for (ProductFlavorData data : flavorDataList) {
            String dimensionName = "";
            DefaultProductFlavor productFlavor = data.getProductFlavor();

            if (productFlavor instanceof GroupableProductFlavorDsl) {
                dimensionName = ((GroupableProductFlavorDsl) productFlavor).getFlavorGroup();
            }
            testVariantConfig.addProductFlavor(
                    productFlavor,
                    data.getTestSourceSet(),
                    dimensionName);
            testVariantProviders.add(data.getTestProvider());
        }

        // now add the default config
        testVariantProviders.add(basePlugin.getDefaultConfigData().getTestProvider());

        // create the internal storage for this variant.
        TestVariantData testVariantData = new TestVariantData(testVariantConfig, testedVariantData);
        localVariantDataList.add(testVariantData);
        // link the testVariant to the tested variant in the other direction
        testedVariantData.setTestVariantData(testVariantData);

        // dependencies for the test variant
        VariantDependencies variantDep = VariantDependencies.compute(
                project, testVariantData.getVariantConfiguration().getFullName(),
                testVariantProviders.toArray(new ConfigurationProvider[testVariantProviders.size()]));
        testVariantData.setVariantDependency(variantDep);

        // now loop on the VariantDependency and resolve them, and create the tasks
        // for each variant
        for (BaseVariantData variantData : localVariantDataList) {
            VariantConfiguration variantConfig = variantData.getVariantConfiguration();
            VariantDependencies variantDeps = variantData.getVariantDependency();

            basePlugin.resolveDependencies(variantDeps);
            variantConfig.setDependencies(variantDeps);

            if (variantData instanceof ApplicationVariantData) {
                BuildTypeData buildTypeData = buildTypes.get(variantConfig.getBuildType().getName());
                createApplicationVariant((ApplicationVariantData) variantData, null);

                buildTypeData.getAssembleTask().dependsOn(variantData.assembleTask);
                assembleTask.dependsOn(variantData.assembleTask);

            } else if (variantData instanceof TestVariantData) {
                testVariantData = (TestVariantData) variantData;
                basePlugin.createTestApkTasks(testVariantData,
                        (BaseVariantData) testVariantData.getTestedVariantData());
            }

            basePlugin.getVariantDataList().add(variantData);
        }
    }

    /**
     * Creates the tasks for a given ApplicationVariantData.
     * @param variantData the non-null ApplicationVariantData.
     * @param assembleTask an optional assembleTask to be used. If null, a new one is created.
     */
    private void createApplicationVariant(
            @NonNull ApplicationVariantData variantData,
            @Nullable Task assembleTask) {

        basePlugin.createAnchorTasks(variantData);

        basePlugin.createCheckManifestTask(variantData);

        // Add a task to process the manifest(s)
        basePlugin.createProcessManifestTask(variantData, "manifests");

        // Add a task to create the res values
        basePlugin.createGenerateResValuesTask(variantData);

        // Add a task to compile renderscript files.
        basePlugin.createRenderscriptTask(variantData);

        // Add a task to merge the resource folders
        basePlugin.createMergeResourcesTask(variantData, true /*process9Patch*/);

        // Add a task to merge the asset folders
        basePlugin.createMergeAssetsTask(variantData, null /*default location*/, true /*includeDependencies*/);

        // Add a task to create the BuildConfig class
        basePlugin.createBuildConfigTask(variantData);

        // Add a task to generate resource source files
        basePlugin.createProcessResTask(variantData, true /*generateResourcePackage*/);

        // Add a task to process the java resources
        basePlugin.createProcessJavaResTask(variantData);

        basePlugin.createAidlTask(variantData);

        // Add a compile task
        basePlugin.createCompileTask(variantData, null/*testedVariant*/);

        // Add NDK tasks
        basePlugin.createNdkTasks(variantData);

        basePlugin.addPackageTasks(variantData, assembleTask);
    }

    @NonNull
    private Task createAssembleTask(ProductFlavorData[] flavorDataList) {
        String name = ProductFlavorData.getFlavoredName(flavorDataList, true);

        Task assembleTask = project.getTasks().create("assemble" + name);
        assembleTask.setDescription("Assembles all builds for flavor " + name);
        assembleTask.setGroup("Build");

        return assembleTask;
    }

    private void createApiObjects() {
        // we always want to have the test/tested objects created at the same time
        // so that dynamic closure call on add can have referenced objects created.
        // This means some objects are created before they are processed from the loop,
        // so we store whether we have processed them or not.
        Map<BaseVariantData, BaseVariant> map = Maps.newHashMap();
        for (BaseVariantData variantData : basePlugin.getVariantDataList()) {
            if (map.get(variantData) != null) {
                continue;
            }

            if (variantData instanceof ApplicationVariantData) {
                ApplicationVariantData appVariantData = (ApplicationVariantData) variantData;
                createVariantApiObjects(map, appVariantData, appVariantData.getTestVariantData());

            } else if (variantData instanceof TestVariantData) {
                TestVariantData testVariantData = (TestVariantData) variantData;
                createVariantApiObjects(map,
                        (ApplicationVariantData) testVariantData.getTestedVariantData(),
                        testVariantData);
            }
        }
    }

    private void createVariantApiObjects(
            @NonNull Map<BaseVariantData, BaseVariant> map,
            @NonNull ApplicationVariantData appVariantData,
            @Nullable TestVariantData testVariantData) {
        ApplicationVariantImpl appVariant = basePlugin.getInstantiator().newInstance(
                ApplicationVariantImpl.class, appVariantData, basePlugin);

        TestVariantImpl testVariant = null;
        if (testVariantData != null) {
            testVariant = basePlugin.getInstantiator().newInstance(TestVariantImpl.class, testVariantData, basePlugin);
        }

        if (appVariant != null && testVariant != null) {
            appVariant.setTestVariant(testVariant);
            testVariant.setTestedVariant(appVariant);
        }

        appExtension.addApplicationVariant(appVariant);
        map.put(appVariantData, appVariant);

        if (testVariant != null) {
            appExtension.addTestVariant(testVariant);
            map.put(testVariantData, testVariant);
        }
    }


    private static void checkName(@NonNull String name, @NonNull String displayName) {
        if (name.startsWith(INSTRUMENT_TEST)) {
            throw new RuntimeException(String.format(
                    "%1$s names cannot start with '%2$s'", displayName, INSTRUMENT_TEST));
        }

        if (name.startsWith(UI_TEST)) {
            throw new RuntimeException(String.format(
                    "%1$s names cannot start with %2$s", displayName, UI_TEST));
        }

        if (LINT.equals(name)) {
            throw new RuntimeException(String.format(
                    "%1$s names cannot be %2$s", displayName, LINT));
        }
    }
}
