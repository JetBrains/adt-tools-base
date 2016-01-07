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

import static com.android.utils.FileUtils.getAllFiles;
import static com.android.utils.FileUtils.withExtension;

import com.android.annotations.NonNull;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.ide.common.internal.WaitableExecutor;
import com.google.common.base.Stopwatch;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.TreeTraverser;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Code shrinker. It analyzes the input classes and the SDK jar and outputs minified classes. Uses
 * the given implementation of {@link ShrinkerGraph} to keep state and persist it for later
 * incremental runs.
 */
public class FullRunShrinker<T> extends AbstractShrinker<T> {

    private final Set<File> mPlatformJars;

    public FullRunShrinker(
            WaitableExecutor<Void> executor,
            ShrinkerGraph<T> graph,
            Set<File> platformJars,
            ShrinkerLogger shrinkerLogger) {
        super(graph, executor, shrinkerLogger);
        mPlatformJars = platformJars;
    }

    /**
     * Performs the full shrinking run. This clears previous incremental state, creates a new
     * {@link ShrinkerGraph} and fills it with data read from the platform JARs as well as input
     * classes. Then we find "entry points" that match {@code -keep} rules from the config file,
     * and walk the graph, setting the counters and finding reachable classes and members. In the
     * last step we rewrite all reachable class files to only contain kept class members and put
     * them in the matching output directories.
     */
    public void run(
            @NonNull Collection<TransformInput> inputs,
            @NonNull Collection<TransformInput> referencedClasses,
            @NonNull TransformOutputProvider output,
            @NonNull ImmutableMap<CounterSet, KeepRules> keepRules,
            boolean saveState) throws IOException {
        output.deleteAll();

        buildGraph(inputs, referencedClasses);

        Stopwatch stopwatch = Stopwatch.createStarted();
        setCounters(keepRules);
        logTime("Set counters", stopwatch);
        writeOutput(inputs, output);
        logTime("Write output", stopwatch);

        if (saveState) {
            mGraph.saveState();
            logTime("Saving state", stopwatch);
        }
    }

    /**
     * Populates the graph with all nodes (classes, members) and edges (dependencies, references),
     * so that it's ready to be traversed in search of reachable ndoes.
     */
    private void buildGraph(
            @NonNull Iterable<TransformInput> programInputs,
            @NonNull Iterable<TransformInput> libraryInputs) throws IOException {
        Stopwatch stopwatch = Stopwatch.createStarted();
        final PostProcessingData<T> postProcessingData = new PostProcessingData<T>();

        readPlatformJars();

        for (TransformInput input : libraryInputs) {
            for (File directory : getAllDirectories(input)) {
                for (final File classFile : getClassFiles(directory)) {
                    mExecutor.execute(new Callable<Void>() {
                        @Override
                        public Void call() throws Exception {
                            processLibraryClass(Files.toByteArray(classFile));
                            return null;
                        }
                    });
                }
            }

            for (final File jarFile : getAllJars(input)) {
                processJarFile(jarFile, new ByteCodeConsumer() {
                    @Override
                    public void process(byte[] bytes) throws IOException {
                        processLibraryClass(bytes);
                    }
                });
            }
        }

        for (TransformInput input : programInputs) {
            for (File directory : getAllDirectories(input)) {
                for (final File classFile : getClassFiles(directory)) {
                    mExecutor.execute(new Callable<Void>() {
                        @Override
                        public Void call() throws Exception {
                            processProgramClassFile(
                                    Files.toByteArray(classFile),
                                    classFile,
                                    postProcessingData);
                            return null;
                        }
                    });
                }
            }

            for (final File jarFile : getAllJars(input)) {
                processJarFile(jarFile, new ByteCodeConsumer() {
                    @Override
                    public void process(byte[] bytes) throws IOException {
                        processProgramClassFile(
                                bytes,
                                jarFile,
                                postProcessingData);
                    }
                });
            }
        }
        waitForAllTasks();
        logTime("Read input", stopwatch);

        handleOverrides(postProcessingData.getVirtualMethods());
        handleMultipleInheritance(postProcessingData.getMultipleInheritance());
        handleInterfaceInheritance(postProcessingData.getInterfaceInheritance());
        resolveReferences(postProcessingData.getUnresolvedReferences());
        waitForAllTasks();
        logTime("Finish graph", stopwatch);

        mGraph.checkDependencies(mShrinkerLogger);
    }

    private void handleInterfaceInheritance(@NonNull Set<T> interfaceInheritance) {
        for (final T klass : interfaceInheritance) {
            mExecutor.execute(new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    TreeTraverser<T> interfaceTraverser =
                            TypeHierarchyTraverser.interfaces(mGraph, mShrinkerLogger);

                    if ((mGraph.getClassModifiers(klass) & Opcodes.ACC_INTERFACE) != 0) {

                        // The "children" name is unfortunate: in the type hierarchy tree traverser,
                        // these are the interfaces that klass (which is an interface itself)
                        // extends (directly).
                        Iterable<T> superinterfaces = interfaceTraverser.children(klass);

                        for (T superinterface : superinterfaces) {
                            if (!mGraph.isLibraryClass(superinterface)) {
                                // Add the arrow going "down", from the superinterface to this one.
                                mGraph.addDependency(
                                        superinterface,
                                        klass,
                                        DependencyType.SUPERINTERFACE_KEPT);
                            } else {
                                // The superinterface is part of the SDK, so it's always kept. As
                                // long as there's any class that implements this interface, it
                                // needs to be kept.
                                mGraph.incrementAndCheck(
                                        klass,
                                        DependencyType.SUPERINTERFACE_KEPT,
                                        CounterSet.SHRINK);
                            }
                        }
                    }

                    for (T iface : interfaceTraverser.preOrderTraversal(klass)) {
                        if (!mGraph.isLibraryClass(iface)) {
                            mGraph.addDependency(
                                    klass,
                                    iface,
                                    DependencyType.INTERFACE_IMPLEMENTED);
                        }
                    }

                    return null;
                }
            });
        }
    }

    @NonNull
    private static FluentIterable<File> getClassFiles(@NonNull File dir) {
        return getAllFiles(dir).filter(withExtension("class"));
    }

    /**
     * Updates the graph to handle a case when a class inherits an interface method implementation
     * from a super class which does not implement the given interface.
     *
     * <p>We handle it by inserting fake nodes into the graph, equivalent to just calling super()
     * to invoke the inherited implementation. This way an "invokeinterface" opcode can cause the
     * fake method to be kept, which in turn causes the real method to be kept, even though on the
     * surface it has nothing to do with the interface.
     */
    private void handleMultipleInheritance(@NonNull Set<T> multipleInheritance) {
        for (final T klass : multipleInheritance) {
            mExecutor.execute(new Callable<Void>() {
                Set<T> methods = mGraph.getMethods(klass);

                @Override
                public Void call() throws Exception {
                    if (!isProgramClass(mGraph.getSuperclass(klass))) {
                        // All the superclass methods are kept anyway.
                        return null;
                    }

                    Iterable<T> interfaces = TypeHierarchyTraverser
                            .interfaces(mGraph, mShrinkerLogger)
                            .preOrderTraversal(klass);
                    for (T iface : interfaces) {
                        for (T method : mGraph.getMethods(iface)) {
                            handleMethod(method);
                        }
                    }
                    return null;
                }

                private void handleMethod(T method) {
                    if (this.methods.contains(method)) {
                        // We implement this interface method directly in the class, which is the
                        // common case. Nothing left to do.
                        return;
                    }

                    // Otherwise, look in the superclasses for the implementation.

                    FluentIterable<T> superclasses = TypeHierarchyTraverser
                            .superclasses(mGraph, mShrinkerLogger)
                            .preOrderTraversal(klass);
                    for (T current : superclasses) {
                        if (!isProgramClass(current)) {
                            // We will not remove the method anyway.
                            return;
                        }

                        T matchingMethod = mGraph.findMatchingMethod(current, method);
                        if (matchingMethod != null) {
                            String name = mGraph.getMethodNameAndDesc(method);
                            String desc = name.substring(name.indexOf(':') + 1);
                            name = name.substring(0, name.indexOf(':'));
                            name = name + "$shrinker_fake";
                            T fakeMethod = mGraph.addMember(
                                    klass, name, desc,
                                    mGraph.getMemberModifiers(method));

                            // Simulate a super call.
                            mGraph.addDependency(fakeMethod, matchingMethod,
                                    DependencyType.REQUIRED_CLASS_STRUCTURE);

                            if (!isProgramClass(mGraph.getClassForMember(method))) {
                                mGraph.addDependency(klass, fakeMethod,
                                        DependencyType.REQUIRED_CLASS_STRUCTURE);
                            } else {
                                mGraph.addDependency(klass, fakeMethod,
                                        DependencyType.CLASS_IS_KEPT);
                                mGraph.addDependency(method, fakeMethod,
                                        DependencyType.IF_CLASS_KEPT);
                            }

                            return;
                        }
                    }
                }
            });
        }
    }

    /**
     * Updates the graph to add edges which model how overridden methods should be handled.
     *
     * <p>A method overriding another one (from a class or interface), is kept if it's invoked
     * directly (naturally) or if the class is kept for whatever reason and the overridden method
     * is also invoked - we don't know if the call site for the overridden method actually operates
     * on objects of the subclass.
     */
    private void handleOverrides(@NonNull Set<T> virtualMethods) {
        for (final T method : virtualMethods) {
            mExecutor.execute(new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    String methodNameAndDesc = mGraph.getMethodNameAndDesc(method);
                    if (isJavaLangObjectMethod(methodNameAndDesc)) {
                        // If we override an SDK method, it just has to be there at runtime
                        // (if the class itself is kept).
                        mGraph.addDependency(
                                mGraph.getClassForMember(method),
                                method,
                                DependencyType.REQUIRED_CLASS_STRUCTURE);
                        return null;
                    }

                    FluentIterable<T> superTypes =
                            TypeHierarchyTraverser.superclassesAndInterfaces(mGraph, mShrinkerLogger)
                                    .preOrderTraversal(mGraph.getClassForMember(method));

                    for (T klass : superTypes) {
                        if (mGraph.getClassName(klass).equals("java/lang/Object")) {
                            continue;
                        }

                        T superMethod = mGraph.findMatchingMethod(klass, method);
                        if (superMethod != null && !superMethod.equals(method)) {
                            if (!isProgramClass(mGraph.getClassForMember(superMethod))) {
                                // If we override an SDK method, it just has to be there at runtime
                                // (if the class itself is kept).
                                mGraph.addDependency(
                                        mGraph.getClassForMember(method),
                                        method,
                                        DependencyType.REQUIRED_CLASS_STRUCTURE);
                                return null;
                            } else {
                                // If we override a program method, there's a chance this method is
                                // never called and we will get rid of it. Set up the dependencies
                                // appropriately.
                                mGraph.addDependency(
                                        mGraph.getClassForMember(method),
                                        method,
                                        DependencyType.CLASS_IS_KEPT);
                                mGraph.addDependency(
                                        superMethod,
                                        method,
                                        DependencyType.IF_CLASS_KEPT);
                            }
                        }
                    }
                    return null;
                }
            });
        }
    }

    private static boolean isJavaLangObjectMethod(@NonNull String nameAndDesc) {
        return nameAndDesc.equals("hashCode:()I")
                || (nameAndDesc.equals("equals:(Ljava/lang/Object;)Z")
                || (nameAndDesc.equals("toString:()Ljava/lang/String;")));
    }

    /**
     * Updates the graph with nodes from a library (read-only) class. There's no point creating
     * edges, since library classes cannot references program classes and we don't shrink library
     * code.
     */
    private void processLibraryClass(@NonNull byte[] source) throws IOException {
        ClassReader classReader = new ClassReader(source);
        classReader.accept(
                new ClassStructureVisitor<T>(mGraph, null, null),
                ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);
    }

    /**
     * Updates the graph with nodes and edges based on the given class file.
     */
    private void processProgramClassFile(
            byte[] bytes,
            @NonNull File classFile,
            @NonNull final PostProcessingData<T> postProcessingData) throws IOException {
        ClassNode classNode = new ClassNode(Opcodes.ASM5);
        ClassVisitor depsFinder =
                new DependencyFinderVisitor<T>(mGraph, classNode) {
                    @Override
                    protected void handleDependency(T source, T target, DependencyType type) {
                        mGraph.addDependency(source, target, type);
                    }

                    @Override
                    protected void handleMultipleInheritance(T klass) {
                        postProcessingData.getMultipleInheritance().add(klass);
                    }

                    @Override
                    protected void handleVirtualMethod(T method) {
                        postProcessingData.getVirtualMethods().add(method);
                    }

                    @Override
                    protected void handleInterfaceInheritance(T klass) {
                        postProcessingData.getInterfaceInheritance().add(klass);
                    }

                    @Override
                    protected void handleUnresolvedReference(PostProcessingData.UnresolvedReference<T> reference) {
                        postProcessingData.getUnresolvedReferences().add(reference);
                    }
                };
        ClassVisitor structureVisitor =
                new ClassStructureVisitor<T>(mGraph, classFile, depsFinder);
        ClassReader classReader = new ClassReader(bytes);
        classReader.accept(structureVisitor, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    }

    private interface ByteCodeConsumer {
        void process(byte[] bytes) throws IOException;
    }

    private void readPlatformJars() throws IOException {
        for (File platformJar : mPlatformJars) {
            processJarFile(platformJar, new ByteCodeConsumer() {
                @Override
                public void process(byte[] bytes) throws IOException {
                    processLibraryClass(bytes);
                }
            });
        }
    }

    private void processJarFile(File platformJar, final ByteCodeConsumer consumer)
            throws IOException {
        JarFile jarFile = new JarFile(platformJar);
        try {
            for (Enumeration<JarEntry> entries = jarFile.entries(); entries.hasMoreElements();) {
                JarEntry entry = entries.nextElement();
                if (!entry.getName().endsWith(".class")) {
                    continue;
                }
                final InputStream inputStream = jarFile.getInputStream(entry);
                try {
                    final byte[] bytes = ByteStreams.toByteArray(inputStream);
                    mExecutor.execute(new Callable<Void>() {
                        @Override
                        public Void call() throws Exception {
                            consumer.process(bytes);
                            return null;
                        }
                    });
                } finally {
                    inputStream.close();
                }
            }
        } finally {
            jarFile.close();
        }
    }

    /**
     * Sets the roots (i.e. entry points) of the graph and marks all nodes reachable from them.
     */
    private void setCounters(@NonNull ImmutableMap<CounterSet, KeepRules> allKeepRules) {
        final CounterSet counterSet = CounterSet.SHRINK;
        final KeepRules keepRules = allKeepRules.get(counterSet);

        for (final T klass : mGraph.getAllProgramClasses()) {
            mExecutor.execute(new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    mGraph.addRoots(keepRules.getSymbolsToKeep(klass, mGraph), counterSet);
                    return null;
                }
            });
        }
        waitForAllTasks();

        setCounters(counterSet);
    }

    private void writeOutput(
            @NonNull Collection<TransformInput> inputs,
            @NonNull TransformOutputProvider output) throws IOException {
        updateClassFiles(
                mGraph.getReachableClasses(CounterSet.SHRINK),
                Collections.<File>emptyList(),
                inputs,
                output);
    }
}
