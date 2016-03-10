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

import static com.google.common.base.Preconditions.checkState;

import com.android.annotations.NonNull;
import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.Format;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.ide.common.internal.LoggedErrorException;
import com.android.ide.common.internal.WaitableExecutor;
import com.android.utils.FileUtils;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Common code for both types of shrinker runs, {@link FullRunShrinker} and
 * {@link IncrementalShrinker}.
 */
public abstract class AbstractShrinker<T> {

    /**
     * Specifies whether the shrinker should assume certain package names are specific to the SDK,
     * to trim the graph as early as possible.
     */
    private static final boolean IGNORE_PACKAGE_NAME =
            Boolean.getBoolean("android.newShrinker.ignorePackageName");

    protected final WaitableExecutor<Void> mExecutor;

    protected final ShrinkerGraph<T> mGraph;

    protected final ShrinkerLogger mShrinkerLogger;

    protected AbstractShrinker(
            ShrinkerGraph<T> graph, WaitableExecutor<Void> executor, ShrinkerLogger shrinkerLogger) {
        mGraph = graph;
        mExecutor = executor;
        mShrinkerLogger = shrinkerLogger;
    }

    /**
     * Checks if a given class name starts with a package name that we assume to be an SDK class.
     *
     * <p>This way we can make the check cheaper in the common case and also filter out a lot of
     * unnecessary edges from the graph early on, where we don't yet know which class is which.
     */
    static boolean isSdkPackage(@NonNull String className) {
        if (IGNORE_PACKAGE_NAME) {
            return false;
        } else {
            return className.startsWith("java/")
                    || (className.startsWith("android/")
                            // Match android/support and android/preview/support, possibly others.
                            && !className.contains("/support/"));
        }
    }

    /**
     * Tries to determine the output class file, for rewriting the given class file.
     *
     * <p>This will return {@link Optional#absent()} if the class is not part of the program to
     * shrink (e.g. comes from a platform JAR).
     */
    @NonNull
    protected Optional<File> chooseOutputFile(
            @NonNull T klass,
            @NonNull File classFile,
            @NonNull Iterable<TransformInput> inputs,
            @NonNull TransformOutputProvider output) {
        String classFilePath = classFile.getAbsolutePath();

        for (TransformInput input : inputs) {
            Iterable<QualifiedContent> directoriesAndJars =
                    Iterables.concat(input.getDirectoryInputs(), input.getJarInputs());

            for (QualifiedContent directoryOrJar : directoriesAndJars) {
                File file = directoryOrJar.getFile();
                if (classFilePath.startsWith(file.getAbsolutePath())) {
                    File outputDir = output.getContentLocation(
                            FileUtils.getDirectoryNameForJar(file),
                            directoryOrJar.getContentTypes(),
                            directoryOrJar.getScopes(),
                            Format.DIRECTORY);

                    return Optional.of(new File(outputDir, mGraph.getClassName(klass) + ".class"));
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
     * Determines all directories where class files can be found in the given
     * {@link TransformInput}.
     */
    @NonNull
    protected static Collection<File> getAllJars(@NonNull TransformInput input) {
        List<File> files = Lists.newArrayList();
        for (JarInput jarInput : input.getJarInputs()) {
            files.add(jarInput.getFile());
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
     * Finds existing methods or fields (graph nodes) which encountered opcodes refer to. Updates
     * the graph with additional edges accordingly.
     */
    protected void resolveReferences(
            @NonNull Iterable<PostProcessingData.UnresolvedReference<T>> unresolvedReferences) {
        for (final PostProcessingData.UnresolvedReference<T> unresolvedReference : unresolvedReferences) {
            mExecutor.execute(new Callable<Void>() {
                @Override
                public Void call() {
                    T target = unresolvedReference.target;
                    T source = unresolvedReference.method;
                    T startClass = mGraph.getClassForMember(target);

                    if (unresolvedReference.invokespecial) {
                        // With invokespecial we disregard the class in target and start walking up
                        // the type hierarchy, starting from the superclass of the caller.
                        T sourceClass = mGraph.getClassForMember(source);
                        try {
                            startClass = mGraph.getSuperclass(sourceClass);
                            checkState(startClass != null);
                        } catch (ClassLookupException e) {
                            mShrinkerLogger.invalidClassReference(
                                    mGraph.getClassName(sourceClass),
                                    e.getClassName());
                        }
                    }

                    if (!mGraph.isClassKnown(startClass)) {
                        mShrinkerLogger.invalidClassReference(
                                mGraph.getMemberName(source),
                                mGraph.getClassName(startClass));

                        return null;
                    }

                    TypeHierarchyTraverser<T> traverser =
                            TypeHierarchyTraverser.superclassesAndInterfaces(mGraph, mShrinkerLogger);

                    for (T currentClass : traverser.preOrderTraversal(startClass)) {
                        T matchingMethod = mGraph.findMatchingMethod(
                                currentClass,
                                target);
                        if (matchingMethod != null) {
                            if (isProgramClass(mGraph.getClassForMember(matchingMethod))) {
                                mGraph.addDependency(
                                        source,
                                        currentClass,
                                        unresolvedReference.dependencyType);
                                mGraph.addDependency(
                                        source,
                                        matchingMethod,
                                        unresolvedReference.dependencyType);
                            }
                            return null;
                        }
                    }

                    mShrinkerLogger.invalidMemberReference(
                            mGraph.getMemberName(source),
                            mGraph.getMemberName(target));

                    return null;
                }
            });
        }
    }

    protected boolean isProgramClass(T klass) {
        return !mGraph.isLibraryClass(klass);
    }

    /**
     * Rewrites the given class (read from file) to only include used methods and fields. Returns
     * the new class bytecode as {@code byte[]}.
     */
    @NonNull
    protected static byte[] rewrite(
            @NonNull String className,
            @NonNull File classFile,
            @NonNull Set<String> membersToKeep,
            @NonNull Predicate<String> keepInterface) throws IOException {
        byte[] bytes;
        if (Files.getFileExtension(classFile.getName()).equals("class")) {
            bytes = Files.toByteArray(classFile);
        } else {
            JarFile jarFile = new JarFile(classFile);
            try {
                JarEntry jarEntry = jarFile.getJarEntry(className + ".class");
                bytes = ByteStreams.toByteArray(jarFile.getInputStream(jarEntry));
            } finally {
                jarFile.close();
            }
        }

        ClassReader classReader = new ClassReader(bytes);
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
    protected void setCounters(@NonNull final CounterSet counterSet) {
        Map<T, DependencyType> roots = mGraph.getRoots(counterSet);
        for (final Map.Entry<T, DependencyType> toIncrementEntry : roots.entrySet()) {
            mExecutor.execute(new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    incrementCounter(
                            toIncrementEntry.getKey(),
                            toIncrementEntry.getValue(),
                            counterSet);
                    return null;
                }
            });
        }
        waitForAllTasks();
    }

    /**
     * Writes updates class files to the outputs.
     */
    protected void updateClassFiles(
            @NonNull Iterable<T> classesToWrite,
            @NonNull Iterable<File> classFilesToDelete,
            @NonNull Iterable<TransformInput> inputs,
            @NonNull TransformOutputProvider output) throws IOException {
        for (final T klass : classesToWrite) {
            final File sourceFile = mGraph.getSourceFile(klass);
            checkState(sourceFile != null, "Program class has no source file.");

            final Optional<File> outputFile = chooseOutputFile(klass, sourceFile, inputs, output);
            if (!outputFile.isPresent()) {
                // The class is from code we don't control.
                continue;
            }
            Files.createParentDirs(outputFile.get());

            final Predicate<String> keepInterfacePredicate = new Predicate<String>() {
                @Override
                public boolean apply(String input) {
                    T iface = mGraph.getClassReference(input);

                    return !isProgramClass(iface)
                            || mGraph.isReachable(iface, CounterSet.SHRINK);
                }
            };

            mExecutor.execute(new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    byte[] newBytes = rewrite(
                            mGraph.getClassName(klass),
                            sourceFile,
                            mGraph.getReachableMembersLocalNames(klass, CounterSet.SHRINK),
                            keepInterfacePredicate);

                    Files.write(newBytes, outputFile.get());
                    return null;
                }
            });
        }

        for (File classFile : classFilesToDelete) {
            FileUtils.delete(classFile);
        }

        waitForAllTasks();
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

    public static void logTime(String section, Stopwatch stopwatch) {
        if (System.getProperty("android.newShrinker.profile") != null) {
            System.out.println(section + ": " + stopwatch);
            stopwatch.reset();
            stopwatch.start();
        }
    }
}
