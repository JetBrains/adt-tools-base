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

package com.android.build.gradle.internal.transforms;

import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACC_SUPER;
import static org.objectweb.asm.Opcodes.V1_6;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.Format;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.QualifiedContent.Scope;
import com.android.build.api.transform.Status;
import com.android.build.api.transform.Transform;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.incremental.DexPackagingPolicy;
import com.android.build.gradle.internal.incremental.InstantRunPatchingPolicy;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.scope.InstantRunVariantScope;
import com.android.utils.FileUtils;
import com.android.utils.ILogger;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;

import org.gradle.api.logging.Logger;
import org.objectweb.asm.ClassWriter;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

/**
 * Transform that slices the project's classes into approximately 10 slices for
 * {@link Scope#PROJECT} and {@link Scope#SUB_PROJECTS}.
 *
 * <p>Dependencies are not processed by the Slicer but will be dex'ed separately.
 */
public class InstantRunSlicer extends Transform {

    public static final String MAIN_SLICE_NAME = "main_slice";

    @VisibleForTesting
    static final String PACKAGE_FOR_GUARD_CLASSS = "com/android/tools/fd/dummy";

    // since we use the last digit of the FQCN hashcode() as the bucket, 10 is the appropriate
    // number of slices.
    public static final int NUMBER_OF_SLICES_FOR_PROJECT_CLASSES = 10;

    private final ILogger logger;

    @NonNull
    private final InstantRunVariantScope variantScope;

    @NonNull
    private final InstantRunPatchingPolicy patchingPolicy;

    public InstantRunSlicer(@NonNull Logger logger, @NonNull InstantRunVariantScope variantScope) {
        this.logger = new LoggerWrapper(logger);
        this.variantScope = variantScope;
        InstantRunPatchingPolicy patchingPolicy =
                variantScope.getInstantRunBuildContext().getPatchingPolicy();
        if (patchingPolicy == null) {
            throw new RuntimeException("Patching policy not set when creating InstantRunSlicer");
        }
        this.patchingPolicy = patchingPolicy;
    }

    @NonNull
    @Override
    public String getName() {
        return "instantRunSlicer";
    }

    @NonNull
    @Override
    public Set<QualifiedContent.ContentType> getInputTypes() {
        return TransformManager.CONTENT_CLASS;
    }

    @NonNull
    @Override
    public Set<QualifiedContent.ContentType> getOutputTypes() {
        return TransformManager.CONTENT_CLASS;
    }

    @NonNull
    @Override
    public Set<Scope> getScopes() {
        // if we are targeting a N and above, it's fine to merge all the dependencies.jar inside
        // the dependencies.jar file as the VM team fixed the issue around split java packages
        // landing in two different dex files.
        return patchingPolicy.getDexPatchingPolicy() == DexPackagingPolicy.INSTANT_RUN_MULTI_APK
                ? TransformManager.SCOPE_FULL_PROJECT
                : Sets.immutableEnumSet(Scope.PROJECT, Scope.SUB_PROJECTS);
    }

    @Override
    public boolean isIncremental() {
        return true;
    }

    @NonNull
    @Override
    public Map<String, Object> getParameterInputs() {
        // Force the instant run transform to re-run when the dex patching policy changes,
        // as the slicer will re-run.
        return ImmutableMap.of("dex patching policy",
                variantScope.getInstantRunBuildContext().getPatchingPolicy()
                        .getDexPatchingPolicy().toString());
    }

    @Override
    public void transform(@NonNull TransformInvocation transformInvocation)
            throws IOException, TransformException, InterruptedException {

        TransformOutputProvider outputProvider = transformInvocation.getOutputProvider();
        boolean isIncremental = transformInvocation.isIncremental();
        Collection<TransformInput> inputs = transformInvocation.getInputs();
        if (outputProvider == null) {
            logger.error(null /* throwable */, "null TransformOutputProvider for InstantRunSlicer");
            return;
        }

        Slices slices = new Slices();

        if (isIncremental) {
            sliceIncrementally(inputs, outputProvider, slices);
        } else {
            slice(inputs, outputProvider, slices);
            combineAllJars(inputs, outputProvider);
        }
    }

    /**
     * Combine all {@link Scope#PROJECT} and {@link Scope#SUB_PROJECTS} inputs into slices, ignore
     * all other inputs.
     *
     * @param inputs the transform's input
     * @param outputProvider the transform's output provider to create streams
     * @throws IOException if the files cannot be copied
     * @throws TransformException never thrown
     * @throws InterruptedException never thrown.
     */
    private void slice(@NonNull Collection<TransformInput> inputs,
            @NonNull TransformOutputProvider outputProvider,
            @NonNull Slices slices)
            throws IOException, TransformException, InterruptedException {

        File dependenciesLocation =
                getDependenciesSliceOutputFolder(outputProvider, Format.DIRECTORY);

        // first path, gather all input files, organize per package.
        for (TransformInput input : inputs) {
            for (DirectoryInput directoryInput : input.getDirectoryInputs()) {

                File inputDir = directoryInput.getFile();
                FluentIterable<File> files = Files.fileTreeTraverser()
                        .breadthFirstTraversal(directoryInput.getFile());

                for (File file : files) {
                    if (file.isDirectory()) {
                        continue;
                    }
                    String packagePath = FileUtils
                            .relativePossiblyNonExistingPath(file.getParentFile(), inputDir);

                    if (directoryInput.getScopes().contains(Scope.PROJECT) ||
                            directoryInput.getScopes().contains(Scope.SUB_PROJECTS)) {

                        slices.addElement(packagePath, file);
                    } else {
                        // dump in a single directory that may end up producing several .dex in case
                        // we are over 65K limit.
                        File outputFile = new File(dependenciesLocation,
                                new File(packagePath, file.getName()).getPath());
                        Files.createParentDirs(outputFile);
                        Files.copy(file, outputFile);
                    }
                }
            }
        }

        // now produces the output streams for each slice.
        slices.writeTo(outputProvider);
    }

    /**
     *
     * WARNING : This code is no longer active as the transform scope if only PROJECT and
     * SUB_PROJECTS but keeping it around in case it becomes useful again with a split APK solution.
     *
     * Combine all input jars with a different scope than {@link Scope#PROJECT} and
     * {@link Scope#SUB_PROJECTS}
     * @param inputs the transform's input
     * @param outputProvider the transform's output provider to create streams
     * @throws IOException if jar files cannot be created successfully
     */
    private void combineAllJars(@NonNull Collection<TransformInput> inputs,
            @NonNull TransformOutputProvider outputProvider) throws IOException {

        List<JarInput> jarFilesToProcess = new ArrayList<>();
        for (TransformInput input : inputs) {
            for (JarInput jarInput : input.getJarInputs()) {
                File jarFile = jarInput.getFile();

                // handle separately instant-run runtime and the generated AppInfo so it is
                // directly packaged in the APK and can be loaded successfully by Android runtime.
                // This is obviously not very clean but until the Transform API can provide a way
                // to attach some metadata to streams.
                if (jarFile.getName().equals("instant-run.jar")) {
                    // this gets packaged in the main slice.
                    File mainSliceOutput = getMainSliceOutputFolder(outputProvider, null);
                    Files.copy(jarFile, mainSliceOutput);
                } else if (variantScope.getInstantRunBuildContext().getPatchingPolicy()
                                != InstantRunPatchingPolicy.MULTI_APK
                        && jarFile.getAbsolutePath().contains("incremental-classes")) {
                    File mainSliceOutput = getMainSliceOutputFolder(outputProvider, "b");
                    Files.copy(jarFile, mainSliceOutput);
                } else {
                    // otherwise, all other dependencies will be combined right below.
                    if (jarInput.getFile().exists()) {
                        jarFilesToProcess.add(jarInput);
                    }
                }
            }
        }
        if (jarFilesToProcess.isEmpty()) {
            return;
        }

        // all the jar files will be combined into a single jar will all other scopes.
        File dependenciesJar = getDependenciesSliceOutputFolder(outputProvider, Format.JAR);
        Set<String> entries = new HashSet<>();

        Files.createParentDirs(dependenciesJar);
        try (JarOutputStream jarOutputStream = new JarOutputStream(
                new BufferedOutputStream(new FileOutputStream(dependenciesJar)))) {
            for (JarInput jarInput : jarFilesToProcess) {
                mergeJarInto(jarInput, entries, jarOutputStream);
            }
        }
    }

    private void mergeJarInto(@NonNull JarInput jarInput,
            @NonNull Set<String> entries, @NonNull JarOutputStream dependenciesJar)
            throws IOException {

        try (JarFile jarFile = new JarFile(jarInput.getFile())) {
            Enumeration<JarEntry> jarEntries = jarFile.entries();
            while (jarEntries.hasMoreElements()) {
                JarEntry jarEntry = jarEntries.nextElement();
                if (jarEntry.isDirectory()) {
                    continue;
                }
                if (entries.contains(jarEntry.getName())) {
                    logger.verbose(
                            String.format("Entry %1$s is duplicated, ignore the one from %2$s",
                                    jarEntry.getName(), jarInput.getName()));
                } else {
                    dependenciesJar.putNextEntry(new JarEntry(jarEntry.getName()));
                    try (InputStream inputStream = jarFile.getInputStream(jarEntry)) {
                        ByteStreams.copy(inputStream, dependenciesJar);
                    }
                    dependenciesJar.closeEntry();
                    entries.add(jarEntry.getName());
                }
            }
        }
    }

    private void sliceIncrementally(@NonNull Collection<TransformInput> inputs,
            @NonNull TransformOutputProvider outputProvider,
            @NonNull Slices slices)
            throws IOException, TransformException, InterruptedException {

        processCodeChanges(inputs, outputProvider, slices);

        // in any case, we always process jar input changes incrementally.
        for (TransformInput input : inputs) {
            for (JarInput jarInput : input.getJarInputs()) {
                if (jarInput.getStatus() != Status.NOTCHANGED) {
                    // one jar has changed, was removed or added, re-merge all of our jars.
                    combineAllJars(inputs, outputProvider);
                    return;
                }
            }
        }
        logger.info("No jar merging necessary, all input jars unchanged");
    }

    private void processCodeChanges(
            @NonNull final Collection<TransformInput> inputs,
            @NonNull final TransformOutputProvider outputProvider,
            @NonNull final Slices slices)
            throws TransformException, InterruptedException, IOException {

        // process all files
        for (TransformInput input : inputs) {
            for (DirectoryInput directoryInput : input.getDirectoryInputs()) {
                for (Map.Entry<File, Status> changedFile : directoryInput.getChangedFiles()
                        .entrySet()) {
                    // get the output stream for this file.
                    File fileToProcess = changedFile.getKey();
                    Status status = changedFile.getValue();
                    File sliceOutputLocation = getOutputStreamForFile(
                            outputProvider, directoryInput, fileToProcess, slices);

                    // add the buildID timestamp to the slice out directory so we force the
                    // dex task to rerun, even if no .class files appear to have changed. This
                    // can happen when doing a lot of hot swapping with changes undoing
                    // themselves resulting in a state that was equal to the last restart state.
                    // In theory, it would not require to rebuild but it will confuse Android
                    // Studio is there is nothing to push so just be safe and rebuild.
                    if (fileToProcess.isFile()) {
                        Files.write(
                                String.valueOf(
                                        variantScope.getInstantRunBuildContext().getBuildId()),
                                new File(sliceOutputLocation, "buildId.txt"), Charsets.UTF_8);
                        logger.info("Writing buildId in %s because of %s",
                                sliceOutputLocation.getAbsolutePath(),
                                changedFile.toString());
                    }


                    String relativePath = FileUtils.relativePossiblyNonExistingPath(
                            fileToProcess, directoryInput.getFile());

                    File outputFile = new File(sliceOutputLocation, relativePath);
                    switch (status) {
                        case ADDED:
                        case CHANGED:
                            if (fileToProcess.isFile()) {
                                Files.createParentDirs(outputFile);
                                Files.copy(fileToProcess, outputFile);
                                logger.info("Copied %s to %s", fileToProcess, outputFile);
                            }
                            break;
                        case REMOVED:
                            // the outputFile may not exist as the fileToProcess was an intermediary
                            // folder
                            if (outputFile.exists()) {
                                if (outputFile.isDirectory()) {
                                    FileUtils.deleteDirectoryContents(outputFile);
                                }
                                if (!outputFile.delete()) {
                                    throw new TransformException(
                                            String.format("Cannot delete file %1$s",
                                                    outputFile.getAbsolutePath()));
                                }
                                logger.info("Deleted %s", outputFile);
                            }
                            break;
                        default:
                            throw new TransformException("Unhandled status " + status);

                    }
                }
            }
        }
    }

    private static File getOutputStreamForFile(
            @NonNull TransformOutputProvider transformOutputProvider,
            @NonNull DirectoryInput input,
            @NonNull File file,
            @NonNull Slices slices) {

        String relativePackagePath = FileUtils.relativePossiblyNonExistingPath(file.getParentFile(),
                input.getFile());
        if (input.getScopes().contains(Scope.PROJECT)
                || input.getScopes().contains(Scope.SUB_PROJECTS)) {

            Slice slice = slices.getSliceFor(new Slice.SlicedElement(relativePackagePath, file));
            return transformOutputProvider.getContentLocation(slice.name,
                    TransformManager.CONTENT_CLASS,
                    Sets.immutableEnumSet(Scope.PROJECT, Scope.SUB_PROJECTS),
                    Format.DIRECTORY);
        } else {
            return getDependenciesSliceOutputFolder(transformOutputProvider, Format.DIRECTORY);
        }
    }

    @NonNull
    private static File getMainSliceOutputFolder(
            @NonNull TransformOutputProvider outputProvider,
            @Nullable String suffix) throws IOException {
        File outputFolder = outputProvider
                .getContentLocation(
                        MAIN_SLICE_NAME + Strings.nullToEmpty(suffix),
                        TransformManager.CONTENT_CLASS,
                        Sets.immutableEnumSet(Scope.PROJECT, Scope.SUB_PROJECTS), Format.JAR);
        Files.createParentDirs(outputFolder);
        return outputFolder;
    }

    @NonNull
    private static File getDependenciesSliceOutputFolder(
            @NonNull TransformOutputProvider outputProvider, @NonNull Format format) {
        String name = format == Format.DIRECTORY ? "dep-classes" : "dependencies";
        return outputProvider
                .getContentLocation(name, TransformManager.CONTENT_CLASS,
                        Sets.immutableEnumSet(Scope.EXTERNAL_LIBRARIES,
                                Scope.SUB_PROJECTS_LOCAL_DEPS,
                                Scope.PROJECT_LOCAL_DEPS), format);
    }

    private static class Slices {
        @NonNull
        private final List<Slice> slices = new ArrayList<>();

        private Slices() {
            for (int i=0; i <NUMBER_OF_SLICES_FOR_PROJECT_CLASSES; i++) {
                Slice newSlice = new Slice("slice_" + i, i);
                slices.add(newSlice);
            }
        }

        private void addElement(@NonNull String packagePath, @NonNull File file) {
            Slice.SlicedElement slicedElement = new Slice.SlicedElement(packagePath, file);
            Slice slice = getSliceFor(slicedElement);
            slice.add(slicedElement);
        }

        private void writeTo(@NonNull TransformOutputProvider outputProvider) throws IOException {
            for (Slice slice : slices) {
                slice.writeTo(outputProvider);
            }
        }

        private Slice getSliceFor(Slice.SlicedElement slicedElement) {
            return slices.get(slicedElement.getHashBucket());
        }
    }

    private static class Slice {

        private static class SlicedElement {
            @NonNull
            private final String packagePath;
            @NonNull
            private final File slicedFile;

            private SlicedElement(@NonNull String packagePath, @NonNull File slicedFile) {
                this.packagePath = packagePath;
                this.slicedFile = slicedFile;
            }

            /**
             * Returns the bucket number in which this {@link SlicedElement} belongs.
             * @return an integer between 0 and {@link #NUMBER_OF_SLICES_FOR_PROJECT_CLASSES}
             * exclusive that will be used to bucket this item.
             */
            public int getHashBucket() {
                String hashTarget = Strings.isNullOrEmpty(packagePath)
                        ? slicedFile.getName()
                        : packagePath;
                return Math.abs(hashTarget.hashCode() % NUMBER_OF_SLICES_FOR_PROJECT_CLASSES);
            }

            @Override
            public String toString() {
                return packagePath + slicedFile.getName();
            }
        }

        @NonNull
        private final String name;
        private final int hashBucket;
        private final List<SlicedElement> slicedElements;

        private Slice(@NonNull String name, int hashBucket) {
            this.name = name;
            this.hashBucket = hashBucket;
            slicedElements = new ArrayList<>();
        }

        private void add(@NonNull SlicedElement slicedElement) {
            if (hashBucket != slicedElement.getHashBucket()) {
                throw new RuntimeException("Wrong bucket for " + slicedElement);
            }
            slicedElements.add(slicedElement);
        }

        private void writeTo(@NonNull TransformOutputProvider outputProvider) throws IOException {

            File sliceOutputLocation = outputProvider.getContentLocation(name,
                    TransformManager.CONTENT_CLASS,
                    Sets.immutableEnumSet(Scope.PROJECT, Scope.SUB_PROJECTS),
                    Format.DIRECTORY);

            // always write our dummy guard class, nobody will ever delete this file which mean
            // the slice will continue existing even it there is no other .class file in it.
            createGuardClass(name, sliceOutputLocation);

            // now copy all the files into its new location.
            for (Slice.SlicedElement slicedElement : slicedElements) {
                File outputFile = new File(sliceOutputLocation, new File(slicedElement.packagePath,
                        slicedElement.slicedFile.getName()).getPath());
                Files.createParentDirs(outputFile);
                Files.copy(slicedElement.slicedFile, outputFile);
            }
        }
    }

    private static void createGuardClass(@NonNull String name, @NonNull File outputDir)
            throws IOException {

        ClassWriter cw = new ClassWriter(0);

        File packageDir = new File(outputDir, PACKAGE_FOR_GUARD_CLASSS);
        File outputFile = new File(packageDir, name + ".class");
        Files.createParentDirs(outputFile);

        // use package separator below which is always /
        String appInfoOwner = PACKAGE_FOR_GUARD_CLASSS + '/' + name;
        cw.visit(V1_6, ACC_PUBLIC + ACC_SUPER, appInfoOwner, null, "java/lang/Object", null);
        cw.visitEnd();

        Files.write(cw.toByteArray(), outputFile);
    }
}
