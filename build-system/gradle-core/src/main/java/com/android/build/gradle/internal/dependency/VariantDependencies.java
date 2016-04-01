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
import com.android.builder.core.VariantType;
import com.android.builder.dependency.DependencyContainer;
import com.android.builder.dependency.JarDependency;
import com.android.builder.dependency.LibraryDependency;
import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;

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
public class VariantDependencies implements DependencyContainer {

    private String name;

    @NonNull
    private Configuration compileConfiguration;
    @NonNull
    private Configuration packageConfiguration;
    @NonNull
    private Configuration publishConfiguration;

    @Nullable
    private Configuration mappingConfiguration;
    @Nullable
    private Configuration classesConfiguration;
    @Nullable
    private Configuration metadataConfiguration;

    @NonNull
    private List<LibraryDependencyImpl> libraries = Lists.newArrayList();
    @NonNull
    private List<JarDependency> jars = Lists.newArrayList();
    @NonNull
    private List<JarDependency> localJars = Lists.newArrayList();

    /**
     *  Whether we have a direct dependency on com.android.support:support-annotations; this
     * is used to drive whether we extract annotations when building libraries for example
     */
    private boolean annotationsPresent;

    @NonNull
    private DependencyChecker checker;

    public static VariantDependencies compute(
            @NonNull Project project,
            @NonNull String name,
            boolean publishVariant,
            @NonNull VariantType variantType,
            @Nullable VariantDependencies parentVariant,
            @NonNull ConfigurationProvider... providers) {
        Set<Configuration> compileConfigs = Sets.newHashSetWithExpectedSize(providers.length * 2);
        Set<Configuration> apkConfigs = Sets.newHashSetWithExpectedSize(providers.length);

        for (ConfigurationProvider provider : providers) {
            if (provider != null) {
                compileConfigs.add(provider.getCompileConfiguration());
                if (provider.getProvidedConfiguration() != null) {
                    compileConfigs.add(provider.getProvidedConfiguration());
                }

                apkConfigs.add(provider.getCompileConfiguration());
                apkConfigs.add(provider.getPackageConfiguration());
            }
        }

        if (parentVariant != null) {
            compileConfigs.add(parentVariant.getCompileConfiguration());
            apkConfigs.add(parentVariant.getPackageConfiguration());
        }

        Configuration compile = project.getConfigurations().maybeCreate("_" + name + "Compile");
        compile.setVisible(false);
        compile.setDescription("## Internal use, do not manually configure ##");
        compile.setExtendsFrom(compileConfigs);

        Configuration apk = project.getConfigurations().maybeCreate(
                variantType == VariantType.LIBRARY
                    ? "_" + name + "Publish"
                    : "_" + name + "Apk");

        apk.setVisible(false);
        apk.setDescription("## Internal use, do not manually configure ##");
        apk.setExtendsFrom(apkConfigs);

        Configuration publish = null, mapping = null, classes = null, metadata = null;
        if (publishVariant) {
            publish = project.getConfigurations().maybeCreate(name);
            publish.setDescription("Published Configuration for Variant " + name);
            // if the variant is not a library, then the publishing configuration should
            // not extend from the apkConfigs. It's mostly there to access the artifact from
            // another project but it shouldn't bring any dependencies with it.
            if (variantType == VariantType.LIBRARY) {
                publish.setExtendsFrom(apkConfigs);
            }

            // create configuration for -metadata.
            metadata = project.getConfigurations().create(name + "-metadata");
            metadata.setDescription("Published APKs metadata for Variant " + name);

            // create configuration for -mapping and -classes.
            mapping = project.getConfigurations().maybeCreate(name + "-mapping");
            mapping.setDescription("Published mapping configuration for Variant " + name);

            classes = project.getConfigurations().maybeCreate(name + "-classes");
            classes.setDescription("Published classes configuration for Variant " + name);
            // because we need the transitive dependencies for the classes, extend the compile config.
            classes.setExtendsFrom(compileConfigs);
        }

        return new VariantDependencies(
                name,
                compile,
                apk,
                publish,
                mapping,
                classes,
                metadata,
                variantType != VariantType.UNIT_TEST);
    }

    private VariantDependencies(@NonNull  String name,
            @NonNull  Configuration compileConfiguration,
            @NonNull  Configuration packageConfiguration,
            @Nullable Configuration publishConfiguration,
            @Nullable Configuration mappingConfiguration,
            @Nullable Configuration classesConfiguration,
            @Nullable Configuration metadataConfiguration,
            boolean skipClassesInAndroid) {
        this.name = name;
        this.compileConfiguration = compileConfiguration;
        this.packageConfiguration = packageConfiguration;
        this.publishConfiguration = publishConfiguration;
        this.mappingConfiguration = mappingConfiguration;
        this.classesConfiguration = classesConfiguration;
        this.metadataConfiguration = metadataConfiguration;
        this.checker = new DependencyChecker(this, skipClassesInAndroid);
    }

    public String getName() {
        return name;
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

    public void addLibraries(@NonNull List<LibraryDependencyImpl> list) {
        libraries.addAll(list);
    }

    public void addJars(@NonNull Collection<JarDependency> list) {
        jars.addAll(list);
    }

    public void addLocalJars(@NonNull Collection<JarDependency> list) {
        localJars.addAll(list);
    }

    @NonNull
    public List<LibraryDependencyImpl> getLibraries() {
        return libraries;
    }

    @NonNull
    @Override
    public List<? extends LibraryDependency> getAndroidDependencies() {
        return libraries;
    }

    @NonNull
    @Override
    public List<JarDependency> getJarDependencies() {
        return jars;
    }

    @NonNull
    @Override
    public List<JarDependency> getLocalDependencies() {
        return localJars;
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
        for (LibraryDependency libraryDependency : libraries) {
            if (!libraryDependency.isOptional()) {
                return true;
            }
        }

        return false;
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .add("name", name)
                .toString();
    }
}
