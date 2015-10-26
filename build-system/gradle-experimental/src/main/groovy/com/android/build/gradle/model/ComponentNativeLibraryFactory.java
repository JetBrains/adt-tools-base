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

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.NdkHandler;
import com.android.build.gradle.internal.core.Abi;
import com.android.build.gradle.internal.dependency.NativeDependencyResolveResult;
import com.android.build.gradle.internal.dependency.NativeLibraryArtifact;
import com.android.build.gradle.internal.dsl.CoreNdkOptions;
import com.android.build.gradle.internal.model.NativeLibraryFactory;
import com.android.build.gradle.internal.model.NativeLibraryImpl;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.build.gradle.managed.NdkAbiOptions;
import com.android.build.gradle.managed.NdkOptions;
import com.android.build.gradle.ndk.internal.BinaryToolHelper;
import com.android.builder.model.NativeLibrary;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

import org.gradle.model.ModelMap;
import org.gradle.nativeplatform.NativeLibraryBinarySpec;
import org.gradle.platform.base.BinaryContainer;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Implementation of NativeLibraryFactory from in the component model plugin.
 *
 * The library extract information directly from the binaries.
 */
public class ComponentNativeLibraryFactory implements NativeLibraryFactory {
    @NonNull
    BinaryContainer binaries;
    @NonNull
    NdkHandler ndkHandler;
    @NonNull
    ModelMap<NdkAbiOptions> abiOptions;
    @NonNull
    Multimap<String, NativeDependencyResolveResult> nativeDependencies;

    public ComponentNativeLibraryFactory(
            @NonNull BinaryContainer binaries,
            @NonNull NdkHandler ndkHandler,
            @NonNull ModelMap<NdkAbiOptions> abiOptions,
            @NonNull Multimap<String, NativeDependencyResolveResult> nativeDependencies) {
        this.binaries = binaries;
        this.ndkHandler = ndkHandler;
        this.abiOptions = abiOptions;
        this.nativeDependencies = nativeDependencies;
    }

    @NonNull
    @Override
    public Optional<NativeLibrary> create(
            @NonNull VariantScope scope,
            @NonNull String toolchainName,
            @NonNull final Abi abi) {
        BaseVariantData<? extends BaseVariantOutputData> variantData = scope.getVariantData();

        DefaultAndroidBinary androidBinary =
                (DefaultAndroidBinary) binaries.findByName(variantData.getName());

        if (androidBinary == null) {
            // Binaries are not created for test variants.
            return Optional.absent();
        }

        @SuppressWarnings("ConstantConditions")
        Optional<NativeLibraryBinarySpec> nativeBinary =
                Iterables.tryFind(androidBinary.getNativeBinaries(),
                        new Predicate<NativeLibraryBinarySpec>() {
                            @Override
                            public boolean apply(NativeLibraryBinarySpec binary) {
                                return binary.getTargetPlatform().getName().equals(abi.getName());
                            }
                        });

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
        List<String> cFlags = BinaryToolHelper.getCCompiler(nativeBinary.get()).getArgs();
        List<String> cppFlags = BinaryToolHelper.getCppCompiler(nativeBinary.get()).getArgs();
        if (targetOptions != null) {
            if (!targetOptions.getCFlags().isEmpty()) {
                cFlags = ImmutableList.copyOf(Iterables.concat(cFlags, targetOptions.getCFlags()));
            }
            if (!targetOptions.getCppFlags().isEmpty()) {
                cppFlags = ImmutableList.copyOf(
                        Iterables.concat(cppFlags, targetOptions.getCppFlags()));
            }
        }

        List<File> debuggableLibDir = findDebuggableLibraryDirectories(variantData, androidBinary, abi);

        CoreNdkOptions ndkConfig = variantData.getVariantConfiguration().getNdkConfig();
        // The DSL currently do not support all options available in the model such as the
        // include dirs and the defines.  Therefore, just pass an empty collection for now.
        return Optional.<NativeLibrary>of(new NativeLibraryImpl(
                ndkConfig.getModuleName(),
                toolchainName,
                abi.getName(),
                Collections.<File>emptyList(),  /*cIncludeDirs*/
                Collections.<File>emptyList(),  /*cppIncludeDirs*/
                Collections.<File>emptyList(),  /*cSystemIncludeDirs*/
                ndkHandler.getStlIncludes(ndkConfig.getStl(), abi),
                Collections.<String>emptyList(),  /*cDefines*/
                Collections.<String>emptyList(),  /*cppDefines*/
                cFlags,
                cppFlags,
                debuggableLibDir));
    }

    /**
     * Find all directories containing library with debug symbol.
     * Include libraries from dependencies.
     */
    private List<File> findDebuggableLibraryDirectories(
            @NonNull BaseVariantData<? extends BaseVariantOutputData> variantData,
            @NonNull AndroidBinary binary,
            @NonNull Abi abi) {
        // Create LinkedHashSet to remove duplicated while maintaining order.
        Set<File> debuggableLibDir = Sets.newLinkedHashSet();

        debuggableLibDir.add(variantData.getScope().getNdkDebuggableLibraryFolders(abi));

        for (NativeDependencyResolveResult dependency : nativeDependencies.get(binary.getName())) {
            for (NativeLibraryArtifact artifact : dependency.getNativeArtifacts()) {
                for (File lib : artifact.getLibraries()) {
                    debuggableLibDir.add(lib.getParentFile());
                }
            }
            for (File lib : dependency.getLibraryFiles().get(abi)) {
                debuggableLibDir.add(lib.getParentFile());
            }
        }
        return ImmutableList.copyOf(debuggableLibDir);
    }
}
