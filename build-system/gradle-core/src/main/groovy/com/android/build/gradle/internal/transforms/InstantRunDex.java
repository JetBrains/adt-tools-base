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
import com.android.annotations.Nullable;
import com.android.build.api.transform.Context;
import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.Transform;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.DexOptions;
import com.android.ide.common.process.LoggedProcessOutputHandler;
import com.android.ide.common.process.ProcessException;
import com.android.utils.FileUtils;
import com.android.utils.ILogger;
import com.google.common.base.CaseFormat;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.io.Files;

import org.gradle.api.logging.Logger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

/**
 * Non incremental transform that looks for a file named "incrementalChanges.txt" in its input
 * and will use the file's content to get a list of .class files to dex in order to produce an
 * incremental dex file that can contains the delta changes from the last invocation.
 */
public class InstantRunDex extends Transform {

    /**
     * Expected dex file use.
     */
    public enum BuildType {
        /**
         * dex file will contain files that can be used to reload classes in a running application.
         */
        RELOAD {
            @NonNull
            @Override
            public File getOutputFolder(VariantScope variantScope) {
                return variantScope.getReloadDexOutputFolder();
            }
        },
        /**
         * dex file will contain the delta files (from the last incremental build) that can be used
         * to restart the application
         */
        RESTART {
            @NonNull
            @Override
            public File getOutputFolder(VariantScope variantScope) {
                return variantScope.getRestartDexOutputFolder();
            }
        };

        @NonNull
        public abstract File getOutputFolder(VariantScope variantScope);
    }

    @NonNull
    private final AndroidBuilder androidBuilder;

    @NonNull
    private final DexOptions dexOptions;

    @NonNull
    private final ILogger logger;

    @NonNull
    private final Set<QualifiedContent.ContentType> inputTypes;

    @NonNull
    private final BuildType buildType;

    @NonNull
    private final VariantScope variantScope;

    public InstantRunDex(
            @NonNull VariantScope variantScope,
            @NonNull BuildType buildType,
            @NonNull AndroidBuilder androidBuilder,
            @NonNull DexOptions dexOptions,
            @NonNull Logger logger,
            @NonNull Set<QualifiedContent.ContentType> inputTypes) {
        this.variantScope = variantScope;
        this.buildType = buildType;
        this.androidBuilder = androidBuilder;
        this.dexOptions = dexOptions;
        this.logger = new LoggerWrapper(logger);
        this.inputTypes = inputTypes;
    }

    @Override
    public void transform(@NonNull Context context, @NonNull Collection<TransformInput> inputs,
            @NonNull Collection<TransformInput> referencedInputs,
            @Nullable TransformOutputProvider output,
            boolean isIncremental) throws IOException, TransformException, InterruptedException {

        File outputFolder = buildType.getOutputFolder(variantScope);

        if (buildType == BuildType.RELOAD) {
            // if we are in reload mode, we should check the result of the verifier.
            if (!variantScope.getInstantRunBuildContext().hasPassedVerification()) {
                // changes are incompatible, we therefore do not produce a reload dex file.
                // Android Studio will take that as a cue to do a cold swap.
                FileUtils.emptyFolder(outputFolder);
                return;
            }
        }

        // create a tmp jar file.
        File classesJar = new File(outputFolder, "classes.jar");
        if (classesJar.exists()) {
            classesJar.delete();
        }
        Files.createParentDirs(classesJar);
        JarOutputStream jarOutputStream = null;

        try {
            for (TransformInput input : referencedInputs) {
                for (DirectoryInput directoryInput : input.getDirectoryInputs()) {
                    if (!directoryInput.getContentTypes().containsAll(inputTypes)) {
                        continue;
                    }
                    File folder = directoryInput.getFile();
                    File incremental = new File(folder, "incrementalChanges.txt");
                    if (!incremental.exists()) {
                        // done
                        continue;
                    }
                    List<String> filesToProcess = Files.readLines(incremental, Charsets.UTF_8);
                    if (filesToProcess == null || filesToProcess.isEmpty()) {
                        return;
                    }

                    if (jarOutputStream == null) {
                        jarOutputStream = new JarOutputStream(new FileOutputStream(classesJar));
                    }

                    for (String fileToProcess : filesToProcess) {
                        copyFileInJar(folder, new File(fileToProcess), jarOutputStream);
                    }
                }

            }
        } finally {
            if (jarOutputStream != null) {
                jarOutputStream.close();
            }
        }
        final ImmutableList.Builder<File> inputFiles = ImmutableList.builder();
        inputFiles.add(classesJar);

        try {
            variantScope.getInstantRunBuildContext().startRecording(
                    InstantRunBuildContext.TaskType.INSTANT_RUN_DEX);
            androidBuilder.convertByteCode(inputFiles.build(),
                    outputFolder,
                    false /* multiDexEnabled */,
                    null /*getMainDexListFile */,
                    dexOptions,
                    ImmutableList.<String>of() /* getAdditionalParameters */,
                    false /* incremental */,
                    true /* optimize */,
                    new LoggedProcessOutputHandler(logger));
        } catch (ProcessException e) {
            throw new TransformException(e);
        } finally {
            variantScope.getInstantRunBuildContext().stopRecording(
                    InstantRunBuildContext.TaskType.INSTANT_RUN_DEX);
        }
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
        return "instant+" + CaseFormat.UPPER_UNDERSCORE.to(
                CaseFormat.LOWER_CAMEL, buildType.name()) + "Dex";
    }

    @NonNull
    @Override
    public Set<QualifiedContent.ContentType> getInputTypes() {
        return inputTypes;
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

    @Override
    public boolean isIncremental() {
        return false;
    }
}
