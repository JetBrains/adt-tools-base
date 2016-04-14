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

package com.android.build.gradle.shrinker;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.shrinker.AbstractShrinker.CounterSet;
import com.android.ide.common.internal.WaitableExecutor;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Set;

/**
 * State the {@link FullRunShrinker} needs to keep during and between invocations.
 *
 * @param <T> Reference to a class member.
 */
public interface ShrinkerGraph<T> {

    /**
     * Returns the source file that this class was read from. Return null for library classes.
     */
    @Nullable
    File getSourceFile(@NonNull T klass);

    @NonNull
    Set<T> getReachableClasses(@NonNull CounterSet counterSet);

    /**
     * Returns all the reachable members of the given class, in the name:desc format, without the
     * class name at the front.
     */
    @NonNull
    Set<String> getReachableMembersLocalNames(@NonNull T klass, @NonNull CounterSet counterSet);

    @NonNull
    Set<Dependency<T>> getDependencies(@NonNull T member);

    @NonNull
    Set<T> getMethods(@NonNull T klass);

    @NonNull
    Set<T> getFields(@NonNull T klass);

    @NonNull
    T addClass(
            @NonNull String name,
            @Nullable String superName,
            @Nullable String[] interfaces,
            int modifiers,
            @Nullable File classFile);

    @NonNull
    T addMember(@NonNull T owner, @NonNull String name, @NonNull String desc, int modifiers);

    @NonNull
    T getClassForMember(@NonNull T member);

    @NonNull
    T getClassReference(@NonNull String className);

    @NonNull
    T getMemberReference(@NonNull String className, @NonNull String memberName, @NonNull String desc);

    boolean incrementAndCheck(
            @NonNull T memberOrClass,
            @NonNull DependencyType dependencyType,
            @NonNull CounterSet counterSet);

    void addDependency(@NonNull T source, @NonNull T target, @NonNull DependencyType type);

    void saveState() throws IOException;

    boolean isReachable(@NonNull T klass, @NonNull CounterSet counterSet);

    void removeAllCodeDependencies(@NonNull T source);

    @Nullable
    T getSuperclass(@NonNull T klass) throws ClassLookupException;

    @Nullable
    T findMatchingMethod(@NonNull T klass, @NonNull T method);

    boolean isLibraryClass(@NonNull T klass);

    @NonNull
    T[] getInterfaces(T klass) throws ClassLookupException;

    void checkDependencies(ShrinkerLogger shrinkerLogger);

    @NonNull
    Iterable<T> getAllProgramClasses();

    @NonNull
    String getClassName(@NonNull T klass);

    @NonNull
    String getMethodNameAndDesc(@NonNull T method);

    @NonNull
    String getFieldName(@NonNull T field);

    @NonNull
    String getFieldDesc(@NonNull T field);

    int getClassModifiers(@NonNull T klass);

    int getMemberModifiers(@NonNull T member);

    void addAnnotation(@NonNull T classOrMember, @NonNull String annotationName);

    @NonNull
    Iterable<String> getAnnotations(@NonNull T classOrMember);

    void addRoots(
            @NonNull Map<T, DependencyType> symbolsToKeep,
            @NonNull CounterSet counterSet);

    @NonNull
    Map<T,DependencyType> getRoots(@NonNull CounterSet counterSet);

    void clearCounters(@NonNull WaitableExecutor<Void> executor);

    String getMemberName(@NonNull T member);

    boolean isClassKnown(@NonNull T klass);
}
