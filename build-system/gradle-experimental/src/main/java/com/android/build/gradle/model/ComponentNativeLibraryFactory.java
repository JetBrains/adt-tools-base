/*
 * Copyright (C) 2015 The Android Open Source Project
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

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.core.Abi;
import com.android.build.gradle.internal.dependency.NativeDependencyResolveResult;
import com.android.build.gradle.internal.dependency.NativeLibraryArtifact;
import com.android.build.gradle.internal.model.NativeLibraryFactory;
import com.android.build.gradle.internal.model.NativeLibraryImpl;
import com.android.build.gradle.internal.ndk.NdkHandler;
import com.android.build.gradle.internal.ndk.Stl;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.build.gradle.managed.NdkAbiOptions;
import com.android.build.gradle.managed.NdkConfig;
import com.android.build.gradle.managed.NdkOptions;
import com.android.build.gradle.model.internal.AndroidBinaryInternal;
import com.android.build.gradle.ndk.internal.NativeCompilerArgsUtil;
import com.android.builder.model.NativeLibrary;
import com.android.utils.StringHelper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

import org.gradle.model.ModelMap;
import org.gradle.nativeplatform.NativeLibraryBinary;
import org.gradle.nativeplatform.NativeLibraryBinarySpec;
import org.gradle.nativeplatform.SharedLibraryBinary;
import org.gradle.nativeplatform.StaticLibraryBinary;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implementation of NativeLibraryFactory from in the component model plugin.
 *
 * The library extract information directly from the binaries.
 */
public class ComponentNativeLibraryFactory implements NativeLibraryFactory {
    @NonNull
    ModelMap<AndroidBinaryInternal> binaries;
    @NonNull
    NdkHandler ndkHandler;
    @NonNull
    ModelMap<NdkAbiOptions> abiOptions;
    @NonNull
    Multimap<String, NativeDependencyResolveResult> nativeDependencies;
    @NonNull
    Multimap<String, NativeDependencyResolveResult> jniLibsDependencies;

    public ComponentNativeLibraryFactory(
            @NonNull ModelMap<AndroidBinaryInternal> binaries,
            @NonNull NdkHandler ndkHandler,
            @NonNull ModelMap<NdkAbiOptions> abiOptions,
            @NonNull Multimap<String, NativeDependencyResolveResult> nativeDependencies,
            @NonNull Multimap<String, NativeDependencyResolveResult> jniLibsDependencies) {
        this.binaries = binaries;
        this.ndkHandler = ndkHandler;
        this.abiOptions = abiOptions;
        this.nativeDependencies = nativeDependencies;
        this.jniLibsDependencies = jniLibsDependencies;
    }

    @NonNull
    @Override
    public Optional<NativeLibrary> create(
            @NonNull VariantScope scope,
            @NonNull String toolchainName,
            @NonNull final Abi abi) {
        BaseVariantData<? extends BaseVariantOutputData> variantData = scope.getVariantData();

        AndroidBinaryInternal androidBinary =
                binaries.get(COMPONENT_NAME + StringHelper.capitalize(variantData.getName()));

        if (androidBinary == null) {
            // Binaries are not created for test variants.
            return Optional.empty();
        }

        Optional<NativeLibraryBinarySpec> nativeBinary = androidBinary.getNativeBinaries().stream()
                .filter(binary -> binary.getTargetPlatform().getName().equals(abi.getName()))
                .findFirst();

        if (!nativeBinary.isPresent()) {
            // We don't have native binaries, but the project may be dependent on other native
            // projects.  Create a NativeLibrary to supply the debuggable library directories.
            return Optional.<NativeLibrary>of(new NativeLibraryImpl(
                    abi.getName(),
                    toolchainName,
                    abi.getName(),
                    Collections.<File>emptyList(),  /*cIncludeDirs*/
                    Collections.<File>emptyList(),  /*cppIncludeDirs*/
                    Collections.<File>emptyList(),  /*cSystemIncludeDirs*/
                    Collections.<File>emptyList(),  /*cppSystemIncludeDirs*/
                    Collections.<String>emptyList(),  /*cDefines*/
                    Collections.<String>emptyList(),  /*cppDefines*/
                    Collections.<String>emptyList(),  /*cFlags*/
                    Collections.<String>emptyList(),  /*cppFlags*/
                    findDebuggableLibraryDirectories(variantData, androidBinary, abi)));
        }

        NdkOptions targetOptions = abiOptions.get(abi.getName());
        Iterable<String> cFlags = nativeBinary.get().getcCompiler().getArgs();
        Iterable<String> cppFlags = nativeBinary.get().getCppCompiler().getArgs();
        if (targetOptions != null) {
            if (!targetOptions.getCFlags().isEmpty()) {
                cFlags = Iterables.concat(cFlags, targetOptions.getCFlags());
            }
            if (!targetOptions.getCppFlags().isEmpty()) {
                cppFlags = Iterables.concat(cppFlags, targetOptions.getCppFlags());
            }
        }

        List<File> debuggableLibDir = findDebuggableLibraryDirectories(variantData, androidBinary, abi);

        NdkConfig ndkConfig = androidBinary.getMergedNdkConfig();
        // The DSL currently do not support all options available in the model such as the
        // include dirs and the defines.  Therefore, just pass an empty collection for now.
        return Optional.<NativeLibrary>of(new NativeLibraryImpl(
                ndkConfig.getModuleName(),
                toolchainName,
                abi.getName(),
                Collections.<File>emptyList(),  /*cIncludeDirs*/
                Collections.<File>emptyList(),  /*cppIncludeDirs*/
                Collections.<File>emptyList(),  /*cSystemIncludeDirs*/
                ndkHandler.getStlNativeToolSpecification(
                        Stl.getById(ndkConfig.getStl()),
                        ndkConfig.getStlVersion(),
                        abi).getIncludes(),
                Collections.<String>emptyList(),  /*cDefines*/
                Collections.<String>emptyList(),  /*cppDefines*/
                NativeCompilerArgsUtil.transform(cFlags),
                NativeCompilerArgsUtil.transform(cppFlags),
                debuggableLibDir));
    }

    /**
     * Find all directories containing library with debug symbol.
     * Include libraries from dependencies.
     */
    private List<File> findDebuggableLibraryDirectories(
            @NonNull BaseVariantData<? extends BaseVariantOutputData> variantData,
            @NonNull AndroidBinaryInternal binary,
            @NonNull Abi abi) {
        // Create LinkedHashSet to remove duplicated while maintaining order.
        Set<File> debuggableLibDir = Sets.newLinkedHashSet();

        debuggableLibDir.add(variantData.getScope().getNdkDebuggableLibraryFolders(abi));
        addNativeDebuggableLib(debuggableLibDir, binary, abi, nativeDependencies);
        addJniLibsDebuggableLib(debuggableLibDir, binary, abi, jniLibsDependencies);

        return ImmutableList.copyOf(debuggableLibDir);
    }

    private static void addNativeDebuggableLib(
            @NonNull Collection<File> debuggableLibDir,
            @NonNull AndroidBinaryInternal binary,
            @NonNull final Abi abi,
            @NonNull Multimap<String, NativeDependencyResolveResult> dependencyMap) {
        Optional<NativeLibraryBinarySpec> nativeBinary = binary.getNativeBinaries().stream()
                .filter(candidateBinary -> candidateBinary.getTargetPlatform().getName()
                                .equals(abi.getName()))
                .findFirst();
        if (nativeBinary.isPresent()) {
            addDebuggableLib(
                    debuggableLibDir,
                    binary,
                    abi,
                    dependencyMap.get(nativeBinary.get().getName()));
        }
    }


    private static void addJniLibsDebuggableLib(
            @NonNull Collection<File> debuggableLibDir,
            @NonNull AndroidBinary binary,
            @NonNull Abi abi,
            @NonNull Multimap<String, NativeDependencyResolveResult> dependencyMap) {
        addDebuggableLib(debuggableLibDir, binary, abi, dependencyMap.get(binary.getName()));
    }


    private static void addDebuggableLib(
            @NonNull Collection<File> debuggableLibDir,
            @NonNull AndroidBinary binary,
            @NonNull Abi abi,
            @NonNull Iterable<NativeDependencyResolveResult> dependencies) {
        for (NativeDependencyResolveResult dependency : dependencies) {
            for (NativeLibraryArtifact artifact : dependency.getNativeArtifacts()) {
                debuggableLibDir.addAll(artifact.getLibraries().stream().map(File::getParentFile)
                        .collect(Collectors.toList()));
            }
            for (final NativeLibraryBinary nativeBinary : dependency.getPrebuiltLibraries()) {
                if (nativeBinary.getTargetPlatform().getName().equals(abi.getName())) {
                    File output = nativeBinary instanceof SharedLibraryBinary
                            ? ((SharedLibraryBinary) nativeBinary).getSharedLibraryFile()
                            : ((StaticLibraryBinary) nativeBinary).getStaticLibraryFile();
                    if (output != null) {
                        debuggableLibDir.add(output.getParentFile());
                    }
                }
            }
        }
    }
}
