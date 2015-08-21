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

import static com.android.builder.model.AndroidProject.FD_INTERMEDIATES;

import com.android.build.gradle.internal.dsl.DexOptions;
import com.android.build.gradle.internal.scope.ConventionMappingHelper;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.BaseTask;
import com.android.ide.common.process.LoggedProcessOutputHandler;
import com.android.ide.common.process.ProcessException;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;

import org.gradle.api.Action;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.incremental.IncrementalTaskInputs;
import org.gradle.api.tasks.incremental.InputFileDetails;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

/**
 * Dex task to compile individual classes during incremental deploy sessions.
 */
public class IncrementalSupportDex extends BaseTask {

    private File inputFolder;
    private File outputFolder;
    private File tmpFolder;

    @Nested
    DexOptions dexOptions;

    @InputDirectory
    public File getInputFolder() {
        return inputFolder;
    }

    @OutputDirectory
    public File getOutputFolder() {
        return outputFolder;
    }

    @TaskAction
    public void taskAction(final IncrementalTaskInputs inputs)
            throws InterruptedException, ProcessException, IOException {

        final ImmutableList.Builder<File> inputFiles = ImmutableList.builder();

        // create a tmp jar file.
        File classesJar = new File(getInputFolder().getParentFile(), "classes.jar");
        if (classesJar.exists()) {
            classesJar.delete();
        }

        final JarOutputStream jarOutputStream = new JarOutputStream(new FileOutputStream(classesJar));
        final AtomicInteger processedFiles = new AtomicInteger(0);

        final File patchesLoader = new File(getInputFolder(),
                "com/android/build/gradle/internal/incremental/AppPatchesLoaderImpl.class");
        inputs.outOfDate(new Action<InputFileDetails>() {
            @Override
            public void execute(InputFileDetails inputFileDetails) {
                if (!inputs.isIncremental() || inputFileDetails.isModified()) {
                    try {
                        if (!patchesLoader.getAbsolutePath().equals(
                                inputFileDetails.getFile().getAbsolutePath())) {
                            copyFileInJar(inputFileDetails.getFile(), jarOutputStream);
                            processedFiles.incrementAndGet();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        // always copy the PatchesLoader generated class if present.
        if (patchesLoader.exists()) {
            copyFileInJar(patchesLoader, jarOutputStream);
        }

        jarOutputStream.close();
        if (processedFiles.get() == 0) {
            return;
        }
        inputFiles.add(classesJar);
        tmpFolder.mkdirs();

        getBuilder().convertByteCode(inputFiles.build(),
                ImmutableList.<File>of() /* inputLibraries */,
                getOutputFolder(),
                false /* multiDexEnabled */,
                null /*getMainDexListFile */,
                dexOptions,
                ImmutableList.<String>of() /* getAdditionalParameters */,
                tmpFolder,
                false /* incremental */,
                true /* optimize */,
                new LoggedProcessOutputHandler(getILogger()));
    }

    private void copyFileInJar(File inputFile, JarOutputStream jarOutputStream) throws IOException {
        String entryName = inputFile.getPath().substring(
                getInputFolder().getPath().length() + 1);
        JarEntry jarEntry = new JarEntry(entryName);
        jarOutputStream.putNextEntry(jarEntry);
        Files.copy(inputFile, jarOutputStream);
        jarOutputStream.closeEntry();
    }

    public static class ConfigAction implements TaskConfigAction<IncrementalSupportDex> {

        private final VariantScope scope;
        private final OutputBuildType buildType;

        public ConfigAction(VariantScope scope, OutputBuildType buildType) {
            this.scope = scope;
            this.buildType = buildType;
        }

        @Override
        public String getName() {
            return scope.getTaskName(
                    buildType.name().toLowerCase(Locale.getDefault()) + "SupportDex");
        }

        @Override
        public Class<IncrementalSupportDex> getType() {
            return IncrementalSupportDex.class;
        }

        @Override
        public void execute(IncrementalSupportDex incrementalSupportDex) {
            ConventionMappingHelper.map(incrementalSupportDex, "inputFolder",
                    new Callable<File>() {
                        @Override
                        public File call() throws Exception {
                            File inputFolder = buildType == OutputBuildType.COLDSWAP_DEX
                                    ? scope.getInitialIncrementalSupportJavaOutputDir()
                                    : scope.getIncrementalSupportJavaOutputDir();
                            if (!inputFolder.exists()) {
                                inputFolder.mkdirs();
                            }
                            return inputFolder;
                        }
                    });

            ConventionMappingHelper.map(incrementalSupportDex, "outputFolder",
                    new Callable<File>() {
                        @Override
                        public File call() throws Exception {
                            switch(buildType) {
                                case COLDSWAP_DEX:
                                    File outputFolder = scope.getRestartDexOutputFolder();
                                    if (!outputFolder.exists()) {
                                        outputFolder.mkdirs();
                                    }
                                    return outputFolder;
                                case HOTSWAP_DEX:
                                    File folder = scope.getReloadDexOutputFolder();
                                    if (!folder.exists()) {
                                        folder.mkdirs();
                                    }
                                    return folder;
                                default :
                                    throw new RuntimeException("Unhandled " + buildType);
                            }
                        };
                    });

            incrementalSupportDex.outputFolder = buildType == OutputBuildType.COLDSWAP_DEX
                    ? scope.getDexOutputFolder()
                    : scope.getReloadDexOutputFolder();

            incrementalSupportDex.setAndroidBuilder(scope.getGlobalScope().getAndroidBuilder());
            incrementalSupportDex.setVariantName(
                    scope.getVariantData().getVariantConfiguration().getFullName());

            incrementalSupportDex.dexOptions = scope.getGlobalScope().getExtension().getDexOptions();
            incrementalSupportDex.tmpFolder = new File(
                    String.valueOf(scope.getGlobalScope().getBuildDir()) + "/" + FD_INTERMEDIATES
                            + "/tmp/dex/"
                            + scope.getVariantData().getVariantConfiguration().getDirName());
        }
    }

    /**
     * Denotes the type of dex output expected from this task.
     */
    public enum OutputBuildType {
        /**
         * dexing .class files necessary for a cold restart of the application.
         */
        COLDSWAP_DEX,
        /**
         * dexing .class files that can be hot swapped in running JVM.
         */
        HOTSWAP_DEX
    }
}
