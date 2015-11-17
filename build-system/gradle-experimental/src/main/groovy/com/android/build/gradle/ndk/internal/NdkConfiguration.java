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

package com.android.build.gradle.ndk.internal;

import static com.android.build.gradle.ndk.internal.BinaryToolHelper.getCCompiler;
import static com.android.build.gradle.ndk.internal.BinaryToolHelper.getCppCompiler;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.NativeDependencyLinkage;
import com.android.build.gradle.internal.dependency.AndroidNativeDependencySpec;
import com.android.build.gradle.internal.dependency.NativeDependencyResolveResult;
import com.android.build.gradle.internal.dependency.NativeDependencyResolver;
import com.android.build.gradle.internal.dependency.NativeLibraryArtifact;
import com.android.build.gradle.internal.NdkHandler;
import com.android.build.gradle.internal.core.Abi;
import com.android.build.gradle.managed.NdkConfig;
import com.android.build.gradle.model.NativeSourceSet;
import com.android.build.gradle.tasks.GdbSetupTask;
import com.android.build.gradle.tasks.StripDebugSymbolTask;
import com.android.utils.StringHelper;
import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;

import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.tasks.Copy;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.language.base.FunctionalSourceSet;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.c.CSourceSet;
import org.gradle.language.c.tasks.CCompile;
import org.gradle.language.cpp.CppSourceSet;
import org.gradle.language.cpp.tasks.CppCompile;
import org.gradle.model.ModelMap;
import org.gradle.nativeplatform.NativeBinarySpec;
import org.gradle.nativeplatform.NativeLibraryBinarySpec;
import org.gradle.nativeplatform.NativeLibrarySpec;
import org.gradle.nativeplatform.SharedLibraryBinarySpec;
import org.gradle.nativeplatform.StaticLibraryBinarySpec;
import org.gradle.platform.base.BinarySpec;
import org.gradle.platform.base.binary.BaseBinarySpec;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Configure settings used by the native binaries.
 */
public class NdkConfiguration {

    public static void configureProperties(
            NativeLibrarySpec library,
            final ModelMap<FunctionalSourceSet> sources,
            final File buildDir,
            final NdkHandler ndkHandler,
            final ServiceRegistry serviceRegistry) {
        for (Abi abi : ndkHandler.getSupportedAbis()) {
            library.targetPlatform(abi.getName());
        }

        // Setting each native binary to not buildable to prevent the native tasks to be
        // automatically added to the "assemble" task.
        library.getBinaries().beforeEach(
                new Action<BinarySpec>() {
                    @Override
                    public void execute(BinarySpec binary) {
                        ((BaseBinarySpec) binary).setBuildable(false);
                    }
                });

        library.getBinaries()
                .withType(NativeLibraryBinarySpec.class, new Action<NativeLibraryBinarySpec>() {
                    @Override
                    public void execute(final NativeLibraryBinarySpec binary) {
                        Map<String, NativeSourceSet> jniSources =
                                findNativeSourceSets(binary, sources);
                        for (Map.Entry<String, NativeSourceSet> entry : jniSources.entrySet()) {
                            addNativeSourceSets(binary, entry.getKey(), entry.getValue());
                        }

                        getCCompiler(binary).define("ANDROID");
                        getCppCompiler(binary).define("ANDROID");
                        getCCompiler(binary).define("ANDROID_NDK");
                        getCppCompiler(binary).define("ANDROID_NDK");

                        // Replace output directory of compile tasks.
                        binary.getTasks().withType(CCompile.class, new Action<CCompile>() {
                            @Override
                            public void execute(CCompile task) {
                                String sourceSetName = task.getObjectFileDir().getName();
                                task.setObjectFileDir(
                                        NdkNamingScheme.getObjectFilesOutputDirectory(
                                                binary,
                                                buildDir,
                                                sourceSetName));
                            }
                        });
                        binary.getTasks().withType(CppCompile.class, new Action<CppCompile>() {
                            @Override
                            public void execute(CppCompile task) {
                                String sourceSetName = task.getObjectFileDir().getName();
                                task.setObjectFileDir(
                                        NdkNamingScheme.getObjectFilesOutputDirectory(
                                                binary,
                                                buildDir,
                                                sourceSetName));
                            }
                        });

                        new DefaultNativeToolSpecification().apply(binary);

                        String sysroot = ndkHandler.getSysroot(
                                Abi.getByName(binary.getTargetPlatform().getName()));

                        getCCompiler(binary).args("--sysroot=" + sysroot);
                        getCppCompiler(binary).args("--sysroot=" + sysroot);
                        binary.getLinker().args("--sysroot=" + sysroot);
                        binary.getLinker().args("-Wl,--build-id");

                        for (NativeSourceSet jniSource : jniSources.values()) {
                            handleDependencies(
                                    binary,
                                    resolveDependency(serviceRegistry, binary, jniSource));
                        }
                    }
                });
    }

    /**
     * Configure native binary with variant specific options.
     */
    public static void configureBinary(
            NativeLibraryBinarySpec binary,
            final File buildDir,
            final NdkConfig ndkConfig,
            final NdkHandler ndkHandler) {
        // Set output library filename.
        if (binary instanceof SharedLibraryBinarySpec) {
            ((SharedLibraryBinarySpec)binary).setSharedLibraryFile(
                    new File(
                            buildDir,
                            NdkNamingScheme.getDebugLibraryDirectoryName(binary)
                                    + "/"
                                    + NdkNamingScheme.getSharedLibraryFileName(
                                    ndkConfig.getModuleName())));
        } else if (binary instanceof StaticLibraryBinarySpec) {
            ((StaticLibraryBinarySpec)binary).setStaticLibraryFile(
                    new File(
                            buildDir,
                            NdkNamingScheme.getDebugLibraryDirectoryName(binary)
                                    + "/"
                                    + NdkNamingScheme.getStaticLibraryFileName(
                                    ndkConfig.getModuleName())));
        } else {
            throw new AssertionError("Should be unreachable");
        }

        String sysroot = ndkHandler.getSysroot(
                Abi.getByName(binary.getTargetPlatform().getName()));

        if (ndkConfig.getRenderscriptNdkMode()) {
            getCCompiler(binary).args("-I" + sysroot + "/usr/include/rs");
            getCCompiler(binary).args("-I" + sysroot + "/usr/include/rs/cpp");
            getCppCompiler(binary).args("-I" + sysroot + "/usr/include/rs");
            getCppCompiler(binary).args("-I" + sysroot + "/usr/include/rs/cpp");
            binary.getLinker().args("-L" + sysroot + "/usr/lib/rs");
        }

        // STL flags must be applied before user defined flags to resolve possible undefined symbols
        // in the STL library.
        StlNativeToolSpecification stlConfig = new StlNativeToolSpecification(
                ndkHandler,
                ndkConfig.getStl(),
                binary.getTargetPlatform());
        stlConfig.apply(binary);

        NativeToolSpecificationFactory.create(
                ndkHandler,
                binary.getTargetPlatform(),
                Objects.firstNonNull(ndkConfig.getDebuggable(), false)).apply(
                binary);

        // Add flags defined in NdkConfig
        for (String flag : ndkConfig.getCFlags()) {
            getCCompiler(binary).args(flag.trim());
        }

        for (String flag : ndkConfig.getCppFlags()) {
            getCppCompiler(binary).args(flag.trim());
        }

        for (String flag : ndkConfig.getLdFlags()) {
            binary.getLinker().args(flag.trim());
        }

        for (String ldLib : ndkConfig.getLdLibs()) {
            binary.getLinker().args("-l" + ldLib.trim());
        }
    }


    public static NativeDependencyResolveResult resolveDependency(
            @NonNull ServiceRegistry serviceRegistry,
            @NonNull NativeBinarySpec binary,
            @NonNull NativeSourceSet jniSource) {
        return new NativeDependencyResolver(
                serviceRegistry,
                jniSource.getDependencies(),
                new AndroidNativeDependencySpec(
                        null,
                        null,
                        binary.getBuildType().getName(),
                        binary.getFlavor().getName(),
                        binary.getTargetPlatform().getName(),
                        NativeDependencyLinkage.SHARED)).resolve();
    }

    private static void handleDependencies(
            @NonNull NativeBinarySpec binary,
            @NonNull NativeDependencyResolveResult dependency) {
        for (final NativeLibraryArtifact artifacts: dependency.getNativeArtifacts()) {
            final String abi = artifacts.getAbi();
            if (binary.getTargetPlatform().getName().equals(abi)) {
                binary.getTasks().all(
                        new Action<Task>() {
                            @Override
                            public void execute(Task task) {
                                task.dependsOn(artifacts.getBuiltBy());
                            }
                        });

                for (File dir : artifacts.getExportedHeaderDirectories()) {
                    getCCompiler(binary).args("-I" + dir);
                    getCppCompiler(binary).args("-I" + dir);
                }
                for(File lib : artifacts.getLibraries()) {
                    if (lib.getName().endsWith(".so") || lib.getName().endsWith(".a")) {
                        binary.getLinker().args(lib.getPath());
                    }
                }
            }
        }
        for (final Map.Entry<Abi, File> entry : dependency.getLibraryFiles().entries()) {
            if (entry.getKey().getName().equals(binary.getTargetPlatform().getName())) {
                binary.getLinker().args(entry.getValue().getPath());
            }
        }
    }

    /**
     * Find all JNI source sets that should be added the a native binary.
     *
     * @return a map from the name of the FunctionalSourceSet containing the JNI source set, to the
     * JNI source set.
     */
    public static Map<String, NativeSourceSet> findNativeSourceSets(
            NativeBinarySpec binary,
            ModelMap<FunctionalSourceSet> projectSourceSet) {
        Map<String, NativeSourceSet> sourceSetMap = Maps.newHashMap();
        addSourceIfExist(sourceSetMap, projectSourceSet, "main");
        addSourceIfExist(sourceSetMap, projectSourceSet, binary.getFlavor().getName());
        addSourceIfExist(sourceSetMap, projectSourceSet, binary.getBuildType().getName());
        addSourceIfExist(sourceSetMap, projectSourceSet,
                binary.getFlavor().getName()
                        + StringHelper.capitalize(binary.getBuildType().getName()));
        return sourceSetMap;
    }

    private static void addSourceIfExist(
            @NonNull Map<String, NativeSourceSet> sourceSetMap,
            @NonNull ModelMap<FunctionalSourceSet> projectSourceSet,
            @NonNull String sourceSetName) {
        FunctionalSourceSet sourceSet = projectSourceSet.get(sourceSetName);
        if (sourceSet != null) {
            sourceSetMap.put(sourceSetName, (NativeSourceSet) sourceSet.getByName("jni"));
        }
    }

    /**
     * Add the sourceSet with the specified name to the binary.
     */
    private static void addNativeSourceSets(
            @NonNull BinarySpec binary,
            @NonNull final String sourceSetName,
            @NonNull final NativeSourceSet jni) {
        binary.sources(new Action<ModelMap<LanguageSourceSet>>() {
            @Override
            public void execute(ModelMap<LanguageSourceSet> languageSourceSets) {
                // Hardcode the acceptable extension until we find a suitable DSL for user to
                // modify.
                languageSourceSets.create(
                        sourceSetName + "C",
                        CSourceSet.class,
                        new Action<CSourceSet>() {
                            @Override
                            public void execute(CSourceSet source) {
                                source.getSource().setSrcDirs(jni.getSource().getSrcDirs());
                                source.getSource().include("**/*.c");
                                source.getSource().exclude(jni.getSource().getExcludes());
                                source.exportedHeaders(new Action<SourceDirectorySet>() {
                                    @Override
                                    public void execute(SourceDirectorySet files) {
                                        files.source(jni.getExportedHeaders());
                                    }
                                });
                            }
                        });
                languageSourceSets.create(
                        sourceSetName + "Cpp",
                        CppSourceSet.class,
                        new Action<CppSourceSet>() {
                            @Override
                            public void execute(CppSourceSet source) {
                                source.getSource().setSrcDirs(jni.getSource().getSrcDirs());
                                source.getSource().include("**/*.C");
                                source.getSource().include("**/*.CPP");
                                source.getSource().include("**/*.c++");
                                source.getSource().include("**/*.cc");
                                source.getSource().include("**/*.cp");
                                source.getSource().include("**/*.cpp");
                                source.getSource().include("**/*.cxx");
                                source.getSource().exclude(jni.getSource().getExcludes());
                                source.exportedHeaders(new Action<SourceDirectorySet>() {
                                    @Override
                                    public void execute(SourceDirectorySet files) {
                                        files.source(jni.getExportedHeaders());
                                    }
                                });
                            }
                        });
            }
        });
    }

    public static void createTasks(
            @NonNull ModelMap<Task> tasks,
            @NonNull final NativeBinarySpec binary,
            @NonNull final File buildDir,
            @NonNull NdkConfig ndkConfig,
            @NonNull NdkHandler ndkHandler,
            @NonNull final Multimap<String, NativeDependencyResolveResult> dependencyMap) {
        String compileNdkTaskName = NdkNamingScheme.getNdkBuildTaskName(binary);
        tasks.create(compileNdkTaskName);

        if (binary instanceof SharedLibraryBinarySpec) {
            StlConfiguration.createStlCopyTask(tasks, binary, buildDir, ndkHandler,
                    ndkConfig.getStl(), compileNdkTaskName);

            if (Boolean.TRUE.equals(ndkConfig.getDebuggable())) {
                // TODO: Use AndroidTaskRegistry and scopes to create tasks in experimental plugin.
                setupNdkGdbDebug(tasks, binary, buildDir, ndkConfig, ndkHandler,
                        compileNdkTaskName);
            }
            createStripDebugTask(
                    tasks,
                    (SharedLibraryBinarySpec) binary,
                    dependencyMap,
                    buildDir,
                    ndkHandler,
                    compileNdkTaskName);
        }
    }

    /**
     * Setup tasks to create gdb.setup and copy gdbserver for NDK debugging.
     */
    private static void setupNdkGdbDebug(
            @NonNull ModelMap<Task> tasks,
            @NonNull final NativeBinarySpec binary,
            @NonNull final File buildDir,
            @NonNull final NdkConfig ndkConfig,
            @NonNull final NdkHandler handler,
            @NonNull String buildTaskName) {
        final String copyGdbServerTaskName = NdkNamingScheme.getTaskName(binary, "copy", "GdbServer");
        tasks.create(copyGdbServerTaskName, Copy.class, new Action<Copy>() {
            @Override
            public void execute(Copy task) {
                task.from(new File(handler.getPrebuiltDirectory(
                        Abi.getByName(binary.getTargetPlatform().getName())),
                        "gdbserver/gdbserver"));
                task.into(new File(buildDir, NdkNamingScheme.getOutputDirectoryName(binary)));
            }
        });

        final String createGdbSetupTaskName = NdkNamingScheme.getTaskName(binary, "create", "Gdbsetup");
        tasks.create(createGdbSetupTaskName, GdbSetupTask.class, new Action<GdbSetupTask>() {
            @Override
            public void execute(GdbSetupTask task) {
                task.setNdkHandler(handler);
                task.setExtension(ndkConfig);
                task.setBinary(binary);
                task.setOutputDir(
                        new File(buildDir, NdkNamingScheme.getOutputDirectoryName(binary)));
            }
        });

        tasks.named(buildTaskName, new Action<Task>() {
            @Override
            public void execute(Task task) {
                task.dependsOn(copyGdbServerTaskName);
                task.dependsOn(createGdbSetupTaskName);
            }
        });
    }

    private static void createStripDebugTask(
            ModelMap<Task> tasks,
            final SharedLibraryBinarySpec binary,
            @NonNull final Multimap<String, NativeDependencyResolveResult> dependencyMap,
            final File buildDir,
            final NdkHandler handler,
            String buildTaskName) {

        final String taskName = NdkNamingScheme.getTaskName(binary, "stripSymbols");
        Abi abi = Abi.getByName(binary.getTargetPlatform().getName());

        List<File> libs = Lists.newArrayList();
        final Collection<NativeDependencyResolveResult> dependencies =
                dependencyMap.get(binary.getName());
        for (NativeDependencyResolveResult dependency : dependencies) {
            for (File lib : dependency.getLibraryFiles().get(abi)) {
                libs.add(lib);
            }
            Collection<NativeLibraryArtifact> artifacts =
                    dependency.getNativeArtifacts();
            for (NativeLibraryArtifact artifact : artifacts) {
                for (File lib : artifact.getLibraries()) {
                    if (lib.getName().endsWith(".so")) {
                        libs.add(lib);
                    }
                }
            }
        }
        tasks.create(
                taskName,
                StripDebugSymbolTask.class,
                new StripDebugSymbolTask.ConfigAction(
                        binary,
                        new File(buildDir, NdkNamingScheme.getDebugLibraryDirectoryName(binary)),
                        libs,
                        buildDir,
                        handler));
        tasks.named(buildTaskName, new Action<Task>() {
            @Override
            public void execute(Task task) {
                task.dependsOn(taskName);
            }
        });
    }
}
