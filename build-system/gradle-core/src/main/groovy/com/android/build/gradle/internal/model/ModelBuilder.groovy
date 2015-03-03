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
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.api.ApkOutputFile
import com.android.build.gradle.internal.BuildTypeData
import com.android.build.gradle.internal.ExtraModelInfo
import com.android.build.gradle.internal.ProductFlavorData
import com.android.build.gradle.internal.TaskManager
import com.android.build.gradle.internal.VariantManager
import com.android.build.gradle.internal.variant.ApkVariantOutputData
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.build.gradle.internal.variant.BaseVariantOutputData
import com.android.build.gradle.internal.variant.TestVariantData
import com.android.build.gradle.internal.variant.TestedVariantData
import com.android.builder.core.AndroidBuilder
import com.android.builder.core.DefaultProductFlavor
import com.android.builder.core.VariantConfiguration
import com.android.builder.core.VariantType
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
import com.android.builder.model.SyncIssue
import com.android.sdklib.AndroidVersion
import com.android.sdklib.IAndroidTarget
import com.google.common.collect.ImmutableCollection
import com.google.common.collect.ImmutableList
import com.google.common.collect.Lists
import groovy.transform.CompileStatic
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.tooling.provider.model.ToolingModelBuilder

import java.util.jar.Attributes
import java.util.jar.Manifest

import static com.android.builder.model.AndroidProject.ARTIFACT_MAIN
/**
 * Builder for the custom Android model.
 */
@CompileStatic
public class ModelBuilder implements ToolingModelBuilder {

    AndroidBuilder androidBuilder

    BaseExtension extension

    ExtraModelInfo extraModelInfo

    VariantManager variantManager

    TaskManager taskManager

    boolean isLibrary

    ModelBuilder(
            AndroidBuilder androidBuilder,
            VariantManager variantManager,
            TaskManager taskManager,
            BaseExtension extension,
            ExtraModelInfo extraModelInfo,
            boolean isLibrary) {
        this.androidBuilder = androidBuilder
        this.extension = extension
        this.extraModelInfo = extraModelInfo
        this.variantManager = variantManager
        this.taskManager = taskManager
        this.isLibrary = isLibrary
    }

    @Override
    public boolean canBuild(String modelName) {
        // The default name for a model is the name of the Java interface.
        return modelName == AndroidProject.name
    }

    @Override
    public Object buildAll(String modelName, Project project) {
        NamedDomainObjectContainer<SigningConfig> signingConfigs

        // Cast is needed due to covariance issues.
        signingConfigs = extension.signingConfigs as NamedDomainObjectContainer<SigningConfig>

        // Get the boot classpath. This will ensure the target is configured.
        List<String> bootClasspath = androidBuilder.bootClasspathAsStrings

        List<File> frameworkSource = Collections.emptyList();

        // List of extra artifacts, with all test variants added.
        List<ArtifactMetaData> artifactMetaDataList = extraModelInfo.extraArtifacts.collect()

        VariantType.testingTypes
                .collect { new ArtifactMetaDataImpl(it.artifactName, true, it.artifactType) }
                .each artifactMetaDataList.&add

        LintOptions lintOptions = com.android.build.gradle.internal.dsl.LintOptions.create(extension.lintOptions)

        AaptOptions aaptOptions = AaptOptionsImpl.create(extension.aaptOptions)

        List<SyncIssue> syncIssues = Lists.newArrayList(extraModelInfo.syncIssues.values());

        def manifestData = parseManifestData()

        DefaultAndroidProject androidProject = new DefaultAndroidProject(
                manifestData.modelVersion as String,
                project.name,
                androidBuilder.getTarget().hashString(),
                bootClasspath,
                frameworkSource,
                cloneSigningConfigs(signingConfigs),
                aaptOptions,
                artifactMetaDataList,
                findUnresolvedDependencies(syncIssues),
                syncIssues,
                extension.compileOptions,
                lintOptions,
                project.getBuildDir(),
                extension.resourcePrefix,
                isLibrary,
                manifestData.modelApiVersion as int)

        androidProject.setDefaultConfig(ProductFlavorContainerImpl.createProductFlavorContainer(
                variantManager.defaultConfig,
                extraModelInfo.getExtraFlavorSourceProviders(
                        variantManager.defaultConfig.productFlavor.name)))

        for (BuildTypeData btData : variantManager.buildTypes.values()) {
            androidProject.addBuildType(BuildTypeContainerImpl.create(
                    btData,
                    extraModelInfo.getExtraBuildTypeSourceProviders(btData.buildType.name)))
        }
        for (ProductFlavorData pfData : variantManager.productFlavors.values()) {
            androidProject.addProductFlavors(ProductFlavorContainerImpl.createProductFlavorContainer(
                    pfData,
                    extraModelInfo.getExtraFlavorSourceProviders(pfData.productFlavor.name)))
        }

        for (BaseVariantData variantData : variantManager.variantDataList) {
            if (!variantData.type.isForTesting()) {
                androidProject.addVariant(createVariant(variantData))
            }
        }

        return androidProject
    }

    @NonNull
    private static Map parseManifestData() {
        Class clazz = AndroidProject.class
        String className = clazz.getSimpleName() + ".class"
        String classPath = clazz.getResource(className).toString()
        if (!classPath.startsWith("jar")) {
            // Class not from JAR, unlikely
            return [modelVersion: "unknown", modelApiVersion: 0]
        }
        String manifestPath = classPath.substring(0, classPath.lastIndexOf("!") + 1) +
                "/META-INF/MANIFEST.MF"
        Manifest manifest = new Manifest(new URL(manifestPath).openStream())
        Attributes attr = manifest.getMainAttributes()
        String modelVersion = attr.getValue("Model-Version")
        def modelApiVersion = attr.getValue("Model-Api-Version")
        if (modelApiVersion) {
            try {
                modelApiVersion = modelApiVersion as int
            } catch (ignored) {
                modelApiVersion = 0
            }
        }
        [modelVersion: modelVersion ?: "unknown",
         modelApiVersion: modelApiVersion]
    }

    @NonNull
    private VariantImpl createVariant(
            @NonNull BaseVariantData variantData) {
        AndroidArtifact mainArtifact = createAndroidArtifact(
                ARTIFACT_MAIN, variantData, androidBuilder, extraModelInfo)

        String variantName = variantData.variantConfiguration.fullName

        List<AndroidArtifact> extraAndroidArtifacts = Lists.newArrayList(
                extraModelInfo.getExtraAndroidArtifacts(variantName))
        // Make sure all extra artifacts are serializable.
        List<JavaArtifact> extraJavaArtifacts =
                extraModelInfo.getExtraJavaArtifacts(variantName).collect(JavaArtifactImpl.&clone)

        if (variantData instanceof TestedVariantData) {
            for (variantType in VariantType.testingTypes) {
                TestVariantData testVariantData = variantData.getTestVariantData(variantType)
                switch (testVariantData?.type) {
                    case VariantType.ANDROID_TEST:
                        extraAndroidArtifacts.add(createAndroidArtifact(
                                variantType.artifactName,
                                testVariantData,
                                androidBuilder,
                                extraModelInfo))
                        break
                    case VariantType.UNIT_TEST:
                        extraJavaArtifacts.add(createUnitTestsJavaArtifact(
                                variantType,
                                testVariantData))
                        break
                    case null:
                        // No test variant with the given type for the current variant.
                        break
                    default:
                        throw new IllegalArgumentException(
                                "Unsupported test variant type ${variantType}.")
                }
            }
        }

        // if the target is a codename, override the model value.
        ApiVersion sdkVersionOverride = null
        IAndroidTarget androidTarget = androidBuilder.getTargetInfo().target
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
                extraJavaArtifacts)

        return variant
    }

    private JavaArtifactImpl createUnitTestsJavaArtifact(
            @NonNull VariantType variantType,
            @NonNull BaseVariantData variantData) {
        def sourceProviders = determineSourceProviders(variantType.artifactName, variantData, extraModelInfo)
        def dependencies = DependenciesImpl.cloneDependencies(variantData, androidBuilder)

        // Add the mockable JAR path. It will be created before tests are actually run from the IDE.
        dependencies.javaLibraries.add(
                new JavaLibraryImpl(
                        taskManager.createMockableJar.outputFile,
                        null,
                        null))

        return new JavaArtifactImpl(
                variantType.artifactName,
                variantData.assembleVariantTask.name,
                variantData.compileTask.name,
                [variantData.prepareDependenciesTask.name, taskManager.createMockableJar.name] as Set,
                variantData.extraGeneratedSourceFolders,
                variantData.javaCompileTask.destinationDir,
                dependencies,
                sourceProviders.variantSourceProvider,
                sourceProviders.multiFlavorSourceProvider)
    }

    private static AndroidArtifact createAndroidArtifact(
            @NonNull String name,
            @NonNull BaseVariantData variantData,
            @NonNull AndroidBuilder androidBuilder,
            @NonNull ExtraModelInfo extraModelInfo) {
        VariantConfiguration variantConfiguration = variantData.variantConfiguration

        SigningConfig signingConfig = variantConfiguration.signingConfig
        String signingConfigName = null
        if (signingConfig != null) {
            signingConfigName = signingConfig.name
        }

        def sourceProviders = determineSourceProviders(name, variantData, extraModelInfo)

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
                    variantOutputData.getMainOutputFile().filters,
                    variantOutputData.getMainOutputFile().getType().name(),
                    variantOutputData.outputFile));

            for (ApkOutputFile splitApk : variantOutputData.getOutputs()) {
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
                variantData.javaCompileTask.destinationDir,
                DependenciesImpl.cloneDependencies(variantData, androidBuilder),
                sourceProviders.variantSourceProvider,
                sourceProviders.multiFlavorSourceProvider,
                variantConfiguration.supportedAbis,
                variantConfiguration.getMergedBuildConfigFields(),
                variantConfiguration.getMergedResValues())
    }

    private static SourceProviders determineSourceProviders(
            @NonNull String name,
            @NonNull BaseVariantData variantData,
            @NonNull ExtraModelInfo extraModelInfo) {
        SourceProvider variantSourceProvider = null
        SourceProvider multiFlavorSourceProvider = null

        if (ARTIFACT_MAIN.equals(name)) {
            variantSourceProvider = variantData.variantConfiguration.variantSourceProvider
            multiFlavorSourceProvider = variantData.variantConfiguration.multiFlavorSourceProvider
        } else {
            SourceProviderContainer container = getSourceProviderContainer(
                    extraModelInfo.getExtraVariantSourceProviders(
                            variantData.getVariantConfiguration().getFullName()),
                    name)
            if (container != null) {
                variantSourceProvider = container.sourceProvider
            }
        }

        return new SourceProviders(
                variantSourceProvider: variantSourceProvider != null ?
                        SourceProviderImpl.cloneProvider(variantSourceProvider) :
                        null,
                multiFlavorSourceProvider: multiFlavorSourceProvider != null ?
                        SourceProviderImpl.cloneProvider(multiFlavorSourceProvider) :
                        null)
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

        List<File> result

        List<File> extraFolders = variantData.extraGeneratedResFolders
        if (extraFolders != null && !extraFolders.isEmpty()) {
            result = Lists.newArrayListWithCapacity(extraFolders.size() + 2)

            result.addAll(extraFolders)
        } else {
            result = Lists.newArrayListWithCapacity(2)
        }

        result.add(variantData.renderscriptCompileTask.resOutputDir)
        result.add(variantData.generateResValuesTask.resOutputDir)

        return result
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

    private static class SourceProviders {
        SourceProviderImpl variantSourceProvider
        SourceProviderImpl multiFlavorSourceProvider
    }

    /**
     * Return the unresolved dependencies in SyncIssues
     */
    private static Collection<String> findUnresolvedDependencies(Collection<SyncIssue> syncIssues) {
        List<String> unresolvedDependencies = Lists.newArrayList();

        for (SyncIssue issue : syncIssues) {
            if (issue.getType() == SyncIssue.TYPE_UNRESOLVED_DEPENDENCY) {
                unresolvedDependencies.add(issue.getData());
            }
        }
        return unresolvedDependencies;
    }

}
