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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.NativeDependencyLinkage;
import com.android.build.gradle.internal.core.Abi;
import com.android.utils.StringHelper;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.resolve.ProjectModelResolver;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.language.base.internal.resolve.LibraryResolveException;
import org.gradle.model.internal.core.ModelPath;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.model.internal.type.ModelType;

import java.io.File;
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
                ModelPath.path("artifacts"),
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


    private static class ArtifactFilter implements Predicate<NativeLibraryArtifact> {
        @Nullable
        private final String buildType;
        @Nullable
        private final String productFlavor;
        @Nullable
        private final NativeDependencyLinkage linkage;

        public ArtifactFilter(
                @Nullable String buildType,
                @Nullable String productFlavor,
                @Nullable NativeDependencyLinkage linkage) {
            this.buildType = buildType;
            this.productFlavor = productFlavor;
            this.linkage = linkage;
        }

        @Override
        public boolean apply(NativeLibraryArtifact artifact) {
            return ((buildType == null || buildType.equals(artifact.getBuildType()))
                    && (productFlavor == null
                            || productFlavor.equals(StringHelper.combineAsCamelCase(artifact.getProductFlavors())))
                    && (linkage == null
                            || linkage.equals(artifact.getLinkage())));
        }
    }
}
