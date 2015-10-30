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
import com.google.common.collect.Lists;

import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.incremental.IncrementalTaskInputs;
import org.gradle.api.tasks.incremental.InputFileDetails;
import org.gradle.nativeplatform.NativeBinarySpec;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

/**
 * Task to remove debug symbols from a native library.
 */
public class StripDebugSymbolTask extends DefaultTask {

    private File stripCommand;

    private File inputFolder;

    private Collection<File> inputFiles = Lists.newArrayList();

    private File outputFolder;

    // ----- PUBLIC API -----

    /**
     * Strip command found in the NDK.
     */
    @Input
    public File getStripCommand() {
        return stripCommand;
    }

    public void setStripCommand(File stripCommand) {
        this.stripCommand = stripCommand;
    }

    /**
     * Directory containing all the files to be stripped.
     */
    @SuppressWarnings("unused") // Used by incremental task action.
    @InputDirectory
    public File getInputFolder() {
        return inputFolder;
    }

    public void setInputFolder(File inputFolder) {
        this.inputFolder = inputFolder;
    }

    @SuppressWarnings("unused") // Used by incremental task action.
    @InputFiles
    public Collection<File> getInputFiles() {
        return inputFiles;
    }

    @OutputDirectory
    public File getOutputFolder() {
        return outputFolder;
    }

    public void setOutputFolder(File outputFolder) {
        this.outputFolder = outputFolder;
    }

    // ----- PRIVATE API -----

    @TaskAction
    void taskAction(IncrementalTaskInputs inputs) throws IOException {
        inputs.outOfDate(new Action<InputFileDetails>() {
            @Override
            public void execute(InputFileDetails inputFileDetails) {
                File input = inputFileDetails.getFile();
                File output = new File(getOutputFolder(), input.getName());
                stripFile(input, output);
            }
        });
        inputs.removed(new Action<InputFileDetails>() {
            @Override
            public void execute(InputFileDetails inputFileDetails) {
                File input = inputFileDetails.getFile();
                File output = new File(getOutputFolder(), input.getName());
                try {
                    FileUtils.deleteIfExists(output);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }


    private void stripFile(File input, File output) {
        if (!getOutputFolder().exists()) {
            boolean result = getOutputFolder().mkdirs();
            if (!result) {
                throw new RuntimeException("Unabled to create directory '"
                        + getOutputFolder().toString() + "' for native binaries.");
            }
        }

        ProcessInfoBuilder builder = new ProcessInfoBuilder();
        builder.setExecutable(getStripCommand());
        builder.addArgs("--strip-unneeded");
        builder.addArgs("-o");
        builder.addArgs(output.toString());
        builder.addArgs(input.toString());
        new GradleProcessExecutor(getProject()).execute(
                builder.createProcess(),
                new LoggedProcessOutputHandler(new LoggerWrapper(getLogger())));
    }

    // ----- ConfigAction -----

    public static class ConfigAction implements Action<StripDebugSymbolTask> {
        @NonNull
        private final NativeBinarySpec binary;
        @NonNull
        private final File inputFolder;
        @NonNull
        private final Collection<File> inputFiles;
        @NonNull
        private final File buildDir;
        @NonNull
        private final NdkHandler handler;

        public ConfigAction(
                @NonNull NativeBinarySpec binary,
                @NonNull File inputFolder,
                @NonNull Collection<File> inputFiles,
                @NonNull File buildDir,
                @NonNull NdkHandler handler) {
            this.binary = binary;
            this.inputFolder = inputFolder;
            this.inputFiles = inputFiles;
            this.buildDir = buildDir;
            this.handler = handler;
        }

        @Override
        public void execute(@NonNull StripDebugSymbolTask task) {
            task.setInputFolder(inputFolder);
            task.getInputFiles().addAll(inputFiles);
            task.setOutputFolder(new File(
                    buildDir,
                    NdkNamingScheme.getOutputDirectoryName(binary)));
            task.setStripCommand(handler.getStripCommand(
                    Abi.getByName(binary.getTargetPlatform().getName())));
            task.dependsOn(binary);
        }
    }
}
