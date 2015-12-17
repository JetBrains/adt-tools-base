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

package com.android.build.gradle.model;

import static com.android.build.gradle.model.AndroidComponentModelPlugin.COMPONENT_NAME;
import static com.android.build.gradle.model.ModelConstants.ARTIFACTS;
import static com.android.build.gradle.model.ModelConstants.EXTERNAL_BUILD_CONFIG;
import static com.android.build.gradle.model.ModelConstants.NATIVE_DEPENDENCIES;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.LanguageRegistryUtils;
import com.android.build.gradle.internal.NativeDependencyLinkage;
import com.android.build.gradle.internal.NdkHandler;
import com.android.build.gradle.internal.ProductFlavorCombo;
import com.android.build.gradle.internal.core.Abi;
import com.android.build.gradle.internal.dependency.ArtifactContainer;
import com.android.build.gradle.internal.dependency.NativeDependencyResolveResult;
import com.android.build.gradle.internal.dependency.NativeLibraryArtifact;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.managed.BuildType;
import com.android.build.gradle.managed.NativeBuildConfig;
import com.android.build.gradle.managed.NativeLibrary;
import com.android.build.gradle.managed.NativeSourceFolder;
import com.android.build.gradle.managed.NativeToolchain;
import com.android.build.gradle.managed.NdkAbiOptions;
import com.android.build.gradle.managed.NdkConfig;
import com.android.build.gradle.managed.ProductFlavor;
import com.android.build.gradle.ndk.internal.BinaryToolHelper;
import com.android.build.gradle.ndk.internal.NdkConfiguration;
import com.android.build.gradle.ndk.internal.NdkExtensionConvention;
import com.android.build.gradle.ndk.internal.NdkNamingScheme;
import com.android.build.gradle.ndk.internal.StlNativeToolSpecification;
import com.android.build.gradle.ndk.internal.ToolchainConfiguration;
import com.android.builder.core.BuilderConstants;
import com.android.builder.core.VariantConfiguration;
import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.base.Predicate;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

import org.gradle.api.Action;
import org.gradle.api.BuildableModelElement;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.internal.project.ProjectIdentifier;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.language.base.FunctionalSourceSet;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.base.internal.registry.LanguageRegistration;
import org.gradle.language.base.internal.registry.LanguageRegistry;
import org.gradle.language.c.plugins.CPlugin;
import org.gradle.language.cpp.plugins.CppPlugin;
import org.gradle.language.nativeplatform.HeaderExportingSourceSet;
import org.gradle.model.Defaults;
import org.gradle.model.Finalize;
import org.gradle.model.Model;
import org.gradle.model.ModelMap;
import org.gradle.model.Mutate;
import org.gradle.model.Path;
import org.gradle.model.RuleSource;
import org.gradle.model.Validate;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.nativeplatform.BuildTypeContainer;
import org.gradle.nativeplatform.FlavorContainer;
import org.gradle.nativeplatform.NativeBinarySpec;
import org.gradle.nativeplatform.NativeLibraryBinarySpec;
import org.gradle.nativeplatform.NativeLibrarySpec;
import org.gradle.nativeplatform.SharedLibraryBinarySpec;
import org.gradle.nativeplatform.StaticLibraryBinarySpec;
import org.gradle.nativeplatform.platform.NativePlatform;
import org.gradle.nativeplatform.toolchain.NativeToolChainRegistry;
import org.gradle.platform.base.BinaryContainer;
import org.gradle.platform.base.ComponentSpecContainer;
import org.gradle.platform.base.PlatformContainer;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

/**
 * Plugin for Android NDK applications.
 */
public class NdkComponentModelPlugin implements Plugin<Project> {
    private Project project;
    @NonNull
    private final ToolingModelBuilderRegistry toolingRegistry;
    @NonNull
    private final ModelRegistry modelRegistry;

    @Inject
    private NdkComponentModelPlugin(
            @NonNull ToolingModelBuilderRegistry toolingRegistry,
            @NonNull ModelRegistry modelRegistry) {
        this.toolingRegistry = toolingRegistry;
        this.modelRegistry = modelRegistry;
    }

    @Override
    public void apply(Project project) {
        this.project = project;

        project.getPluginManager().apply(AndroidComponentModelPlugin.class);
        project.getPluginManager().apply(CPlugin.class);
        project.getPluginManager().apply(CppPlugin.class);

        toolingRegistry.register(new NativeComponentModelBuilder(modelRegistry));
    }

    public static class Rules extends RuleSource {

        @Defaults
        public static void initializeNdkConfig(@Path("android.ndk") NdkConfig ndk) {
            ndk.setModuleName("");
            ndk.setToolchain("");
            ndk.setToolchainVersion("");
            ndk.setStl("");
            ndk.setRenderscriptNdkMode(false);
        }

        @Finalize
        public static void setDefaultNdkExtensionValue(@Path("android.ndk") NdkConfig ndkConfig) {
            NdkExtensionConvention.setExtensionDefault(ndkConfig);
        }

        @Validate
        public static void checkNdkDir(
                NdkHandler ndkHandler,
                @Path("android.ndk") NdkConfig ndkConfig) {
            if (!ndkConfig.getModuleName().isEmpty() && !ndkHandler.isNdkDirConfigured()) {
                throw new InvalidUserDataException(
                        "NDK location not found. Define location with ndk.dir in the "
                                + "local.properties file or with an ANDROID_NDK_HOME environment "
                                + "variable.");
            }
            if (ndkHandler.isNdkDirConfigured()) {
                //noinspection ConstantConditions - isNdkDirConfigured ensures getNdkDirectory is not null
                if (!ndkHandler.getNdkDirectory().exists()) {
                    throw new InvalidUserDataException(
                            "Specified NDK location does not exists.  Please ensure ndk.dir in "
                                    + "local.properties file or ANDROID_NDK_HOME is configured "
                                    + "correctly.");

                }
            }
        }

        @Defaults
        public static void addDefaultNativeSourceSet(
                @Path("android.sources") ModelMap<FunctionalSourceSet> sources,
                final LanguageRegistry languageRegistry) {
            final LanguageRegistration languageRegistration =
                    LanguageRegistryUtils.find(languageRegistry, NativeSourceSet.class);
            sources.beforeEach(new Action<FunctionalSourceSet>() {
                @Override
                public void execute(FunctionalSourceSet sourceSet) {
                    sourceSet.registerFactory(
                            languageRegistration.getSourceSetType(),
                            languageRegistration.getSourceSetFactory(sourceSet.getName()));
                    sourceSet.create("jni", NativeSourceSet.class);
                }
            });
        }

        @Model(ModelConstants.NDK_HANDLER)
        public static NdkHandler ndkHandler(
                ProjectIdentifier projectId,
                @Path("android.compileSdkVersion") String compileSdkVersion,
                @Path("android.ndk") NdkConfig ndkConfig) {
            while (projectId.getParentIdentifier() != null) {
                projectId = projectId.getParentIdentifier();
            }

            return new NdkHandler(
                    projectId.getProjectDir(),
                    Objects.firstNonNull(ndkConfig.getPlatformVersion(), compileSdkVersion),
                    ndkConfig.getToolchain(),
                    ndkConfig.getToolchainVersion());
        }

        @Defaults
        public static void initBuildTypeNdk(
                @Path("android.buildTypes") ModelMap<BuildType> buildTypes) {
            buildTypes.named(
                    BuilderConstants.DEBUG,
                    new Action<BuildType>() {
                        @Override
                        public void execute(BuildType buildType) {
                            if (buildType.getNdk().getDebuggable() == null) {
                                buildType.getNdk().setDebuggable(true);
                            }
                        }
                    });
        }

        @Mutate
        public static void createAndroidPlatforms(PlatformContainer platforms,
                NdkHandler ndkHandler) {
            if (!ndkHandler.isNdkDirConfigured()) {
                return;
            }
            // Create android platforms.
            ToolchainConfiguration.configurePlatforms(platforms, ndkHandler);
        }

        @Validate
        public static void validateAbi(@Path("android.abis") ModelMap<NdkAbiOptions> abiConfigs) {
            abiConfigs.afterEach(new Action<NdkAbiOptions>() {
                @Override
                public void execute(NdkAbiOptions abiOptions) {
                    if (Abi.getByName(abiOptions.getName()) == null) {
                        throw new InvalidUserDataException("Target ABI '" + abiOptions.getName()
                                + "' is not supported.");
                    }
                }
            });
        }

        @Mutate
        public static void createToolchains(
                NativeToolChainRegistry toolchainRegistry,
                @Path("android.abis") ModelMap<NdkAbiOptions> abis,
                @Path("android.ndk") NdkConfig ndkConfig,
                NdkHandler ndkHandler) {
            if (!ndkHandler.isNdkDirConfigured()) {
                return;
            }
            // Create toolchain for each ABI.
            ToolchainConfiguration.configureToolchain(
                    toolchainRegistry,
                    ndkConfig.getToolchain(),
                    abis,
                    ndkHandler);
        }

        @Mutate
        public static void createNativeBuildTypes(BuildTypeContainer nativeBuildTypes,
                @Path("android.buildTypes") ModelMap<BuildType> androidBuildTypes) {
            for (BuildType buildType : androidBuildTypes.values()) {
                nativeBuildTypes.maybeCreate(buildType.getName());
            }
        }

        @Mutate
        public static void createNativeFlavors(
                FlavorContainer nativeFlavors,
                List<ProductFlavorCombo<ProductFlavor>> androidFlavorGroups) {
            if (androidFlavorGroups.isEmpty()) {
                // Create empty native flavor to override Gradle's default name.
                nativeFlavors.maybeCreate("");
            } else {
                for (ProductFlavorCombo group : androidFlavorGroups) {
                    nativeFlavors.maybeCreate(group.getName());
                }
            }
        }

        @Mutate
        public static void createNativeLibrary(
                final ComponentSpecContainer specs,
                @Path("android.ndk") final NdkConfig ndkConfig,
                final NdkHandler ndkHandler,
                @Path("android.sources") final ModelMap<FunctionalSourceSet> sources,
                @Path("buildDir") final File buildDir,
                final ServiceRegistry serviceRegistry) {
            if (!ndkHandler.isNdkDirConfigured()) {
                return;
            }
            if (!ndkConfig.getModuleName().isEmpty()) {
                specs.create(
                        ndkConfig.getModuleName(),
                        NativeLibrarySpec.class,
                        new Action<NativeLibrarySpec>() {
                            @Override
                            public void execute(final NativeLibrarySpec nativeLib) {
                                ((DefaultAndroidComponentSpec) specs.get(COMPONENT_NAME))
                                        .setNativeLibrary(nativeLib);
                                NdkConfiguration.configureProperties(
                                        nativeLib,
                                        sources,
                                        buildDir,
                                        ndkHandler,
                                        serviceRegistry);
                            }
                        });
                DefaultAndroidComponentSpec androidSpecs =
                        (DefaultAndroidComponentSpec) specs.get(COMPONENT_NAME);
                androidSpecs.setNativeLibrary(
                        (NativeLibrarySpec) specs.get(ndkConfig.getModuleName()));
            }
        }

        /**
         * Find the native dependency for each native binaries.
         *
         * TODO: Remove duplication of functionality with NdkConfiguration.configureProperties.
         * We need to predict the NativeBinarySpec that will be produce instead of creating this map
         * after the binaries are created.
         */
        @Model(NATIVE_DEPENDENCIES)
        public static Multimap<String, NativeDependencyResolveResult> resolveNativeDependencies(
                ModelMap<NativeLibraryBinarySpec> nativeBinaries,
                @Path("android.sources") final ModelMap<FunctionalSourceSet> sources,
                final ServiceRegistry serviceRegistry) {
            Multimap<String, NativeDependencyResolveResult> dependencies =
                    ArrayListMultimap.create();
            for (NativeLibraryBinarySpec nativeBinary : nativeBinaries.values()) {
                Collection<NativeSourceSet> jniSources =
                        NdkConfiguration.findNativeSourceSets(nativeBinary, sources).values();

                for (NativeSourceSet jniSource : jniSources) {
                    dependencies.put(
                            nativeBinary.getName(),
                            NdkConfiguration.resolveDependency(
                                    serviceRegistry,
                                    nativeBinary,
                                    jniSource));
                }
            }
            return dependencies;
        }

        @Mutate
        public static void createAdditionalTasksForNatives(
                final ModelMap<Task> tasks,
                ModelMap<AndroidComponentSpec> specs,
                @Path("android.ndk") final NdkConfig ndkConfig,
                @Path(NATIVE_DEPENDENCIES) final Multimap<String, NativeDependencyResolveResult> dependencies,
                final NdkHandler ndkHandler,
                BinaryContainer binaries,
                @Path("buildDir") final File buildDir) {
            if (!ndkHandler.isNdkDirConfigured()) {
                return;
            }
            final DefaultAndroidComponentSpec androidSpec =
                    (DefaultAndroidComponentSpec) specs.get(COMPONENT_NAME);
            if (androidSpec.getNativeLibrary() != null) {
                binaries.withType(DefaultAndroidBinary.class, new Action<DefaultAndroidBinary>() {
                    @Override
                    public void execute(DefaultAndroidBinary binary) {
                        for (NativeBinarySpec nativeBinary : binary.getNativeBinaries()) {
                            NdkConfiguration.createTasks(
                                    tasks,
                                    nativeBinary,
                                    buildDir,
                                    binary.getMergedNdkConfig(),
                                    ndkHandler,
                                    dependencies);
                        }
                    }
                });
            }
        }

        @Mutate
        public static void configureNativeBinary(
                BinaryContainer binaries,
                ComponentSpecContainer specs,
                @Path("android.ndk") final NdkConfig ndkConfig,
                @Path("buildDir") final File buildDir,
                final NdkHandler ndkHandler) {
            final NativeLibrarySpec library = specs.withType(NativeLibrarySpec.class)
                    .get(ndkConfig.getModuleName());

            final ImmutableSet.Builder<String> builder = ImmutableSet.builder();
            for(Abi abi : NdkHandler.getAbiList()) {
                builder.add(abi.getName());
            }
            final ImmutableSet<String> supportedAbis = builder.build();

            binaries.withType(
                    DefaultAndroidBinary.class,
                    new Action<DefaultAndroidBinary>() {
                        @Override
                        public void execute(DefaultAndroidBinary binary) {
                            binary.computeMergedNdk(
                                    ndkConfig,
                                    binary.getProductFlavors(),
                                    binary.getBuildType());
                            if (binary.getMergedNdkConfig().getAbiFilters().isEmpty()) {
                                binary.getMergedNdkConfig().getAbiFilters().addAll(supportedAbis);
                            }

                            if (library != null) {
                                Collection<NativeLibraryBinarySpec> nativeBinaries =
                                        getNativeBinaries(
                                                library,
                                                binary.getBuildType(),
                                                binary.getProductFlavors());
                                for (NativeLibraryBinarySpec nativeBin : nativeBinaries) {
                                    if (binary.getMergedNdkConfig().getAbiFilters().contains(
                                            nativeBin.getTargetPlatform().getName())) {
                                        NdkConfiguration.configureBinary(
                                                nativeBin,
                                                buildDir,
                                                binary.getMergedNdkConfig(),
                                                ndkHandler);
                                        binary.getNativeBinaries().add(nativeBin);
                                    }
                                }
                            }
                        }
                    });
        }

        /**
         * Create external build config to allow NativeComponentModelBuilder to create native model.
         */
        @Model(EXTERNAL_BUILD_CONFIG)
        public static void createNativeBuildModel(
                NativeBuildConfig config,
                ModelMap<DefaultAndroidBinary> binaries,
                final NdkHandler ndkHandler) {
            for (final DefaultAndroidBinary binary : binaries.values()) {
                for (final NativeLibraryBinarySpec nativeBinary : binary.getNativeBinaries()) {
                    if (!(nativeBinary instanceof SharedLibraryBinarySpec)) {
                        continue;
                    }
                    final String abi = nativeBinary.getTargetPlatform().getName();
                    config.getLibraries().create(
                            binary.getName() + '-' + abi,
                            new Action<NativeLibrary>() {
                                @Override
                                public void execute(NativeLibrary nativeLibrary) {
                                    nativeLibrary.setOutput(
                                            ((SharedLibraryBinarySpec) nativeBinary).getSharedLibraryFile());
                                    Set<File> srcFolders = Sets.newHashSet();

                                    for (LanguageSourceSet sourceSet : nativeBinary.getSources()) {
                                        srcFolders.addAll(sourceSet.getSource().getSrcDirs());
                                    }
                                    final List<String> cFlags =
                                            BinaryToolHelper.getCCompiler(nativeBinary).getArgs();
                                    final List<String> cppFlags =
                                            BinaryToolHelper.getCppCompiler(nativeBinary).getArgs();
                                    for (final File srcFolder : srcFolders) {
                                        nativeLibrary.getFolders().create(
                                                new Action<NativeSourceFolder>() {
                                                    @Override
                                                    public void execute(NativeSourceFolder nativeSourceFolder) {
                                                        nativeSourceFolder.setSrc(srcFolder);
                                                        nativeSourceFolder.getCFlags().addAll(cFlags);
                                                        nativeSourceFolder.getCppFlags().addAll(cppFlags);
                                                    }
                                                });
                                    }
                                    nativeLibrary.setToolchain("ndk-" + abi);
                                }
                            });
                }
            }
            for (final Abi abi : ndkHandler.getSupportedAbis()) {
                config.getToolchains().create("ndk-" + abi.getName(),
                        new Action<NativeToolchain>() {
                            @Override
                            public void execute(NativeToolchain nativeToolchain) {
                                nativeToolchain.setCCompilerExecutable(
                                        ndkHandler.getCCompiler(abi));
                                nativeToolchain.setCppCompilerExecutable(
                                        ndkHandler.getCppCompiler(abi));
                            }
                        });
            }
        }

        @Finalize
        public static void attachNativeTasksToAndroidBinary(ModelMap<AndroidBinary> binaries) {
            binaries.afterEach(new Action<AndroidBinary>() {
                @Override
                public void execute(AndroidBinary androidBinary) {
                    DefaultAndroidBinary binary = (DefaultAndroidBinary) androidBinary;
                    for (NativeLibraryBinarySpec nativeBinary : binary.getNativeBinaries()) {
                        if (binary.getTargetAbi().isEmpty() || binary.getTargetAbi().contains(
                                nativeBinary.getTargetPlatform().getName())) {
                            binary.getBuildTask().dependsOn(
                                    NdkNamingScheme.getNdkBuildTaskName(nativeBinary));
                        }
                    }
                }
            });
        }

        @Model(ARTIFACTS)
        public static void createNativeLibraryArtifacts(
                ArtifactContainer artifactContainer,
                ModelMap<DefaultAndroidBinary> binaries,
                @Path("android.sources") final ModelMap<FunctionalSourceSet> sources,
                final ModelMap<Task> tasks,
                @Path(NATIVE_DEPENDENCIES) final Multimap<String, NativeDependencyResolveResult> dependencies,
                NdkHandler ndkHandler) {
            for(final DefaultAndroidBinary binary : binaries.values()) {
                for (final NativeLibraryBinarySpec nativeBinary : binary.getNativeBinaries()) {
                    final String linkage = nativeBinary instanceof SharedLibraryBinarySpec
                            ? "shared"
                            : "static";
                    String name = Joiner.on('-').join(
                            binary.getName(),
                            nativeBinary.getTargetPlatform().getName(),
                            linkage);
                    artifactContainer.getNativeArtifacts().create(
                            name,
                            new CreateNativeLibraryArtifactAction(
                                    binary,
                                    nativeBinary,
                                    dependencies.get(nativeBinary.getName()),
                                    ndkHandler));
                }
            }
        }

        private static class CreateNativeLibraryArtifactAction
                implements Action<NativeLibraryArtifact> {
            private final DefaultAndroidBinary binary;
            private final NativeLibraryBinarySpec nativeBinary;
            private final Collection<NativeDependencyResolveResult> dependencies;
            private final NdkHandler ndkHandler;

            public CreateNativeLibraryArtifactAction(DefaultAndroidBinary binary,
                    NativeLibraryBinarySpec nativeBinary,
                    Collection<NativeDependencyResolveResult> dependencies,
                    NdkHandler ndkHandler) {
                this.binary = binary;
                this.nativeBinary = nativeBinary;
                this.dependencies = dependencies;
                this.ndkHandler = ndkHandler;
            }

            @Override
            public void execute(NativeLibraryArtifact artifact) {
                final NativeDependencyLinkage linkage =
                        nativeBinary instanceof SharedLibraryBinarySpec
                                ? NativeDependencyLinkage.SHARED
                                : NativeDependencyLinkage.STATIC;
                File output  = nativeBinary instanceof SharedLibraryBinarySpec
                        ? ((SharedLibraryBinarySpec) nativeBinary).getSharedLibraryFile()
                        : ((StaticLibraryBinarySpec) nativeBinary).getStaticLibraryFile();

                artifact.getLibraries().add(output);
                artifact.setBuildType(binary.getBuildType().getName());
                for (ProductFlavor flavor : binary.getProductFlavors()) {
                    artifact.getProductFlavors().add(flavor.getName());
                }
                artifact.setVariantName(binary.getName());
                artifact.setAbi(nativeBinary.getTargetPlatform().getName());
                artifact.setLinkage(linkage);
                artifact.setBuiltBy(nativeBinary);
                for (LanguageSourceSet sourceSet : nativeBinary.getSources()) {
                    if (sourceSet instanceof HeaderExportingSourceSet) {
                        HeaderExportingSourceSet source = (HeaderExportingSourceSet) sourceSet;
                        artifact.getExportedHeaderDirectories().addAll(
                                source.getExportedHeaders().getSrcDirs());
                    }
                }
                String stl = binary.getMergedNdkConfig().getStl();
                if (stl.endsWith("_shared")) {
                    NativePlatform abi = nativeBinary.getTargetPlatform();
                    final StlNativeToolSpecification stlConfig = new StlNativeToolSpecification(
                            ndkHandler,
                            stl,
                            abi);
                    artifact.getLibraries().add(stlConfig.getStlLib(abi.getName()));
                }

                // Include transitive dependencies.
                // Dynamic objects from dependencies needs to be added to the library list.
                Abi abi = Abi.getByName(nativeBinary.getTargetPlatform().getName());
                assert abi != null;
                for (NativeDependencyResolveResult dependency : dependencies) {
                    Iterables.addAll(
                            artifact.getLibraries(),
                            Iterables.filter(
                                    dependency.getLibraryFiles().get(abi),
                                    SHARED_OBJECT_FILTER));
                    Collection<NativeLibraryArtifact> artifacts = dependency.getNativeArtifacts();
                    for (NativeLibraryArtifact dep : artifacts) {
                        if (abi.getName().equals(dep.getAbi())) {
                            Iterables.addAll(
                                    artifact.getLibraries(),
                                    Iterables.filter(dep.getLibraries(), SHARED_OBJECT_FILTER));
                        }
                    }
                }
            }
        }

        private static final Predicate<File> SHARED_OBJECT_FILTER =
                new Predicate<File>() {
                    @Override
                    public boolean apply(File file) {
                        return file.getName().endsWith(".so");
                    }
                };

        /**
         * Remove unintended tasks created by Gradle native plugin from task list.
         *
         * Gradle native plugins creates static library tasks automatically.  This method removes
         * them to avoid cluttering the task list.
         */
        @Mutate
        public static void hideNativeTasks(TaskContainer tasks, BinaryContainer binaries) {
            // Gradle do not support a way to remove created tasks.  The best workaround is to clear
            // the group of the task and have another task depends on it.  Therefore, we have to
            // create a dummy task to depend on all the tasks that we do not want to show up on the
            // task list. The dummy task dependsOn itself, effectively making it non-executable and
            // invisible unless the --all option is use.
            final Task nonExecutableTask = tasks.create("nonExecutableTask");
            nonExecutableTask.dependsOn(nonExecutableTask);
            nonExecutableTask
                    .setDescription("Dummy task to hide other unwanted tasks in the task list.");

            binaries.withType(NativeLibraryBinarySpec.class, new Action<NativeLibraryBinarySpec>() {
                @Override
                public void execute(NativeLibraryBinarySpec binary) {
                    Task buildTask = binary.getBuildTask();
                    nonExecutableTask.dependsOn(buildTask);
                    buildTask.setGroup(null);
                }
            });
        }
    }


    public static void configureScopeForNdk(VariantScope scope) {
        VariantConfiguration config = scope.getVariantConfiguration();
        ImmutableSet.Builder<File> builder = ImmutableSet.builder();
        for (Abi abi : NdkHandler.getAbiList()) {
            scope.addNdkDebuggableLibraryFolders(
                    abi,
                    new File(
                            scope.getGlobalScope().getBuildDir(),
                            NdkNamingScheme.getDebugLibraryDirectoryName(
                                    config.getBuildType().getName(),
                                    config.getFlavorName(),
                                    abi.getName())));

            // Return the parent directory of the binaries' output.
            // If output directory is "/path/to/lib/platformName".  We want to return
            // "/path/to/lib".
            builder.add(new File(
                    scope.getGlobalScope().getBuildDir(),
                    NdkNamingScheme.getOutputDirectoryName(
                            config.getBuildType().getName(),
                            config.getFlavorName(),
                            abi.getName())).getParentFile());
        }
        scope.setNdkSoFolder(builder.build());
    }


    private static Collection<NativeLibraryBinarySpec> getNativeBinaries(
            NativeLibrarySpec library,
            final BuildType buildType,
            final List<ProductFlavor> productFlavors) {
        final ProductFlavorCombo<ProductFlavor> flavorGroup =
                new ProductFlavorCombo<ProductFlavor>(productFlavors);
        return ImmutableList.copyOf(Iterables.filter(
                library.getBinaries().withType(NativeLibraryBinarySpec.class).values(),
                new Predicate<NativeLibraryBinarySpec>() {
                    @Override
                    public boolean apply(NativeLibraryBinarySpec binary) {
                        return binary.getBuildType().getName().equals(buildType.getName())
                                && (binary.getFlavor().getName().equals(flavorGroup.getName())
                                || (productFlavors.isEmpty()
                                && binary.getFlavor().getName().equals("default")));
                    }
                }));
    }

    /**
     * Return library binaries for a VariantConfiguration.
     */
    @NonNull
    public Collection<? extends BuildableModelElement> getBinaries(
            @NonNull final VariantConfiguration variantConfig) {
        if (variantConfig.getType().isForTesting()) {
            // Do not return binaries for test variants as test source set is not supported at the
            // moment.
            return Collections.emptyList();
        }
        BinaryContainer binaries = (BinaryContainer) project.getExtensions().getByName("binaries");
        return binaries.withType(AndroidBinary.class).matching(
                new Spec<AndroidBinary>() {
                    @Override
                    public boolean isSatisfiedBy(AndroidBinary binary) {
                        return (binary.getName().equals(variantConfig.getFullName()));
                    }
                }
        );
    }
}
