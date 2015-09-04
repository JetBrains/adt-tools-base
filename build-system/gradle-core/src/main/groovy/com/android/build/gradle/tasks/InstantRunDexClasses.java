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

package com.android.build.gradle.tasks;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.dsl.DexOptions;
import com.android.build.gradle.internal.pipeline.StreamBasedTask;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.scope.ConventionMappingHelper;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.transform.api.ScopedContent;
import com.android.ide.common.process.LoggedProcessOutputHandler;
import com.android.ide.common.process.ProcessException;
import com.google.common.base.CaseFormat;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;

import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

/**
 * Non incremental task that looks for a file named "incrementalChanges.txt" in its input folders
 * and will use the file's content to get a list of .class files to dex in order to produce an
 * incremental dex file that can contains the delta changes from the last invocation.
 */
public class InstantRunDexClasses extends StreamBasedTask {

    File outputFolder;

    @Nested
    DexOptions dexOptions;

    @OutputDirectory
    File getOutputFolder() {
        return outputFolder;
    }

    /**
     * This is not an incremental task on purpose, we need to check for the existence of the
     * incremental.txt file to gather our input.
     */
    @TaskAction
    public void dexClasses() throws IOException, ProcessException, InterruptedException {

        // create a tmp jar file.
        File classesJar = new File(getOutputFolder(), "classes.jar");
        if (classesJar.exists()) {
            classesJar.delete();
        }

        JarOutputStream jarOutputStream = null;

        for (File inputFolder : getStreamInputs()) {
            File incremental = new File(inputFolder, "incrementalChanges.txt");
            if (!incremental.exists()) {
                // done
                continue;
            }
            try {
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
            } finally {
                if (jarOutputStream!=null) {
                    jarOutputStream.close();
                }
            }
        }

        final ImmutableList.Builder<File> inputFiles = ImmutableList.builder();
        inputFiles.add(classesJar);

        getBuilder().convertByteCode(inputFiles.build(),
                ImmutableList.<File>of() /* inputLibraries */,
                getOutputFolder(),
                false /* multiDexEnabled */,
                null /*getMainDexListFile */,
                dexOptions,
                ImmutableList.<String>of() /* getAdditionalParameters */,
                false /* incremental */,
                true /* optimize */,
                new LoggedProcessOutputHandler(getILogger()));
    }

    private static void copyFileInJar(File inputDir, File inputFile, JarOutputStream jarOutputStream)
            throws IOException {

        String entryName = inputFile.getPath().substring(inputDir.getPath().length() + 1);
        JarEntry jarEntry = new JarEntry(entryName);
        jarOutputStream.putNextEntry(jarEntry);
        Files.copy(inputFile, jarOutputStream);
        jarOutputStream.closeEntry();
    }

    public static class ConfigAction implements TaskConfigAction<InstantRunDexClasses> {

        public enum BuildType { RELOAD, RESTART }

        private final TransformManager.StreamFilter streamFilter;
        private final BuildType buildType;
        private final VariantScope scope;

        public ConfigAction(VariantScope scope, final BuildType buildType) {
            this.scope = scope;
            this.buildType = buildType;
            this.streamFilter = new TransformManager.StreamFilter() {
                @Override
                public boolean accept(@NonNull Set<ScopedContent.ContentType> types,
                        @NonNull Set<ScopedContent.Scope> scopes) {
                    return types.contains(buildType == BuildType.RESTART
                                ? ScopedContent.ContentType.CLASSES
                                : ScopedContent.ContentType.CLASSES_ENHANCED) &&
                            !scopes.contains(ScopedContent.Scope.TESTED_CODE) &&
                            !scopes.contains(ScopedContent.Scope.PROVIDED_ONLY);
                }
            };
        }

        public TransformManager.StreamFilter getStreamFilter() {
            return streamFilter;
        }

        @NonNull
        @Override
        public String getName() {
            return scope.getTaskName(
                    CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, buildType.name()),
                    "SupportDex");
        }

        @NonNull
        @Override
        public Class<InstantRunDexClasses> getType() {
            return InstantRunDexClasses.class;
        }

        @Override
        public void execute(@NonNull InstantRunDexClasses task) {

            task.outputStreams = ImmutableList.of();
            task.referencedInputStreams = ImmutableList.of();
            task.dexOptions = scope.getGlobalScope().getExtension().getDexOptions();
            task.setAndroidBuilder(scope.getGlobalScope().getAndroidBuilder());

            task.setVariantName(scope.getVariantConfiguration().getFullName());
            task.consumedInputStreams = scope.getTransformManager().getStreams(streamFilter);
            ConventionMappingHelper.map(task, "outputFolder", new Callable<File>() {
                @Override
                public File call() throws Exception {
                    switch(buildType) {
                        case RELOAD:
                            return scope.getReloadDexOutputFolder();
                        case RESTART:
                            return scope.getRestartDexOutputFolder();
                        default:
                            throw new RuntimeException("Unhandled build type : " + buildType);
                    }
                }
            });
        }
    }
}
