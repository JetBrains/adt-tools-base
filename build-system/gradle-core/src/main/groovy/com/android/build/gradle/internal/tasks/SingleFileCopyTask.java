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

package com.android.build.gradle.internal.tasks;

import com.android.annotations.NonNull;
import com.google.common.io.Files;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;

/**
 * Simple task to do a single file copy.
 *
 * Using the built-in Copy task to copy a single file is dangerous if multiple files
 * go in the same folder. This is because Copy uses a folder destination rather than a file
 * destination, and if 2+ Copy task write in the same folder, it'll disable task-level
 * parallelism.
 * Also, when a task output (in this case the folder) is clobbered by another task (which would
 * simply write in the same folder), on the next run the tasks will run again in a non
 * incremental way which is bad.
 *
 * So this task is very simple file to file copy which does not have all these issues, and allow
 * to copy multiple files into the same folder.
 *
 */
public class SingleFileCopyTask extends DefaultTask {

    protected File inputFile;
    protected File outputFile;

    @InputFile
    public File getInputFile() {
        return inputFile;
    }

    @OutputFile
    public File getOutputFile() {
        return outputFile;
    }

    public void setInputFile(@NonNull File inputFile) {
        this.inputFile = inputFile;
    }

    public void setOutputFile(@NonNull File outputFile) {
        this.outputFile = outputFile;
    }

    @TaskAction
    public void copy() throws IOException {
        Files.copy(inputFile, outputFile);
    }
}
