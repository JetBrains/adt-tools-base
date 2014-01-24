/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.build.gradle.internal.model;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.BasePlugin;
import com.android.build.gradle.internal.dependency.LibraryDependencyImpl;
import com.android.build.gradle.internal.dependency.VariantDependencies;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.builder.dependency.JarDependency;
import com.android.builder.dependency.LibraryDependency;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.Dependencies;
import com.google.common.collect.Lists;

import org.gradle.api.Project;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 */
public class DependenciesImpl implements Dependencies, Serializable {
    private static final long serialVersionUID = 1L;

    @NonNull
    private final List<AndroidLibrary> libraries;
    @NonNull
    private final List<File> jars;
    @NonNull
    private final List<String> projects;

    @NonNull
    static DependenciesImpl cloneDependenciesForJavaArtifacts(@NonNull Dependencies dependencies) {
        List<AndroidLibrary> libraries = Collections.emptyList();
        List<File> jars = Lists.newArrayList(dependencies.getJars());
        List<String> projects = Collections.emptyList();

        return new DependenciesImpl(libraries, jars, projects);
    }

    @NonNull
    static DependenciesImpl cloneDependencies(
            @NonNull BaseVariantData variantData,
            @NonNull BasePlugin basePlugin,
            @NonNull Set<Project> gradleProjects) {

        VariantDependencies variantDependencies = variantData.getVariantDependency();

        List<AndroidLibrary> libraries;
        List<File> jars;
        List<String> projects;

        List<LibraryDependencyImpl> libs = variantDependencies.getLibraries();
        libraries = Lists.newArrayListWithCapacity(libs.size());
        for (LibraryDependencyImpl libImpl : libs) {
            AndroidLibrary clonedLib = getAndroidLibrary(libImpl, gradleProjects);
            libraries.add(clonedLib);
        }

        List<JarDependency> jarDeps = variantDependencies.getJarDependencies();
        List<JarDependency> localDeps = variantDependencies.getLocalDependencies();

        jars = Lists.newArrayListWithExpectedSize(jarDeps.size() + localDeps.size());
        projects = Lists.newArrayList();

        for (JarDependency jarDep : jarDeps) {
            File jarFile = jarDep.getJarFile();
            Project projectMatch = getProject(jarFile, gradleProjects);
            if (projectMatch != null) {
                projects.add(projectMatch.getPath());
            } else {
                jars.add(jarFile);
            }
        }

        for (JarDependency jarDep : localDeps) {
            jars.add(jarDep.getJarFile());
        }

        if (variantData.getVariantConfiguration().getMergedFlavor().getRenderscriptSupportMode()) {
            jars.add(basePlugin.getAndroidBuilder(variantData).getRenderScriptSupportJar());
        }

        return new DependenciesImpl(libraries, jars, projects);
    }

    public DependenciesImpl(@NonNull Set<File> jars) {
        this.jars = Lists.newArrayList(jars);
        this.libraries = Collections.emptyList();
        this.projects = Collections.emptyList();
    }

    private DependenciesImpl(@NonNull List<AndroidLibrary> libraries,
                             @NonNull List<File> jars,
                             @NonNull List<String> projects) {
        this.libraries = libraries;
        this.jars = jars;
        this.projects = projects;
    }

    @NonNull
    @Override
    public List<AndroidLibrary> getLibraries() {
        return libraries;
    }

    @NonNull
    @Override
    public List<File> getJars() {
        return jars;
    }

    @NonNull
    @Override
    public List<String> getProjects() {
        return projects;
    }

    @NonNull
    private static AndroidLibrary getAndroidLibrary(@NonNull LibraryDependency libImpl,
                                                    @NonNull Set<Project> gradleProjects) {
        File bundle = libImpl.getBundle();
        Project projectMatch = getProject(bundle, gradleProjects);

        List<LibraryDependency> deps = libImpl.getDependencies();
        List<AndroidLibrary> clonedDeps = Lists.newArrayListWithCapacity(deps.size());
        for (LibraryDependency child : deps) {
            AndroidLibrary clonedLib = getAndroidLibrary(child, gradleProjects);
            clonedDeps.add(clonedLib);
        }

        return new AndroidLibraryImpl(libImpl, clonedDeps,
                projectMatch != null ? projectMatch.getPath() : null);
    }

    @Nullable
    private static Project getProject(File outputFile, Set<Project> gradleProjects) {
        // search for a project that contains this file in its output folder.
        Project projectMatch = null;
        for (Project project : gradleProjects) {
            File buildDir = project.getBuildDir();
            if (contains(buildDir, outputFile)) {
                projectMatch = project;
                break;
            }
        }
        return projectMatch;
    }

    private static boolean contains(@NonNull File dir, @NonNull File file) {
        try {
            dir = dir.getCanonicalFile();
            file = file.getCanonicalFile();
        } catch (IOException e) {
            return false;
        }

        // quick fail
        return file.getAbsolutePath().startsWith(dir.getAbsolutePath()) && doContains(dir, file);
    }

    private static boolean doContains(@NonNull File dir, @NonNull File file) {
        File parent = file.getParentFile();
        return parent != null && (parent.equals(dir) || doContains(dir, parent));
    }
}
