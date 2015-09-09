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
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.transform.api.NoOpTransform;
import com.android.build.transform.api.ScopedContent;
import com.android.build.transform.api.TransformException;
import com.android.build.transform.api.TransformInput;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.DexOptions;
import com.android.ide.common.process.LoggedProcessOutputHandler;
import com.android.ide.common.process.ProcessException;
import com.android.utils.ILogger;
import com.google.common.base.CaseFormat;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.io.Files;

import org.gradle.api.logging.Logger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

/**
 * Non incremental transform that looks for a file named "incrementalChanges.txt" in its input
 * and will use the file's content to get a list of .class files to dex in order to produce an
 * incremental dex file that can contains the delta changes from the last invocation.
 */
public class InstantRunDex implements NoOpTransform {

    /**
     * Expected dex file use.
     */
    public enum BuildType {
        /**
         * dex file will contain files that can be used to reload classes in a running application.
         */
        RELOAD,
        /**
         * dex file will contain the delta files (from the last incremental build) that can be used
         * to restart the application
         */
        RESTART}

    @NonNull
    private final File outputFolder;

    @NonNull
    private final AndroidBuilder androidBuilder;

    @NonNull
    private final DexOptions dexOptions;

    @NonNull
    private final ILogger logger;

    @NonNull
    private final Set<ScopedContent.ContentType> inputTypes;

    @NonNull
    private final BuildType buildType;

    public InstantRunDex(
            @NonNull BuildType buildType,
            @NonNull File outputFolder,
            @NonNull AndroidBuilder androidBuilder,
            @NonNull DexOptions dexOptions,
            @NonNull Logger logger,
            @NonNull Set<ScopedContent.ContentType> inputTypes) {
        this.buildType = buildType;
        this.outputFolder = outputFolder;
        this.androidBuilder = androidBuilder;
        this.dexOptions = dexOptions;
        this.logger = new LoggerWrapper(logger);
        this.inputTypes = inputTypes;
    }

    @Override
    public void transform(@NonNull Collection<TransformInput> inputs,
            @NonNull Collection<TransformInput> referencedInputs, boolean isIncremental)
            throws IOException, TransformException, InterruptedException {

        // create a tmp jar file.
        File classesJar = new File(outputFolder, "classes.jar");
        if (classesJar.exists()) {
            classesJar.delete();
        }
        Files.createParentDirs(classesJar);
        JarOutputStream jarOutputStream = null;

        try {
            for (TransformInput input : inputs) {
                for (File inputFolder : input.getFiles()) {
                    if (inputFolder.isDirectory()) {

                    }
                    File incremental = new File(inputFolder, "incrementalChanges.txt");
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
                        copyFileInJar(inputFolder, new File(fileToProcess), jarOutputStream);
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
    public Set<ScopedContent.ContentType> getInputTypes() {
        return inputTypes;
    }

    @NonNull
    @Override
    public Set<ScopedContent.ContentType> getOutputTypes() {
        return ImmutableSet.of();
    }

    @NonNull
    @Override
    public Set<ScopedContent.Scope> getScopes() {
        return Sets.immutableEnumSet(ScopedContent.Scope.PROJECT);
    }

    @NonNull
    @Override
    public Set<ScopedContent.Scope> getReferencedScopes() {
        return ImmutableSet.of();
    }

    @NonNull
    @Override
    public Type getTransformType() {
        return Type.NO_OP;
    }

    @NonNull
    @Override
    public ScopedContent.Format getOutputFormat() {
        return ScopedContent.Format.SINGLE_JAR;
    }

    @NonNull
    @Override
    public Collection<File> getSecondaryFileInputs() {
        return ImmutableList.of();
    }

    @NonNull
    @Override
    public Collection<File> getSecondaryFileOutputs() {
        return ImmutableList.of();
    }

    @NonNull
    @Override
    public Collection<File> getSecondaryFolderOutputs() {
        return ImmutableList.of();
    }

    @NonNull
    @Override
    public Map<String, Object> getParameterInputs() {
        return ImmutableMap.of();
    }

    @Override
    public boolean isIncremental() {
        return false;
    }
}
