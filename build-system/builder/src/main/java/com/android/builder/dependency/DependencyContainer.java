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

package com.android.builder.dependency;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.JavaLibrary;
import com.google.common.collect.ImmutableList;

/**
 * An object able to provide the three types of dependencies an Android project can have:
 * - local jar dependencies
 * - java library (jar) dependencies
 * - android library (aar) dependencies
 */
public interface DependencyContainer {

    /**
     * Returns a list of top level android library dependencies.
     *
     * Each library object should contain its own dependencies (as android libs, java libs,
     * and local jars). This is actually a dependency graph.
     *
     * @return a non null (but possibly empty) list.
     */
    @NonNull
    ImmutableList<AndroidLibrary> getAndroidDependencies();

    /**
     * Returns a list of top level java library dependencies.
     *
     * Each library object should contain its own dependencies (as java libs only).
     * This is actually a dependency graph.
     *
     * @return a non null (but possibly empty) list.
     */
    @NonNull
    ImmutableList<JavaLibrary> getJarDependencies();

    /**
     * Returns a list of local jar dependencies.
     *
     * @return a non null (but possibly empty) list.
     */
    @NonNull
    ImmutableList<JavaLibrary> getLocalDependencies();

    /**
     * Returns a version of this container where the graph is flattened into direct dependencies.
     *
     * This also adds (if applicable) the tested library and its transitive dependencies.

     * @param testedLibrary the tested aar
     * @param testedDependencyContainer the container of the tested aar
     * @return the flattened container.
     */
    @NonNull
    DependencyContainer flatten(
            @Nullable AndroidLibrary testedLibrary,
            @Nullable DependencyContainer testedDependencyContainer);
}
