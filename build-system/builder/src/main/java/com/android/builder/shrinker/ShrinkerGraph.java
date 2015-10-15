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
import com.android.builder.shrinker.AbstractShrinker.CounterSet;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Set;

/**
 * State the {@link FullRunShrinker} needs to keep during and between invocations.
 *
 * @param <T> Reference to a class member.
 */
interface ShrinkerGraph<T> {

    File getClassFile(T klass);

    Iterable<T> getReachableClasses(CounterSet counterSet);

    Set<String> getReachableMembers(T klass, CounterSet counterSet);

    Set<Dependency<T>> getDependencies(T member);

    Set<T> getMethods(T klass);

    Set<T> getFields(T klass);

    T addClass(String name, String superName, String[] interfaces, int access, File classFile);

    T addMember(T owner, String name, String desc, int modifiers);

    T getClassForMember(T member);

    T getClassReference(String className);

    T getMemberReference(String className, String memberName, String methodDesc);

    boolean incrementAndCheck(T member, DependencyType dependencyType, CounterSet counterSet);

    void addDependency(T source, T target, DependencyType type);

    void removeStoredState() throws IOException;

    void saveState() throws IOException;

    boolean isReachable(T member, CounterSet counterSet);

    void removeAllCodeDependencies(T source);

    @Nullable
    T getSuperclass(T klass) throws ClassLookupException;

    @Nullable
    T findMatchingMethod(T klass, T method);

    boolean isLibraryMember(T method);

    boolean isLibraryClass(T klass);

    T[] getInterfaces(T klass);

    void checkDependencies();

    boolean keepInterface(String klass, CounterSet counterSet);

    Iterable<T> getAllProgramClasses();

    String getClassName(T classOrMember);

    String getMethodNameAndDesc(T method);

    String getFieldName(T field);

    String getFieldDesc(T field);

    int getClassModifiers(T klass);

    int getMemberModifiers(T member);

    void addAnnotation(T klass, String desc);

    Iterable<String> getAnnotations(T classOrMember);

    void addRoots(Map<T, DependencyType> symbolsToKeep, CounterSet counterSet);

    Map<T,DependencyType> getRoots(CounterSet counterSet);

    void clearCounters();
}
