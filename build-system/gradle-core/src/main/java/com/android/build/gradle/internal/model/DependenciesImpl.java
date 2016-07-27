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

import static com.android.SdkConstants.DOT_JAR;
import static com.android.SdkConstants.FD_JARS;

import com.android.annotations.NonNull;
import com.android.annotations.concurrency.Immutable;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.VariantConfiguration;
import com.android.builder.dependency.DependencyContainer;
import com.android.builder.dependency.MavenCoordinatesImpl;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Dependencies;
import com.android.builder.model.JavaLibrary;
import com.android.ide.common.caching.CreatingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Serializable implementation of Dependencies for use in the model.
 */
@Immutable
public class DependenciesImpl implements Dependencies, Serializable {
    private static final long serialVersionUID = 1L;

    private static final CreatingCache<AndroidLibrary, AndroidLibraryImpl> sLibCache
            = new CreatingCache<AndroidLibrary, AndroidLibraryImpl>(DependenciesImpl::convertAndroidLibrary);
    private static final CreatingCache<JavaLibrary, JavaLibraryImpl> sJarCache
            = new CreatingCache<JavaLibrary, JavaLibraryImpl>(DependenciesImpl::convertJarLibrary);
    private static final CreatingCache<JavaLibrary, JavaLibraryImpl> sFlatJarCache
            = new CreatingCache<JavaLibrary, JavaLibraryImpl>(DependenciesImpl::convertFlatJarLibrary);

    @NonNull
    private final List<AndroidLibrary> libraries;
    @NonNull
    private final List<JavaLibrary> javaLibraries;
    @NonNull
    private final List<String> projects;

    private static int sModelLevel = AndroidProject.MODEL_LEVEL_0_ORIGNAL;

    public static void setModelLevel(int modelLevel) {
        sModelLevel = modelLevel;
    }

    public static void clearCaches() {
        sLibCache.clear();
        sJarCache.clear();
        sFlatJarCache.clear();
    }

    @NonNull
    static DependenciesImpl getEmpty() {
        return new DependenciesImpl(ImmutableList.of(), ImmutableList.of(), ImmutableList.of());
    }

    @NonNull
    static DependenciesImpl cloneDependenciesForJavaArtifacts(@NonNull Dependencies dependencies) {
        List<AndroidLibrary> libraries = Collections.emptyList();
        List<JavaLibrary> javaLibraries = Lists.newArrayList(dependencies.getJavaLibraries());
        List<String> projects = Collections.emptyList();

        return new DependenciesImpl(libraries, javaLibraries, projects);
    }

    @NonNull
    static DependenciesImpl cloneDependencies(
            @NonNull DependencyContainer dependencies,
            @NonNull VariantConfiguration variantConfiguration,
            @NonNull AndroidBuilder androidBuilder) {
        List<AndroidLibrary> clonedAndroidLibraries;
        List<JavaLibrary> clonedJavaLibraries;
        List<String> clonedProjects = Lists.newArrayList();

        List<AndroidLibrary> androidLibraries = dependencies.getAndroidDependencies();
        clonedAndroidLibraries = Lists.newArrayListWithCapacity(androidLibraries.size());

        List<JavaLibrary> javaLibraries = dependencies.getJarDependencies();
        List<JavaLibrary> localJavaLibraries = dependencies.getLocalDependencies();

        clonedJavaLibraries = Lists.newArrayListWithExpectedSize(javaLibraries.size() + localJavaLibraries.size());

        for (AndroidLibrary libImpl : androidLibraries) {
            AndroidLibrary clonedLib = sLibCache.get(libImpl);
            if (clonedLib != null) {
                clonedAndroidLibraries.add(clonedLib);
            }
        }

        // if we are in compatibility mode, we need to look through the android libraries,
        // for the java dependencies, and put them in the cloned java libraries list.
        if (sModelLevel < AndroidProject.MODEL_LEVEL_2_DEP_GRAPH) {
            for (AndroidLibrary androidLibrary : androidLibraries) {
                if (androidLibrary.getProject() == null) {
                    handleFlatClonedAndroidLib(androidLibrary, clonedJavaLibraries, clonedProjects);
                }
            }
        }

        for (JavaLibrary javaLibrary : javaLibraries) {
            if (sModelLevel < AndroidProject.MODEL_LEVEL_2_DEP_GRAPH) {
                handleFlatClonedJavaLib(javaLibrary, clonedJavaLibraries, clonedProjects);
            } else {
                // just convert the library using the cache. It'll recursively
                // handle the dependencies.
                JavaLibraryImpl clonedJavaLibrary = sJarCache.get(javaLibrary);
                if (clonedJavaLibrary != null) {
                    clonedJavaLibraries.add(clonedJavaLibrary);
                }
            }
        }

        for (JavaLibrary localJavaLibrary : localJavaLibraries) {
            clonedJavaLibraries.add(
                    new JavaLibraryImpl(
                            localJavaLibrary.getJarFile(),
                            null /*project*/,
                            ImmutableList.<JavaLibrary>of(),
                            localJavaLibrary.getRequestedCoordinates(),
                            localJavaLibrary.getResolvedCoordinates(),
                            localJavaLibrary.isSkipped(),
                            localJavaLibrary.isProvided()));
        }

        if (variantConfiguration.getRenderscriptSupportModeEnabled()) {
            File supportJar = androidBuilder.getRenderScriptSupportJar();
            if (supportJar != null) {
                clonedJavaLibraries.add(new JavaLibraryImpl(
                        supportJar,
                        null /*project*/,
                        ImmutableList.<JavaLibrary>of(),
                        null,
                        new MavenCoordinatesImpl(
                                "com.android.support",
                                "renderscript",
                                androidBuilder.getTargetInfo().getBuildTools().getRevision().toString()),
                        false, /*isSkipped*/
                        false /*isProvided*/));
            }
        }

        return new DependenciesImpl(
                clonedAndroidLibraries,
                clonedJavaLibraries,
                clonedProjects);
    }

    private static void handleFlatClonedAndroidLib(
            @NonNull AndroidLibrary androidLibrary,
            @NonNull List<JavaLibrary> clonedJavaLibraries,
            @NonNull List<String> clonedProjects) {
        // only handled the java dependencies.
        for (JavaLibrary javaDependency : androidLibrary.getJavaDependencies()) {
            handleFlatClonedJavaLib(javaDependency, clonedJavaLibraries, clonedProjects);
        }

        // then recursively go through the android children
        for (AndroidLibrary androidDependency : androidLibrary.getLibraryDependencies()) {
            handleFlatClonedAndroidLib(androidDependency, clonedJavaLibraries, clonedProjects);
        }
    }

    private static void handleFlatClonedJavaLib(
            @NonNull JavaLibrary javaLibrary,
            @NonNull List<JavaLibrary> clonedJavaLibraries,
            @NonNull List<String> clonedProjects) {
        boolean customArtifact = javaLibrary.getResolvedCoordinates().getClassifier() != null;

        if (!customArtifact && javaLibrary.getProject() != null) {
            clonedProjects.add(javaLibrary.getProject().intern());
        } else {
            JavaLibrary clonedJavaLib = sFlatJarCache.get(javaLibrary);
            if (clonedJavaLib != null && !clonedJavaLibraries.contains(clonedJavaLib)) {
                clonedJavaLibraries.add(clonedJavaLib);
            }
            // then recursively go through the rest.
            for (JavaLibrary javaDependency : javaLibrary.getDependencies()) {
                handleFlatClonedJavaLib(javaDependency, clonedJavaLibraries, clonedProjects);
            }
        }
    }

    private DependenciesImpl(
            @NonNull List<AndroidLibrary> libraries,
            @NonNull List<JavaLibrary> javaLibraries,
            @NonNull List<String> projects) {
        this.libraries = libraries;
        this.javaLibraries = javaLibraries;
        this.projects = projects;
    }

    @NonNull
    @Override
    public Collection<AndroidLibrary> getLibraries() {
        return libraries;
    }

    @NonNull
    @Override
    public Collection<JavaLibrary> getJavaLibraries() {
        return javaLibraries;
    }

    @NonNull
    @Override
    public List<String> getProjects() {
        return projects;
    }

    @NonNull
    private static AndroidLibraryImpl convertAndroidLibrary(
            @NonNull AndroidLibrary libraryDependency) {
        boolean newDepModel = sModelLevel >= AndroidProject.MODEL_LEVEL_2_DEP_GRAPH;

        List<JavaLibrary> clonedJavaLibraries = ImmutableList.of();
        List<AndroidLibrary> clonedDeps = ImmutableList.of();

        // if this is a sub-project, no need to include the children.
        if (newDepModel || libraryDependency.getProject() == null) {
            List<? extends AndroidLibrary> deps = libraryDependency.getLibraryDependencies();
            clonedDeps = Lists.newArrayListWithCapacity(deps.size());
            for (AndroidLibrary child : deps) {
                AndroidLibrary clonedLib = sLibCache.get(child);
                if (clonedLib != null) {
                    clonedDeps.add(clonedLib);
                }
            }

            // get the clones of the Java libraries, only in level2. In level1, this is passed
            // as direct dependencies since we don't care about order.
            if (newDepModel) {
                Collection<? extends JavaLibrary> jarDeps = libraryDependency.getJavaDependencies();
                clonedJavaLibraries = Lists.newArrayListWithCapacity(jarDeps.size());
                for (JavaLibrary javaLibrary : jarDeps) {
                    JavaLibraryImpl clonedJar = sJarCache.get(javaLibrary);
                    if (clonedJar != null) {
                        clonedJavaLibraries.add(clonedJar);
                    }
                }
            }
        }

        // compute local jar even if the bundle isn't exploded.
        Collection<File> localJarOverride = findLocalJar(libraryDependency);

        return new AndroidLibraryImpl(
                libraryDependency,
                clonedDeps,
                clonedJavaLibraries,
                localJarOverride);
    }

    @NonNull
    private static JavaLibraryImpl convertJarLibrary(@NonNull JavaLibrary javaLibrary) {
        List<? extends JavaLibrary> javaDependencies = javaLibrary.getDependencies();
        List<JavaLibrary> clonedDependencies = Lists.newArrayListWithCapacity(
                javaDependencies.size());

        for (JavaLibrary javaDependency : javaDependencies) {
            JavaLibraryImpl clonedJar = sJarCache.get(javaDependency);
            if (clonedJar != null) {
                clonedDependencies.add(clonedJar);
            }
        }

        return new JavaLibraryImpl(
                javaLibrary.getJarFile(),
                javaLibrary.getProject(),
                clonedDependencies,
                javaLibrary.getRequestedCoordinates(),
                javaLibrary.getResolvedCoordinates(),
                javaLibrary.isSkipped(),
                javaLibrary.isProvided());
    }


    @NonNull
    private static JavaLibraryImpl convertFlatJarLibrary(@NonNull JavaLibrary javaLibrary) {
        return new JavaLibraryImpl(
                javaLibrary.getJarFile(),
                null /*project*/,
                ImmutableList.of(),
                javaLibrary.getRequestedCoordinates(),
                javaLibrary.getResolvedCoordinates(),
                javaLibrary.isSkipped(),
                javaLibrary.isProvided());
    }

    /**
     * Finds the local jar for an aar.
     *
     * Since the model can be queried before the aar are exploded, we attempt to get them
     * from inside the aar.
     *
     * @param library the library.
     * @return its local jars.
     */
    @NonNull
    private static Collection<File> findLocalJar(@NonNull AndroidLibrary library) {
        // if the library is exploded, just use the normal method.
        File explodedFolder = library.getFolder();
        if (explodedFolder.isDirectory()) {
            return library.getLocalJars();
        }

        // if the aar file is present, search inside it for jar files under libs/
        File aarFile = library.getBundle();
        if (aarFile.isFile()) {
            List<File> jarList = Lists.newArrayList();

            File jarsFolder = new File(explodedFolder, FD_JARS);

            ZipFile zipFile = null;
            try {
                //noinspection IOResourceOpenedButNotSafelyClosed
                zipFile = new ZipFile(aarFile);

                for (Enumeration<? extends ZipEntry> e = zipFile.entries(); e.hasMoreElements();) {
                    ZipEntry zipEntry = e.nextElement();
                    String name = zipEntry.getName();
                    if (name.startsWith("libs/") && name.endsWith(DOT_JAR)) {
                        jarList.add(new File(jarsFolder, name.replace('/', File.separatorChar)));
                    }
                }

                return jarList;
            } catch (FileNotFoundException ignored) {
                // should not happen since we check ahead of time
            } catch (IOException e) {
                // we'll return an empty list below
            } finally {
                if (zipFile != null) {
                    try {
                        zipFile.close();
                    } catch (IOException ignored) {
                    }
                }
            }
        }

        return Collections.emptyList();
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("DependenciesImpl{");
        sb.append("libraries=").append(libraries);
        sb.append(", javaLibraries=").append(javaLibraries);
        sb.append(", projects=").append(projects);
        sb.append('}');
        return sb.toString();
    }
}
