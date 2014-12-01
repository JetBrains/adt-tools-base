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

package com.android.build.gradle.internal.model
import com.android.annotations.NonNull
import com.android.annotations.Nullable
import com.android.build.OutputFile
import com.android.build.gradle.BasePlugin
import com.android.build.gradle.LibraryPlugin
import com.android.build.gradle.api.ApkOutputFile
import com.android.build.gradle.internal.BuildTypeData
import com.android.build.gradle.internal.ProductFlavorData
import com.android.build.gradle.internal.variant.ApkVariantOutputData
import com.android.build.gradle.internal.variant.ApplicationVariantData
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.build.gradle.internal.variant.BaseVariantOutputData
import com.android.build.gradle.internal.variant.LibraryVariantData
import com.android.build.gradle.internal.variant.TestVariantData
import com.android.builder.core.DefaultProductFlavor
import com.android.builder.core.VariantConfiguration
import com.android.builder.model.AaptOptions
import com.android.builder.model.AndroidArtifact
import com.android.builder.model.AndroidArtifactOutput
import com.android.builder.model.AndroidProject
import com.android.builder.model.ApiVersion
import com.android.builder.model.ArtifactMetaData
import com.android.builder.model.JavaArtifact
import com.android.builder.model.LintOptions
import com.android.builder.model.SigningConfig
import com.android.builder.model.SourceProvider
import com.android.builder.model.SourceProviderContainer
import com.android.sdklib.AndroidVersion
import com.android.sdklib.IAndroidTarget
import com.google.common.collect.ImmutableCollection
import com.google.common.collect.ImmutableList
import com.google.common.collect.Lists
import org.gradle.api.Project
import org.gradle.api.tasks.compile.AbstractCompile
import org.gradle.tooling.provider.model.ToolingModelBuilder

import java.util.jar.Attributes
import java.util.jar.Manifest

import static com.android.builder.model.AndroidProject.ARTIFACT_ANDROID_TEST
import static com.android.builder.model.AndroidProject.ARTIFACT_MAIN
/**
 * Builder for the custom Android model.
 */
public class ModelBuilder implements ToolingModelBuilder {
    @Override
    public boolean canBuild(String modelName) {
        // The default name for a model is the name of the Java interface
        return modelName.equals(AndroidProject.class.getName())
    }

    @Override
    public Object buildAll(String modelName, Project project) {
        Collection<SigningConfig> signingConfigs

        BasePlugin basePlugin = BasePlugin.findBasePlugin(project);

        if (basePlugin == null) {
            project.logger.error("Failed to find Android plugin for project " + project.name)
            return null
        }

        signingConfigs = basePlugin.extension.signingConfigs

        // get the boot classpath. This will ensure the target is configured.
        List<String> bootClasspath = basePlugin.bootClasspathAsStrings

        List<File> frameworkSource = Collections.emptyList();

        // list of extra artifacts
        List<ArtifactMetaData> artifactMetaDataList = Lists.newArrayList(basePlugin.extraArtifacts)
        // plus the instrumentation test one.
        artifactMetaDataList.add(
                new ArtifactMetaDataImpl(
                        ARTIFACT_ANDROID_TEST,
                        true /*isTest*/,
                        ArtifactMetaData.TYPE_ANDROID));

        LintOptions lintOptions = com.android.build.gradle.internal.dsl.LintOptions.create(basePlugin.extension.lintOptions)

        AaptOptions aaptOptions = AaptOptionsImpl.create(basePlugin.extension.aaptOptions)

        //noinspection GroovyVariableNotAssigned
        DefaultAndroidProject androidProject = new DefaultAndroidProject(
                getModelVersion(),
                project.name,
                basePlugin.getAndroidBuilder().getTarget().hashString(),
                bootClasspath,
                frameworkSource,
                cloneSigningConfigs(signingConfigs),
                aaptOptions,
                artifactMetaDataList,
                basePlugin.unresolvedDependencies,
                basePlugin.extension.compileOptions,
                lintOptions,
                project.getBuildDir(),
                basePlugin.extension.resourcePrefix,
                basePlugin instanceof LibraryPlugin)
                    .setDefaultConfig(ProductFlavorContainerImpl.createProductFlavorContainer(
                        basePlugin.defaultConfigData,
                        basePlugin.getExtraFlavorSourceProviders(basePlugin.defaultConfigData.productFlavor.name)))

        for (BuildTypeData btData : basePlugin.variantManager.buildTypes.values()) {
            androidProject.addBuildType(BuildTypeContainerImpl.createBTC(
                    btData,
                    basePlugin.getExtraBuildTypeSourceProviders(btData.buildType.name)))
        }
        for (ProductFlavorData pfData : basePlugin.variantManager.productFlavors.values()) {
            androidProject.addProductFlavors(ProductFlavorContainerImpl.createProductFlavorContainer(
                    pfData,
                    basePlugin.getExtraFlavorSourceProviders(pfData.productFlavor.name)))
        }

        Set<Project> gradleProjects = project.getRootProject().getAllprojects();

        for (BaseVariantData variantData : basePlugin.variantDataList) {
            if (!(variantData instanceof TestVariantData)) {
                androidProject.addVariant(createVariant(variantData, basePlugin, gradleProjects))
            }
        }

        return androidProject
    }

    @NonNull
    private static String getModelVersion() {
        Class clazz = AndroidProject.class
        String className = clazz.getSimpleName() + ".class"
        String classPath = clazz.getResource(className).toString()
        if (!classPath.startsWith("jar")) {
            // Class not from JAR, unlikely
            return "unknown"
        }
        String manifestPath = classPath.substring(0, classPath.lastIndexOf("!") + 1) +
                "/META-INF/MANIFEST.MF"
        Manifest manifest = new Manifest(new URL(manifestPath).openStream())
        Attributes attr = manifest.getMainAttributes()
        String version = attr.getValue("Model-Version")
        if (version != null) {
            return version
        }

        return "unknown"
    }

    @NonNull
    private static VariantImpl createVariant(@NonNull BaseVariantData variantData,
                                             @NonNull BasePlugin basePlugin,
                                             @NonNull Set<Project> gradleProjects) {
        TestVariantData testVariantData = null
        if (variantData instanceof ApplicationVariantData ||
                variantData instanceof LibraryVariantData) {
            testVariantData = variantData.getTestVariantData(VariantConfiguration.Type.ANDROID_TEST)
        }

        AndroidArtifact mainArtifact = createArtifactInfo(
                ARTIFACT_MAIN, variantData, basePlugin, gradleProjects)

        String variantName = variantData.variantConfiguration.fullName

        // extra Android Artifacts
        AndroidArtifact testArtifact = testVariantData != null ?
                createArtifactInfo(ARTIFACT_ANDROID_TEST, testVariantData, basePlugin, gradleProjects) : null

        List<AndroidArtifact> extraAndroidArtifacts = Lists.newArrayList(
                basePlugin.getExtraAndroidArtifacts(variantName))
        if (testArtifact != null) {
            extraAndroidArtifacts.add(testArtifact)
        }

        // clone the Java Artifacts
        Collection<JavaArtifact> javaArtifacts = basePlugin.getExtraJavaArtifacts(variantName)
        List<JavaArtifact> clonedJavaArtifacts = Lists.newArrayListWithCapacity(javaArtifacts.size())
        for (JavaArtifact javaArtifact : javaArtifacts) {
            clonedJavaArtifacts.add(JavaArtifactImpl.clone(javaArtifact))
        }

        // if the target is a codename, override the model value.
        ApiVersion sdkVersionOverride = null
        IAndroidTarget androidTarget = basePlugin.androidBuilder.getTargetInfo().target
        AndroidVersion version = androidTarget.getVersion()
        if (version.codename != null) {
            sdkVersionOverride = ApiVersionImpl.clone(version)
        }

        VariantImpl variant = new VariantImpl(
                variantName,
                variantData.variantConfiguration.baseName,
                variantData.variantConfiguration.buildType.name,
                getProductFlavorNames(variantData),
                ProductFlavorImpl.cloneFlavor(
                        variantData.variantConfiguration.mergedFlavor,
                        sdkVersionOverride,
                        sdkVersionOverride),
                mainArtifact,
                extraAndroidArtifacts,
                clonedJavaArtifacts)

        return variant
    }

    private static AndroidArtifact createArtifactInfo(
            @NonNull String name,
            @NonNull BaseVariantData variantData,
            @NonNull BasePlugin basePlugin,
            @NonNull Set<Project> gradleProjects) {
        VariantConfiguration variantConfiguration = variantData.variantConfiguration

        SigningConfig signingConfig = variantConfiguration.signingConfig
        String signingConfigName = null
        if (signingConfig != null) {
            signingConfigName = signingConfig.name
        }

        SourceProvider variantSourceProvider = null;
        SourceProvider multiFlavorSourceProvider = null;

        if (ARTIFACT_MAIN.equals(name)) {
            variantSourceProvider = variantData.variantConfiguration.variantSourceProvider
            multiFlavorSourceProvider = variantData.variantConfiguration.multiFlavorSourceProvider
        } else {
            SourceProviderContainer container = getSourceProviderContainer(
                    basePlugin.getExtraVariantSourceProviders(variantData.getVariantConfiguration().getFullName()),
                    name)
            if (container != null) {
                variantSourceProvider = container.sourceProvider
            }
        }

        variantSourceProvider = variantSourceProvider != null ? SourceProviderImpl.cloneProvider(variantSourceProvider) : null
        multiFlavorSourceProvider = multiFlavorSourceProvider != null ? SourceProviderImpl.cloneProvider(multiFlavorSourceProvider) : null

        // get the outputs
        List<? extends BaseVariantOutputData> variantOutputs = variantData.outputs
        List<AndroidArtifactOutput> outputs = Lists.newArrayListWithCapacity(variantOutputs.size())

        for (BaseVariantOutputData variantOutputData : variantOutputs) {
            Integer versionCode = (variantOutputData instanceof ApkVariantOutputData) ?
                    ((ApkVariantOutputData) variantOutputData).versionCode :
                    variantConfiguration.mergedFlavor.versionCode

            int intVersionCode = versionCode != null ? versionCode.intValue() : 1;

            ImmutableCollection.Builder<OutputFile> outputFiles = ImmutableList.builder();

            // add the main APK
            outputFiles.add(new OutputFileImpl(
                    variantOutputData.mainOutputFile.filters,
                    variantOutputData.mainOutputFile.getType().name(),
                    variantOutputData.outputFile));

            for (ApkOutputFile splitApk : variantOutputData.outputs) {
                if (splitApk.getType() == OutputFile.OutputType.SPLIT) {
                    outputFiles.add(new OutputFileImpl(
                            splitApk.getFilters(), OutputFile.SPLIT, splitApk.getOutputFile()));
                }
            }

            // add the main APK.
            outputs.add(new AndroidArtifactOutputImpl(
                    outputFiles.build(),
                    variantOutputData.assembleTask.name,
                    variantOutputData.manifestProcessorTask.manifestOutputFile,
                    intVersionCode));
        }

        AbstractCompile compileTask = variantData.javaCompileTask
        if (compileTask == null) {
            compileTask = variantData.jackTask
        }

        return new AndroidArtifactImpl(
                name,
                outputs,
                variantData.assembleVariantTask.name,
                variantConfiguration.isSigningReady() || variantData.outputsAreSigned,
                signingConfigName,
                variantConfiguration.applicationId,
                variantData.sourceGenTask.name,
                variantData.compileTask.name,
                getGeneratedSourceFolders(variantData),
                getGeneratedResourceFolders(variantData),
                compileTask.destinationDir,
                DependenciesImpl.cloneDependencies(variantData, basePlugin, gradleProjects),
                variantSourceProvider,
                multiFlavorSourceProvider,
                variantConfiguration.supportedAbis,
                variantConfiguration.getMergedBuildConfigFields(),
                variantConfiguration.getMergedResValues())
    }

    @NonNull
    private static List<String> getProductFlavorNames(@NonNull BaseVariantData variantData) {
        List<String> flavorNames = Lists.newArrayList()

        for (DefaultProductFlavor flavor : variantData.variantConfiguration.productFlavors) {
            flavorNames.add(flavor.name)
        }

        return flavorNames
    }

    @NonNull
    private static List<File> getGeneratedSourceFolders(
            @Nullable BaseVariantData<? extends BaseVariantOutputData> variantData) {
        if (variantData == null) {
            return Collections.emptyList()
        }

        List<File> folders = Lists.newArrayList()

        // The R class is only generated by the first output.
        BaseVariantOutputData variantOutputData = variantData.outputs.get(0)
        folders.add(variantOutputData.processResourcesTask.sourceOutputDir)

        folders.add(variantData.aidlCompileTask.sourceOutputDir)
        folders.add(variantData.generateBuildConfigTask.sourceOutputDir)
        if (!variantData.variantConfiguration.mergedFlavor.renderscriptNdkModeEnabled) {
            folders.add(variantData.renderscriptCompileTask.sourceOutputDir)
        }

        List<File> extraFolders = variantData.extraGeneratedSourceFolders
        if (extraFolders != null) {
            folders.addAll(extraFolders)
        }

        return folders
    }

    @NonNull
    private static List<File> getGeneratedResourceFolders(@Nullable BaseVariantData variantData) {
        if (variantData == null) {
            return Collections.emptyList()
        }

        return Lists.asList(
                variantData.renderscriptCompileTask.resOutputDir,
                variantData.generateResValuesTask.resOutputDir)
    }

    @NonNull
    private static Collection<SigningConfig> cloneSigningConfigs(
            @NonNull Collection<SigningConfig> signingConfigs) {
        Collection<SigningConfig> results = Lists.newArrayListWithCapacity(signingConfigs.size())

        for (SigningConfig signingConfig : signingConfigs) {
            results.add(SigningConfigImpl.createSigningConfig(signingConfig))
        }

        return results
    }

    @Nullable
    private static SourceProviderContainer getSourceProviderContainer(
            @NonNull Collection<SourceProviderContainer> items,
            @NonNull String name) {
        for (SourceProviderContainer item : items) {
            if (name.equals(item.getArtifactName())) {
                return item;
            }
        }

        return null;
    }
}
