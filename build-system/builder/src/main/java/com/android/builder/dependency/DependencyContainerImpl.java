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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.concurrency.Immutable;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.JavaLibrary;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * implementation of DependencyContainer
 */
@Immutable
public class DependencyContainerImpl implements DependencyContainer {

    @NonNull
    private final ImmutableList<AndroidLibrary> mLibraryDependencies;

    @NonNull
    private final ImmutableList<JavaLibrary> mJavaDependencies;

    @NonNull
    private final ImmutableList<JavaLibrary> mLocalJars;

    public DependencyContainerImpl(
            @NonNull List<? extends AndroidLibrary> aars,
            @NonNull Collection<? extends JavaLibrary> jars,
            @NonNull Collection<? extends JavaLibrary> localJars) {
        mLibraryDependencies = ImmutableList.copyOf(aars);
        mJavaDependencies = ImmutableList.copyOf(jars);
        mLocalJars = ImmutableList.copyOf(localJars);
    }

    public static DependencyContainer getEmpty() {
        return new DependencyContainerImpl(
                ImmutableList.of(),
                ImmutableList.of(),
                ImmutableList.of());
    }

    @NonNull
    @Override
    public ImmutableList<AndroidLibrary> getAndroidDependencies() {
        return mLibraryDependencies;
    }

    @NonNull
    @Override
    public ImmutableList<JavaLibrary> getJarDependencies() {
        return mJavaDependencies;
    }

    @NonNull
    @Override
    public ImmutableList<JavaLibrary> getLocalDependencies() {
        return mLocalJars;
    }

    @NonNull
    @Override
    public DependencyContainer flatten(
            @Nullable AndroidLibrary testedLibrary,
            @Nullable DependencyContainer testedDependencyContainer) {
        /*
        The handling of test for aars is a bit special due to how the dependencies are setup.
        Because we cannot have the test app depend directly on the generated aar, we have a weird
        setup:
        - The configuration for the test extends the configuration of the aar
        - The VariantConfiguration manually adds the AndroidLibrary representing the aar.

        So instead of having:
            test
            +- espresso
            +- aar
               +- guava
        We have:
            test
            +- espresso
            +- guava
            +- aar

        We also have a problem with local jars. Because of the configuration extension, they show
        up in both configuration objects so we have to remove the duplicated ones, and use the ones
        coming through the aar.
        We could more easily take the one from the configuration and drop the one inside the aar but
        it wouldn't work. The ones in the aar are slightly different (during java res merging, the
        res move from the local jars to the main classes.jar), so we really need those rather
        than the original ones.
         */

        List<AndroidLibrary> flatAndroidLibs = Lists.newArrayList();
        Set<JavaLibrary> flatJavaLibs = Sets.newIdentityHashSet();

        computeFlatLibraryList(mLibraryDependencies, flatAndroidLibs, flatJavaLibs);

        // add the tested libs after since it'll be added at the beginning of the list.
        if (testedLibrary != null) {
            computeFlatLibraryList(testedLibrary, flatAndroidLibs, flatJavaLibs);
        }

        computeFlatJarList(mJavaDependencies, flatJavaLibs);

        // handle the local jars. Remove the duplicated ones from mLocalJars.
        // They will actually show up through the testedLibrary's local jars.
        List<JavaLibrary> localJars = mLocalJars;
        if (testedDependencyContainer != null && testedLibrary != null) {
            Collection<JavaLibrary> testedLocalJars = testedDependencyContainer.getLocalDependencies();

            localJars = Lists.newArrayListWithExpectedSize(mLocalJars.size());
            for (JavaLibrary javaLibrary : mLocalJars) {
                if (!testedLocalJars.contains(javaLibrary)) {
                    localJars.add(javaLibrary);
                }
            }
        }

        return new DependencyContainerImpl(flatAndroidLibs, flatJavaLibs, localJars);
    }

    /**
     * Resolves a given list of libraries, finds out if they depend on other libraries, and
     * returns a flat list of all the direct and indirect dependencies in the proper order (first
     * is higher priority when calling aapt).
     *
     * @param androidLibs the libraries to resolve.
     * @param outFlatAndroidLibs where to store all the android libraries.
     * @param outFlatJavaLibs where to store all the java libraries
     */
    private static void computeFlatLibraryList(
            @NonNull List<? extends AndroidLibrary> androidLibs,
            @NonNull List<AndroidLibrary> outFlatAndroidLibs,
            @NonNull Set<JavaLibrary> outFlatJavaLibs) {
        // loop in the inverse order to resolve dependencies on the libraries, so that if a library
        // is required by two higher level libraries it can be inserted in the correct place
        // (behind both higher level libraries).
        // For instance:
        //        A
        //       / \
        //      B   C
        //       \ /
        //        D
        //
        // Must give: A B C D
        // So that both B and C override D (and B overrides C)
        for (int i = androidLibs.size() - 1  ; i >= 0 ; i--) {
            computeFlatLibraryList(
                    androidLibs.get(i),
                    outFlatAndroidLibs,
                    outFlatJavaLibs);
        }
    }

    private static void computeFlatLibraryList(
            @NonNull AndroidLibrary androidLibrary,
            @NonNull List<AndroidLibrary> outFlatAndroidLibs,
            @NonNull Set<JavaLibrary> outFlatJavaLibs) {
        // resolve the dependencies for those libraries
        //noinspection unchecked
        computeFlatLibraryList(
                androidLibrary.getLibraryDependencies(),
                outFlatAndroidLibs,
                outFlatJavaLibs);

        computeFlatJarList(androidLibrary.getJavaDependencies(), outFlatJavaLibs);

        // and add the current one (if needed) in front (higher priority)
        if (!androidLibrary.isSkipped() && !outFlatAndroidLibs.contains(androidLibrary)) {
            outFlatAndroidLibs.add(0, androidLibrary);
        }
    }

    private static void computeFlatJarList(
            @NonNull Collection<? extends JavaLibrary> javaLibs,
            @NonNull Set<JavaLibrary> outFlatJavaLibs) {

        for (JavaLibrary javaLib : javaLibs) {
            if (!javaLib.isSkipped()) {
                outFlatJavaLibs.add(javaLib);
            }
            //noinspection unchecked
            computeFlatJarList(javaLib.getDependencies(), outFlatJavaLibs);
        }
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .add("mLibraryDependencies", mLibraryDependencies)
                .add("mJavaDependencies", mJavaDependencies)
                .add("mLocalJars", mLocalJars)
                .toString();
    }
 }
