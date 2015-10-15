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

import static com.google.common.base.Preconditions.checkState;

import com.android.annotations.NonNull;
import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.Format;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.ide.common.internal.LoggedErrorException;
import com.android.ide.common.internal.WaitableExecutor;
import com.android.utils.FileUtils;
import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.Lists;
import com.google.common.io.Files;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * Common code for both types of shrinker runs, {@link FullRunShrinker} and
 * {@link IncrementalShrinker}.
 */
public abstract class AbstractShrinker<T> {

    protected final WaitableExecutor<Void> mExecutor;

    protected final ShrinkerGraph<T> mGraph;

    protected AbstractShrinker(
            ShrinkerGraph<T> graph, WaitableExecutor<Void> executor) {
        mGraph = graph;
        mExecutor = executor;
    }

    /**
     * Tries to determine the output class file, for rewriting the given class file.
     *
     * <p>This will return {@link Optional#absent()} if the class is not part of the program to
     * shrink (e.g. comes from a platform JAR).
     */
    @NonNull
    protected static Optional<File> chooseOutputFile(
            @NonNull File classFile,
            @NonNull Collection<TransformInput> inputs,
            @NonNull TransformOutputProvider output) {
        String absolutePath = classFile.getAbsolutePath();

        for (TransformInput input : inputs) {
            for (DirectoryInput directoryInput : input.getDirectoryInputs()) {
                File directory = directoryInput.getFile();
                if (absolutePath.startsWith(directory.getAbsolutePath())) {
                    File outputDir = output.getContentLocation(
                            directoryInput.getName(),
                            directoryInput.getContentTypes(),
                            directoryInput.getScopes(),
                            Format.DIRECTORY);

                    String relativePath = FileUtils.relativePath(classFile, directory);
                    return Optional.of(new File(outputDir, relativePath));
                }
            }
        }

        return Optional.absent();
    }

    /**
     * Determines all directories where class files can be found in the given
     * {@link TransformInput}.
     */
    @NonNull
    protected static Collection<File> getAllDirectories(@NonNull TransformInput input) {
        List<File> files = Lists.newArrayList();
        for (DirectoryInput directoryInput : input.getDirectoryInputs()) {
            files.add(directoryInput.getFile());
        }

        return files;
    }

    /**
     * Increments the counter on the given graph node. If the node just became reachable, keeps on
     * walking the graph to find newly reachable nodes.
     *
     * @param member node to increment
     * @param dependencyType type of counter to increment
     * @param counterSet set of counters to work on
     */
    protected void incrementCounter(
            @NonNull T member,
            @NonNull DependencyType dependencyType,
            @NonNull CounterSet counterSet) {
        if (mGraph.incrementAndCheck(member, dependencyType, counterSet)) {
            for (Dependency<T> dependency : mGraph.getDependencies(member)) {
                incrementCounter(dependency.target, dependency.type, counterSet);
            }
        }
    }

    /**
     * Finds existing methods oe fields (graph nodes) which encountered opcodes refer to. Updates
     * the graph with additional edges accordingly.
     */
    protected void resolveReferences(
            @NonNull Iterable<UnresolvedReference<T>> unresolvedReferences) {
        for (final UnresolvedReference<T> unresolvedReference : unresolvedReferences) {
            mExecutor.execute(new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    T startClass = mGraph.getClassForMember(unresolvedReference.target);

                    if (unresolvedReference.opcode == Opcodes.INVOKESPECIAL) {
                        // With invokespecial we disregard the class in target and start walking up
                        // the type hierarchy, starting from the superclass of the caller.
                        startClass =
                                mGraph.getSuperclass(
                                        mGraph.getClassForMember(unresolvedReference.method));
                        checkState(startClass != null);
                    }

                    TypeHierarchyTraverser<T> traverser = new TypeHierarchyTraverser<T>(mGraph);
                    for (T currentClass : traverser.preOrderTraversal(startClass)) {
                        T target = mGraph.findMatchingMethod(
                                currentClass,
                                unresolvedReference.target);
                        if (target != null) {
                            if (!mGraph.isLibraryMember(target)) {
                                mGraph.addDependency(
                                        unresolvedReference.method,
                                        currentClass,
                                        DependencyType.REQUIRED_CODE_REFERENCE);
                                mGraph.addDependency(
                                        unresolvedReference.method,
                                        target,
                                        DependencyType.REQUIRED_CODE_REFERENCE);
                            }
                            return null;
                        }
                    }

                    // TODO: Check -dontwarn.
                    String className = mGraph.getClassName(
                            mGraph.getClassForMember(
                                    unresolvedReference.target));
                    if (!className.startsWith("sun/misc/Unsafe")) {
                        System.out.println(
                                String.format(
                                        "Unresolved reference: %s.%s",
                                        className,
                                        mGraph.getMethodNameAndDesc(unresolvedReference.target)));
                    }
                    return null;
                }
            });
        }
    }

    /**
     * Rewrites the given class (read from file) to only include used methods and fields. Returns
     * the new class bytecode as {@code byte[]}.
     */
    @NonNull
    protected static byte[] rewrite(
            @NonNull File classFile,
            @NonNull Set<String> membersToKeep,
            @NonNull Predicate<String> keepInterface) throws IOException {
        ClassReader classReader = new ClassReader(Files.toByteArray(classFile));
        // Don't pass the reader as an argument to the writer. This forces the writer to recompute
        // the constant pool, which we want, since it can contain unused entries that end up in the
        // dex file.
        ClassWriter classWriter = new ClassWriter(0);
        ClassVisitor filter = new FilterMembersVisitor(membersToKeep, keepInterface, classWriter);
        classReader.accept(filter, 0);
        return classWriter.toByteArray();
    }

    /**
     * Walks the entire graph, starting from the roots, and increments counters for reachable nodes.
     */
    protected void setCounters(@NonNull CounterSet counterSet) {
        Map<T, DependencyType> roots = mGraph.getRoots(counterSet);
        for (Map.Entry<T, DependencyType> toIncrementEntry : roots.entrySet()) {
            incrementCounter(toIncrementEntry.getKey(), toIncrementEntry.getValue(), counterSet);
        }
    }

    @NonNull
    protected static UnsupportedOperationException todo(String message) {
        return new UnsupportedOperationException("TODO: " + message);
    }

    /**
     * Writes updates class files to the outputs.
     */
    protected void updateClassFiles(
            @NonNull Iterable<T> classesToWrite,
            @NonNull List<File> classFilesToDelete,
            @NonNull Collection<TransformInput> inputs,
            @NonNull TransformOutputProvider output) throws IOException {
        for (T klass : classesToWrite) {
            File classFile = mGraph.getClassFile(klass);
            Optional<File> outputFile = chooseOutputFile(classFile, inputs, output);
            if (!outputFile.isPresent()) {
                // The class is from code we don't control.
                continue;
            }
            Files.createParentDirs(outputFile.get());
            Files.write(
                    rewrite(classFile,
                            mGraph.getReachableMembers(klass, CounterSet.SHRINK),
                            new Predicate<String>() {
                                @Override
                                public boolean apply(String input) {
                                    return mGraph.keepInterface(input, CounterSet.SHRINK);
                                }
                            }),
                    outputFile.get());
        }

        for (File classFile : classFilesToDelete) {
            FileUtils.delete(classFile);
        }
    }

    protected void waitForAllTasks() {
        try {
            mExecutor.waitForTasksWithQuickFail(true);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (LoggedErrorException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Set of counters, for keeping different sets of reachable nodes for different purposes.
     */
    public enum CounterSet {
        /** Counters for removing dead code. */
        SHRINK,

        /** Counters for finding classes that have to be in the main classes.dex file. */
        LEGACY_MULTIDEX
    }

    static class UnresolvedReference<T> {
        final T method;
        final T target;
        final int opcode;

        UnresolvedReference(@NonNull T method, @NonNull T target, int opcode) {
            this.method = method;
            this.target = target;
            this.opcode = opcode;
        }

        @Override
        public String toString() {
            return Objects.toStringHelper(this)
                    .add("method", method)
                    .add("target", target)
                    .add("opcode", opcode)
                    .toString();
        }
    }
}
