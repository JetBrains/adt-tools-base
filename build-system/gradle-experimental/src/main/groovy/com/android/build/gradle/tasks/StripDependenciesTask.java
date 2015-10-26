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
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.NdkHandler;
import com.android.build.gradle.internal.core.Abi;
import com.android.build.gradle.internal.process.GradleProcessExecutor;
import com.android.build.gradle.ndk.internal.NdkNamingScheme;
import com.android.ide.common.process.LoggedProcessOutputHandler;
import com.android.ide.common.process.ProcessInfoBuilder;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.io.Files;

import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.incremental.IncrementalTaskInputs;
import org.gradle.api.tasks.incremental.InputFileDetails;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;

/**
 * Task to remove debug symbols from a native library depended on from jniLibs sourceSet.
 *
 * For files that already has the debug symbols stripped, simply copy the file.
 */
public class StripDependenciesTask extends DefaultTask {

    private Map<Abi, File> stripCommands = Maps.newHashMap();

    private Map<File, Abi> inputFiles = Maps.newHashMap();

    @NonNull
    private Map<File, Abi> stripedFiles = Maps.newHashMap();

    private File outputFolder;


    // ----- PUBLIC API -----

    /**
     * Strip command found in the NDK.
     */
    @SuppressWarnings("unused")  // Used for task input monitoring.
    @Input
    public Collection<File> getStripCommands() {
        return ImmutableList.copyOf(stripCommands.values());
    }

    public void addStripCommands(Map<Abi, File> stripCommands) {
        this.stripCommands.putAll(stripCommands);
    }

    @OutputDirectory
    public File getOutputFolder() {
        return outputFolder;
    }

    public void setOutputFolder(File outputFolder) {
        this.outputFolder = outputFolder;
    }

    @SuppressWarnings("unused") // Used by incremental task action.
    @InputFiles
    Iterable<File> getInputFiles() {
        return Iterables.concat(stripedFiles.keySet(), inputFiles.keySet());
    }

    public void addInputFiles(Map<File, Abi> files) {
        inputFiles.putAll(files);
    }

    public void addStripedFiles(Map<File, Abi> files) {
        stripedFiles.putAll(files);
    }
    // ----- PRIVATE API -----

    @TaskAction
    void taskAction(IncrementalTaskInputs inputs) throws IOException {
        inputs.outOfDate(new Action<InputFileDetails>() {
            @Override
            public void execute(InputFileDetails inputFileDetails) {
                File input = inputFileDetails.getFile();
                Abi abi = inputFiles.get(input);
                if (abi == null) {
                    abi = stripedFiles.get(input);
                    assert abi != null;

                    File output = FileUtils.join(getOutputFolder(), abi.getName(), input.getName());
                    try {
                        FileUtils.mkdirs(output.getParentFile());
                        Files.copy(input, output);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                } else {
                    File output = FileUtils.join(getOutputFolder(), abi.getName(), input.getName());
                    stripFile(input, output, abi);
                }
            }
        });
        inputs.removed(new Action<InputFileDetails>() {
            @Override
            public void execute(InputFileDetails inputFileDetails) {
                File input = inputFileDetails.getFile();
                Abi abi = inputFiles.get(input);
                if (abi == null) {
                    abi = stripedFiles.get(input);
                }
                File output = FileUtils.join(getOutputFolder(), abi.getName(), input.getName());
                try {
                    FileUtils.deleteIfExists(output);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    private void stripFile(File input, File output, Abi abi) {
        File outputDir = output.getParentFile();
        if (!output.getParentFile().exists()) {
            boolean result = outputDir.mkdirs();
            if (!result) {
                throw new RuntimeException("Unabled to create directory '"
                        + outputDir.toString() + "' for native binaries.");
            }
        }

        ProcessInfoBuilder builder = new ProcessInfoBuilder();
        builder.setExecutable(stripCommands.get(abi));
        builder.addArgs("--strip-unneeded");
        builder.addArgs("-o");
        builder.addArgs(output.toString());
        builder.addArgs(input.toString());
        new GradleProcessExecutor(getProject()).execute(
                builder.createProcess(),
                new LoggedProcessOutputHandler(new LoggerWrapper(getLogger())));
    }


    // ----- ConfigAction -----

    public static class ConfigAction implements Action<StripDependenciesTask> {
        @NonNull
        private final String buildType;
        @NonNull
        private final String flavor;
        @NonNull
        private final Map<File, Abi> inputFiles;
        @NonNull
        private final Map<File, Abi> stripedFiles;
        @NonNull
        private final File buildDir;
        @NonNull
        private final NdkHandler handler;

        public ConfigAction(
                @NonNull String buildType,
                @NonNull String flavor,
                @NonNull Map<File, Abi> inputFiles,
                @NonNull Map<File, Abi> stripedFiles,
                @NonNull File buildDir,
                @NonNull NdkHandler handler) {
            this.buildType = buildType;
            this.flavor = flavor;
            this.buildDir = buildDir;
            this.handler = handler;
            this.inputFiles = inputFiles;
            this.stripedFiles = stripedFiles;
        }

        @Override
        public void execute(@NonNull StripDependenciesTask task) {
            task.addInputFiles(inputFiles);
            task.addStripedFiles(stripedFiles);
            task.setOutputFolder(new File(
                    buildDir,
                    NdkNamingScheme.getOutputDirectoryName(buildType, flavor, "")));
            Map<Abi, File> stripCommands = Maps.newHashMap();
            if (handler.isNdkDirConfigured()) {
                for(Abi abi : handler.getSupportedAbis()) {
                    stripCommands.put(abi, handler.getStripCommand(abi));
                    task.addStripCommands(stripCommands);
                }
            }
        }
    }
}
