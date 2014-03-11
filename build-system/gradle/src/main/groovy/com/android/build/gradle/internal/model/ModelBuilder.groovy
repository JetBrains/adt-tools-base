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
import com.android.build.gradle.AppPlugin
import com.android.build.gradle.BasePlugin
import com.android.build.gradle.LibraryPlugin
import com.android.build.gradle.internal.BuildTypeData
import com.android.build.gradle.internal.ProductFlavorData
import com.android.build.gradle.internal.dsl.LintOptionsImpl
import com.android.build.gradle.internal.variant.ApplicationVariantData
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.build.gradle.internal.variant.LibraryVariantData
import com.android.build.gradle.internal.variant.TestVariantData
import com.android.builder.DefaultProductFlavor
import com.android.builder.SdkParser
import com.android.builder.VariantConfiguration
import com.android.builder.model.AndroidArtifact
import com.android.builder.model.AndroidProject
import com.android.builder.model.ArtifactMetaData
import com.android.builder.model.JavaArtifact
import com.android.builder.model.LintOptions
import com.android.builder.model.SigningConfig
import com.android.builder.model.SourceProvider
import com.android.builder.model.SourceProviderContainer
import com.google.common.collect.Lists
import org.gradle.api.Project
import org.gradle.api.plugins.UnknownPluginException
import org.gradle.tooling.provider.model.ToolingModelBuilder

import java.util.jar.Attributes
import java.util.jar.Manifest

import static com.android.builder.model.AndroidProject.ARTIFACT_INSTRUMENT_TEST
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
        AppPlugin appPlugin = getPlugin(project, AppPlugin.class)
        LibraryPlugin libPlugin = null
        BasePlugin basePlugin = appPlugin

        Collection<SigningConfig> signingConfigs

        if (appPlugin == null) {
            basePlugin = libPlugin = getPlugin(project, LibraryPlugin.class)
        } else {
            signingConfigs = appPlugin.extension.signingConfigs
        }

        if (basePlugin == null) {
            project.logger.error("Failed to find Android plugin for project " + project.name)
            return null
        }

        if (libPlugin != null) {
            signingConfigs = Collections.singletonList(libPlugin.extension.debugSigningConfig)
        }

        SdkParser sdkParser = basePlugin.getLoadedSdkParser()
        List<String> bootClasspath = basePlugin.runtimeJarList
        List<File> frameworkSource = Collections.emptyList();
        String compileTarget = sdkParser.target.hashString()

        // list of extra artifacts
        List<ArtifactMetaData> artifactMetaDataList = Lists.newArrayList(basePlugin.extraArtifacts)
        // plus the instrumentation test one.
        artifactMetaDataList.add(
                new ArtifactMetaDataImpl(
                        ARTIFACT_INSTRUMENT_TEST,
                        true /*isTest*/,
                        ArtifactMetaData.TYPE_ANDROID));

        LintOptions lintOptions = LintOptionsImpl.create(basePlugin.extension.lintOptions)

        //noinspection GroovyVariableNotAssigned
        DefaultAndroidProject androidProject = new DefaultAndroidProject(
                getModelVersion(),
                project.name,
                compileTarget,
                bootClasspath,
                frameworkSource,
                cloneSigningConfigs(signingConfigs),
                artifactMetaDataList,
                basePlugin.unresolvedDependencies,
                basePlugin.extension.compileOptions,
                lintOptions,
                libPlugin != null)
                    .setDefaultConfig(ProductFlavorContainerImpl.createPFC(
                        basePlugin.defaultConfigData,
                        basePlugin.getExtraFlavorSourceProviders(basePlugin.defaultConfigData.productFlavor.name)))

        if (appPlugin != null) {
            for (BuildTypeData btData : appPlugin.buildTypes.values()) {
                androidProject.addBuildType(BuildTypeContainerImpl.createBTC(
                        btData,
                        basePlugin.getExtraBuildTypeSourceProviders(btData.buildType.name)))
            }
            for (ProductFlavorData pfData : appPlugin.productFlavors.values()) {
                androidProject.addProductFlavors(ProductFlavorContainerImpl.createPFC(
                        pfData,
                        basePlugin.getExtraFlavorSourceProviders(pfData.productFlavor.name)))
            }

        } else if (libPlugin != null) {
            androidProject.addBuildType(BuildTypeContainerImpl.createBTC(
                        libPlugin.debugBuildTypeData,
                        basePlugin.getExtraBuildTypeSourceProviders(libPlugin.debugBuildTypeData.buildType.name)))
                 .addBuildType(BuildTypeContainerImpl.createBTC(
                        libPlugin.releaseBuildTypeData,
                        basePlugin.getExtraBuildTypeSourceProviders(libPlugin.releaseBuildTypeData.buildType.name)))
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
            testVariantData = variantData.testVariantData
        }

        AndroidArtifact mainArtifact = createArtifactInfo(
                ARTIFACT_MAIN, variantData, basePlugin, gradleProjects)

        String variantName = variantData.variantConfiguration.fullName

        // extra Android Artifacts
        AndroidArtifact testArtifact = testVariantData != null ?
                createArtifactInfo(ARTIFACT_INSTRUMENT_TEST, testVariantData, basePlugin, gradleProjects) : null

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

        VariantImpl variant = new VariantImpl(
                variantName,
                variantData.variantConfiguration.baseName,
                variantData.variantConfiguration.buildType.name,
                getProductFlavorNames(variantData),
                ProductFlavorImpl.cloneFlavor(variantData.variantConfiguration.mergedFlavor),
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
        VariantConfiguration vC = variantData.variantConfiguration

        SigningConfig signingConfig = vC.signingConfig
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

        return new AndroidArtifactImpl(
                name,
                variantData.assembleTask.name,
                variantData.outputFile,
                vC.isSigningReady(),
                signingConfigName,
                vC.packageName,
                variantData.sourceGenTask.name,
                variantData.javaCompileTask.name,
                variantData.processManifestTask.manifestOutputFile,
                getGeneratedSourceFolders(variantData),
                getGeneratedResourceFolders(variantData),
                variantData.javaCompileTask.destinationDir,
                DependenciesImpl.cloneDependencies(variantData, basePlugin, gradleProjects),
                variantSourceProvider,
                multiFlavorSourceProvider)
    }

    @NonNull
    private static List<String> getProductFlavorNames(@NonNull BaseVariantData variantData) {
        List<String> flavorNames = Lists.newArrayList()

        for (DefaultProductFlavor flavor : variantData.variantConfiguration.flavorConfigs) {
            flavorNames.add(flavor.name)
        }

        return flavorNames
    }

    @NonNull
    private static List<File> getGeneratedSourceFolders(@Nullable BaseVariantData variantData) {
        if (variantData == null) {
            return Collections.emptyList()
        }

        List<File> folders = Lists.newArrayList()

        folders.add(variantData.processResourcesTask.sourceOutputDir)
        folders.add(variantData.aidlCompileTask.sourceOutputDir)
        folders.add(variantData.generateBuildConfigTask.sourceOutputDir)
        if (!variantData.variantConfiguration.mergedFlavor.renderscriptNdkMode) {
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

        return Collections.singletonList(variantData.renderscriptCompileTask.resOutputDir)
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

    /**
     * Safely queries a project for a given plugin class.
     * @param project the project to query
     * @param pluginClass the plugin class.
     * @return the plugin instance or null if it is not applied.
     */
    private static <T> T getPlugin(@NonNull Project project, @NonNull Class<T> pluginClass) {
        try {
            return project.getPlugins().findPlugin(pluginClass)
        } catch (UnknownPluginException ignored) {
            // ignore, return null below.
        }

        return null
    }
}
