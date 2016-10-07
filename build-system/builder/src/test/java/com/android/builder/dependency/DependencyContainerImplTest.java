/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.builder.dependency;

import static com.google.common.truth.Truth.assertThat;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.JavaLibrary;
import com.android.builder.model.MavenCoordinates;
import com.google.common.collect.ImmutableList;

import org.junit.Test;
import org.mockito.Mockito;

import java.io.File;
import java.util.Collection;
import java.util.List;

public class DependencyContainerImplTest {

    @Test
    public void testFlatten() throws Exception {
        /* test a simple case:
            AAR: project:library:aar:unspecified
            Path: :library
                JAR: project:jar:jar:unspecified
                Path: :jar
                    JAR: com.google.guava:guava:jar:18.0
                    Path: null
        */

        // start from the depth of the graph.
        JavaLibrary guava = mockJavaLibrary(
                new File("guava.jar"),
                new MavenCoordinatesImpl("com.google.guava", "guava", "18.0"),
                ImmutableList.of(),
                null);

        JavaLibrary jarModule = mockJavaLibrary(
                new File("jar.jar"),
                new MavenCoordinatesImpl("project", "jar", "unspecified"),
                ImmutableList.of(guava),
                ":jar");

        AndroidLibrary libraryModule = mockAndroidLibrary(
                new File("library.jar"),
                new MavenCoordinatesImpl("project", "library", "unspecified"),
                ImmutableList.of(),
                ImmutableList.of(jarModule),
                ":library");

        DependencyContainerImpl container = new DependencyContainerImpl(
                ImmutableList.of(libraryModule),
                ImmutableList.of(),
                ImmutableList.of());

        DependencyContainer flatContainer = container.flatten(null, null);

        ImmutableList<AndroidLibrary> androidLibraries = flatContainer.getAndroidDependencies();
        assertThat(androidLibraries).containsExactly(libraryModule);

        ImmutableList<JavaLibrary> javaLibraries = flatContainer.getJarDependencies();
        assertThat(javaLibraries).containsExactly(jarModule, guava);
    }

    @Test
    public void testFlattenWithTestedLib() throws Exception {
        /* test a simple case where the test artifact of a lib has dependency, and the
           tested lib has some too.

            lib:
              com.google.random:random:18.0 (jar, project: null)
            test:
              project:library:aar:unspecified (aar, project: :library)
                project:jar:jar:unspecified (jar, project :jar)
                   com.google.guava:guava:jar:18.0 (jar, project: null)
        */

        // start from the depth of the graph.
        JavaLibrary guava = mockJavaLibrary(
                new File("guava.jar"),
                new MavenCoordinatesImpl("com.google.guava", "guava", "18.0"),
                ImmutableList.of(),
                null);

        JavaLibrary jarModule = mockJavaLibrary(
                new File("jar.jar"),
                new MavenCoordinatesImpl("project", "jar", "unspecified"),
                ImmutableList.of(guava),
                ":jar");

        AndroidLibrary libraryModule = mockAndroidLibrary(
                new File("library.jar"),
                new MavenCoordinatesImpl("project", "library", "unspecified"),
                ImmutableList.of(),
                ImmutableList.of(jarModule),
                ":library");

        // also have a dependency on the lib
        JavaLibrary randomLib = mockJavaLibrary(
                new File("random.jar"),
                new MavenCoordinatesImpl("com.google.random", "random", "18.0"),
                ImmutableList.of(),
                null);

        AndroidLibrary testedModule = mockAndroidLibrary(
                new File("tested.jar"),
                new MavenCoordinatesImpl("project", "tested", "unspecified"),
                ImmutableList.of(),
                ImmutableList.of(),
                ":tested");

        // when the graph is resolved, the dependencies of the lib show up directly in the tested
        // graph, but not the lib.
        DependencyContainerImpl container = new DependencyContainerImpl(
                ImmutableList.of(libraryModule),
                ImmutableList.of(randomLib),
                ImmutableList.of());

        DependencyContainerImpl testedContainer = new DependencyContainerImpl(
                ImmutableList.of(),
                ImmutableList.of(randomLib),
                ImmutableList.of());

        DependencyContainer flatContainer = container.flatten(testedModule, testedContainer);

        ImmutableList<AndroidLibrary> androidLibraries = flatContainer.getAndroidDependencies();
        assertThat(androidLibraries).containsExactly(testedModule, libraryModule).inOrder();

        ImmutableList<JavaLibrary> javaLibraries = flatContainer.getJarDependencies();
        assertThat(javaLibraries).containsExactly(jarModule, guava, randomLib);
    }

    @Test
    public void testFlattenWithTestedLocalJar() throws Exception {
        // create a local jar
        File localJarFile = new File("local.jar");
        JavaLibrary localJar = mockJavaLibrary(
                localJarFile,
                JarDependency.getCoordForLocalJar(localJarFile),
                ImmutableList.of(),
                null);

        // create the processed localjar that's bundled in the AAR
        File processedLocalJarFile = new File("processed/local.jar");
        JavaLibrary processedLocalJar = mockJavaLibrary(
                localJarFile,
                JarDependency.getCoordForLocalJar(processedLocalJarFile),
                ImmutableList.of(),
                null);

        // the tested lib
        AndroidLibrary testedModule = mockAndroidLibrary(
                new File("tested.jar"),
                new MavenCoordinatesImpl("project", "tested", "unspecified"),
                ImmutableList.of(),
                ImmutableList.of(),
                ":tested");

        // create both container both of them contains the local jar
        DependencyContainerImpl container = new DependencyContainerImpl(
                ImmutableList.of(),
                ImmutableList.of(),
                ImmutableList.of(localJar));

        DependencyContainerImpl testedContainer = new DependencyContainerImpl(
                ImmutableList.of(),
                ImmutableList.of(),
                ImmutableList.of(localJar));

        DependencyContainer flatContainer = container.flatten(testedModule, testedContainer);

        ImmutableList<AndroidLibrary> androidLibraries = flatContainer.getAndroidDependencies();

        // we want to test that the libraries contains exactly testedModule.
        // However the item is actually a wrapper around it, so we can't use containsExactly()
        assertThat(androidLibraries).containsExactly(testedModule);

        // check the local jar is not in the flattened container.
        ImmutableList<JavaLibrary> javaLibraries = flatContainer.getLocalDependencies();
        assertThat(javaLibraries).isEmpty();
    }

    @Test
    public void testWithJavaLibShowingUpTwice() throws Exception {
        /* test a simple case:
            AAR: project:library:aar:unspecified
            Path: :library
                JAR: project:jar:jar:unspecified
                Path: :jar
            AAR: project:library2:aar:unspecified
            Path: :library
                JAR: project:jar:jar:unspecified
                Path: :jar

        */

        // start from the depth of the graph.
        JavaLibrary jarModule = mockJavaLibrary(
                new File("jar.jar"),
                new MavenCoordinatesImpl("project", "jar", "unspecified"),
                ImmutableList.of(),
                ":jar");

        AndroidLibrary libraryModule = mockAndroidLibrary(
                new File("library.jar"),
                new MavenCoordinatesImpl("project", "library", "unspecified"),
                ImmutableList.of(),
                ImmutableList.of(jarModule),
                ":library");

        AndroidLibrary libraryModule2 = mockAndroidLibrary(
                new File("library2.jar"),
                new MavenCoordinatesImpl("project", "library2", "unspecified"),
                ImmutableList.of(),
                ImmutableList.of(jarModule),
                ":library2");

        DependencyContainerImpl container = new DependencyContainerImpl(
                ImmutableList.of(libraryModule, libraryModule2),
                ImmutableList.of(),
                ImmutableList.of());

        DependencyContainer flatContainer = container.flatten(null, null);

        ImmutableList<AndroidLibrary> androidLibraries = flatContainer.getAndroidDependencies();
        assertThat(androidLibraries).containsExactly(libraryModule, libraryModule2);

        ImmutableList<JavaLibrary> javaLibraries = flatContainer.getJarDependencies();
        assertThat(javaLibraries).hasSize(1);
        assertThat(javaLibraries).containsExactly(jarModule);
    }

    @NonNull
    private static JavaLibrary mockJavaLibrary(
            @NonNull File jarFile,
            @NonNull MavenCoordinates coordinates,
            @NonNull final List<JavaLibrary> dependencies,
            @Nullable String path) {
        JavaLibrary library = Mockito.mock(JavaLibrary.class);

        Mockito.when(library.getJarFile()).thenReturn(jarFile);
        Mockito.when(library.getResolvedCoordinates()).thenReturn(coordinates);
        Mockito.when(library.getDependencies()).thenAnswer(invocation -> dependencies);
        Mockito.when(library.getProject()).thenReturn(path);

        return library;
    }

    @NonNull
    private static AndroidLibrary mockAndroidLibrary(
            @NonNull File jarFile,
            @NonNull MavenCoordinates coordinates,
            @NonNull final List<AndroidLibrary> androidDependencies,
            @NonNull final Collection<JavaLibrary> javaDependencies,
            @Nullable String path) {

        AndroidLibrary library = Mockito.mock(AndroidLibrary.class);

        Mockito.when(library.getJarFile()).thenReturn(jarFile);
        Mockito.when(library.getResolvedCoordinates()).thenReturn(coordinates);
        Mockito.when(library.getProject()).thenReturn(path);
        Mockito.when(library.getLibraryDependencies()).thenAnswer(invocation -> androidDependencies);
        Mockito.when(library.getJavaDependencies()).thenAnswer(invocation -> javaDependencies);
        return library;
    }
}