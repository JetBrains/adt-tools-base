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

import static com.android.build.gradle.model.ModelConstants.ARTIFACTS;
import static com.android.build.gradle.model.ModelConstants.EXTERNAL_BUILD_CONFIG;
import static com.android.build.gradle.model.ModelConstants.NATIVE_DEPENDENCIES;

import com.android.annotations.NonNull;
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
import com.android.build.gradle.model.internal.AndroidBinaryInternal;
import com.android.build.gradle.model.internal.DefaultNativeSourceSet;
import com.android.build.gradle.ndk.internal.NdkConfiguration;
import com.android.build.gradle.ndk.internal.NdkExtensionConvention;
import com.android.build.gradle.ndk.internal.NdkNamingScheme;
import com.android.build.gradle.ndk.internal.StlNativeToolSpecification;
import com.android.build.gradle.ndk.internal.ToolchainConfiguration;
import com.android.builder.core.BuilderConstants;
import com.android.builder.core.VariantConfiguration;
import com.android.utils.NativeSourceFileExtensions;
import com.android.utils.StringHelper;
import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.base.Predicate;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

import org.gradle.api.Action;
import org.gradle.api.BuildableModelElement;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.internal.project.ProjectIdentifier;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.util.PatternFilterable;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.language.base.FunctionalSourceSet;
import org.gradle.language.base.LanguageSourceSet;
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
import org.gradle.model.internal.core.ModelPath;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.model.internal.type.ModelType;
import org.gradle.model.internal.type.ModelTypes;
import org.gradle.nativeplatform.BuildTypeContainer;
import org.gradle.nativeplatform.FlavorContainer;
import org.gradle.nativeplatform.NativeBinarySpec;
import org.gradle.nativeplatform.NativeDependencySet;
import org.gradle.nativeplatform.NativeLibraryBinary;
import org.gradle.nativeplatform.NativeLibraryBinarySpec;
import org.gradle.nativeplatform.NativeLibrarySpec;
import org.gradle.nativeplatform.SharedLibraryBinary;
import org.gradle.nativeplatform.SharedLibraryBinarySpec;
import org.gradle.nativeplatform.StaticLibraryBinarySpec;
import org.gradle.nativeplatform.platform.NativePlatform;
import org.gradle.nativeplatform.toolchain.NativeToolChainRegistry;
import org.gradle.platform.base.BinaryContainer;
import org.gradle.platform.base.BinarySpec;
import org.gradle.platform.base.LanguageType;
import org.gradle.platform.base.LanguageTypeBuilder;
import org.gradle.platform.base.PlatformContainer;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;

/**
 * Plugin for Android NDK applications.
 */
public class NdkComponentModelPlugin implements Plugin<Project> {

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
        project.getPluginManager().apply(AndroidComponentModelPlugin.class);
        project.getPluginManager().apply(CPlugin.class);
        project.getPluginManager().apply(CppPlugin.class);

        toolingRegistry.register(new NativeComponentModelBuilder(modelRegistry));
    }

    public static class Rules extends RuleSource {

        @LanguageType
        public static void registerNativeSourceSet(LanguageTypeBuilder<NativeSourceSet> builder) {
            builder.setLanguageName("jni");
            builder.defaultImplementation(DefaultNativeSourceSet.class);
        }


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
                @Path("android.sources") ModelMap<FunctionalSourceSet> sources) {
            sources.beforeEach(new Action<FunctionalSourceSet>() {
                @Override
                public void execute(FunctionalSourceSet sourceSet) {
                    sourceSet.create("jni", NativeSourceSet.class, new Action<NativeSourceSet>() {
                        @Override
                        public void execute(NativeSourceSet nativeSourceSet) {
                            nativeSourceSet.getSource().srcDir("src/" + nativeSourceSet.getParentName() + "/" + "jni");
                            addInclude(nativeSourceSet.getcFilter(), NativeSourceFileExtensions.C_FILE_EXTENSIONS);
                            addInclude(nativeSourceSet.getCppFilter(), NativeSourceFileExtensions.CPP_FILE_EXTENSIONS);
                        }
                    });
                }
            });
        }

        /**
         * Add include to PatternFilterable for a list of file extensions.
         */
        private static void addInclude(
                @NonNull PatternFilterable filter,
                @NonNull Iterable<String> fileExtensions) {
            for (String ext : fileExtensions) {
                filter.include("**/*." + ext);
            }
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
                final ModelMap<NativeLibrarySpec> nativeComponents,
                @Path("android.ndk") final NdkConfig ndkConfig,
                final NdkHandler ndkHandler,
                @Path("android.sources") final ModelMap<FunctionalSourceSet> sources,
                @Path("buildDir") final File buildDir,
                final ServiceRegistry serviceRegistry) {
            if (!ndkHandler.isNdkDirConfigured()) {
                return;
            }
            if (!ndkConfig.getModuleName().isEmpty()) {
                nativeComponents.create(
                        ndkConfig.getModuleName(),
                        new Action<NativeLibrarySpec>() {
                            @Override
                            public void execute(final NativeLibrarySpec nativeLib) {
                                NdkConfiguration.configureProperties(
                                        nativeLib,
                                        sources,
                                        buildDir,
                                        ndkHandler,
                                        serviceRegistry);
                                nativeLib.getBinaries().all(new Action<BinarySpec>() {
                                    @Override
                                    public void execute(BinarySpec binarySpec) {
                                        NdkConfiguration.configureNativeBinaryOutputFile(
                                                (NativeLibraryBinarySpec)binarySpec,
                                                buildDir,
                                                ndkConfig.getModuleName());
                                    }
                                });
                            }
                        });
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
                ModelMap<AndroidBinaryInternal> binaries,
                @Path("buildDir") final File buildDir) {
            if (!ndkHandler.isNdkDirConfigured()) {
                return;
            }
            for (AndroidBinaryInternal binary : binaries.values()) {
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
        }

        @Mutate
        public static void configureNativeBinary(
                BinaryContainer binaries,
                ModelMap<NativeLibrarySpec> specs,
                @Path("android.ndk") final NdkConfig ndkConfig,
                final NdkHandler ndkHandler) {
            final NativeLibrarySpec library = specs.get(ndkConfig.getModuleName());

            final ImmutableSet.Builder<String> builder = ImmutableSet.builder();
            for(Abi abi : NdkHandler.getAbiList()) {
                builder.add(abi.getName());
            }
            final ImmutableSet<String> supportedAbis = builder.build();

            binaries.withType(
                    AndroidBinaryInternal.class,
                    new Action<AndroidBinaryInternal>() {
                        @Override
                        public void execute(AndroidBinaryInternal binary) {
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
                ModelMap<AndroidBinaryInternal> binaries,
                final NdkHandler ndkHandler) {

            config.getcFileExtensions().addAll(NativeSourceFileExtensions.C_FILE_EXTENSIONS);
            config.getCppFileExtensions().addAll(NativeSourceFileExtensions.CPP_FILE_EXTENSIONS);

            for (final AndroidBinaryInternal binary : binaries.values()) {
                for (final NativeLibraryBinarySpec nativeBinary : binary.getNativeBinaries()) {
                    if (!(nativeBinary instanceof SharedLibraryBinarySpec)) {
                        continue;
                    }
                    final String abi = nativeBinary.getTargetPlatform().getName();
                    config.getLibraries().create(
                            binary.getName() + '-' + abi,
                            new CreateNativeLibraryAction(
                                    binary,
                                    (SharedLibraryBinarySpec) nativeBinary));
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
        private static class CreateNativeLibraryAction implements Action<NativeLibrary> {
            @NonNull
            private final AndroidBinaryInternal binary;
            @NonNull
            private final SharedLibraryBinarySpec nativeBinary;

            public CreateNativeLibraryAction(
                    @NonNull AndroidBinaryInternal binary,
                    @NonNull SharedLibraryBinarySpec nativeBinary) {
                this.binary = binary;
                this.nativeBinary = nativeBinary;
            }

            @Override
            public void execute(NativeLibrary nativeLibrary) {
                final String abi = nativeBinary.getTargetPlatform().getName();

                nativeLibrary.setOutput(nativeBinary.getSharedLibraryFile());
                Set<File> srcFolders = Sets.newHashSet();
                nativeLibrary.setGroupName(binary.getName());
                nativeLibrary.setAssembleTaskName(nativeBinary.getBuildTask().getName());

                final List<String> cFlags = nativeBinary.getcCompiler().getArgs();
                final List<String> cppFlags = nativeBinary.getCppCompiler().getArgs();

                for (LanguageSourceSet sourceSet : nativeBinary.getSources()) {
                    srcFolders.addAll(sourceSet.getSource().getSrcDirs());
                    if (sourceSet instanceof HeaderExportingSourceSet) {
                        Set<File> headerDirs =
                                ((HeaderExportingSourceSet) sourceSet).getExportedHeaders()
                                        .getSrcDirs();
                        for (File headerDir : headerDirs) {
                            if (!nativeLibrary.getExportedHeaders().contains(headerDir)) {
                                nativeLibrary.getExportedHeaders().add(headerDir);

                                // Exported headers are not part of the binary's flag.  Need to add
                                // it manually.
                                cFlags.add("-I" + headerDir);
                                cppFlags.add("-I" + headerDir);
                            }
                        }
                    }
                }

                for (NativeDependencySet dependency : nativeBinary.getLibs()) {
                    for (File includeDir : dependency.getIncludeRoots()) {
                        cFlags.add("-I" + includeDir);
                        cppFlags.add("-I" + includeDir);
                    }
                }

                for (final File srcFolder : srcFolders) {
                    nativeLibrary.getFolders().create(
                            new Action<NativeSourceFolder>() {
                                @Override
                                public void execute(NativeSourceFolder nativeSourceFolder) {
                                    nativeSourceFolder.setSrc(srcFolder);
                                    nativeSourceFolder.setcFlags(
                                            nativeSourceFolder.getcFlags() + ' ' +
                                                    StringHelper.quoteAndJoinTokens(cFlags));
                                    nativeSourceFolder.setCppFlags(
                                            nativeSourceFolder.getCppFlags() + ' ' +
                                                    StringHelper.quoteAndJoinTokens(cppFlags));
                                }
                            });
                }
                nativeLibrary.setToolchain("ndk-" + abi);
            }
        }

        @Finalize
        public static void attachNativeTasksToAndroidBinary(ModelMap<AndroidBinaryInternal> binaries) {
            binaries.afterEach(new Action<AndroidBinaryInternal>() {
                @Override
                public void execute(AndroidBinaryInternal binary) {
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
                ModelMap<AndroidBinaryInternal> binaries,
                @Path("android.sources") final ModelMap<FunctionalSourceSet> sources,
                final ModelMap<Task> tasks,
                @Path(NATIVE_DEPENDENCIES) final Multimap<String, NativeDependencyResolveResult> dependencies,
                NdkHandler ndkHandler,
                ProjectIdentifier project) {
            for(final AndroidBinaryInternal binary : binaries.values()) {
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
                                    ndkHandler,
                                    project));
                }
            }
        }

        private static class CreateNativeLibraryArtifactAction
                implements Action<NativeLibraryArtifact> {
            private final AndroidBinaryInternal binary;
            private final NativeLibraryBinarySpec nativeBinary;
            private final Collection<NativeDependencyResolveResult> dependencies;
            private final NdkHandler ndkHandler;
            private final ProjectIdentifier projectId;

            public CreateNativeLibraryArtifactAction(AndroidBinaryInternal binary,
                    NativeLibraryBinarySpec nativeBinary,
                    Collection<NativeDependencyResolveResult> dependencies,
                    NdkHandler ndkHandler,
                    ProjectIdentifier projectId) {
                this.binary = binary;
                this.nativeBinary = nativeBinary;
                this.dependencies = dependencies;
                this.ndkHandler = ndkHandler;
                this.projectId = projectId;
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

                List<Object> builtBy = Lists.newArrayList();
                builtBy.add(nativeBinary);
                builtBy.add(projectId.getPath() + ":" + NdkNamingScheme.getNdkBuildTaskName(nativeBinary));
                artifact.setBuiltBy(builtBy);
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
                            binary.getMergedNdkConfig().getStlVersion(),
                            abi);
                    artifact.getLibraries().add(stlConfig.getStlLib(abi.getName()));
                }

                // Include transitive dependencies.
                // Dynamic objects from dependencies needs to be added to the library list.
                Abi abi = Abi.getByName(nativeBinary.getTargetPlatform().getName());
                assert abi != null;
                for (NativeDependencyResolveResult dependency : dependencies) {
                    for (NativeLibraryBinary lib : dependency.getPrebuiltLibraries()) {
                        if (lib instanceof SharedLibraryBinary
                                && abi.getName().equals(lib.getTargetPlatform().getName())) {
                            artifact.getLibraries().add(((SharedLibraryBinary) lib).getSharedLibraryFile());
                        }
                    }
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
        public static void hideNativeTasks(
                TaskContainer tasks,
                ModelMap<NativeLibraryBinarySpec> binaries) {
            // Gradle do not support a way to remove created tasks.  The best workaround is to clear
            // the group of the task and have another task depends on it.  Therefore, we have to
            // create a dummy task to depend on all the tasks that we do not want to show up on the
            // task list. The dummy task dependsOn itself, effectively making it non-executable and
            // invisible unless the --all option is use.
            final Task nonExecutableTask = tasks.create("nonExecutableTask");
            nonExecutableTask.dependsOn(nonExecutableTask);
            nonExecutableTask.setDescription(
                    "Dummy task to hide other unwanted tasks in the task list.");

            for (NativeLibraryBinarySpec binary : binaries.values()) {
                Task buildTask = binary.getBuildTask();
                nonExecutableTask.dependsOn(buildTask);
                buildTask.setGroup(null);
            }
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
                new ProductFlavorCombo<>(productFlavors);
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

        ModelMap<BinarySpec> binaries = modelRegistry.realize(
                new ModelPath("binaries"),
                ModelTypes.modelMap(ModelType.of(BinarySpec.class)));

        return binaries.values().stream()
                .filter(binary -> binary instanceof AndroidBinary &&
                        binary.getName().equals(variantConfig.getFullName()))
                .map(binary -> (AndroidBinary) binary)
                .collect(Collectors.toList());
    }
}
