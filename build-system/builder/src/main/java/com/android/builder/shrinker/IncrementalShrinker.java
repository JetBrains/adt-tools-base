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

import com.android.annotations.NonNull;
import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.Status;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.ide.common.internal.WaitableExecutor;
import com.google.common.base.Optional;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.SetMultimap;
import com.google.common.io.Files;

import org.objectweb.asm.ClassReader;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * Code for incremental shrinking.
 */
public class IncrementalShrinker<T> extends AbstractShrinker<T> {

    /**
     * Exception thrown when the incremental shrinker detects incompatible changes and requests
     * a full run instead.
     */
    public static class IncrementalRunImpossibleException extends Exception {}

    public IncrementalShrinker(
            WaitableExecutor<Void> executor,
            ShrinkerGraph<T> graph) {
        super(graph, executor);
    }

    /**
     * Perform incremental shrinking, in the supported cases (where only code in pre-existing
     * methods has been modified).
     *
     * <p>The general idea is this: for every method in modified classes, remove all outgoing
     * "code reference" edges, add them again based on the current code and then set the counters
     * again (traverse the graph) using the new set of edges.
     *
     * <p>The counters are re-calculated every time from scratch (starting from known entry points
     * from the config file) to avoid cycles being left in the output.
     *
     * @throws IncrementalRunImpossibleException If incremental shrinking is impossible and a full
     *     run should be done instead.
     */
    public void incrementalRun(
            @NonNull Collection<TransformInput> inputs,
            @NonNull TransformOutputProvider output)
            throws IOException, IncrementalRunImpossibleException {
        final List<T> classesToWrite = Lists.newArrayList();
        final List<File> classFilesToDelete = Lists.newArrayList();
        final Collection<UnresolvedReference<T>> unresolvedReferences = Lists.newArrayList();

        SetMultimap<T, String> oldState = resetState();

        processInputs(inputs, classesToWrite, unresolvedReferences);
        finishGraph(unresolvedReferences);
        setCounters(CounterSet.SHRINK);
        chooseClassesToWrite(inputs, output, classesToWrite, classFilesToDelete, oldState);
        updateClassFiles(classesToWrite, classFilesToDelete, inputs, output);
    }

    /**
     * Decides which classes need to be updated on disk and which need to be deleted. It puts
     * appropriate entries in the lists passed as arguments.
     */
    private void chooseClassesToWrite(
            @NonNull Collection<TransformInput> inputs,
            @NonNull TransformOutputProvider output,
            @NonNull List<T> classesToWrite,
            @NonNull List<File> classFilesToDelete,
            @NonNull SetMultimap<T, String> oldState) {
        for (T klass : mGraph.getReachableClasses(CounterSet.SHRINK)) {
            if (!oldState.containsKey(klass)) {
                classesToWrite.add(klass);
            } else {
                Set<String> newMembers = mGraph.getReachableMembers(klass, CounterSet.SHRINK);
                Set<String> oldMembers = oldState.get(klass);

                // Reverse of the trick above, where we store one artificial member for empty
                // classes.
                if (oldMembers.size() == 1) {
                    oldMembers.remove(mGraph.getClassName(klass));
                }

                if (!newMembers.equals(oldMembers)) {
                    classesToWrite.add(klass);
                }
            }

            oldState.removeAll(klass);
        }

        // All keys that remained in oldState should be deleted.
        for (T klass : oldState.keySet()) {
            Optional<File> outputFile = chooseOutputFile(klass, mGraph.getClassFile(klass), inputs, output);
            if (!outputFile.isPresent()) {
                throw new IllegalStateException(
                        "Can't determine path of " + mGraph.getClassName(klass));
            }
            classFilesToDelete.add(outputFile.get());
        }
    }

    /**
     * Saves all reachable classes and members in a {@link SetMultimap} and clears all counters, so
     * that the graph can be traversed again, using the new edges.
     */
    @NonNull
    private SetMultimap<T, String> resetState() {
        SetMultimap<T, String> oldState = HashMultimap.create();

        for (T klass : mGraph.getReachableClasses(CounterSet.SHRINK)) {
            Set<String> reachableMembers = mGraph.getReachableMembers(klass, CounterSet.SHRINK);
            for (String member : reachableMembers) {
                oldState.put(klass, member);
            }

            // Make sure the key is in the map.
            if (reachableMembers.isEmpty()) {
                oldState.put(klass, mGraph.getClassName(klass));
            }
        }

        mGraph.clearCounters();
        return oldState;
    }

    private void finishGraph(@NonNull Collection<UnresolvedReference<T>> unresolvedReferences) {
        resolveReferences(unresolvedReferences);
        waitForAllTasks();
    }

    private void processInputs(
            @NonNull Collection<TransformInput> inputs,
            @NonNull final List<T> classesToWrite,
            @NonNull final Collection<UnresolvedReference<T>> unresolvedReferences) {
        for (final TransformInput input : inputs) {
            for (JarInput jarInput : input.getJarInputs()) {
                switch (jarInput.getStatus()) {
                    case ADDED:
                        break;
                    case REMOVED:
                        break;
                    case CHANGED:
                        break;
                }
            }

            for (DirectoryInput directoryInput : input.getDirectoryInputs()) {
                for (final Map.Entry<File, Status> changedFile : directoryInput.getChangedFiles().entrySet()) {
                    mExecutor.execute(new Callable<Void>() {
                        @Override
                        public Void call() throws Exception {
                            switch (changedFile.getValue()) {
                                case ADDED:
                                    throw todo("new file added");
                                case REMOVED:
                                    throw todo("removed file");
                                case CHANGED:
                                    processChangedClassFile(
                                            changedFile.getKey(),
                                            unresolvedReferences,
                                            classesToWrite);
                                    break;
                            }
                            return null;
                        }
                    });
                }
            }
        }
        waitForAllTasks();
    }

    /**
     * Handles a changed class file by removing old code references (graph edges) and adding
     * up-to-date edges, according to the current state of the class.
     *
     * <p>This only works on {@link DependencyType#REQUIRED_CODE_REFERENCE} edges, which are only
     * ever created from method containing the opcode to target member. The first pass is equivalent
     * to removing all code from the method, the second to adding "current" opcodes to it.
     *
     * @throws IncrementalRunImpossibleException If current members of the class are not the same as
     *     they used to be. This means that edges of other types need to be updated, and we don't
     *     handle this incrementally. It also means that -keep rules would need to be re-applied,
     *     which is something we also don't do incrementally.
     */
    private void processChangedClassFile(
            @NonNull File file,
            @NonNull final Collection<UnresolvedReference<T>> unresolvedReferences,
            @NonNull final Collection<T> classesToWrite)
            throws IOException, IncrementalRunImpossibleException {
        ClassReader classReader = new ClassReader(Files.toByteArray(file));
        // TODO: Detect structure changes.
        DependencyFinderVisitor<T> finder = new DependencyFinderVisitor<T>(mGraph, null) {

            @Override
            public void visit(int version, int access, String name, String signature,
                    String superName,
                    String[] interfaces) {
                T klass = mGraph.getClassReference(name);
                classesToWrite.add(klass);
                super.visit(version, access, name, signature, superName, interfaces);
            }

            @Override
            protected void handleDependency(T source, T target, DependencyType type) {
                if (type == DependencyType.REQUIRED_CODE_REFERENCE) {
                    mGraph.addDependency(source, target, type);
                }
            }

            @Override
            protected void handleMultipleInheritance(T klass) {
            }

            @Override
            protected void handleVirtualMethod(T method) {
            }

            @Override
            protected void handleUnresolvedReference(UnresolvedReference<T> reference) {
                unresolvedReferences.add(reference);
            }
        };
        DependencyRemoverVisitor<T> remover = new DependencyRemoverVisitor<T>(mGraph, finder);
        classReader.accept(remover, 0);
    }
}
