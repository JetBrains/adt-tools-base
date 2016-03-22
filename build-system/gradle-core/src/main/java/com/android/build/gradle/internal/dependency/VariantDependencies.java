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

package com.android.build.gradle.internal.dependency;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.ConfigurationProvider;
import com.android.builder.core.ErrorReporter;
import com.android.builder.core.VariantType;
import com.android.builder.dependency.DependencyContainer;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ResolvedArtifact;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Object that represents the dependencies of a "config", in the sense of defaultConfigs, build
 * type and flavors.
 *
 * <p>The dependencies are expressed as composite Gradle configuration objects that extends
 * all the configuration objects of the "configs".</p>
 *
 * <p>It optionally contains the dependencies for a test config for the given config.</p>
 */
public class VariantDependencies {

    /** Name of the metadata configuration */
    public static final String CONFIGURATION_METADATA = "-metadata";
    /** Name of the mapping configuration */
    public static final String CONFIGURATION_MAPPING = "-mapping";
    /** Name of the classes configuration */
    public static final String CONFIGURATION_CLASSES = "-classes";
    /** Name of the manifest configuration */
    public static final String CONFIGURATION_MANIFEST = "-manifest";

    @NonNull
    private final String variantName;

    @NonNull
    private final Configuration compileConfiguration;
    @NonNull
    private final Configuration packageConfiguration;
    @Nullable
    private final Configuration publishConfiguration;
    @NonNull
    private final Configuration annotationProcessorConfiguration;

    @Nullable
    private final Configuration mappingConfiguration;
    @Nullable
    private final Configuration classesConfiguration;
    @Nullable
    private final Configuration metadataConfiguration;

    @Nullable
    private Configuration manifestConfiguration;

    private DependencyContainer compileDependencies;
    private DependencyContainer packageDependencies;

    /**
     *  Whether we have a direct dependency on com.android.support:support-annotations; this
     * is used to drive whether we extract annotations when building libraries for example
     */
    private boolean annotationsPresent;

    @NonNull
    private DependencyChecker checker;

    public static VariantDependencies compute(
            @NonNull Project project,
            @NonNull ErrorReporter errorReporter,
            @NonNull String variantName,
            boolean publishVariant,
            @NonNull VariantType variantType,
            @Nullable VariantType testedVariantType,
            @Nullable VariantDependencies parentVariant,
            @NonNull ConfigurationProvider... providers) {
        Set<Configuration> compileConfigs = Sets.newHashSetWithExpectedSize(providers.length * 2);
        Set<Configuration> apkConfigs = Sets.newHashSetWithExpectedSize(providers.length);
        Set<Configuration> annotationConfigs = Sets.newHashSetWithExpectedSize(providers.length);

        for (ConfigurationProvider provider : providers) {
            if (provider != null) {
                compileConfigs.add(provider.getCompileConfiguration());
                if (provider.getProvidedConfiguration() != null) {
                    compileConfigs.add(provider.getProvidedConfiguration());
                }

                apkConfigs.add(provider.getCompileConfiguration());
                apkConfigs.add(provider.getPackageConfiguration());
                annotationConfigs.add(provider.getAnnotationProcessorConfiguration());
            }
        }

        if (parentVariant != null) {
            compileConfigs.add(parentVariant.getCompileConfiguration());
            apkConfigs.add(parentVariant.getPackageConfiguration());
            annotationConfigs.add(parentVariant.getAnnotationProcessorConfiguration());
        }

        Configuration compile = project.getConfigurations().maybeCreate("_" + variantName + "Compile");
        compile.setVisible(false);
        compile.setDescription("## Internal use, do not manually configure ##");
        compile.setExtendsFrom(compileConfigs);

        Configuration annotationProcessor =
                project.getConfigurations().maybeCreate("_" + variantName + "AnnotationProcessor");
        annotationProcessor.setVisible(false);
        annotationProcessor.setDescription("## Internal use, do not manually configure ##");
        annotationProcessor.setExtendsFrom(annotationConfigs);

        Configuration apk = project.getConfigurations().maybeCreate(
                variantType == VariantType.LIBRARY
                    ? "_" + variantName + "Publish"
                    : "_" + variantName + "Apk");

        apk.setVisible(false);
        apk.setDescription("## Internal use, do not manually configure ##");
        apk.setExtendsFrom(apkConfigs);

        Configuration publish = null;
        Configuration mapping = null;
        Configuration classes = null;
        Configuration metadata = null;
        Configuration manifest = null;
        if (publishVariant) {
            publish = project.getConfigurations().maybeCreate(variantName);
            publish.setDescription("Published Configuration for Variant " + variantName);
            // if the variant is not a library, then the publishing configuration should
            // not extend from the apkConfigs. It's mostly there to access the artifact from
            // another project but it shouldn't bring any dependencies with it.
            if (variantType == VariantType.LIBRARY) {
                publish.setExtendsFrom(apkConfigs);
            }

            // create configuration for -metadata.
            metadata = project.getConfigurations().create(variantName + CONFIGURATION_METADATA);
            metadata.setDescription("Published APKs metadata for Variant " + variantName);

            // create configuration for -mapping and -classes.
            mapping = project.getConfigurations().maybeCreate(variantName + CONFIGURATION_MAPPING);
            mapping.setDescription("Published mapping configuration for Variant " + variantName);

            classes = project.getConfigurations().maybeCreate(variantName + CONFIGURATION_CLASSES);
            classes.setDescription("Published classes configuration for Variant " + variantName);

            // create configuration for -manifest
            manifest =
                    project.getConfigurations().maybeCreate(variantName + CONFIGURATION_MANIFEST);
            manifest.setDescription("Published manifest configuration for Variant " + variantName);

            // because we need the transitive dependencies for the classes, extend the compile config.
            classes.setExtendsFrom(compileConfigs);
        }

        DependencyChecker checker = new DependencyChecker(
                project.getName(),
                variantName,
                errorReporter,
                variantType,
                testedVariantType);

        return new VariantDependencies(
                variantName,
                checker,
                compile,
                apk,
                publish,
                annotationProcessor,
                mapping,
                classes,
                metadata,
                manifest);
    }

    private VariantDependencies(
            @NonNull String variantName,
            @NonNull DependencyChecker dependencyChecker,
            @NonNull  Configuration compileConfiguration,
            @NonNull  Configuration packageConfiguration,
            @Nullable Configuration publishConfiguration,
            @NonNull  Configuration annotationProcessorConfiguration,
            @Nullable Configuration mappingConfiguration,
            @Nullable Configuration classesConfiguration,
            @Nullable Configuration metadataConfiguration,
            @Nullable Configuration manifestConfiguration) {
        this.variantName = variantName;
        this.compileConfiguration = compileConfiguration;
        this.packageConfiguration = packageConfiguration;
        this.publishConfiguration = publishConfiguration;
        this.annotationProcessorConfiguration = annotationProcessorConfiguration;
        this.mappingConfiguration = mappingConfiguration;
        this.classesConfiguration = classesConfiguration;
        this.metadataConfiguration = metadataConfiguration;
        this.manifestConfiguration = manifestConfiguration;
        this.checker = dependencyChecker;
    }

    public String getName() {
        return variantName;
    }

    @NonNull
    public Configuration getCompileConfiguration() {
        return compileConfiguration;
    }

    @NonNull
    public Configuration getPackageConfiguration() {
        return packageConfiguration;
    }

    @Nullable
    public Configuration getPublishConfiguration() {
        return publishConfiguration;
    }

    @NonNull
    public Configuration getAnnotationProcessorConfiguration() {
        return annotationProcessorConfiguration;
    }

    @Nullable
    public Configuration getMappingConfiguration() {
        return mappingConfiguration;
    }

    @Nullable
    public Configuration getClassesConfiguration() {
        return classesConfiguration;
    }

    @Nullable
    public Configuration getMetadataConfiguration() {
        return metadataConfiguration;
    }

    @Nullable
    public Configuration getManifestConfiguration() {
        return manifestConfiguration;
    }

    public void setDependencies(@NonNull DependencyContainer compileDependencies, @NonNull DependencyContainer packageDependencies) {
        this.compileDependencies = compileDependencies;
        this.packageDependencies = packageDependencies;
    }

    public DependencyContainer getCompileDependencies() {
        return compileDependencies;
    }

    public DependencyContainer getPackageDependencies() {
        return packageDependencies;
    }

    @NonNull
    public List<File> resolveAndGetAnnotationProcessorClassPath() {
        Set<ResolvedArtifact> artifacts = getAnnotationProcessorConfiguration()
                .getResolvedConfiguration().getResolvedArtifacts();

        ImmutableList.Builder<File> files = ImmutableList.builder();
        for (ResolvedArtifact artifact: artifacts) {
            files.add(artifact.getFile());
        }
        return files.build();
    }

    @NonNull
    public DependencyChecker getChecker() {
        return checker;
    }

    public void setAnnotationsPresent(boolean annotationsPresent) {
        this.annotationsPresent = annotationsPresent;
    }

    public boolean isAnnotationsPresent() {
        return annotationsPresent;
    }

    public boolean hasNonOptionalLibraries() {
        // non optional libraries mean that there is some libraries in the package
        // dependencies
        return !packageDependencies.getAndroidDependencies().isEmpty();
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .add("name", variantName)
                .toString();
    }
}
