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

package com.android.build.gradle.internal.dependency;

import static com.android.build.gradle.model.ModelConstants.ARTIFACTS;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.NativeDependencyLinkage;
import com.android.utils.StringHelper;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.NamedDomainObjectSet;
import org.gradle.api.internal.resolve.ProjectModelResolver;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.language.base.internal.resolve.LibraryResolveException;
import org.gradle.model.internal.core.ModelPath;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.model.internal.type.ModelType;
import org.gradle.nativeplatform.NativeLibraryBinary;
import org.gradle.nativeplatform.PrebuiltLibraries;
import org.gradle.nativeplatform.PrebuiltLibrary;
import org.gradle.nativeplatform.Repositories;
import org.gradle.nativeplatform.SharedLibraryBinary;
import org.gradle.nativeplatform.StaticLibraryBinary;
import org.gradle.nativeplatform.internal.prebuilt.PrebuiltLibraryResolveException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

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
                result.getNativeArtifacts().addAll(resolveForNativeBinaries(dependency));
            } else if (dependency.getLibraryPath() != null) {
                resolveForPrebuiltLibraries(result, dependency);
            }
        }

        return result;
    }

    private void resolveForPrebuiltLibraries(
            NativeDependencyResolveResult result,
            AndroidNativeDependencySpec dependency) {
        NativeDependencyLinkage linkage =
                Objects.firstNonNull(dependency.getLinkage(), defaultDependencySpec.getLinkage());
        for (NativeLibraryBinary binary : getBinaries(dependency.getLibraryPath())) {
            if (linkage.equals(NativeDependencyLinkage.STATIC)) {
                if (binary instanceof StaticLibraryBinary
                        && ((StaticLibraryBinary) binary).getStaticLibraryFile() != null) {
                    result.getPrebuiltLibraries().add(binary);
                }
            } else {
                if (binary instanceof SharedLibraryBinary
                        && ((SharedLibraryBinary) binary).getSharedLibraryFile() != null) {
                    result.getPrebuiltLibraries().add(binary);
                }
            }
        }
    }

    private Collection<NativeLibraryBinary> getBinaries(String library) {
        ModelRegistry projectModel = serviceRegistry.get(ModelRegistry.class);
        NamedDomainObjectSet<PrebuiltLibraries> repositories = projectModel.realize(
                ModelPath.path("repositories"),
                ModelType.of(Repositories.class)).withType(PrebuiltLibraries.class);
        projectModel.realize(
                ModelPath.path("repositories"),
                ModelType.of(Repositories.class));
        if (repositories.isEmpty()) {
            throw new PrebuiltLibraryResolveException(
                    "Project does not have any prebuilt library repositories.");
        }
        PrebuiltLibrary prebuiltLibrary = getPrebuiltLibrary(repositories, library);

        return prebuiltLibrary.getBinaries();
    }

    private static PrebuiltLibrary getPrebuiltLibrary(
            NamedDomainObjectSet<PrebuiltLibraries> repositories, String libraryName) {
        List<String> repositoryNames = new ArrayList<String>();
        for (PrebuiltLibraries prebuiltLibraries : repositories) {
            repositoryNames.add(prebuiltLibraries.getName());
            PrebuiltLibrary prebuiltLibrary = prebuiltLibraries.resolveLibrary(libraryName);
            if (prebuiltLibrary != null) {
                return prebuiltLibrary;
            }
        }
        throw new PrebuiltLibraryResolveException(
                String.format(
                        "Prebuilt library with name '%s' not found in repositories '%s'.",
                        libraryName,
                        repositoryNames));
    }

    /**
     * Find all NativeLibraryArtifact matching the dependency spec.
     */
    @NonNull
    private Collection<NativeLibraryArtifact> resolveForNativeBinaries(
            AndroidNativeDependencySpec dependency) {
        String project = dependency.getProjectPath();

        // Find ArtifactContainer model in project.
        ProjectModelResolver projectModelResolver = serviceRegistry.get(ProjectModelResolver.class);
        ModelRegistry projectModel = projectModelResolver.resolveProjectModel(project);
        ArtifactContainer artifactContainer = projectModel.find(
                ModelPath.path(ARTIFACTS),
                ModelType.of(ArtifactContainer.class));
        if (artifactContainer == null) {
            throw new InvalidUserDataException(
                    "Project '" + project + "' does not export native artifacts");
        }

        // We filter the artifacts twice.
        // The first time filters according to user's dependency spec.  If there is any ambiguity,
        // we filter again to using the supplied default values.

        Collection<NativeLibraryArtifact> matches = filter(
                artifactContainer.getNativeArtifacts().values(),
                dependency.getBuildType(),
                dependency.getProductFlavor(),
                dependency.getLinkage());

        String buildType = findUniqueBuildTypeCount(matches) > 1
                ? defaultDependencySpec.getBuildType()
                : null;
        String productFlavor = findUniqueProductFlavorCount(matches) > 1
                ? defaultDependencySpec.getProductFlavor()
                : null;
        NativeDependencyLinkage linkage = findUniqueLinkageCount(matches) > 1
                ? defaultDependencySpec.getLinkage()
                : null;

        Collection<NativeLibraryArtifact> libraries =
                filter(matches, buildType, productFlavor, linkage);

        if (libraries.isEmpty()) {
            throw new LibraryResolveException(
                    String.format(
                            "Unable to find Android binary with buildType '%s' and productFlavor "
                                    + "'%s' in project '%s'",
                            buildType,
                            productFlavor,
                            project));
        }
        return libraries;
    }


    /**
     * Find the number of unique build types.
     */
    private static int findUniqueBuildTypeCount(Iterable<NativeLibraryArtifact> artifacts) {
        Set<String> unique = Sets.newHashSet();
        for (NativeLibraryArtifact artifact : artifacts) {
            unique.add(artifact.getBuildType() == null ? null : artifact.getBuildType());
        }
        return unique.size();
    }

    /**
     * Find the number of unique product flavors.
     */
    private static int findUniqueProductFlavorCount(Iterable<NativeLibraryArtifact> artifacts) {
        Set<List<String>> unique = Sets.newHashSet();
        for (NativeLibraryArtifact artifact : artifacts) {
            unique.add(artifact.getProductFlavors() == null ? null : artifact.getProductFlavors());
        }
        return unique.size();
    }

    /**
     * Find the number of unique linkage.
     */
    private static int findUniqueLinkageCount(Iterable<NativeLibraryArtifact> artifacts) {
        Set<NativeDependencyLinkage> unique = Sets.newHashSet();
        for (NativeLibraryArtifact artifact : artifacts) {
            unique.add(artifact.getLinkage() == null ? null : artifact.getLinkage());
        }
        return unique.size();
    }

    /**
     * Filter artifacts with the specified build type, product flavor and linkage.
     * Null implies we don't care about the value.
     */
    private static Collection<NativeLibraryArtifact> filter(
            @NonNull Collection<NativeLibraryArtifact> artifacts,
            @Nullable String buildType,
            @Nullable String productFlavor,
            @Nullable NativeDependencyLinkage linkage) {
        if (buildType == null && productFlavor == null && linkage == null) {
            // Just return if all filters are null for performance.
            return artifacts;
        }

        ImmutableList.Builder<NativeLibraryArtifact> builder = ImmutableList.builder();
        for (NativeLibraryArtifact artifact : artifacts) {
            if ((buildType == null || buildType.equals(artifact.getBuildType()))
                    && (productFlavor == null
                            || productFlavor.equals(
                                    StringHelper.combineAsCamelCase(artifact.getProductFlavors())))
                    && (linkage == null
                            || linkage.equals(artifact.getLinkage())
                            || artifact.getLinkage() == null)) {
                builder.add(artifact);
            }
        }
        return builder.build();
    }
}
