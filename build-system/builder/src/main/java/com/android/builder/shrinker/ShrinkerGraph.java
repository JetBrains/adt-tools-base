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

package com.android.builder.shrinker;

import com.android.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Set;

/**
 * State the {@link Shrinker} needs to keep during and between invocations.
 *
 * @param <T> Reference to a class member.
 */
interface ShrinkerGraph<T> {

    File getClassFile(T klass);

    Iterable<T> getClassesToKeep(Shrinker.ShrinkType shrinkType);

    Set<String> getMembersToKeep(T klass, Shrinker.ShrinkType shrinkType);

    Set<Dependency<T>> getDependencies(T member);

    Set<T> getMembers(T klass);

    T addClass(String name, String superName, String[] interfaces, File classFile);

    T addMember(String owner, String name, String desc);

    T getClassForMember(T member);

    T getClassReference(String className);

    T getMemberReference(String className, String memberName, String methodDesc);

    boolean incrementAndCheck(T member, DependencyType dependencyType, Shrinker.ShrinkType shrinkType);

    void addDependency(T source, T target, DependencyType type);

    void loadState() throws IOException;

    void removeStoredState() throws IOException;

    void saveState() throws IOException;

    boolean isReachable(T member, Shrinker.ShrinkType shrinkType);

    void removeDependency(T source, Dependency<T> dep);

    boolean decrementAndCheck(T member, DependencyType dependencyType, Shrinker.ShrinkType shrinkType);

    @Nullable
    T getSuperclass(T klass);

    @Nullable
    T findMatchingMethod(T klass, T method);

    boolean isLibraryMember(T method);

    T[] getInterfaces(T klass);

    void checkDependencies();

    boolean keepClass(String klass, Shrinker.ShrinkType shrinkType);
}
