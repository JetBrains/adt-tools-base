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

package com.android.build.gradle.internal.dependency
import com.android.annotations.NonNull

import com.android.build.gradle.internal.ConfigurationProvider
import com.android.builder.dependency.DependencyContainer
import com.android.builder.dependency.JarDependency
import com.android.builder.dependency.LibraryDependency
import com.google.common.collect.Sets
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
/**
 * Object that represents the dependencies of a "config", in the sense of defaultConfigs, build
 * type and flavors.
 *
 * The dependencies are expressed as composite Gradle configuration objects that extends
 * all the configuration objects of the "configs".
 *
 * It optionally contains the dependencies for a test config for the given config.
 */
public class VariantDependencies implements DependencyContainer, ConfigurationProvider {

    final String name

    @NonNull
    final Configuration compileConfiguration
    @NonNull
    final Configuration packageConfiguration

    @NonNull
    private final List<LibraryDependencyImpl> libraries = []
    @NonNull
    private final List<JarDependency> jars = []
    @NonNull
    private final List<JarDependency> localJars = []

    DependencyChecker checker

    static VariantDependencies compute(@NonNull Project project,
                                       @NonNull String name,
                                       @NonNull ConfigurationProvider... providers) {
        Set<Configuration> compileConfigs = Sets.newHashSet()
        Set<Configuration> apkConfigs = Sets.newHashSet()

        for (ConfigurationProvider provider : providers) {
            compileConfigs.add(provider.compileConfiguration)
            if (provider.providedConfiguration != null) {
                compileConfigs.add(provider.providedConfiguration)
            }

            apkConfigs.add(provider.compileConfiguration)
            apkConfigs.add(provider.packageConfiguration)
        }

        Configuration compile = project.configurations.create("_${name}Compile")
        compile.setExtendsFrom(compileConfigs)

        Configuration apk = project.configurations.create("_${name}Apk")
        apk.setExtendsFrom(apkConfigs)

        return new VariantDependencies(name, compile, apk);
    }

    private VariantDependencies(@NonNull String name,
                                @NonNull Configuration compileConfiguration,
                                @NonNull Configuration packageConfiguration) {
        this.name = name
        this.compileConfiguration = compileConfiguration
        this.packageConfiguration = packageConfiguration
    }

    public String getName() {
        return name
    }

    @Override
    Configuration getProvidedConfiguration() {
        return null
    }

    void addLibraries(@NonNull List<LibraryDependencyImpl> list) {
        libraries.addAll(list)
    }

    void addJars(@NonNull Collection<JarDependency> list) {
        jars.addAll(list)
    }

    void addLocalJars(@NonNull Collection<JarDependency> list) {
        localJars.addAll(list)
    }

    @NonNull
    List<LibraryDependencyImpl> getLibraries() {
        return libraries
    }

    @NonNull
    @Override
    List<? extends LibraryDependency> getAndroidDependencies() {
        return libraries
    }

    @NonNull
    @Override
    List<JarDependency> getJarDependencies() {
        return jars
    }

    @NonNull
    @Override
    List<JarDependency> getLocalDependencies() {
        return localJars
    }
}
