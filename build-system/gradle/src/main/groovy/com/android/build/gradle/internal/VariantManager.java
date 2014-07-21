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

import static com.android.builder.core.BuilderConstants.ANDROID_TEST;
import static com.android.builder.core.BuilderConstants.DEBUG;
import static com.android.builder.core.BuilderConstants.LINT;
import static com.android.builder.core.BuilderConstants.UI_TEST;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.BaseExtension;
import com.android.build.gradle.BasePlugin;
import com.android.build.gradle.api.AndroidSourceSet;
import com.android.build.gradle.api.BaseVariant;
import com.android.build.gradle.internal.api.DefaultAndroidSourceSet;
import com.android.build.gradle.internal.api.TestVariantImpl;
import com.android.build.gradle.internal.api.TestedVariant;
import com.android.build.gradle.internal.dependency.VariantDependencies;
import com.android.build.gradle.internal.dsl.BuildTypeDsl;
import com.android.build.gradle.internal.dsl.GroupableProductFlavorDsl;
import com.android.build.gradle.internal.dsl.SigningConfigDsl;
import com.android.build.gradle.internal.variant.ApplicationVariantFactory;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.build.gradle.internal.variant.TestVariantData;
import com.android.build.gradle.internal.variant.TestedVariantData;
import com.android.build.gradle.internal.variant.VariantFactory;
import com.android.builder.core.DefaultProductFlavor;
import com.android.builder.core.VariantConfiguration;
import com.android.builder.model.ProductFlavor;
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
import java.util.Set;

import groovy.lang.Closure;

/**
 * Class to create, manage variants.
 */
public class VariantManager {

    @NonNull
    private final Project project;
    @NonNull
    private final BasePlugin basePlugin;
    @NonNull
    private final BaseExtension extension;
    @NonNull
    private final VariantFactory variantFactory;

    private final Map<String, BuildTypeData> buildTypes = Maps.newHashMap();
    private final Map<String, ProductFlavorData<GroupableProductFlavorDsl>> productFlavors = Maps.newHashMap();
    private final Map<String, SigningConfig> signingConfigs = Maps.newHashMap();

    private final VariantFilterImpl variantFilter = new VariantFilterImpl();

    public VariantManager(
            @NonNull Project project,
            @NonNull BasePlugin basePlugin,
            @NonNull BaseExtension extension,
            @NonNull VariantFactory variantFactory) {
        this.extension = extension;
        this.basePlugin = basePlugin;
        this.project = project;
        this.variantFactory = variantFactory;
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

        DefaultAndroidSourceSet sourceSet = (DefaultAndroidSourceSet) extension.getSourceSetsContainer().maybeCreate(name);

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

        DefaultAndroidSourceSet mainSourceSet = (DefaultAndroidSourceSet) extension.getSourceSetsContainer().maybeCreate(
                productFlavor.getName());
        String testName = ANDROID_TEST + StringHelper.capitalize(productFlavor.getName());
        DefaultAndroidSourceSet testSourceSet = (DefaultAndroidSourceSet) extension.getSourceSetsContainer().maybeCreate(
                testName);

        ProductFlavorData<GroupableProductFlavorDsl> productFlavorData =
                new ProductFlavorData<GroupableProductFlavorDsl>(
                        productFlavor, mainSourceSet, testSourceSet, project);

        productFlavors.put(productFlavor.getName(), productFlavorData);
    }

    /**
     * Task creation entry point.
     *
     * @param signingOverride a signing override. Generally driven through the IDE.
     */
    public void createAndroidTasks(@Nullable SigningConfig signingOverride) {
        // Add a compile lint task
        basePlugin.createLintCompileTask();

        Set<String> densities = basePlugin.getExtension().getSplits().getDensityFilters();
        Set<String> abis = basePlugin.getExtension().getSplits().getAbiFilters();

        if (productFlavors.isEmpty()) {
            createTasksForDefaultBuild(densities, abis, signingOverride);
        } else {
            // there'll be more than one test app, so we need a top level assembleTest
            Task assembleTest = project.getTasks().create("assembleTest");
            assembleTest.setGroup(org.gradle.api.plugins.BasePlugin.BUILD_GROUP);
            assembleTest.setDescription("Assembles all the Test applications");
            basePlugin.setAssembleTest(assembleTest);

            // check whether we have multi flavor builds
            List<String> flavorDimensionList = extension.getFlavorDimensionList();
            if (flavorDimensionList == null || flavorDimensionList.size() < 2) {
                for (ProductFlavorData productFlavorData : productFlavors.values()) {
                    createTasksForFlavoredBuild(densities, abis, signingOverride, productFlavorData);
                }
            } else {
                // need to group the flavor per dimension.
                // First a map of dimension -> list(ProductFlavor)
                ArrayListMultimap<String, ProductFlavorData<GroupableProductFlavorDsl>> map = ArrayListMultimap.create();
                for (ProductFlavorData<GroupableProductFlavorDsl> productFlavorData : productFlavors.values()) {

                    GroupableProductFlavorDsl flavor = productFlavorData.getProductFlavor();
                    String flavorDimension = flavor.getFlavorDimension();

                    if (flavorDimension == null) {
                        throw new RuntimeException(String.format(
                                "Flavor '%1$s' has no flavor dimension.", flavor.getName()));
                    }
                    if (!flavorDimensionList.contains(flavorDimension)) {
                        throw new RuntimeException(String.format(
                                "Flavor '%1$s' has unknown dimension '%2$s'.",
                                flavor.getName(), flavor.getFlavorDimension()));
                    }

                    map.put(flavorDimension, productFlavorData);
                }

                // now we use the flavor dimensions to generate an ordered array of flavor to use
                ProductFlavorData[] array = new ProductFlavorData[flavorDimensionList.size()];
                createTasksForMultiFlavoredBuilds(array, 0, map, densities, abis, signingOverride);
            }
        }

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
     * in extension.flavorDimensionList), creating all possible combination.
     *  @param datas the arrays to fill
     * @param index the current index to fill
     * @param map the map of dimension -> list(ProductFlavor)
     * @param densities the list of density-specific apk to generate. null means universal apk.
     * @param abis the list of abi-specific apk to generate. null means universal apk.
     * @param signingOverride a signing override. Generally driven through the IDE.
     */
    private void createTasksForMultiFlavoredBuilds(
            @NonNull ProductFlavorData[] datas,
            int index,
            @NonNull ListMultimap<String, ? extends ProductFlavorData> map,
            @NonNull Set<String> densities,
            @NonNull Set<String> abis,
            @Nullable SigningConfig signingOverride) {
        if (index == datas.length) {
            createTasksForFlavoredBuild(densities, abis, signingOverride, datas);
            return;
        }

        // fill the array at the current index.
        // get the dimension name that matches the index we are filling.
        String dimension = extension.getFlavorDimensionList().get(index);

        // from our map, get all the possible flavors in that dimension.
        List<? extends ProductFlavorData> flavorList = map.get(dimension);

        // loop on all the flavors to add them to the current index and recursively fill the next
        // indices.
        for (ProductFlavorData flavor : flavorList) {
            datas[index] = flavor;
            createTasksForMultiFlavoredBuilds(datas, index + 1, map,
                    densities, abis, signingOverride);
        }
    }

    /**
     * Creates Tasks for non-flavored build. This means assembleDebug, assembleRelease, and other
     * assemble<Type> are directly building the <type> build instead of all build of the given
     * <type>.
     *
     * @param densities the list of density-specific apk to generate. null means universal apk.
     * @param abis the list of abi-specific apk to generate. null means universal apk.
     * @param signingOverride a signing override. Generally driven through the IDE.
     */
    private void createTasksForDefaultBuild(
            @NonNull Set<String> densities,
            @NonNull Set<String> abis,
            @Nullable SigningConfig signingOverride) {
        BuildTypeData testData = buildTypes.get(extension.getTestBuildType());
        if (testData == null) {
            throw new RuntimeException(String.format(
                    "Test Build Type '%1$s' does not exist.", extension.getTestBuildType()));
        }

        BaseVariantData<?> testedVariantData = null;

        ProductFlavorData defaultConfigData = basePlugin.getDefaultConfigData();

        DefaultProductFlavor defaultConfig = defaultConfigData.getProductFlavor();
        DefaultAndroidSourceSet defaultConfigSourceSet = defaultConfigData.getSourceSet();

        Closure<Void> variantFilterClosure = basePlugin.getExtension().getVariantFilter();

        for (BuildTypeData buildTypeData : buildTypes.values()) {
            boolean ignore = false;
            if (variantFilterClosure != null) {
                variantFilter.reset(defaultConfig, buildTypeData.getBuildType(), null);
                variantFilterClosure.call(variantFilter);
                ignore = variantFilter.isIgnore();
            }

            if (!ignore) {
                VariantConfiguration variantConfig = new VariantConfiguration(
                        defaultConfig,
                        defaultConfigSourceSet,
                        buildTypeData.getBuildType(),
                        buildTypeData.getSourceSet(),
                        variantFactory.getVariantConfigurationType(),
                        signingOverride);

                // create the variant, and outputs and get its internal storage object.
                BaseVariantData<?> variantData = variantFactory.createVariantData(variantConfig, densities, abis);

                // create its dependencies. They'll be resolved below.
                VariantDependencies variantDep = VariantDependencies.compute(
                        project, variantConfig.getFullName(),
                        isVariantPublished(),
                        variantFactory.isLibrary(),
                        buildTypeData, defaultConfigData.getMainProvider());
                variantData.setVariantDependency(variantDep);

                if (buildTypeData == testData) {
                    testedVariantData = variantData;
                }

                basePlugin.resolveDependencies(variantDep);
                variantConfig.setDependencies(variantDep);

                basePlugin.getVariantDataList().add(variantData);
                variantFactory.createTasks(variantData,
                        buildTypes.get(variantConfig.getBuildType().getName()).getAssembleTask());
            }
        }

        if (testedVariantData != null) {
            VariantConfiguration testedConfig = testedVariantData.getVariantConfiguration();
            // handle the test variant
            VariantConfiguration testVariantConfig = new VariantConfiguration(
                    defaultConfig,
                    defaultConfigData.getTestSourceSet(),
                    testData.getBuildType(),
                    null,
                    VariantConfiguration.Type.TEST, testedConfig,
                    signingOverride);

            // create the internal storage for this test variant.
            TestVariantData testVariantData = new TestVariantData(
                    basePlugin, testVariantConfig, (TestedVariantData) testedVariantData);

            // link the testVariant to the tested variant in the other direction
            ((TestedVariantData) testedVariantData).setTestVariantData(testVariantData);

            // dependencies for the test variant, they'll be resolved below
            VariantDependencies variantDep = VariantDependencies.compute(
                    project, testVariantConfig.getFullName(),
                    false /*publishVariant*/,
                    variantFactory.isLibrary(),
                    defaultConfigData.getTestProvider(),
                    testedConfig.getType() == VariantConfiguration.Type.LIBRARY ?
                            testedVariantData.getVariantDependency() : null);
            testVariantData.setVariantDependency(variantDep);

            basePlugin.resolveDependencies(variantDep);
            testVariantConfig.setDependencies(variantDep);

            basePlugin.getVariantDataList().add(testVariantData);
            basePlugin.createTestApkTasks(testVariantData, testedVariantData);
        }
    }

    /**
     * Creates Task for a given flavor. This will create tasks for all build types for the given
     * flavor.
     *
     * @param densities the list of density-specific apk to generate. null means universal apk.
     * @param abis the list of abi-specific apk to generate. null means universal apk.
     * @param signingOverride a signing override. Generally driven through the IDE.
     * @param flavorDataList the flavor(s) to build.
     */
    private void createTasksForFlavoredBuild(
            @NonNull Set<String> densities,
            @NonNull Set<String> abis,
            @Nullable SigningConfig signingOverride,
            @NonNull ProductFlavorData... flavorDataList) {

        BuildTypeData testData = buildTypes.get(extension.getTestBuildType());
        if (testData == null) {
            throw new RuntimeException(String.format(
                    "Test Build Type '%1$s' does not exist.", extension.getTestBuildType()));
        }

        // because this method is called multiple times, we need to keep track
        // of the variantData only for this call.
        final List<BaseVariantData  > localVariantDataList = Lists.newArrayListWithCapacity(buildTypes.size());

        BaseVariantData testedVariantData = null;

        // assembleTask for this flavor(dimension), created on demand if needed.
        Task assembleTask = null;

        ProductFlavorData defaultConfigData = basePlugin.getDefaultConfigData();
        DefaultProductFlavor defaultConfig = defaultConfigData.getProductFlavor();
        DefaultAndroidSourceSet defaultConfigSourceSet = defaultConfigData.getSourceSet();

        final List<ConfigurationProvider> variantProviders = Lists.newArrayListWithCapacity(flavorDataList.length + 2);

        Closure<Void> variantFilterClosure = basePlugin.getExtension().getVariantFilter();
        final List<ProductFlavor> productFlavorList = (variantFilterClosure != null) ? Lists.<ProductFlavor>newArrayListWithCapacity(flavorDataList.length) : null;

        for (BuildTypeData buildTypeData : buildTypes.values()) {
            boolean ignore = false;
            if (variantFilterClosure != null) {
                productFlavorList.clear();
                for (ProductFlavorData data : flavorDataList) {
                    productFlavorList.add(data.getProductFlavor());
                }
                variantFilter.reset(defaultConfig, buildTypeData.getBuildType(), productFlavorList);
                variantFilterClosure.call(variantFilter);
                ignore = variantFilter.isIgnore();
            }

            if (!ignore) {
                if (assembleTask == null && flavorDataList.length > 1) {
                    assembleTask = createAssembleTask(flavorDataList);
                    project.getTasks().getByName("assemble").dependsOn(assembleTask);
                }

                /// add the container of dependencies
                // the order of the libraries is important. In descending order:
                // build types, flavors, defaultConfig.
                variantProviders.clear();
                variantProviders.add(buildTypeData);

                VariantConfiguration variantConfig = new VariantConfiguration(
                        defaultConfig,
                        defaultConfigSourceSet,
                        buildTypeData.getBuildType(),
                        buildTypeData.getSourceSet(),
                        variantFactory.getVariantConfigurationType(),
                        signingOverride);

                for (ProductFlavorData data : flavorDataList) {
                    String dimensionName = "";
                    DefaultProductFlavor productFlavor = data.getProductFlavor();

                    if (productFlavor instanceof GroupableProductFlavorDsl) {
                        dimensionName = ((GroupableProductFlavorDsl) productFlavor).getFlavorDimension();
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
                BaseVariantData<?> variantData = variantFactory.createVariantData(variantConfig,
                        densities, abis);
                // get its single output (for now)
                BaseVariantOutputData variantOutputData = variantData.getOutputs().get(0);

                NamedDomainObjectContainer<AndroidSourceSet> sourceSetsContainer = extension
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
                        isVariantPublished(),
                        variantFactory.isLibrary(),
                        variantProviders.toArray(new ConfigurationProvider[variantProviders.size()]));
                variantData.setVariantDependency(variantDep);

                localVariantDataList.add(variantData);

                if (buildTypeData == testData) {
                    testedVariantData = variantData;
                }

                basePlugin.resolveDependencies(variantDep);
                variantConfig.setDependencies(variantDep);

                basePlugin.getVariantDataList().add(variantData);
                variantFactory.createTasks(variantData, null);

                // setup the task dependencies
                // build type
                buildTypeData.getAssembleTask().dependsOn(variantOutputData.assembleTask);
                // each flavor
                for (ProductFlavorData data : flavorDataList) {
                    data.getAssembleTask().dependsOn(variantOutputData.assembleTask);
                }

                // flavor combo
                if (assembleTask != null) {
                    assembleTask.dependsOn(variantOutputData.assembleTask);
                }
            }
        }

        if (testedVariantData != null) {
            VariantConfiguration testedConfig = testedVariantData.getVariantConfiguration();

            // handle test variant
            VariantConfiguration testVariantConfig = new VariantConfiguration(
                    defaultConfig,
                    defaultConfigData.getTestSourceSet(),
                    testData.getBuildType(),
                    null,
                    VariantConfiguration.Type.TEST,
                    testedVariantData.getVariantConfiguration(),
                    signingOverride);

            /// add the container of dependencies
            // the order of the libraries is important. In descending order:
            // flavors, defaultConfig. No build type for tests
            List<ConfigurationProvider> testVariantProviders = Lists.newArrayListWithExpectedSize(1 + flavorDataList.length);

            for (ProductFlavorData data : flavorDataList) {
                String dimensionName = "";
                DefaultProductFlavor productFlavor = data.getProductFlavor();

                if (productFlavor instanceof GroupableProductFlavorDsl) {
                    dimensionName = ((GroupableProductFlavorDsl) productFlavor).getFlavorDimension();
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
            TestVariantData testVariantData = new TestVariantData(
                    basePlugin, testVariantConfig, (TestedVariantData) testedVariantData);
            localVariantDataList.add(testVariantData);
            // link the testVariant to the tested variant in the other direction
            ((TestedVariantData) testedVariantData).setTestVariantData(testVariantData);

            if (testedConfig.getType() == VariantConfiguration.Type.LIBRARY) {
                testVariantProviders.add(testedVariantData.getVariantDependency());
            }

            // dependencies for the test variant
            VariantDependencies variantDep = VariantDependencies.compute(
                    project, testVariantData.getVariantConfiguration().getFullName(),
                    false /*publishVariant*/,
                    variantFactory.isLibrary(),
                    testVariantProviders.toArray(new ConfigurationProvider[testVariantProviders.size()]));
            testVariantData.setVariantDependency(variantDep);

            basePlugin.resolveDependencies(variantDep);
            testVariantConfig.setDependencies(variantDep);

            basePlugin.getVariantDataList().add(testVariantData);
            basePlugin.createTestApkTasks(testVariantData,
                    (BaseVariantData<?>) testVariantData.getTestedVariantData());
        }
    }

    @NonNull
    private Task createAssembleTask(ProductFlavorData[] flavorDataList) {
        String name = ProductFlavorData.getFlavoredName(flavorDataList, true);

        Task assembleTask = project.getTasks().create("assemble" + name);
        assembleTask.setDescription("Assembles all builds for flavor combination: " + name);
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

            if (variantData instanceof TestVariantData) {
                TestVariantData testVariantData = (TestVariantData) variantData;
                createVariantApiObjects(
                        map,
                        (BaseVariantData) testVariantData.getTestedVariantData(),
                        testVariantData);
            } else {
                createVariantApiObjects(
                        map,
                        variantData,
                        ((TestedVariantData) variantData).getTestVariantData());
            }
        }
    }

    private boolean isVariantPublished() {
        return extension.getPublishNonDefault();
    }

    private void createVariantApiObjects(
            @NonNull Map<BaseVariantData, BaseVariant> map,
            @NonNull BaseVariantData<?> variantData,
            @Nullable TestVariantData testVariantData) {
        BaseVariant variantApi = variantFactory.createVariantApi(variantData);

        TestVariantImpl testVariant = null;
        if (testVariantData != null) {
            testVariant = basePlugin.getInstantiator().newInstance(TestVariantImpl.class, testVariantData, basePlugin);

            // add the test output.
            ApplicationVariantFactory.createApkOutputApiObjects(basePlugin, testVariantData, testVariant);
        }

        if (testVariant != null) {
            ((TestedVariant) variantApi).setTestVariant(testVariant);
            testVariant.setTestedVariant(variantApi);
        }

        extension.addVariant(variantApi);
        map.put(variantData, variantApi);

        if (testVariant != null) {
            extension.addTestVariant(testVariant);
            map.put(testVariantData, testVariant);
        }
    }

    private static void checkName(@NonNull String name, @NonNull String displayName) {
        if (name.startsWith(ANDROID_TEST)) {
            throw new RuntimeException(String.format(
                    "%1$s names cannot start with '%2$s'", displayName, ANDROID_TEST));
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
