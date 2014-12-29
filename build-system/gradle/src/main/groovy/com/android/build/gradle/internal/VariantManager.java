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

import static com.android.build.OutputFile.NO_FILTER;
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
import com.android.build.gradle.internal.ProductFlavorData.ConfigurationProviderImpl;
import com.android.build.gradle.internal.api.DefaultAndroidSourceSet;
import com.android.build.gradle.internal.api.ReadOnlyObjectProvider;
import com.android.build.gradle.internal.api.TestVariantImpl;
import com.android.build.gradle.internal.api.TestedVariant;
import com.android.build.gradle.internal.api.VariantFilter;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.dependency.VariantDependencies;
import com.android.build.gradle.internal.dsl.BuildType;
import com.android.build.gradle.internal.dsl.GroupableProductFlavor;
import com.android.build.gradle.internal.dsl.ProductFlavor;
import com.android.build.gradle.internal.dsl.SigningConfig;
import com.android.build.gradle.internal.dsl.Splits;
import com.android.build.gradle.internal.variant.ApplicationVariantFactory;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.build.gradle.internal.variant.TestVariantData;
import com.android.build.gradle.internal.variant.TestedVariantData;
import com.android.build.gradle.internal.variant.VariantFactory;
import com.android.builder.core.VariantConfiguration;
import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.tasks.TaskContainer;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import groovy.lang.Closure;

/**
 * Class to create, manage variants.
 */
public class VariantManager implements VariantModel {

    protected static final String COM_ANDROID_SUPPORT_MULTIDEX
            = "com.android.support:multidex:1.0.0";

    @NonNull
    private final Project project;
    @NonNull
    private final BasePlugin basePlugin;
    @NonNull
    private final BaseExtension extension;
    @NonNull
    private final VariantFactory variantFactory;

    @NonNull
    private final Map<String, BuildTypeData> buildTypes = Maps.newHashMap();
    @NonNull
    private final Map<String, ProductFlavorData<GroupableProductFlavor>> productFlavors = Maps.newHashMap();
    @NonNull
    private final Map<String, SigningConfig> signingConfigs = Maps.newHashMap();

    @NonNull
    private final ReadOnlyObjectProvider readOnlyObjectProvider = new ReadOnlyObjectProvider();
    @NonNull
    private final VariantFilter variantFilter = new VariantFilter(readOnlyObjectProvider);

    @NonNull
    private final List<BaseVariantData<? extends BaseVariantOutputData>> variantDataList = Lists.newArrayList();

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
    @Override
    public ProductFlavorData<ProductFlavor> getDefaultConfig() {
        return basePlugin.getDefaultConfigData();
    }

    @Override
    @NonNull
    public Map<String, BuildTypeData> getBuildTypes() {
        return buildTypes;
    }

    @Override
    @NonNull
    public Map<String, ProductFlavorData<GroupableProductFlavor>> getProductFlavors() {
        return productFlavors;
    }

    @Override
    @NonNull
    public Map<String, SigningConfig> getSigningConfigs() {
        return signingConfigs;
    }

    public void addSigningConfig(@NonNull SigningConfig signingConfig) {
        signingConfigs.put(signingConfig.getName(), signingConfig);
    }

    /**
     * Adds new BuildType, creating a BuildTypeData, and the associated source set,
     * and adding it to the map.
     * @param buildType the build type.
     */
    public void addBuildType(@NonNull BuildType buildType) {
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
    public void addProductFlavor(@NonNull GroupableProductFlavor productFlavor) {
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

        ProductFlavorData<GroupableProductFlavor> productFlavorData =
                new ProductFlavorData<GroupableProductFlavor>(
                        productFlavor, mainSourceSet, testSourceSet, project);

        productFlavors.put(productFlavor.getName(), productFlavorData);
    }

    /**
     * Return a list of all created VariantData.
     */
    @NonNull
    public List<BaseVariantData<? extends BaseVariantOutputData>> getVariantDataList() {
        return variantDataList;
    }

    /**
     * Task creation entry point.
     */
    public void createAndroidTasks(@Nullable com.android.builder.model.SigningConfig signingOverride) {
        variantFactory.validateModel(this);

        if (!productFlavors.isEmpty()) {
            // there'll be more than one test app, so we need a top level assembleTest
            Task assembleTest = project.getTasks().create("assembleTest");
            assembleTest.setGroup(org.gradle.api.plugins.BasePlugin.BUILD_GROUP);
            assembleTest.setDescription("Assembles all the Test applications");
            basePlugin.setAssembleTest(assembleTest);
        }

        if (variantDataList.isEmpty()) {
            populateVariantDataList(signingOverride);
        }

        for (BaseVariantData variantData : variantDataList) {
            createTasksForVariantData(project.getTasks(), variantData);
        }

        // create the lint tasks.
        basePlugin.createLintTasks();

        // create the test tasks.
        basePlugin.createCheckTasks(!productFlavors.isEmpty(), false /*isLibrary*/);

        // Create the variant API objects after the tasks have been created!
        createApiObjects();
    }

    /**
     * Create tasks for the specified variantData.
     */
    public void createTasksForVariantData(TaskContainer tasks, BaseVariantData variantData) {
        if (variantData.getVariantConfiguration().getType()
                == GradleVariantConfiguration.Type.TEST) {
            GradleVariantConfiguration testVariantConfig = variantData.getVariantConfiguration();
            BaseVariantData testedVariantData = (BaseVariantData) ((TestVariantData) variantData)
                    .getTestedVariantData();

            /// add the container of dependencies
            // the order of the libraries is important. In descending order:
            // flavors, defaultConfig. No build type for tests
            List<ConfigurationProvider> testVariantProviders = Lists.newArrayListWithExpectedSize(
                    2 + testVariantConfig.getProductFlavors().size());

            for (com.android.build.gradle.api.GroupableProductFlavor productFlavor : testVariantConfig.getProductFlavors()) {
                ProductFlavorData<GroupableProductFlavor> data = productFlavors.get(productFlavor.getName());
                testVariantProviders.add(data.getTestProvider());
            }

            // now add the default config
            testVariantProviders.add(basePlugin.getDefaultConfigData().getTestProvider());

            assert(testVariantConfig.getTestedConfig() != null);
            if (testVariantConfig.getTestedConfig().getType() == VariantConfiguration.Type.LIBRARY) {
                testVariantProviders.add(testedVariantData.getVariantDependency());
            }

            // If the variant being tested is a library variant, VariantDependencies must be
            // computed the tasks for the tested variant is created.  Therefore, the
            // VariantDependencies is computed here instead of when the VariantData was created.
            VariantDependencies variantDep = VariantDependencies.compute(
                    project, testVariantConfig.getFullName(),
                    false /*publishVariant*/,
                    variantFactory.isLibrary(),
                    testVariantProviders.toArray(
                            new ConfigurationProvider[testVariantProviders.size()]));
            variantData.setVariantDependency(variantDep);

            basePlugin.resolveDependencies(variantDep);
            testVariantConfig.setDependencies(variantDep);
            basePlugin.createTestApkTasks((TestVariantData) variantData);
        } else {
            if (productFlavors.isEmpty()) {
                variantFactory.createTasks(
                        variantData,
                        buildTypes.get(
                                variantData.getVariantConfiguration().getBuildType().getName())
                                .getAssembleTask());
            } else {
                variantFactory.createTasks(variantData, null);

                // setup the task dependencies
                // build type
                buildTypes.get(variantData.getVariantConfiguration().getBuildType().getName())
                        .getAssembleTask().dependsOn(variantData.assembleVariantTask);

                // each flavor
                GradleVariantConfiguration variantConfig = variantData.getVariantConfiguration();
                for (GroupableProductFlavor flavor : variantConfig.getProductFlavors()) {
                    productFlavors.get(flavor.getName()).getAssembleTask()
                            .dependsOn(variantData.assembleVariantTask);
                }

                Task assembleTask = null;
                // assembleTask for this flavor(dimension), created on demand if needed.
                if (variantConfig.getProductFlavors().size() > 1) {
                    String name = StringHelper.capitalize(variantConfig.getFlavorName());
                    assembleTask = tasks.findByName("assemble" + name);
                    if (assembleTask == null) {
                        assembleTask = tasks.create("assemble" + name);
                        assembleTask.setDescription(
                                "Assembles all builds for flavor combination: " + name);
                        assembleTask.setGroup("Build");

                        tasks.getByName("assemble").dependsOn(assembleTask);
                    }
                }
                // flavor combo
                if (assembleTask != null) {
                    assembleTask.dependsOn(variantData.assembleVariantTask);
                }
            }
        }
    }

    /**
     * Create all variants.
     *
     * @param signingOverride a signing override. Generally driven through the IDE.
     */
    public void populateVariantDataList(@Nullable com.android.builder.model.SigningConfig signingOverride) {
        // Add a compile lint task
        basePlugin.createLintCompileTask();

        if (productFlavors.isEmpty()) {
            createVariantDataForProductFlavors(signingOverride,
                    Collections.<com.android.build.gradle.api.GroupableProductFlavor>emptyList());
        } else {
            List<String> flavorDimensionList = extension.getFlavorDimensionList();

            // Create iterable to get GroupableProductFlavor from ProductFlavorData.
            Iterable<GroupableProductFlavor> flavorDsl =
                    Iterables.transform(
                            productFlavors.values(),
                            new Function<ProductFlavorData<GroupableProductFlavor>, GroupableProductFlavor>() {
                                @Override
                                public GroupableProductFlavor apply(
                                        ProductFlavorData<GroupableProductFlavor> data) {
                                    return data.getProductFlavor();
                                }
                            });

            // Get a list of all combinations of product flavors.
            List<ProductFlavorCombo> flavorComboList = ProductFlavorCombo.createCombinations(
                    flavorDimensionList,
                    flavorDsl);

            for (ProductFlavorCombo flavorCombo : flavorComboList) {
                createVariantDataForProductFlavors(signingOverride, flavorCombo.getFlavorList());
            }
        }
    }

    /**
     * Create a VariantData for a specific combination of BuildType and GroupableProductFlavor list.
     */
    public BaseVariantData<? extends BaseVariantOutputData> createVariantData(
            @NonNull com.android.builder.model.BuildType buildType,
            @NonNull List<com.android.build.gradle.api.GroupableProductFlavor> productFlavorList,
            @Nullable com.android.builder.model.SigningConfig signingOverride) {
        Splits splits = basePlugin.getExtension().getSplits();
        Set<String> densities = splits.getDensityFilters();
        Set<String> abis = splits.getAbiFilters();

        // check against potentially empty lists. We always need to generate at least one output
        densities = densities.isEmpty() ? Collections.singleton(NO_FILTER) : densities;
        abis = abis.isEmpty() ? Collections.singleton(NO_FILTER) : abis;

        ProductFlavorData<ProductFlavor> defaultConfigData = basePlugin.getDefaultConfigData();

        ProductFlavor defaultConfig = defaultConfigData.getProductFlavor();
        DefaultAndroidSourceSet defaultConfigSourceSet = defaultConfigData.getSourceSet();

        BuildTypeData buildTypeData = buildTypes.get(buildType.getName());

        Set<String> compatibleScreens = basePlugin.getExtension().getSplits().getDensity()
                .getCompatibleScreens();

        GradleVariantConfiguration variantConfig = new GradleVariantConfiguration(
                defaultConfig,
                defaultConfigSourceSet,
                buildTypeData.getBuildType(),
                buildTypeData.getSourceSet(),
                variantFactory.getVariantConfigurationType(),
                signingOverride);

        // sourceSetContainer in case we are creating variant specific sourceSets.
        NamedDomainObjectContainer<AndroidSourceSet> sourceSetsContainer = extension
                .getSourceSetsContainer();

        // We must first add the flavors to the variant config, in order to get the proper
        // variant-specific and multi-flavor name as we add/create the variant providers later.
        for (com.android.build.gradle.api.GroupableProductFlavor productFlavor : productFlavorList) {
            ProductFlavorData<GroupableProductFlavor> data = productFlavors.get(
                    productFlavor.getName());

            String dimensionName = productFlavor.getFlavorDimension();
            if (dimensionName == null) {
                dimensionName = "";
            }

            variantConfig.addProductFlavor(
                    data.getProductFlavor(),
                    data.getSourceSet(),
                    dimensionName);
        }

        // Add the container of dependencies.
        // The order of the libraries is important, in descending order:
        // variant-specific, build type, multi-flavor, flavor1, flavor2, ..., defaultConfig.
        // variant-specific if the full combo of flavors+build type. Does not exist if no flavors.
        // multi-flavor is the combination of all flavor dimensions. Does not exist if <2 dimension.

        final List<ConfigurationProvider> variantProviders =
                Lists.newArrayListWithExpectedSize(productFlavorList.size() + 4);

        // 1. add the variant-specific if applicable.
        if (!productFlavorList.isEmpty()) {
            DefaultAndroidSourceSet variantSourceSet =
                    (DefaultAndroidSourceSet) sourceSetsContainer.maybeCreate(
                            variantConfig.getFullName());
            variantConfig.setVariantSourceProvider(variantSourceSet);
            variantProviders.add(new ConfigurationProviderImpl(project, variantSourceSet));
        }

        // 2. the build type.
        variantProviders.add(buildTypeData);

        // 3. the multi-flavor combination
        if (productFlavorList.size() > 1) {
            DefaultAndroidSourceSet multiFlavorSourceSet =
                    (DefaultAndroidSourceSet) sourceSetsContainer.maybeCreate(
                            variantConfig.getFlavorName());
            variantConfig.setMultiFlavorSourceProvider(multiFlavorSourceSet);
            variantProviders.add(new ConfigurationProviderImpl(project, multiFlavorSourceSet));
        }

        // 4. the flavors.
        for (com.android.build.gradle.api.GroupableProductFlavor productFlavor : productFlavorList) {
            variantProviders.add(productFlavors.get(productFlavor.getName()).getMainProvider());
        }

        // 5. The defaultConfig
        variantProviders.add(defaultConfigData.getMainProvider());

        // Done. Create the variant and get its internal storage object.
        BaseVariantData<?> variantData = variantFactory.createVariantData(variantConfig,
                densities, abis, compatibleScreens);

        VariantDependencies variantDep = VariantDependencies.compute(
                project, variantConfig.getFullName(),
                isVariantPublished(),
                variantFactory.isLibrary(),
                variantProviders.toArray(new ConfigurationProvider[variantProviders.size()]));
        variantData.setVariantDependency(variantDep);

        if (variantConfig.isMultiDexEnabled() && variantConfig.isLegacyMultiDexMode()) {
            project.getDependencies().add(
                    variantDep.getCompileConfiguration().getName(), COM_ANDROID_SUPPORT_MULTIDEX);
            project.getDependencies().add(
                    variantDep.getPackageConfiguration().getName(), COM_ANDROID_SUPPORT_MULTIDEX);
        }

        basePlugin.resolveDependencies(variantDep);
        variantConfig.setDependencies(variantDep);

        return variantData;
    }

    /**
     * Create a TestVariantData for the specified testedVariantData.
     */
    public TestVariantData createTestVariantData(
            BaseVariantData testedVariantData,
            com.android.builder.model.SigningConfig signingOverride) {
        BuildTypeData testData = buildTypes.get(extension.getTestBuildType());

        ProductFlavorData<ProductFlavor> defaultConfigData = basePlugin.getDefaultConfigData();
        ProductFlavor defaultConfig = defaultConfigData.getProductFlavor();

        GradleVariantConfiguration testedConfig = testedVariantData.getVariantConfiguration();
        List<? extends com.android.build.gradle.api.GroupableProductFlavor> productFlavorList = testedConfig.getProductFlavors();

        // handle test variant
        GradleVariantConfiguration testVariantConfig = new GradleVariantConfiguration(
                defaultConfig,
                defaultConfigData.getTestSourceSet(),
                testData.getBuildType(),
                null,
                VariantConfiguration.Type.TEST,
                testedVariantData.getVariantConfiguration(),
                signingOverride);

        for (com.android.build.gradle.api.GroupableProductFlavor productFlavor : productFlavorList) {
            ProductFlavorData<GroupableProductFlavor> data = productFlavors
                    .get(productFlavor.getName());

            String dimensionName = productFlavor.getFlavorDimension();
            if (dimensionName == null) {
                dimensionName = "";
            }
            testVariantConfig.addProductFlavor(
                    data.getProductFlavor(),
                    data.getTestSourceSet(),
                    dimensionName);
        }

        // create the internal storage for this variant.
        TestVariantData testVariantData = new TestVariantData(
                basePlugin, testVariantConfig, (TestedVariantData) testedVariantData);
        // link the testVariant to the tested variant in the other direction
        ((TestedVariantData) testedVariantData).setTestVariantData(testVariantData);

        return testVariantData;
    }

    /**
     * Creates VariantData for a specified list of product flavor.
     *
     * This will create VariantData for all build types of the given flavors.
     *
     * @param signingOverride a signing override. Generally driven through the IDE.
     * @param productFlavorList the flavor(s) to build.
     */
    private void createVariantDataForProductFlavors(
            @Nullable com.android.builder.model.SigningConfig signingOverride,
            @NonNull List<com.android.build.gradle.api.GroupableProductFlavor> productFlavorList) {
        BuildTypeData testData = buildTypes.get(extension.getTestBuildType());
        if (testData == null) {
            throw new RuntimeException(String.format(
                    "Test Build Type '%1$s' does not exist.", extension.getTestBuildType()));
        }

        BaseVariantData testedVariantData = null;

        ProductFlavorData<ProductFlavor> defaultConfigData = basePlugin.getDefaultConfigData();
        ProductFlavor defaultConfig = defaultConfigData.getProductFlavor();

        Closure<Void> variantFilterClosure = basePlugin.getExtension().getVariantFilter();

        for (BuildTypeData buildTypeData : buildTypes.values()) {
            boolean ignore = false;
            if (variantFilterClosure != null) {
                variantFilter.reset(defaultConfig, buildTypeData.getBuildType(), productFlavorList);
                variantFilterClosure.call(variantFilter);
                ignore = variantFilter.isIgnore();
            }

            if (!ignore) {
                BaseVariantData<?> variantData = createVariantData(
                        buildTypeData.getBuildType(),
                        productFlavorList,
                        signingOverride);
                variantDataList.add(variantData);

                if (buildTypeData == testData) {
                    GradleVariantConfiguration variantConfig = variantData.getVariantConfiguration();
                    if (variantConfig.isMinifyEnabled() && variantConfig.getUseJack()) {
                        throw new RuntimeException("Cannot test obfuscated variants when compiling with jack.");
                    }
                    testedVariantData = variantData;
                }
            }
        }

        if (testedVariantData != null) {
            TestVariantData testVariantData =
                    createTestVariantData(testedVariantData, signingOverride);
            variantDataList.add(testVariantData);
        }
    }

    private void createApiObjects() {
        // we always want to have the test/tested objects created at the same time
        // so that dynamic closure call on add can have referenced objects created.
        // This means some objects are created before they are processed from the loop,
        // so we store whether we have processed them or not.
        Map<BaseVariantData, BaseVariant> map = Maps.newHashMap();
        for (BaseVariantData variantData : variantDataList) {
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
        BaseVariant variantApi = variantFactory.createVariantApi(variantData,
                readOnlyObjectProvider);

        TestVariantImpl testVariant = null;
        if (testVariantData != null) {
            testVariant = basePlugin.getInstantiator().newInstance(
                    TestVariantImpl.class, testVariantData, basePlugin, readOnlyObjectProvider);

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
