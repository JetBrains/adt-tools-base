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
 * State that {@link FullRunShrinker} and {@link IncrementalShrinker} use for their computations.
 *
 * <p>The graph contains nodes for every class, method and field in the program. The nodes are
 * connected using different types of directed edges (see {@link DependencyType}), which represent
 * different kinds of relationships between the nodes.
 *
 * <p>Nodes are added to the graph once all the necessary information is known, i.e. the class or
 * class member is visited a {@link org.objectweb.asm.ClassVisitor}. Before the node is added to
 * the graph, it can be created "by name" when analyzing other class files. Every node reference
 * obtained "by name" needs to remain valid after the fully built node has been added to the graph.
 *
 * <p>The graph is also stored on disk for incremental runs.
 *
 * @param <T> type used to represent a node in the graph: a class, method or field.
 */
public interface ShrinkerGraph<T> {

    /**
     * Returns the source file that this class was read from. Return null for library classes.
     */
    @Nullable
    File getSourceFile(@NonNull T klass);

    /**
     * Returns all nodes that represent program classes.
     *
     * @see #isLibraryClass(Object)
     */
    @NonNull
    Iterable<T> getAllProgramClasses();

    /**
     * Returns all classes that are reachable in a given {@link CounterSet}.
     */
    @NonNull
    Set<T> getReachableClasses(@NonNull CounterSet counterSet);

    /**
     * Returns all the reachable members of the given class, in the name:desc format, without the
     * class name at the front.
     */
    @NonNull
    Set<String> getReachableMembersLocalNames(@NonNull T klass, @NonNull CounterSet counterSet);

    /**
     * Returns all dependencies of the given node, i.e. outgoing graph edges.
     */
    @NonNull
    Set<Dependency<T>> getDependencies(@NonNull T node);

    /**
     * Returns all methods of the given class.
     */
    @NonNull
    Set<T> getMethods(@NonNull T klass);

    /**
     * Returns all fields of the given class.
     */
    @NonNull
    Set<T> getFields(@NonNull T klass);

    /**
     * Creates a new node representing the given class, and adds it to the graph.
     *
     * @param name internal name of the class
     * @param superName internal name of the superclass
     * @param interfaces internal names of the interfaces
     * @param modifiers modifiers bit set, as used by {@link org.objectweb.asm.ClassVisitor}
     * @param classFile class file that contains the given class
     * @return the newly created node
     */
    @NonNull
    T addClass(
            @NonNull String name,
            @Nullable String superName,
            @Nullable String[] interfaces,
            int modifiers,
            @Nullable File classFile);

    /**
     * Creates a new node representing the given class member (method or field), and adds it to
     * the graph.
     *
     * @param owner internal name of the owner class
     * @param name class member name
     * @param desc class member descriptor
     * @param modifiers modifiers bit set, as used by {@link org.objectweb.asm.ClassVisitor}
     * @return the newly created node
     */
    @NonNull
    T addMember(@NonNull T owner, @NonNull String name, @NonNull String desc, int modifiers);

    /**
     * Returns the owner class of a given method or field.
     *
     * @param member node representing a given class member
     * @return node representing the owner class
     */
    @NonNull
    T getOwnerClass(@NonNull T member);

    /**
     * Returns the node representing the given class. It may not be fully initialized yet, if the
     * class file for the class in question has not been read yet.
     *
     * @param className internal name of the class
     * @return node representing the class
     */
    @NonNull
    T getClassReference(@NonNull String className);

    /**
     * Returns the node representing a given class member (method or field). It may not be fully
     * initialized yet, if the class file for the owner class has not been read yet.
     *
     * @param className internal name of the owner class
     * @param memberName member name
     * @param desc member descriptor
     * @return node representing the class member
     */
    @NonNull
    T getMemberReference(@NonNull String className, @NonNull String memberName, @NonNull String desc);

    /**
     * Increments the counter of the given type ({@link DependencyType}) and checks if this
     * operation made the node reachable, atomically.
     *
     * @param node graph node
     * @param dependencyType type of counter
     * @param counterSet the {@link CounterSet} to use
     * @return true if this operation made the node reachable, false otherwise
     */
    boolean incrementAndCheck(
            @NonNull T node,
            @NonNull DependencyType dependencyType,
            @NonNull CounterSet counterSet);

    /**
     * Adds a new dependency (edge) to the graph.
     *
     * @param source source node
     * @param target target node
     * @param type edge type
     */
    void addDependency(@NonNull T source, @NonNull T target, @NonNull DependencyType type);

    /**
     * Serializes the graph to disk, in a location that will be known when building incrementally.
     */
    void saveState() throws IOException;

    /**
     * Checks if the given node is reachable, using the given {@link CounterSet}.
     */
    boolean isReachable(@NonNull T node, @NonNull CounterSet counterSet);

    /**
     * Removes all {@link DependencyType#REQUIRED_CODE_REFERENCE} and
     * {@link DependencyType#REQUIRED_CODE_REFERENCE_REFLECTION} edges that start from the given
     * node.
     */
    void removeAllCodeDependencies(@NonNull T node);

    /**
     * Returns the superclass of the given class.
     *
     * @throws ClassLookupException if the node for the superclass has not been created (yet)
     */
    @Nullable
    T getSuperclass(@NonNull T klass) throws ClassLookupException;

    /**
     * Searches the given class for a method with the same name and descriptor as the provided
     * method. This can be used to look for overrides.
     * @param klass class to search
     * @param method method to match
     * @return a node representing the matching method if found, null otherwise
     */
    @Nullable
    T findMatchingMethod(@NonNull T klass, @NonNull T method);

    /**
     * Checks if the given class comes from the program or the platform (and we don't control it).
     *
     * <p>Program classes are written to the output and can be changed in the process. Library
     * classes come from the SDK and we don't control them.
     */
    boolean isLibraryClass(@NonNull T klass);

    /**
     * Gets the interfaces of a given class.
     *
     * @throws ClassLookupException if the node for one of the interfaces has not been created (yet)
     */
    @NonNull
    T[] getInterfaces(T klass) throws ClassLookupException;


    /**
     * Returns the internal class name for the given class node.
     */
    @NonNull
    String getClassName(@NonNull T klass);

    /**
     * Returns the name of a given method or field.
     */
    String getMemberName(@NonNull T member);

    /**
     * Returns the full name of a given method or field, e.g. com/example/Class.method:()V
     */
    String getFullMemberName(@NonNull T member);

    /**
     * Returns the name of a given method or field.
     */
    String getMemberDescriptor(@NonNull T member);

    /**
     * Returns the modifiers for the given node, as used by {@link org.objectweb.asm.ClassVisitor}.
     */
    int getModifiers(@NonNull T node);

    /**
     * Adds an annotation to the given node.
     * @param node node to store the data for
     * @param annotationName internal name of the annotation
     */
    void addAnnotation(@NonNull T node, @NonNull String annotationName);

    /**
     * Returns all annotations present on the given node.
     */
    @NonNull
    Iterable<String> getAnnotations(@NonNull T node);

    /**
     * Adds the given roots to the graph, for the given {@link CounterSet}.
     *
     * <p>Roots are the sources from which graph walking starts. When we walk the graph looking for
     * reachable nodes, keys in the map will get their counters incremented, using the
     * {@link DependencyType} from the matching map value. They can be considered as nodes with
     * incoming edges that don't have a start node.
     */
    void addRoots(
            @NonNull Map<T, DependencyType> symbolsToKeep,
            @NonNull CounterSet counterSet);

    /**
     * Returns the roots.
     *
     * @see #addRoots(Map, CounterSet)
     */
    @NonNull
    Map<T,DependencyType> getRoots(@NonNull CounterSet counterSet);

    /**
     * Clears all the counters, for all nodes.
     */
    void clearCounters(@NonNull WaitableExecutor<Void> executor);

    /**
     * Checks if the given given (representing a class) was added to the graph.
     *
     * <p>When project dependencies are setup incorrectly, unknown classes may be referenced from
     * program classes. In this case nodes for the unknown classes are created "by name" when
     * analyzing program classes, but at a later stage we need to make sure the references are
     * actually valid (otherwise we emit a warning).
     */
    boolean isClassKnown(@NonNull T klass);

    /**
     * Checks that all edges in the graph point to fully known nodes.
     *
     * <p>If it's not the case, warnings are emitted using the given {@link ShrinkerLogger} and
     * these edges are removed from the graph.
     */
    void checkDependencies(ShrinkerLogger shrinkerLogger);
}
