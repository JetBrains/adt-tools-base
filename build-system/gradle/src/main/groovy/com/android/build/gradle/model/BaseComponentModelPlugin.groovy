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

import com.android.annotations.Nullable
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.BasePlugin
import com.android.build.gradle.api.AndroidSourceDirectorySet
import com.android.build.gradle.api.AndroidSourceSet
import com.android.build.gradle.internal.BuildTypeData
import com.android.build.gradle.internal.ProductFlavorData
import com.android.build.gradle.internal.VariantManager
import com.android.build.gradle.internal.dsl.BuildType
import com.android.build.gradle.internal.dsl.GroupableProductFlavor
import com.android.build.gradle.internal.dsl.SigningConfigFactory
import com.android.build.gradle.internal.tasks.DependencyReportTask
import com.android.build.gradle.internal.tasks.PrepareSdkTask
import com.android.build.gradle.internal.tasks.SigningReportTask
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.build.gradle.internal.variant.TestVariantData
import com.android.build.gradle.internal.variant.VariantFactory
import com.android.build.gradle.ndk.NdkExtension
import com.android.builder.core.BuilderConstants
import com.android.builder.core.DefaultBuildType
import com.android.builder.model.SigningConfig
import org.gradle.api.DefaultTask
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.tasks.TaskContainer
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.service.ServiceRegistry
import org.gradle.language.base.FunctionalSourceSet
import org.gradle.language.base.LanguageSourceSet
import org.gradle.language.base.ProjectSourceSet
import org.gradle.language.base.internal.LanguageRegistration
import org.gradle.language.base.internal.LanguageRegistry
import org.gradle.language.base.internal.SourceTransformTaskConfig
import org.gradle.model.Model
import org.gradle.model.Mutate
import org.gradle.model.Path
import org.gradle.model.RuleSource
import org.gradle.model.internal.core.ModelCreators
import org.gradle.model.internal.core.ModelReference
import org.gradle.platform.base.BinaryContainer
import org.gradle.platform.base.BinarySpec
import org.gradle.platform.base.BinaryType
import org.gradle.platform.base.BinaryTypeBuilder
import org.gradle.platform.base.ComponentSpecContainer
import org.gradle.platform.base.TransformationFileType
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry

import javax.inject.Inject

import static com.android.builder.core.BuilderConstants.DEBUG

public class BaseComponentModelPlugin extends BasePlugin implements Plugin<Project> {
    @Inject
    protected BaseComponentModelPlugin(Instantiator instantiator, ToolingModelBuilderRegistry registry) {
        super(instantiator, registry);
    }

    @Override
    protected Class<? extends BaseExtension> getExtensionClass() {
        throw new RuntimeException("getExtensionClass should not called for component model plugin.")
    }

    @Override
    protected VariantFactory getVariantFactory() {
        throw new RuntimeException("getVariantFactory should not called for component model plugin.")
    }

    @Override
    void apply(Project project) {
        super.apply(project)
    }

    /**
     * Replace BasePlugin's apply method for component model.
     */
    @Override
    protected void doApply() {
        project.plugins.apply(AndroidComponentModelPlugin)

        configureProject()

        project.plugins.apply(NdkComponentModelPlugin)

        // Setup Android's FunctionalSourceSet.
        project.getExtensions().getByType(LanguageRegistry.class).add(new AndroidSource());
        project.sources.create("main")
        project.sources.all {
                resources(AndroidLanguageSourceSet)
                java(AndroidLanguageSourceSet)
                manifest(AndroidLanguageSourceSet)
                res(AndroidLanguageSourceSet)
                assets(AndroidLanguageSourceSet)
                aidl(AndroidLanguageSourceSet)
                renderscript(AndroidLanguageSourceSet)
                jni(AndroidLanguageSourceSet)
                jniLibs(AndroidLanguageSourceSet)
        }

        project.modelRegistry.create(
                ModelCreators.of(ModelReference.of("androidBasePlugin", BasePlugin.class), this)
                        .simpleDescriptor("Android BaseComponentModelPlugin.")
                        .build())
    }

    @RuleSource
    static class Rules {
        @Model("android")
        BaseExtension androidapp(
                ServiceRegistry serviceRegistry,
                NamedDomainObjectContainer<DefaultBuildType> buildTypeContainer,
                NamedDomainObjectContainer<GroupableProductFlavor> productFlavorContainer,
                NamedDomainObjectContainer<SigningConfig> signingConfigContainer,
                @Path("extensionClass") Class extensionClass,
                BasePlugin plugin) {
            Instantiator instantiator = serviceRegistry.get(Instantiator.class);
            Project project = plugin.getProject()

            BaseExtension extension = (BaseExtension)instantiator.newInstance(extensionClass,
                    plugin, (ProjectInternal) project, instantiator,
                    buildTypeContainer, productFlavorContainer, signingConfigContainer, false)
            plugin.setBaseExtension(extension)

            // Android component model always use new plugin.
            extension.useNewNativePlugin = true

            return extension
        }

        @Mutate
        void forwardCompileSdkVersion(NdkExtension ndkExtension, BaseExtension baseExtension) {
            if (ndkExtension.compileSdkVersion.isEmpty()) {
                ndkExtension.compileSdkVersion(baseExtension.compileSdkVersion);
            }
        }

        @Model("android.signingConfig")
        NamedDomainObjectContainer<SigningConfig> signingConfig(ServiceRegistry serviceRegistry,
                BasePlugin plugin) {
            Instantiator instantiator = serviceRegistry.get(Instantiator.class);
            Project project = plugin.getProject()
            def signingConfigContainer = project.container(SigningConfig,
                new SigningConfigFactory(instantiator))
            signingConfigContainer.create(DEBUG)
            signingConfigContainer.whenObjectRemoved {
                throw new UnsupportedOperationException("Removing signingConfigs is not supported.")
            }
            return signingConfigContainer
        }

        @Mutate
        void closeProjectSourceSet(ProjectSourceSet sources) {
        }

        @Mutate
        void createAndroidComponents(
                ComponentSpecContainer specContainer,
                BaseExtension androidExtension,
                NamedDomainObjectContainer<DefaultBuildType> buildTypeContainer,
                NamedDomainObjectContainer<GroupableProductFlavor> productFlavorContainer,
                NamedDomainObjectContainer<SigningConfig> signingConfigContainer,
                ProjectSourceSet sources,
                VariantFactory variantFactory,
                BasePlugin plugin) {
            VariantManager variantManager = new VariantManager(
                    plugin.project,
                    plugin,
                    androidExtension,
                    variantFactory)

            signingConfigContainer.all { SigningConfig signingConfig ->
                variantManager.addSigningConfig((com.android.build.gradle.internal.dsl.SigningConfig) signingConfig)
            }
            buildTypeContainer.all { DefaultBuildType buildType ->
                variantManager.addBuildType((BuildType) buildType)
            }
            productFlavorContainer.all { GroupableProductFlavor productFlavor ->
                variantManager.addProductFlavor(productFlavor)
            }
            plugin.variantManager = variantManager;

            specContainer.withType(AndroidComponentSpec) { DefaultAndroidComponentSpec spec ->
                spec.extension = androidExtension
                spec.variantManager = variantManager
                spec.signingOverride = plugin.getSigningOverride()

                applyProjectSourceSet(spec, sources, plugin)
            }
        }

        @BinaryType
        void defineBinaryType(BinaryTypeBuilder<AndroidTestBinary> builder) {
            builder.defaultImplementation(DefaultAndroidTestBinary)
        }

        @Mutate
        void createTestBinary(BinaryContainer binaries, ComponentSpecContainer specs) {
            AndroidComponentSpec spec =
                    (AndroidComponentSpec) specs.getByName(AndroidComponentModelPlugin.COMPONENT_NAME)
            spec.binaries.withType(AndroidBinary) { binary ->
                if (binary.buildType.name.equals(DEBUG)) {
                    DefaultAndroidTestBinary testBinary =
                            (DefaultAndroidTestBinary) binaries.create(
                                    binary.name + "Test", AndroidTestBinary);
                    testBinary.testedBinary = binary
                    spec.binaries.add(testBinary)
                }
            }
        }

        @Mutate
        void createAndroidTasks(
                TaskContainer tasks,
                BinaryContainer binaries,
                ComponentSpecContainer specContainer,
                BasePlugin plugin) {
            DefaultAndroidComponentSpec spec = (DefaultAndroidComponentSpec)specContainer.withType(AndroidComponentSpec)[0]
            VariantManager variantManager = spec.variantManager

            // Create lifecycle tasks.
            Task uninstallAll = tasks.create("uninstallAll")
            uninstallAll.description = "Uninstall all applications."
            uninstallAll.group = INSTALL_GROUP

            Task deviceCheck = tasks.create("deviceCheck")
            deviceCheck.description = "Runs all device checks using Device Providers and Test Servers."
            deviceCheck.group = JavaBasePlugin.VERIFICATION_GROUP

            Task connectedCheck = tasks.create("connectedCheck")
            connectedCheck.description = "Runs all device checks on currently connected devices."
            connectedCheck.group = JavaBasePlugin.VERIFICATION_GROUP

            Task mainPreBuild = tasks.create("preBuild", PrepareSdkTask)
            mainPreBuild.plugin = plugin

            plugin.uninstallAll = uninstallAll
            plugin.deviceCheck = deviceCheck
            plugin.connectedCheck = connectedCheck
            plugin.mainPreBuild = mainPreBuild

            // setup SDK repositories.
            for (File file : plugin.sdkHandler.sdkLoader.repositories) {
                plugin.project.repositories.maven {
                    url = file.toURI()
                }
            }

            plugin.createLintCompileTask();

            // Create tasks for each binaries.
            if (!variantManager.productFlavors.isEmpty()) {
                // there'll be more than one test app, so we need a top level assembleTest
                Task assembleTest = tasks.create("assembleTest");
                assembleTest.setGroup(org.gradle.api.plugins.BasePlugin.BUILD_GROUP);
                assembleTest.setDescription("Assembles all the Test applications");
                plugin.setAssembleTest(assembleTest);
            }

            binaries.withType(AndroidBinary) { DefaultAndroidBinary binary ->
                BaseVariantData variantData = variantManager.createVariantData(
                        binary.buildType,
                        binary.productFlavors,
                        plugin.signingOverride
                )
                binary.variantData = variantData
                variantManager.getVariantDataList().add(variantData)
                variantManager.createTasksForVariantData(tasks, variantData)
            }

            // Create test tasks.
            binaries.withType(AndroidTestBinary) { binary ->
                TestVariantData testVariantData =
                        variantManager.createTestVariantData(((DefaultAndroidBinary)binary.testedBinary).variantData, plugin.signingOverride)
                variantManager.getVariantDataList().add(testVariantData);
                variantManager.createTasksForVariantData(tasks, testVariantData)
            }

            // create the lint tasks.
            plugin.createLintTasks();

            // create the test tasks.
            plugin.createCheckTasks(!variantManager.productFlavors.isEmpty(), false /*isLibrary*/);

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
                ProjectSourceSet sources,
                BasePlugin plugin) {
            DefaultAndroidComponentSpec spec = (DefaultAndroidComponentSpec)androidSpec
            VariantManager variantManager = spec.variantManager
            for (FunctionalSourceSet source : sources) {
                String name = source.getName();
                AndroidSourceSet androidSource = (
                        name.equals(BuilderConstants.MAIN)
                                ? plugin.mainSourceSet
                                : (name.equals(BuilderConstants.ANDROID_TEST)
                                        ? plugin.testSourceSet
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

            boolean isTest = name.startsWith(BuilderConstants.ANDROID_TEST)
            name = name.replaceFirst(BuilderConstants.ANDROID_TEST, "")
            ProductFlavorData productFlavorData = variantManager.getProductFlavors().get(name)
            if (productFlavorData != null) {
                return isTest ? productFlavorData.getTestSourceSet() : productFlavorData.getSourceSet();
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
    private static class AndroidSource implements LanguageRegistration<AndroidLanguageSourceSet> {
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

        public Class<? extends TransformationFileType> getOutputType() {
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
            return binary instanceof AndroidBinary
        }
    }
}
