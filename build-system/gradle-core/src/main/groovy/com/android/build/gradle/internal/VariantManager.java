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
import static com.android.builder.core.BuilderConstants.DEBUG;
import static com.android.builder.core.BuilderConstants.LINT;
import static com.android.builder.core.VariantType.ANDROID_TEST;
import static com.android.builder.core.VariantType.UNIT_TEST;
import static com.android.builder.model.AndroidProject.PROPERTY_SIGNING_KEY_ALIAS;
import static com.android.builder.model.AndroidProject.PROPERTY_SIGNING_KEY_PASSWORD;
import static com.android.builder.model.AndroidProject.PROPERTY_SIGNING_STORE_FILE;
import static com.android.builder.model.AndroidProject.PROPERTY_SIGNING_STORE_PASSWORD;
import static com.android.builder.model.AndroidProject.PROPERTY_SIGNING_STORE_TYPE;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.BaseExtension;
import com.android.build.gradle.TestedExtension;
import com.android.build.gradle.api.AndroidSourceSet;
import com.android.build.gradle.api.BaseVariant;
import com.android.build.gradle.internal.ProductFlavorData.ConfigurationProviderImpl;
import com.android.build.gradle.internal.api.DefaultAndroidSourceSet;
import com.android.build.gradle.internal.api.ReadOnlyObjectProvider;
import com.android.build.gradle.internal.api.TestVariantImpl;
import com.android.build.gradle.internal.api.TestedVariant;
import com.android.build.gradle.internal.api.UnitTestVariantImpl;
import com.android.build.gradle.internal.api.VariantFilter;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.dependency.VariantDependencies;
import com.android.build.gradle.internal.dsl.BuildType;
import com.android.build.gradle.internal.dsl.GroupableProductFlavor;
import com.android.build.gradle.internal.dsl.ProductFlavor;
import com.android.build.gradle.internal.dsl.SigningConfig;
import com.android.build.gradle.internal.dsl.Splits;
import com.android.build.gradle.internal.profile.SpanRecorders;
import com.android.build.gradle.internal.variant.ApplicationVariantFactory;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.build.gradle.internal.variant.TestVariantData;
import com.android.build.gradle.internal.variant.TestedVariantData;
import com.android.build.gradle.internal.variant.VariantFactory;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.VariantType;
import com.android.builder.profile.ExecutionType;
import com.android.builder.profile.Recorder;
import com.android.builder.profile.ThreadRecorder;
import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.internal.reflect.Instantiator;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import groovy.lang.Closure;

/**
 * Class to create, manage variants.
 */
public class VariantManager implements VariantModel {

    protected static final String COM_ANDROID_SUPPORT_MULTIDEX =
            "com.android.support:multidex:1.0.0";

    @NonNull
    private final Project project;
    @NonNull
    private final AndroidBuilder androidBuilder;
    @NonNull
    private final BaseExtension extension;
    @NonNull
    private final VariantFactory variantFactory;
    @NonNull
    private final TaskManager taskManager;
    @NonNull
    private final Instantiator instantiator;
    @NonNull
    private ProductFlavorData<ProductFlavor> defaultConfigData;
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
    @Nullable
    private SigningConfig signingOverride;

    public VariantManager(
            @NonNull Project project,
            @NonNull AndroidBuilder androidBuilder,
            @NonNull BaseExtension extension,
            @NonNull VariantFactory variantFactory,
            @NonNull TaskManager taskManager,
            @NonNull Instantiator instantiator) {
        this.extension = extension;
        this.androidBuilder = androidBuilder;
        this.project = project;
        this.variantFactory = variantFactory;
        this.taskManager = taskManager;
        this.instantiator = instantiator;

        DefaultAndroidSourceSet mainSourceSet =
                (DefaultAndroidSourceSet) extension.getSourceSets().getByName(extension.getDefaultConfig().getName());

        DefaultAndroidSourceSet androidTestSourceSet = null;
        DefaultAndroidSourceSet unitTestSourceSet = null;
        if (variantFactory.hasTestScope()) {
            androidTestSourceSet =
                    (DefaultAndroidSourceSet) extension.getSourceSets()
                            .getByName(ANDROID_TEST.getPrefix());
            unitTestSourceSet =
                    (DefaultAndroidSourceSet) extension.getSourceSets()
                            .getByName(UNIT_TEST.getPrefix());
        }

        defaultConfigData = new ProductFlavorData<ProductFlavor>(
                extension.getDefaultConfig(), mainSourceSet,
                androidTestSourceSet, unitTestSourceSet, project);
        signingOverride = createSigningOverride();
    }

    @NonNull
    @Override
    public ProductFlavorData<ProductFlavor> getDefaultConfig() {
        return defaultConfigData;
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

        DefaultAndroidSourceSet mainSourceSet = (DefaultAndroidSourceSet) extension.getSourceSetsContainer().maybeCreate(name);

        DefaultAndroidSourceSet unitTestSourceSet = null;
        if (variantFactory.hasTestScope()) {
            unitTestSourceSet = (DefaultAndroidSourceSet) extension
                    .getSourceSetsContainer().maybeCreate(
                            UNIT_TEST.getPrefix() + StringHelper.capitalize(buildType.getName()));
        }

        BuildTypeData buildTypeData = new BuildTypeData(
                buildType, project, mainSourceSet, unitTestSourceSet);
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

        DefaultAndroidSourceSet androidTestSourceSet = null;
        DefaultAndroidSourceSet unitTestSourceSet = null;
        if (variantFactory.hasTestScope()) {
            androidTestSourceSet = (DefaultAndroidSourceSet) extension
                    .getSourceSetsContainer().maybeCreate(
                            ANDROID_TEST.getPrefix() + StringHelper
                                    .capitalize(productFlavor.getName()));
            unitTestSourceSet = (DefaultAndroidSourceSet) extension
                    .getSourceSetsContainer().maybeCreate(
                            UNIT_TEST.getPrefix() + StringHelper
                                    .capitalize(productFlavor.getName()));
        }

        ProductFlavorData<GroupableProductFlavor> productFlavorData =
                new ProductFlavorData<GroupableProductFlavor>(
                        productFlavor,
                        mainSourceSet,
                        androidTestSourceSet,
                        unitTestSourceSet,
                        project);

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
     * Variant/Task creation entry point.
     */
    public void createAndroidTasks() {
        variantFactory.validateModel(this);
        variantFactory.preVariantWork(project);

        final TaskFactory tasks = new TaskContainerAdaptor(project.getTasks());
        if (variantDataList.isEmpty()) {
            populateVariantDataList();
        }

        // Create top level test tasks.
        ThreadRecorder.get().record(ExecutionType.VARIANT_MANAGER_CREATE_TESTS_TASKS,
                new Recorder.Block<Void>() {
                    @Override
                    public Void call() throws Exception {
                        taskManager.createTopLevelTestTasks(tasks, !productFlavors.isEmpty());
                        return null;
                    }
                });

        for (final BaseVariantData<? extends BaseVariantOutputData> variantData : variantDataList) {
            SpanRecorders.record(project, ExecutionType.VARIANT_MANAGER_CREATE_TASKS_FOR_VARIANT,
                    new Recorder.Block<Void>() {
                        @Override
                        public Void call() throws Exception {
                            createTasksForVariantData(tasks, variantData);
                            return null;
                        }
                    },
                    new Recorder.Property(SpanRecorders.VARIANT, variantData.getName()));
        }

        // create the lint tasks.
        ThreadRecorder.get().record(ExecutionType.VARIANT_MANAGER_CREATE_LINT_TASKS,
                new Recorder.Block<Void>() {
                    @Override
                    public Void call() throws Exception {
                        taskManager.createLintTasks(tasks, variantDataList);
                        return null;
                    }
                });

        // Create the variant API objects after the tasks have been created!
        createApiObjects();

        taskManager.createReportTasks(variantDataList);
    }

    /**
     * Create assemble task for VariantData.
     */
    private void createAssembleTaskForVariantData(
            TaskFactory tasks,
            final BaseVariantData<?> variantData) {
        if (variantData.getType().isForTesting()) {
            variantData.assembleVariantTask = taskManager.createAssembleTask(variantData);
        } else {
            BuildTypeData buildTypeData =
                    buildTypes.get(variantData.getVariantConfiguration().getBuildType().getName());

            if (productFlavors.isEmpty()) {
                // Reuse assemble task for build type if there is no product flavor.
                variantData.assembleVariantTask = buildTypeData.getAssembleTask();
            } else {
                variantData.assembleVariantTask = taskManager.createAssembleTask(variantData);

                // setup the task dependencies
                // build type
                buildTypeData.getAssembleTask().dependsOn(variantData.assembleVariantTask);

                // each flavor
                GradleVariantConfiguration variantConfig = variantData.getVariantConfiguration();
                for (GroupableProductFlavor flavor : variantConfig.getProductFlavors()) {
                    productFlavors.get(flavor.getName()).getAssembleTask()
                            .dependsOn(variantData.assembleVariantTask);
                }

                // assembleTask for this flavor(dimension), created on demand if needed.
                if (variantConfig.getProductFlavors().size() > 1) {
                    final String name = StringHelper.capitalize(variantConfig.getFlavorName());
                    final String variantAssembleTaskName = "assemble" + name;
                    if (!tasks.containsKey(variantAssembleTaskName)) {
                        tasks.create(variantAssembleTaskName, new Action<Task>() {
                            @Override
                            public void execute(Task task) {
                                task.setDescription(
                                        "Assembles all builds for flavor combination: " + name);
                                task.setGroup("Build");
                                task.dependsOn(variantData.assembleVariantTask);

                            }
                        });
                    }
                    tasks.named("assemble", new Action<Task>() {
                        @Override
                        public void execute(Task task) {
                            task.dependsOn(variantAssembleTaskName);
                        }
                    });
                }
            }
        }
    }

    /**
     * Create tasks for the specified variantData.
     */
    public void createTasksForVariantData(
            TaskFactory tasks,
            BaseVariantData<? extends BaseVariantOutputData> variantData) {
        VariantType variantType = variantData.getType();

        createAssembleTaskForVariantData(tasks, variantData);
        if (variantType.isForTesting()) {
            final GradleVariantConfiguration testVariantConfig = variantData.getVariantConfiguration();
            final BaseVariantData testedVariantData = (BaseVariantData) ((TestVariantData) variantData)
                    .getTestedVariantData();

            /// add the container of dependencies
            // the order of the libraries is important. In descending order:
            // flavors, defaultConfig. No build type for tests
            List<ConfigurationProvider> testVariantProviders = Lists.newArrayListWithExpectedSize(
                    2 + testVariantConfig.getProductFlavors().size());

            for (com.android.build.gradle.api.GroupableProductFlavor productFlavor :
                    testVariantConfig.getProductFlavors()) {
                ProductFlavorData<GroupableProductFlavor> data =
                        productFlavors.get(productFlavor.getName());
                testVariantProviders.add(data.getTestConfigurationProvider(variantType));
            }

            // now add the default config
            testVariantProviders.add(defaultConfigData.getTestConfigurationProvider(variantType));

            assert(testVariantConfig.getTestedConfig() != null);
            if (testVariantConfig.getTestedConfig().getType() == VariantType.LIBRARY) {
                testVariantProviders.add(testedVariantData.getVariantDependency());
            }

            // If the variant being tested is a library variant, VariantDependencies must be
            // computed after the tasks for the tested variant is created.  Therefore, the
            // VariantDependencies is computed here instead of when the VariantData was created.
            final VariantDependencies variantDep = VariantDependencies.compute(
                    project, testVariantConfig.getFullName(),
                    false /*publishVariant*/,
                    variantType,
                    testVariantProviders.toArray(
                            new ConfigurationProvider[testVariantProviders.size()]));
            variantData.setVariantDependency(variantDep);

            SpanRecorders.record(project, ExecutionType.RESOLVE_DEPENDENCIES,
                    new Recorder.Block<Void>() {
                        @Override
                        public Void call() {
                            taskManager.resolveDependencies(variantDep,
                                    testVariantConfig.getTestedConfig().getType() == VariantType.LIBRARY
                                            ? null
                                            : testedVariantData.getVariantDependency());
                            return null;
                        }
                    },
                    new Recorder.Property(SpanRecorders.VARIANT, testVariantConfig.getFullName()));
            testVariantConfig.setDependencies(variantDep);
            switch (variantType) {
                case ANDROID_TEST:
                    taskManager.createAndroidTestVariantTasks(tasks, (TestVariantData) variantData);
                    break;
                case UNIT_TEST:
                    taskManager.createUnitTestVariantTasks(tasks, (TestVariantData) variantData);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown test type " + variantType);
            }
        } else {
            taskManager.createTasksForVariantData(tasks, variantData);
        }
    }

    /**
     * Create all variants.
     */
    public void populateVariantDataList() {
        // Add a compile lint task
        taskManager.createLintCompileTask();

        if (productFlavors.isEmpty()) {
            createVariantDataForProductFlavors(
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
                createVariantDataForProductFlavors(flavorCombo.getFlavorList());
            }
        }
    }

    /**
     * Create a VariantData for a specific combination of BuildType and GroupableProductFlavor list.
     */
    public BaseVariantData<? extends BaseVariantOutputData> createVariantData(
            @NonNull com.android.builder.model.BuildType buildType,
            @NonNull List<? extends com.android.build.gradle.api.GroupableProductFlavor> productFlavorList) {
        Splits splits = extension.getSplits();
        Set<String> densities = splits.getDensityFilters();
        Set<String> abis = splits.getAbiFilters();

        // check against potentially empty lists. We always need to generate at least one output
        densities = densities.isEmpty() ? Collections.singleton(NO_FILTER) : densities;
        abis = abis.isEmpty() ? Collections.singleton(NO_FILTER) : abis;

        ProductFlavor defaultConfig = defaultConfigData.getProductFlavor();
        DefaultAndroidSourceSet defaultConfigSourceSet = defaultConfigData.getSourceSet();

        BuildTypeData buildTypeData = buildTypes.get(buildType.getName());

        Set<String> compatibleScreens = extension.getSplits().getDensity()
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
                densities, abis, compatibleScreens, taskManager);

        final VariantDependencies variantDep = VariantDependencies.compute(
                project, variantConfig.getFullName(),
                isVariantPublished(),
                variantData.getType(),
                variantProviders.toArray(new ConfigurationProvider[variantProviders.size()]));
        variantData.setVariantDependency(variantDep);

        if (variantConfig.isMultiDexEnabled() && variantConfig.isLegacyMultiDexMode()) {
            project.getDependencies().add(
                    variantDep.getCompileConfiguration().getName(), COM_ANDROID_SUPPORT_MULTIDEX);
            project.getDependencies().add(
                    variantDep.getPackageConfiguration().getName(), COM_ANDROID_SUPPORT_MULTIDEX);
        }

        SpanRecorders.record(project, ExecutionType.RESOLVE_DEPENDENCIES,
                new Recorder.Block<Void>() {
                    @Override
                    public Void call() {
                        taskManager.resolveDependencies(variantDep, null);
                        return null;
                    }
                }, new Recorder.Property(SpanRecorders.VARIANT, variantConfig.getFullName()));

        variantConfig.setDependencies(variantDep);

        return variantData;
    }

    /**
     * Create a TestVariantData for the specified testedVariantData.
     */
    public TestVariantData createTestVariantData(
            BaseVariantData testedVariantData,
            VariantType type) {
        ProductFlavor defaultConfig = defaultConfigData.getProductFlavor();
        BuildType buildType = testedVariantData.getVariantConfiguration().getBuildType();
        BuildTypeData buildTypeData = buildTypes.get(buildType.getName());

        GradleVariantConfiguration testedConfig = testedVariantData.getVariantConfiguration();
        List<? extends com.android.build.gradle.api.GroupableProductFlavor> productFlavorList = testedConfig.getProductFlavors();

        // handle test variant
        // need a suppress warning because ProductFlavor.getTestSourceSet(type) is annotated
        // to return @Nullable and the constructor is @NonNull on this parameter,
        // but it's never the case on defaultConfigData
        // The constructor does a runtime check on the instances so we should be safe.
        @SuppressWarnings("ConstantConditions")
        GradleVariantConfiguration testVariantConfig = new GradleVariantConfiguration(
                testedVariantData.getVariantConfiguration(),
                defaultConfig,
                defaultConfigData.getTestSourceSet(type),
                buildType,
                buildTypeData.getTestSourceSet(type),
                type,
                signingOverride);

        for (com.android.build.gradle.api.GroupableProductFlavor productFlavor : productFlavorList) {
            ProductFlavorData<GroupableProductFlavor> data = productFlavors
                    .get(productFlavor.getName());

            String dimensionName = productFlavor.getFlavorDimension();
            if (dimensionName == null) {
                dimensionName = "";
            }
            // same supress warning here.
            //noinspection ConstantConditions
            testVariantConfig.addProductFlavor(
                    data.getProductFlavor(),
                    data.getTestSourceSet(type),
                    dimensionName);
        }

        // create the internal storage for this variant.
        TestVariantData testVariantData = new TestVariantData(
                extension, taskManager, testVariantConfig, (TestedVariantData) testedVariantData);
        // link the testVariant to the tested variant in the other direction
        ((TestedVariantData) testedVariantData).setTestVariantData(testVariantData, type);

        return testVariantData;
    }

    /**
     * Creates VariantData for a specified list of product flavor.
     *
     * This will create VariantData for all build types of the given flavors.
     *
     * @param productFlavorList the flavor(s) to build.
     */
    private void createVariantDataForProductFlavors(
            @NonNull List<com.android.build.gradle.api.GroupableProductFlavor> productFlavorList) {

        BuildTypeData testBuildTypeData = null;
        if (extension instanceof TestedExtension) {
            TestedExtension testedExtension = (TestedExtension) extension;

            testBuildTypeData = buildTypes.get(testedExtension.getTestBuildType());
            if (testBuildTypeData == null) {
                throw new RuntimeException(String.format(
                        "Test Build Type '%1$s' does not exist.", testedExtension.getTestBuildType()));
            }
        }

        BaseVariantData variantForAndroidTest = null;

        ProductFlavor defaultConfig = defaultConfigData.getProductFlavor();

        Closure<Void> variantFilterClosure = extension.getVariantFilter();

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
                        productFlavorList);
                variantDataList.add(variantData);

                if (variantFactory.hasTestScope()) {
                    TestVariantData unitTestVariantData = createTestVariantData(
                            variantData,
                            UNIT_TEST);
                    variantDataList.add(unitTestVariantData);

                    if (buildTypeData == testBuildTypeData) {
                        GradleVariantConfiguration variantConfig = variantData
                                .getVariantConfiguration();
                        if (variantConfig.isMinifyEnabled() && variantConfig.getUseJack()) {
                            throw new RuntimeException(
                                    "Cannot test obfuscated variants when compiling with jack.");
                        }
                        variantForAndroidTest = variantData;
                    }
                }
            }
        }

        if (variantForAndroidTest != null) {
            TestVariantData androidTestVariantData = createTestVariantData(
                    variantForAndroidTest,
                    ANDROID_TEST);
            variantDataList.add(androidTestVariantData);
        }
    }

    public void createApiObjects() {
        for (BaseVariantData<?> variantData : variantDataList) {
            if (variantData.getType().isForTesting()) {
                // Testing variants are handled together with their "owners".
                continue;
            }

            BaseVariant variantApi =
                    variantFactory.createVariantApi(variantData, readOnlyObjectProvider);

            if (variantFactory.hasTestScope()) {
                TestVariantData androidTestVariantData =
                        ((TestedVariantData) variantData).getTestVariantData(ANDROID_TEST);

                if (androidTestVariantData != null) {
                    TestVariantImpl androidTestVariant = instantiator.newInstance(
                            TestVariantImpl.class,
                            androidTestVariantData,
                            variantApi,
                            androidBuilder,
                            readOnlyObjectProvider);

                    // add the test output.
                    ApplicationVariantFactory.createApkOutputApiObjects(
                            instantiator,
                            androidTestVariantData,
                            androidTestVariant);

                    ((TestedExtension) extension).addTestVariant(androidTestVariant);
                    ((TestedVariant) variantApi).setTestVariant(androidTestVariant);
                }

                TestVariantData unitTestVariantData =
                        ((TestedVariantData) variantData).getTestVariantData(UNIT_TEST);
                if (unitTestVariantData != null) {
                    UnitTestVariantImpl unitTestVariant = instantiator.newInstance(
                            UnitTestVariantImpl.class,
                            unitTestVariantData,
                            variantApi,
                            androidBuilder,
                            readOnlyObjectProvider);

                    ((TestedExtension) extension).addUnitTestVariant(unitTestVariant);
                    ((TestedVariant) variantApi).setUnitTestVariant(unitTestVariant);
                }
            }

            // Only add the variant API object to the domain object set once it's been fully
            // initialized.
            extension.addVariant(variantApi);
        }
    }

    private boolean isVariantPublished() {
        return extension.getPublishNonDefault();
    }

    private static void checkName(@NonNull String name, @NonNull String displayName) {
        checkPrefix(name, displayName, ANDROID_TEST.getPrefix());
        checkPrefix(name, displayName, UNIT_TEST.getPrefix());

        if (LINT.equals(name)) {
            throw new RuntimeException(String.format(
                    "%1$s names cannot be %2$s", displayName, LINT));
        }
    }

    private static void checkPrefix(String name, String displayName, String prefix) {
        if (name.startsWith(prefix)) {
            throw new RuntimeException(String.format(
                    "%1$s names cannot start with '%2$s'", displayName, prefix));
        }
    }

    private SigningConfig createSigningOverride() {
        if (project.hasProperty(PROPERTY_SIGNING_STORE_FILE) &&
                project.hasProperty(PROPERTY_SIGNING_STORE_PASSWORD) &&
                project.hasProperty(PROPERTY_SIGNING_KEY_ALIAS) &&
                project.hasProperty(PROPERTY_SIGNING_KEY_PASSWORD)) {

            SigningConfig signingConfigDsl = new SigningConfig("externalOverride");
            Map<String, ?> props = project.getProperties();

            signingConfigDsl.setStoreFile(new File((String) props.get(PROPERTY_SIGNING_STORE_FILE)));
            signingConfigDsl.setStorePassword((String) props.get(PROPERTY_SIGNING_STORE_PASSWORD));
            signingConfigDsl.setKeyAlias((String) props.get(PROPERTY_SIGNING_KEY_ALIAS));
            signingConfigDsl.setKeyPassword((String) props.get(PROPERTY_SIGNING_KEY_PASSWORD));

            if (project.hasProperty(PROPERTY_SIGNING_STORE_TYPE)) {
                signingConfigDsl.setStoreType((String) props.get(PROPERTY_SIGNING_STORE_TYPE));
            }

            return signingConfigDsl;
        }
        return null;
    }

}
