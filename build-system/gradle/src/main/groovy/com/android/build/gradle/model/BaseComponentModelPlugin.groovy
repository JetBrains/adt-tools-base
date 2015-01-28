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

package com.android.build.gradle.model

import com.android.annotations.NonNull
import com.android.annotations.Nullable
import com.android.build.gradle.AppExtension
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.BasePlugin
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.api.AndroidSourceDirectorySet
import com.android.build.gradle.api.AndroidSourceSet
import com.android.build.gradle.internal.BuildTypeData
import com.android.build.gradle.internal.DependencyManager
import com.android.build.gradle.internal.ExtraModelInfo
import com.android.build.gradle.internal.ProductFlavorData
import com.android.build.gradle.internal.SdkHandler
import com.android.build.gradle.internal.TaskManager
import com.android.build.gradle.internal.VariantManager
import com.android.build.gradle.internal.dsl.BuildType
import com.android.build.gradle.internal.dsl.GroupableProductFlavor
import com.android.build.gradle.internal.dsl.SigningConfig
import com.android.build.gradle.internal.dsl.SigningConfigFactory
import com.android.build.gradle.internal.model.ModelBuilder
import com.android.build.gradle.internal.tasks.DependencyReportTask
import com.android.build.gradle.internal.tasks.SigningReportTask
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.build.gradle.internal.variant.TestVariantData
import com.android.build.gradle.internal.variant.VariantFactory
import com.android.build.gradle.ndk.NdkExtension
import com.android.builder.core.AndroidBuilder
import com.android.builder.core.BuilderConstants
import com.google.common.collect.Lists
import groovy.transform.CompileStatic
import org.gradle.api.DefaultTask
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.tasks.TaskContainer
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.service.ServiceRegistry
import org.gradle.language.base.FunctionalSourceSet
import org.gradle.language.base.LanguageSourceSet
import org.gradle.language.base.internal.SourceTransformTaskConfig
import org.gradle.language.base.internal.registry.LanguageTransform
import org.gradle.model.Finalize
import org.gradle.model.Model
import org.gradle.model.Mutate
import org.gradle.model.Path
import org.gradle.model.RuleSource
import org.gradle.model.internal.core.ModelCreators
import org.gradle.model.internal.core.ModelPath
import org.gradle.model.internal.core.ModelReference
import org.gradle.model.internal.registry.ModelRegistry
import org.gradle.platform.base.BinaryContainer
import org.gradle.platform.base.BinarySpec
import org.gradle.platform.base.BinaryType
import org.gradle.platform.base.BinaryTypeBuilder
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry

import javax.inject.Inject

import static com.android.builder.core.BuilderConstants.DEBUG
import static com.android.builder.core.VariantType.ANDROID_TEST

@CompileStatic
public class BaseComponentModelPlugin extends BasePlugin implements Plugin<Project> {
    ModelRegistry modelRegistry

    @Inject
    protected BaseComponentModelPlugin(
            Instantiator instantiator,
            ToolingModelBuilderRegistry registry,
            ModelRegistry modelRegistry) {
        super(instantiator, registry);
        this.modelRegistry = modelRegistry

    }

    @Override
    protected TaskManager createTaskManager(
            Project project,
            TaskContainer tasks,
            AndroidBuilder androidBuilder,
            BaseExtension extension,
            SdkHandler sdkHandler,
            DependencyManager dependencyManager,
            ToolingModelBuilderRegistry toolingRegistry) {
        throw new RuntimeException("createTaskManager should not called for component model plugin.")
    }

    @Override
    protected Class<? extends BaseExtension> getExtensionClass() {
        throw new RuntimeException("getExtensionClass should not called for component model plugin.")
    }

    @Override
    protected VariantFactory getVariantFactory() {
        throw new RuntimeException("getVariantFactory should not called for component model plugin.")
    }

    /**
     * Replace BasePlugin's apply method for component model.
     */
    @Override
    public void apply(Project project) {
        this.project = project
        project.apply plugin: AndroidComponentModelPlugin

        configureProject()

        project.apply plugin: NdkComponentModelPlugin

        modelRegistry.create(
                ModelCreators.bridgedInstance(
                        ModelReference.of("androidBasePlugin", BasePlugin.class), this)
                                .simpleDescriptor("Android BaseComponentModelPlugin.")
                                .build(),
                ModelPath.ROOT)

        modelRegistry.create(
                ModelCreators.bridgedInstance(
                        ModelReference.of("toolingRegistry", ToolingModelBuilderRegistry), registry)
                        .simpleDescriptor("Tooling model builder model registry.")
                        .build(),
                ModelPath.ROOT)
    }

    @RuleSource
    static class Rules {
        @Mutate
        void configureAndroidModel(
                AndroidModel androidModel,
                @Path("androidConfig") BaseExtension config,
                @Path("androidSigningConfigs") NamedDomainObjectContainer<SigningConfig> signingConfigs) {
            androidModel.config = config
            androidModel.signingConfigs = signingConfigs
        }

        @Model("androidConfig")
        BaseExtension androidConfig(
                ServiceRegistry serviceRegistry,
                @Path("androidBuildTypes") NamedDomainObjectContainer<BuildType> buildTypeContainer,
                @Path("androidProductFlavors") NamedDomainObjectContainer<GroupableProductFlavor> productFlavorContainer,
                @Path("androidSigningConfigs") NamedDomainObjectContainer<SigningConfig> signingConfigContainer,
                @Path("isApplication") Boolean isApplication,
                Project project,
                BasePlugin plugin) {
            Instantiator instantiator = serviceRegistry.get(Instantiator.class);

            Class extensionClass = isApplication ? AppExtension : LibraryExtension

            BaseExtension extension = (BaseExtension)instantiator.newInstance(extensionClass,
                    (ProjectInternal) project, instantiator, plugin.androidBuilder,
                    plugin.sdkHandler,
                    buildTypeContainer, productFlavorContainer, signingConfigContainer,
                    plugin.extraModelInfo, !isApplication)

            // Android component model always use new plugin.
            extension.useNewNativePlugin = true
            plugin.setBaseExtension(extension)

            return extension
        }

        @Mutate
        void addDefaulAndroidSourceSet(AndroidComponentModelSourceSet sources) {
            sources.addDefaultSourceSet("resources", AndroidLanguageSourceSet.class);
            sources.addDefaultSourceSet("java", AndroidLanguageSourceSet.class);
            sources.addDefaultSourceSet("manifest", AndroidLanguageSourceSet.class);
            sources.addDefaultSourceSet("res", AndroidLanguageSourceSet.class);
            sources.addDefaultSourceSet("assets", AndroidLanguageSourceSet.class);
            sources.addDefaultSourceSet("aidl", AndroidLanguageSourceSet.class);
            sources.addDefaultSourceSet("renderscript", AndroidLanguageSourceSet.class);
            sources.addDefaultSourceSet("jniLibs", AndroidLanguageSourceSet.class);
        }

        @Mutate
        void forwardCompileSdkVersion(
                @Path("android.ndk") NdkExtension ndkExtension,
                @Path("android.config") BaseExtension baseExtension) {
            if (ndkExtension.compileSdkVersion.isEmpty() && baseExtension.compileSdkVersion != null) {
                ndkExtension.compileSdkVersion(baseExtension.compileSdkVersion);
            }
        }

        @Model("androidSigningConfigs")
        NamedDomainObjectContainer<SigningConfig> signingConfig(ServiceRegistry serviceRegistry,
                Project project) {
            Instantiator instantiator = serviceRegistry.get(Instantiator.class);
            def signingConfigContainer = project.container(SigningConfig,
                new SigningConfigFactory(instantiator))
            signingConfigContainer.create(DEBUG)
            signingConfigContainer.whenObjectRemoved {
                throw new UnsupportedOperationException("Removing signingConfigs is not supported.")
            }
            return signingConfigContainer
        }

        @Mutate
        void closeProjectSourceSet(AndroidComponentModelSourceSet sources) {
        }

        @Mutate
        void createAndroidComponents(
                AndroidComponentSpec androidSpec,
                ServiceRegistry serviceRegistry,
                BaseExtension androidExtension,
                @Path("android.buildTypes") NamedDomainObjectContainer<BuildType> buildTypeContainer,
                @Path("android.productFlavors") NamedDomainObjectContainer<GroupableProductFlavor> productFlavorContainer,
                @Path("android.signingConfigs") NamedDomainObjectContainer<SigningConfig> signingConfigContainer,
                VariantFactory variantFactory,
                TaskManager taskManager,
                Project project,
                BasePlugin plugin,
                ToolingModelBuilderRegistry toolingRegistry,
                @Path("isApplication") Boolean isApplication) {
            Instantiator instantiator = serviceRegistry.get(Instantiator.class);

            plugin.ensureTargetSetup()

            VariantManager variantManager = new VariantManager(
                    project,
                    plugin.androidBuilder,
                    androidExtension,
                    variantFactory,
                    taskManager,
                    instantiator)

            signingConfigContainer.all { SigningConfig signingConfig ->
                variantManager.addSigningConfig(signingConfig)
            }
            buildTypeContainer.all { BuildType buildType ->
                variantManager.addBuildType(buildType)
            }
            productFlavorContainer.all { GroupableProductFlavor productFlavor ->
                variantManager.addProductFlavor(productFlavor)
            }

            ModelBuilder modelBuilder = new ModelBuilder(
                    plugin.androidBuilder, variantManager, plugin.taskManager,
                    androidExtension, plugin.extraModelInfo, !isApplication);
            toolingRegistry.register(modelBuilder);


            def spec = androidSpec as DefaultAndroidComponentSpec
            spec.extension = androidExtension
            spec.variantManager = variantManager
            spec.signingOverride = plugin.getSigningOverride()
        }

        @BinaryType
        void defineBinaryType(BinaryTypeBuilder<AndroidTestBinary> builder) {
            builder.defaultImplementation(DefaultAndroidTestBinary)
        }

        // Must run after AndroidBinaries are created.
        @Finalize
        void createTestBinary(BinaryContainer binaries, AndroidComponentSpec spec) {
            List<AndroidTestBinary> testBinaries = Lists.newArrayList();
            spec.binaries.each { binarySpec ->
                def binary = binarySpec as AndroidBinary
                if (binary.buildType.name.equals(DEBUG)) {
                    DefaultAndroidTestBinary testBinary =
                            (DefaultAndroidTestBinary) binaries.create(
                                    binary.name + "Test", AndroidTestBinary);
                    testBinary.testedBinary = binary
                    testBinaries.add(testBinary)
                }
            }
            spec.binaries.addAll(testBinaries)
        }

        @Mutate
        void createAndroidTasks(
                TaskContainer tasks,
                BinaryContainer binaries,
                AndroidComponentSpec androidSpec,
                TaskManager taskManager,
                BasePlugin plugin,
                AndroidComponentModelSourceSet androidSources) {
            DefaultAndroidComponentSpec spec = (DefaultAndroidComponentSpec) androidSpec
            VariantManager variantManager = spec.variantManager

            applyProjectSourceSet(spec, androidSources, spec.extension)

            // Create lifecycle tasks.
            taskManager.createTasks()

            // setup SDK repositories.
            for (File file : plugin.sdkHandler.sdkLoader.repositories) {
                plugin.project.repositories.maven { MavenArtifactRepository repo ->
                    repo.url = file.toURI()
                }
            }

            taskManager.createLintCompileTask();

            taskManager.createAssembleAndroidTestTask()

            // Create tasks for each binaries.
            binaries.withType(AndroidBinary) { DefaultAndroidBinary binary ->
                BaseVariantData variantData = variantManager.createVariantData(
                        binary.buildType,
                        binary.productFlavors,
                        plugin.signingOverride)
                binary.variantData = variantData
                variantManager.getVariantDataList().add(variantData)
                variantManager.createTasksForVariantData(tasks, variantData)
            }

            // Create test tasks.
            binaries.withType(AndroidTestBinary) { binarySpec ->
                def binary = binarySpec as DefaultAndroidTestBinary
                TestVariantData testVariantData =
                        variantManager.createTestVariantData(
                            (binary.testedBinary as DefaultAndroidBinary).variantData,
                            plugin.signingOverride as com.android.builder.model.SigningConfig,
                            ANDROID_TEST)
                variantManager.getVariantDataList().add(testVariantData);
                variantManager.createTasksForVariantData(tasks, testVariantData)
            }

            // create the lint tasks.
            taskManager.createLintTasks(variantManager.variantDataList);

            // create the test tasks.
            taskManager.createConnectedCheckTasks(
                    variantManager.variantDataList,
                    !variantManager.productFlavors.isEmpty() /*hasFlavors*/,
                    false /*isLibrary*/);

            // Create the variant API objects after the tasks have been created!
            variantManager.createApiObjects();

            // Create report tasks.
            def dependencyReportTask = tasks.create("androidDependencies", DependencyReportTask)
            dependencyReportTask.setDescription("Displays the Android dependencies of the project")
            dependencyReportTask.setVariants(variantManager.variantDataList)
            dependencyReportTask.setGroup("Android")

            def signingReportTask = tasks.create("signingReport", SigningReportTask)
            signingReportTask.setDescription("Displays the signing info for each variant")
            signingReportTask.setVariants(variantManager.variantDataList)
            signingReportTask.setGroup("Android")
        }


        private static void applyProjectSourceSet(
                AndroidComponentSpec androidSpec,
                AndroidComponentModelSourceSet sources,
                BaseExtension baseExtension) {
            DefaultAndroidComponentSpec spec = (DefaultAndroidComponentSpec)androidSpec
            VariantManager variantManager = spec.variantManager
            for (FunctionalSourceSet source : sources) {
                String name = source.getName();
                AndroidSourceSet androidSource = (
                        name.equals(BuilderConstants.MAIN)
                                ? baseExtension.sourceSets.getByName(baseExtension.getDefaultConfig().getName())
                                : (name.equals(ANDROID_TEST.prefix)
                                        ? baseExtension.sourceSets.getByName(ANDROID_TEST.getPrefix())
                                        : findAndroidSourceSet(variantManager, name)))

                if (androidSource == null) {
                    continue;
                }

                convertSourceSet(androidSource.getResources(), source.findByName("resource")?.getSource())
                convertSourceSet(androidSource.getJava(), source.findByName("java")?.getSource())
                convertSourceSet(androidSource.getRes(), source.findByName("res")?.getSource())
                convertSourceSet(androidSource.getAssets(), source.findByName("assets")?.getSource())
                convertSourceSet(androidSource.getAidl(), source.findByName("aidl")?.getSource())
                convertSourceSet(androidSource.getRenderscript(), source.findByName("renderscript")?.getSource())
                convertSourceSet(androidSource.getJni(), source.findByName("jni")?.getSource())
                convertSourceSet(androidSource.getJniLibs(), source.findByName("jniLibs")?.getSource())
            }
        }

        private static convertSourceSet(
                AndroidSourceDirectorySet androidDir,
                @Nullable SourceDirectorySet dir) {
            if (dir == null) {
                return
            }
            androidDir.setSrcDirs(dir.getSrcDirs())
            androidDir.include(dir.getIncludes())
            androidDir.exclude(dir.getExcludes())
        }

        @Nullable
        private static AndroidSourceSet findAndroidSourceSet(
                VariantManager variantManager,
                String name) {
            BuildTypeData buildTypeData = variantManager.getBuildTypes().get(name)
            if (buildTypeData != null) {
                return buildTypeData.getSourceSet();
            }

            boolean isTest = name.startsWith(ANDROID_TEST.prefix)
            name = name.replaceFirst(ANDROID_TEST.prefix, "")
            ProductFlavorData productFlavorData = variantManager.getProductFlavors().get(name)
            if (productFlavorData != null) {
                return isTest ? productFlavorData.getTestSourceSet(ANDROID_TEST) : productFlavorData.getSourceSet();
            }
            return null;
        }

    }

    /**
     * Default Android LanguageRegistration.
     *
     * Allows default LanguageSourceSet to be create until specialized LanguageRegistration is
     * created.
     */
    private static class AndroidSource implements LanguageTransform<AndroidLanguageSourceSet, AndroidObject> {
        public String getName() {
            return "main";
        }

        public Class<AndroidLanguageSourceSet> getSourceSetType() {
            return AndroidLanguageSourceSet.class;
        }

        public Class<? extends AndroidLanguageSourceSet> getSourceSetImplementation() {
            return AndroidLanguageSourceSet.class;
        }

        public Map<String, Class<?>> getBinaryTools() {
            return Collections.emptyMap();
        }

        public Class<AndroidObject> getOutputType() {
            return null;
        }

        public SourceTransformTaskConfig getTransformTask() {
            return new SourceTransformTaskConfig() {
                public String getTaskPrefix() {
                    return "process";
                }

                public Class<? extends DefaultTask> getTaskType() {
                    return DefaultTask;
                }

                public void configureTask(Task task, BinarySpec binary, LanguageSourceSet sourceSet) {
                }
            };
        }

        public boolean applyToBinary(BinarySpec binary) {
            return false
        }
    }
}
