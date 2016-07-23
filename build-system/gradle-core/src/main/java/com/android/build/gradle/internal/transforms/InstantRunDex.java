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

import com.android.annotations.NonNull;
import com.android.annotations.VisibleForTesting;
import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.Status;
import com.android.build.api.transform.Transform;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.pipeline.ExtendedContentType;
import com.android.build.gradle.internal.scope.InstantRunVariantScope;
import com.android.builder.core.DexByteCodeConverter;
import com.android.builder.core.DexOptions;
import com.android.builder.model.OptionalCompilationStep;
import com.android.ide.common.process.LoggedProcessOutputHandler;
import com.android.ide.common.process.ProcessException;
import com.android.utils.FileUtils;
import com.android.utils.ILogger;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.io.Files;

import org.gradle.api.logging.Logger;

import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

/**
 * Transform that takes the hot (or warm) swap classes and dexes them.
 *
 * <p>The instant run transform outputs all of the hot swap files in the
 * {@link ExtendedContentType#CLASSES_ENHANCED} stream. This transform runs incrementally to re-dex
 * and redeliver only the classes changed since the last build, even in a series of hot swaps.
 *
 * <p>Note that a non-incremental run is still correct, it will just dex all of the hot swap changes
 * since the last cold swap or full build.
 */
public class InstantRunDex extends Transform {

    @NonNull
    private final Supplier<DexByteCodeConverter> dexByteCodeConverter;

    @NonNull
    private final DexOptions dexOptions;

    @NonNull
    private final ILogger logger;

    @NonNull
    private final InstantRunVariantScope variantScope;

    public InstantRunDex(
            @NonNull InstantRunVariantScope transformVariantScope,
            @NonNull Supplier<DexByteCodeConverter> dexByteCodeConverter,
            @NonNull DexOptions dexOptions,
            @NonNull Logger logger) {
        this.variantScope = transformVariantScope;
        this.dexByteCodeConverter = dexByteCodeConverter;
        this.dexOptions = dexOptions;
        this.logger = new LoggerWrapper(logger);
    }

    @Override
    public void transform(@NonNull TransformInvocation invocation)
            throws IOException, TransformException, InterruptedException {

        File outputFolder = InstantRunBuildType.RELOAD.getOutputFolder(variantScope);

        boolean changesAreCompatible =
                variantScope.getInstantRunBuildContext().hasPassedVerification();
        boolean restartDexRequested =
                variantScope.getGlobalScope().isActive(OptionalCompilationStep.RESTART_ONLY);

        if (!changesAreCompatible || restartDexRequested) {
            FileUtils.cleanOutputDir(outputFolder);
            return;
        }

        // create a tmp jar file.
        File classesJar = new File(outputFolder, "classes.jar");
        if (classesJar.exists()) {
            FileUtils.delete(classesJar);
        }
        Files.createParentDirs(classesJar);
        final JarClassesBuilder jarClassesBuilder = getJarClassBuilder(classesJar);

        try {
            for (TransformInput input : invocation.getReferencedInputs()) {
                for (DirectoryInput directoryInput : input.getDirectoryInputs()) {
                    if (!directoryInput.getContentTypes()
                            .contains(ExtendedContentType.CLASSES_ENHANCED)) {
                        continue;
                    }
                    final File folder = directoryInput.getFile();
                    if (invocation.isIncremental()) {
                        for (Map.Entry<File, Status> entry :
                                directoryInput.getChangedFiles().entrySet()) {
                            if (entry.getValue() != Status.REMOVED) {
                                File file = entry.getKey();
                                if (file.isFile()) {
                                    jarClassesBuilder.add(folder, file);
                                }
                            }
                        }
                    } else {
                        Iterable<File> files = FileUtils.getAllFiles(folder);
                        for (File inputFile : files) {
                            jarClassesBuilder.add(folder, inputFile);
                        }
                    }
                }
            }
        } finally {
            jarClassesBuilder.close();
        }

        // if no files were added, clean up and return.
        if (jarClassesBuilder.isEmpty()) {
            FileUtils.cleanOutputDir(outputFolder);
            return;
        }
        final ImmutableList.Builder<File> inputFiles = ImmutableList.builder();
        inputFiles.add(classesJar);

        try {
            variantScope.getInstantRunBuildContext().startRecording(
                    InstantRunBuildContext.TaskType.INSTANT_RUN_DEX);
            convertByteCode(inputFiles.build(), outputFolder);
            variantScope.getInstantRunBuildContext().addChangedFile(
                    InstantRunBuildContext.FileType.RELOAD_DEX,
                    new File(outputFolder, "classes.dex"));
        } catch (ProcessException e) {
            throw new TransformException(e);
        } finally {
            variantScope.getInstantRunBuildContext().stopRecording(
                    InstantRunBuildContext.TaskType.INSTANT_RUN_DEX);
        }
    }

    @VisibleForTesting
    static class JarClassesBuilder implements Closeable{
        final File outputFile;
        private JarOutputStream jarOutputStream;
        boolean empty = true;

        private JarClassesBuilder(@NonNull File outputFile) {
            this.outputFile = outputFile;
        }

        void add(File inputDir, File file) throws IOException {
            if (jarOutputStream == null) {
                jarOutputStream = new JarOutputStream(
                        new BufferedOutputStream(new FileOutputStream(outputFile)));
            }
            empty = false;
            copyFileInJar(inputDir, file, jarOutputStream);
        }

        @Override
        public void close() throws IOException {
            if (jarOutputStream != null) {
                jarOutputStream.close();
            }
        }

        boolean isEmpty() {
            return empty;
        }
    }

    @VisibleForTesting
    protected void convertByteCode(List<File> inputFiles, File outputFolder)
            throws InterruptedException, ProcessException, IOException {
        dexByteCodeConverter.get().convertByteCode(inputFiles,
                outputFolder,
                false /* multiDexEnabled */,
                null /*getMainDexListFile */,
                dexOptions,
                Objects.firstNonNull(dexOptions.getOptimize(), false) /* optimize */,
                new LoggedProcessOutputHandler(logger));
    }

    @VisibleForTesting
    protected JarClassesBuilder getJarClassBuilder(File outputFile) {
        return new JarClassesBuilder(outputFile);
    }

    private static void copyFileInJar(File inputDir, File inputFile, JarOutputStream jarOutputStream)
            throws IOException {

        String entryName = inputFile.getPath().substring(inputDir.getPath().length() + 1);
        JarEntry jarEntry = new JarEntry(entryName);
        jarOutputStream.putNextEntry(jarEntry);
        Files.copy(inputFile, jarOutputStream);
        jarOutputStream.closeEntry();
    }

    @NonNull
    @Override
    public String getName() {
        return "instantReloadDex";
    }

    @NonNull
    @Override
    public Set<QualifiedContent.ContentType> getInputTypes() {
        return ImmutableSet.of(ExtendedContentType.CLASSES_ENHANCED);
    }

    @NonNull
    @Override
    public Set<QualifiedContent.Scope> getScopes() {
        return ImmutableSet.of();
    }

    @NonNull
    @Override
    public Set<QualifiedContent.Scope> getReferencedScopes() {
        return Sets.immutableEnumSet(QualifiedContent.Scope.PROJECT);
    }

    @NonNull
    @Override
    public Map<String, Object> getParameterInputs() {
        return ImmutableMap.of(
                "changesAreCompatible",
                variantScope.getInstantRunBuildContext().hasPassedVerification(),
                "restartDexRequested",
                variantScope.getGlobalScope().isActive(OptionalCompilationStep.RESTART_ONLY));
    }

    @NonNull
    @Override
    public Collection<File> getSecondaryDirectoryOutputs() {
        return ImmutableList.of(InstantRunBuildType.RELOAD.getOutputFolder(variantScope));
    }

    @Override
    public boolean isIncremental() {
        return true;
    }

}
