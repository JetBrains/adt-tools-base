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

package com.android.build.gradle.internal;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.core.Abi;
import com.android.build.gradle.model.AndroidBinary;
import com.android.build.gradle.model.AndroidComponentSpec;
import com.android.build.gradle.model.DefaultAndroidBinary;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

import org.gradle.api.UnknownDomainObjectException;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.resolve.ProjectModelResolver;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.language.base.internal.resolve.LibraryResolveException;
import org.gradle.model.ModelMap;
import org.gradle.model.internal.core.ModelPath;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.model.internal.type.ModelType;
import org.gradle.nativeplatform.NativeLibraryBinarySpec;
import org.gradle.nativeplatform.SharedLibraryBinarySpec;
import org.gradle.nativeplatform.StaticLibraryBinarySpec;
import org.gradle.platform.base.ComponentSpecContainer;

import java.io.File;
import java.util.Collection;

/**
 * Resolver for determining native dependencies based on {@link AndroidNativeDependencySpec}
 */
public class NativeDependencyResolver {
    @NonNull
    private ServiceRegistry serviceRegistry;
    @NonNull
    private AndroidNativeDependencySpecContainer dependencyContainer;
    @NonNull
    private AndroidNativeDependencySpec defaultDependencySpec;

    public NativeDependencyResolver(
            @NonNull ServiceRegistry serviceRegistry,
            @NonNull AndroidNativeDependencySpecContainer dependencyContainer,
            @NonNull AndroidNativeDependencySpec defaultDependencySpec) {
        this.serviceRegistry = serviceRegistry;
        this.dependencyContainer = dependencyContainer;
        this.defaultDependencySpec = defaultDependencySpec;
    }

    /**
     * Determine native dependencies from all {@link AndroidNativeDependencySpec}.
     */
    @NonNull
    public NativeDependencyResolveResult resolve() {
        NativeDependencyResolveResult result = new NativeDependencyResolveResult();
        for (final AndroidNativeDependencySpec dependency : dependencyContainer.getDependencies()) {
            if (dependency.getProjectPath() != null) {
                result.getNativeBinaries().addAll(resolveForNativeBinaries(dependency));
            } else if (dependency.getLibraryPath() != null) {
                Preconditions.checkState(dependency.getAbi() != null);
                result.getLibraryFiles().put(
                        Abi.getByName(dependency.getAbi()),
                        resolveForFiles(dependency));
            }
        }

        return result;
    }

    @NonNull
    private File resolveForFiles(AndroidNativeDependencySpec dependency) {
        FileResolver fileResolver = serviceRegistry.get(FileResolver.class);
        return fileResolver.resolve(dependency.getLibraryPath());
    }

    @NonNull
    private Collection<NativeLibraryBinarySpec> resolveForNativeBinaries(
            AndroidNativeDependencySpec dependency) {
        ProjectModelResolver projectModelResolver = serviceRegistry.get(ProjectModelResolver.class);
        String project = dependency.getProjectPath();
        String buildType = Objects.firstNonNull(
                dependency.getBuildType(),
                defaultDependencySpec.getBuildType());
        String productFlavor = Objects.firstNonNull(
                dependency.getProductFlavor(),
                defaultDependencySpec.getProductFlavor());
        String linkage = Objects.firstNonNull(
                dependency.getLinkage(),
                defaultDependencySpec.getLinkage());

        ModelRegistry projectModel = projectModelResolver.resolveProjectModel(project);
        ComponentSpecContainer components = projectModel.find(
                ModelPath.path("components"),
                ModelType.of(ComponentSpecContainer.class));
        if (components == null) {
            throw new LibraryResolveException(
                    String.format("Project '%s' does not have a component container", project));
        }
        AndroidComponentSpec library = Iterables.getOnlyElement(
                components.withType(AndroidComponentSpec.class));
        if (library == null) {
            throw new UnknownDomainObjectException(
                    String.format("%s not found.  Project '%s' does not apply an Android component "
                                    + "model plugin.",
                            AndroidComponentSpec.class.getSimpleName(),
                            project));
        }
        ModelMap<AndroidBinary> androidBinaries = library.getBinaries().withType(
                AndroidBinary.class);

        for (AndroidBinary b : androidBinaries.values()) {
            DefaultAndroidBinary androidBinary = (DefaultAndroidBinary) b;

            // TODO: Make it more flexible.  If dependency.getBuildType()/getProductFlavor() is
            // null, it should match anything as long as there is no ambiguity.
            if (buildType.equals(androidBinary.getBuildType().getName())
                    && productFlavor.equals(
                    ProductFlavorCombo.getFlavorComboName(androidBinary.getProductFlavors()))) {
                ImmutableList.Builder<NativeLibraryBinarySpec> match = ImmutableList.builder();
                for (NativeLibraryBinarySpec nativeBinary : ((DefaultAndroidBinary) b).getNativeBinaries()) {
                    if ((linkage.equals("static") && nativeBinary instanceof StaticLibraryBinarySpec)
                            || (linkage.equals("shared")
                                    && nativeBinary instanceof SharedLibraryBinarySpec)) {
                        match.add(nativeBinary);
                    }
                }
                return match.build();
            }
        }
        throw new LibraryResolveException(
                String.format(
                        "Unable to find Android binary with buildType '%s' and productFlavor: '%s' "
                                + "in project '%s'",
                        buildType,
                        productFlavor,
                        project));
    }
}
