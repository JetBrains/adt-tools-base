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
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.api.AndroidSourceDirectorySet
import com.android.build.gradle.api.AndroidSourceSet
import com.android.build.gradle.internal.BuildTypeData
import com.android.build.gradle.internal.ExtraModelInfo
import com.android.build.gradle.internal.LibraryCache
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.ProductFlavorData
import com.android.build.gradle.internal.SdkHandler
import com.android.build.gradle.internal.TaskManager
import com.android.build.gradle.internal.VariantManager
import com.android.build.gradle.internal.coverage.JacocoPlugin
import com.android.build.gradle.internal.model.DefaultAndroidConfig
import com.android.build.gradle.internal.model.ModelBuilder
import com.android.build.gradle.internal.process.GradleJavaProcessExecutor
import com.android.build.gradle.internal.process.GradleProcessExecutor
import com.android.build.gradle.internal.profile.RecordingBuildListener
import com.android.build.gradle.internal.tasks.DependencyReportTask
import com.android.build.gradle.internal.tasks.SigningReportTask
import com.android.build.gradle.internal.variant.VariantFactory
import com.android.build.gradle.managed.BuildTypeAdaptor
import com.android.build.gradle.managed.BuildType
import com.android.build.gradle.managed.SigningConfig
import com.android.build.gradle.managed.ProductFlavor
import com.android.build.gradle.managed.ProductFlavorAdaptor
import com.android.build.gradle.managed.SigningConfigAdaptor
import com.android.build.gradle.ndk.managed.NdkConfig
import com.android.build.gradle.tasks.JillTask
import com.android.build.gradle.tasks.PreDex
import com.android.builder.core.AndroidBuilder
import com.android.builder.core.BuilderConstants
import com.android.builder.internal.compiler.JackConversionCache
import com.android.builder.internal.compiler.PreDexCache
import com.android.builder.profile.ExecutionType
import com.android.builder.profile.ProcessRecorderFactory
import com.android.builder.profile.Recorder
import com.android.builder.profile.ThreadRecorder
import com.android.builder.sdk.TargetInfo
import com.android.builder.signing.DefaultSigningConfig
import com.android.ide.common.internal.ExecutorSingleton
import com.android.ide.common.process.LoggedProcessOutputHandler
import com.android.ide.common.signing.KeystoreHelper
import com.android.utils.ILogger
import groovy.transform.CompileStatic
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.execution.TaskExecutionGraph
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.logging.LogLevel
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.service.ServiceRegistry
import org.gradle.language.base.FunctionalSourceSet
import org.gradle.language.base.LanguageSourceSet
import org.gradle.language.base.internal.SourceTransformTaskConfig
import org.gradle.language.base.internal.registry.LanguageTransform
import org.gradle.model.Model
import org.gradle.model.Mutate
import org.gradle.model.Path
import org.gradle.model.RuleSource
import org.gradle.model.collection.CollectionBuilder
import org.gradle.model.collection.ManagedSet
import org.gradle.model.internal.core.ModelCreators
import org.gradle.model.internal.core.ModelReference
import org.gradle.model.internal.registry.ModelRegistry
import org.gradle.platform.base.BinaryContainer
import org.gradle.platform.base.BinarySpec
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry

import javax.inject.Inject
import java.security.KeyStore

import static com.android.builder.core.BuilderConstants.DEBUG
import static com.android.builder.core.VariantType.ANDROID_TEST
import static com.android.builder.model.AndroidProject.FD_INTERMEDIATES

@CompileStatic
public class BaseComponentModelPlugin implements Plugin<Project> {
    ToolingModelBuilderRegistry toolingRegistry
    ModelRegistry modelRegistry

    @Inject
    protected BaseComponentModelPlugin(
            ToolingModelBuilderRegistry toolingRegistry,
            ModelRegistry modelRegistry) {
        this.toolingRegistry = toolingRegistry
        this.modelRegistry = modelRegistry
    }

    /**
     * Replace BasePlugin's apply method for component model.
     */
    @Override
    public void apply(Project project) {
        ProcessRecorderFactory.initialize(new LoggerWrapper(project.logger), project.rootProject.
                file("profiler" + System.currentTimeMillis() + ".json"))
        project.gradle.addListener(new RecordingBuildListener(ThreadRecorder.get()));

        project.apply plugin: AndroidComponentModelPlugin

        project.apply plugin: JavaBasePlugin
        project.apply plugin: JacocoPlugin

        // TODO: Create configurations for build types and flavors, or migrate to new dependency
        // management if it's ready.
        ConfigurationContainer configurations = project.getConfigurations()
        createConfiguration(
                configurations,
                "compile",
                "Classpath for default sources.")

        createConfiguration(
                configurations,
                "default-metadata",
                "Metadata for published APKs")

        createConfiguration(
                configurations,
                "default-mapping",
                "Metadata for published APKs")

        project.tasks.getByName("assemble").description =
                "Assembles all variants of all applications and secondary packages."

        project.apply plugin: NdkComponentModelPlugin

        // Remove this when our models no longer depends on Project.
        modelRegistry.create(
                ModelCreators.bridgedInstance(
                        ModelReference.of("projectModel", Project), project)
                        .descriptor("Model of project.")
                        .build())
        modelRegistry.create(
                ModelCreators.bridgedInstance(
                        ModelReference.of("toolingRegistry", ToolingModelBuilderRegistry), toolingRegistry)
                        .descriptor("Tooling model builder model registry.")
                        .build())
    }

    @SuppressWarnings("GrMethodMayBeStatic")
    static class Rules extends RuleSource {

        @Mutate
        void configureAndroidModel(
                AndroidModel androidModel,
                @Path("androidConfig") BaseExtension config) {
            androidModel.config = config

            androidModel.signingConfigs.create {
                it.name = DEBUG
                it.storeFile = KeystoreHelper.defaultDebugKeystoreLocation();
                it.storePassword = DefaultSigningConfig.DEFAULT_PASSWORD;
                it.keyAlias = DefaultSigningConfig.DEFAULT_ALIAS;
                it.keyPassword = DefaultSigningConfig.DEFAULT_PASSWORD;
                it.storeType = KeyStore.getDefaultType();
            }
        }

        // TODO: Remove code duplicated from BasePlugin.
        @Model
        ExtraModelInfo createExtraModelInfo(Project project) {
            return new ExtraModelInfo(project)
        }

        @Model
        SdkHandler createSdkHandler(Project project) {
            ILogger logger = new LoggerWrapper(project.logger)
            SdkHandler sdkHandler = new SdkHandler(project, logger)

            // call back on execution. This is called after the whole build is done (not
            // after the current project is done).
            // This is will be called for each (android) projects though, so this should support
            // being called 2+ times.
            project.gradle.buildFinished {
                ExecutorSingleton.shutdown()
                sdkHandler.unload()
                PreDexCache.getCache().clear(
                        project.rootProject.file(
                                "${project.rootProject.buildDir}/${FD_INTERMEDIATES}/dex-cache/cache.xml"),
                        logger)
                JackConversionCache.getCache().clear(
                        project.rootProject.file(
                                "${project.rootProject.buildDir}/${FD_INTERMEDIATES}/jack-cache/cache.xml"),
                        logger)
                LibraryCache.getCache().unload()
            }

            project.gradle.taskGraph.whenReady { TaskExecutionGraph taskGraph ->
                for (Task task : taskGraph.allTasks) {
                    if (task instanceof PreDex) {
                        PreDexCache.getCache().load(
                                project.rootProject.file(
                                        "${project.rootProject.buildDir}/${FD_INTERMEDIATES}/dex-cache/cache.xml"))
                        break;
                    } else if (task instanceof JillTask) {
                        JackConversionCache.getCache().load(
                                project.rootProject.file(
                                        "${project.rootProject.buildDir}/${FD_INTERMEDIATES}/jack-cache/cache.xml"))
                        break;
                    }
                }
            }
            return sdkHandler
        }

        @Model
        AndroidBuilder createAndroidBuilder(Project project, ExtraModelInfo extraModelInfo) {
            String creator = "Android Gradle"
            ILogger logger = new LoggerWrapper(project.logger)

            return new AndroidBuilder(
                    project == project.rootProject ? project.name : project.path,
                    creator,
                    new GradleProcessExecutor(project),
                    new GradleJavaProcessExecutor(project),
                    new LoggedProcessOutputHandler(logger),
                    extraModelInfo,
                    logger,
                    project.logger.isEnabled(LogLevel.INFO))

        }

        @Model("androidConfig")
        BaseExtension androidConfig(
                ServiceRegistry serviceRegistry,
                @Path("isApplication") Boolean isApplication,
                AndroidBuilder androidBuilder,
                SdkHandler sdkHandler,
                ExtraModelInfo extraModelInfo,
                Project project) {
            Instantiator instantiator = serviceRegistry.get(Instantiator.class);

            Class extensionClass = isApplication ? AppExtension : LibraryExtension

            BaseExtension extension = (BaseExtension) instantiator.newInstance(extensionClass,
                    (ProjectInternal) project, instantiator, androidBuilder,
                    sdkHandler, null, null, null, extraModelInfo, !isApplication)

            return extension
        }

        @Mutate
        void initDebugBuildTypes(
                @Path("android.buildTypes") ManagedSet<BuildType> buildTypes,
                @Path("android.signingConfigs") ManagedSet<SigningConfig> signingConfigs) {
            final SigningConfig debugSigningConfig = signingConfigs.find { it -> DEBUG }

            buildTypes.beforeEach { BuildType buildType ->
                initBuildType(buildType)
            }

            buildTypes.afterEach { BuildType buildType ->
                if (buildType.getName().equals(DEBUG)) {
                    buildType.setSigningConfig(debugSigningConfig)
                }
            }
        }

        private static void initBuildType(@NonNull BuildType buildType) {
            buildType.setIsDebuggable(false)
            buildType.setIsTestCoverageEnabled(false)
            buildType.setIsJniDebuggable(false)
            buildType.setIsPseudoLocalesEnabled(false)
            buildType.setIsRenderscriptDebuggable(false)
            buildType.setRenderscriptOptimLevel(3)
            buildType.setIsMinifyEnabled(false)
            buildType.setIsZipAlignEnabled(true)
            buildType.setIsEmbedMicroApp(true)
            buildType.setUseJack(false)
            buildType.setShrinkResources(false)
        }


        @Mutate
        void addDefaultAndroidSourceSet(
                @Path("android.sources") AndroidComponentModelSourceSet sources) {
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
                @Path("android.ndk") NdkConfig ndkConfig,
                @Path("android.config") BaseExtension baseExtension) {
            if (ndkConfig.compileSdkVersion.isEmpty() && baseExtension.compileSdkVersion != null) {
                ndkConfig.compileSdkVersion = baseExtension.compileSdkVersion
            }
        }

        @Mutate
        void createAndroidComponents(
                AndroidComponentSpec androidSpec,
                ServiceRegistry serviceRegistry,
                BaseExtension androidExtension,
                @Path("android.buildTypes") ManagedSet<BuildType> buildTypes,
                @Path("android.productFlavors") ManagedSet<ProductFlavor> productFlavors,
                @Path("android.signingConfigs") ManagedSet<SigningConfig> signingConfigs,
                VariantFactory variantFactory,
                TaskManager taskManager,
                Project project,
                AndroidBuilder androidBuilder,
                SdkHandler sdkHandler,
                ExtraModelInfo extraModelInfo,
                ToolingModelBuilderRegistry toolingRegistry,
                @Path("isApplication") Boolean isApplication) {
            Instantiator instantiator = serviceRegistry.get(Instantiator.class);

            // check if the target has been set.
            TargetInfo targetInfo = androidBuilder.getTargetInfo()
            if (targetInfo == null) {
                sdkHandler.initTarget(
                        androidExtension.getCompileSdkVersion(),
                        androidExtension.buildToolsRevision,
                        androidExtension.libraryRequests,
                        androidBuilder)
            }

            VariantManager variantManager = new VariantManager(
                    project,
                    androidBuilder,
                    androidExtension,
                    variantFactory,
                    taskManager,
                    instantiator)

            for(BuildType buildType : buildTypes) {
                variantManager.addBuildType(new BuildTypeAdaptor(buildType))
            }
            for(ProductFlavor productFlavor : productFlavors) {
                variantManager.addProductFlavor(new ProductFlavorAdaptor(productFlavor))
            }

            ModelBuilder modelBuilder = new ModelBuilder(
                    androidBuilder,
                    variantManager,
                    taskManager,
                    new DefaultAndroidConfig(
                            androidExtension,
                            signingConfigs.collect { new SigningConfigAdaptor(it) }),
                    extraModelInfo,
                    !isApplication);
            toolingRegistry.register(modelBuilder);


            def spec = androidSpec as DefaultAndroidComponentSpec
            spec.extension = androidExtension
            spec.variantManager = variantManager
        }

        @Mutate
        void createVariantData(
                CollectionBuilder<AndroidBinary> binaries,
                AndroidComponentSpec spec) {
            VariantManager variantManager = (spec as DefaultAndroidComponentSpec).variantManager
            binaries.all {
                DefaultAndroidBinary binary = it as DefaultAndroidBinary
                binary.variantData =
                        variantManager.createVariantData(binary.buildType, binary.productFlavors)
                variantManager.getVariantDataList().add(binary.variantData);
            }
        }

        @Mutate
        void createLifeCycleTasks(
                CollectionBuilder<Task> tasks,
                TaskManager taskManager) {
            taskManager.createTasksBeforeEvaluate(new TaskCollectionBuilderAdaptor(tasks))
        }

        @Mutate
        void createAndroidTasks(
                CollectionBuilder<Task> tasks,
                AndroidComponentSpec androidSpec,
                TaskManager taskManager,
                SdkHandler sdkHandler,
                Project project,
                AndroidComponentModelSourceSet androidSources) {
            DefaultAndroidComponentSpec spec = (DefaultAndroidComponentSpec) androidSpec

            applyProjectSourceSet(spec, androidSources, spec.extension)

            // setup SDK repositories.
            for (File file : sdkHandler.sdkLoader.repositories) {
                project.repositories.maven { MavenArtifactRepository repo ->
                    repo.url = file.toURI()
                }
            }

            // TODO: determine how to provide functionalities of variant API objects.
        }

        // TODO: Use @BinaryTasks after figuring how to configure non-binary specific tasks.
        @Mutate
        void createBinaryTasks(
                CollectionBuilder<Task> tasks,
                BinaryContainer binaries,
                AndroidComponentSpec spec,
                TaskManager taskManager) {

            VariantManager variantManager = (spec as DefaultAndroidComponentSpec).variantManager
            binaries.withType(AndroidBinary) { androidBinary ->
                ThreadRecorder.get().record(ExecutionType.VARIANT_MANAGER_CREATE_TASKS_FOR_VARIANT,
                        new Recorder.Block<Void>() {
                            @Override
                            public Void call() throws Exception {
                                    DefaultAndroidBinary binary = androidBinary as DefaultAndroidBinary
                                    variantManager.createTasksForVariantData(
                                            new TaskCollectionBuilderAdaptor(tasks),
                                            binary.variantData)
                                return null;
                            }
                        });
            }
        }

        /**
         * Create tasks that must be created after other tasks for variants are created.
         */
        @Mutate
        void createRemainingTasks(
                CollectionBuilder<Task> tasks,
                TaskManager taskManager,
                AndroidComponentSpec spec) {
            VariantManager variantManager = (spec as DefaultAndroidComponentSpec).variantManager

            // create the test tasks.
            taskManager.createTopLevelTestTasks (
                    new TaskCollectionBuilderAdaptor(tasks),
                    !variantManager.productFlavors.isEmpty() /*hasFlavors*/);
        }

        @Mutate
        void createReportTasks(CollectionBuilder<Task> tasks, AndroidComponentSpec spec) {
            VariantManager variantManager = (spec as DefaultAndroidComponentSpec).variantManager

            tasks.create("androidDependencies", DependencyReportTask) { DependencyReportTask dependencyReportTask ->
                dependencyReportTask.setDescription("Displays the Android dependencies of the project")
                dependencyReportTask.setVariants(variantManager.variantDataList)
                dependencyReportTask.setGroup("Android")
            }

            tasks.create("signingReport", SigningReportTask) { SigningReportTask signingReportTask ->
                signingReportTask.setDescription("Displays the signing info for each variant")
                signingReportTask.setVariants(variantManager.variantDataList)
                signingReportTask.setGroup("Android")
            }
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

    private void createConfiguration(
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

}
