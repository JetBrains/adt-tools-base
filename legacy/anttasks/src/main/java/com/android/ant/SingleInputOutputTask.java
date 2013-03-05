/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.ant;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.types.Path;

import java.io.File;

/**
 * Single input single output class. Execution is controlled
 * by modification timestamp of the input and output files.
 *
 * Implementation classes must implement {@link #createOutput()}
 *
 */
public abstract class SingleInputOutputTask extends Task {

    private String mInput;
    private String mOutput;

    public void setInput(Path inputPath) {
        mInput = TaskHelper.checkSinglePath("input", inputPath);
    }

    public void setOutput(Path outputPath) {
        mOutput = TaskHelper.checkSinglePath("output", outputPath);
    }

    @Override
    public final void execute() throws BuildException {
        if (mInput == null) {
            throw new BuildException("Missing attribute input");
        }
        if (mOutput == null) {
            throw new BuildException("Missing attribute output");
        }

        // check if there's a need for the task to run.
        File outputFile = new File(mOutput);
        if (outputFile.isFile()) {
            File inputFile = new File(mInput);
            if (outputFile.lastModified() >= inputFile.lastModified()) {
                System.out.println(String.format(
                        "Run cancelled: no changes to input file %1$s",
                        inputFile.getAbsolutePath()));
                return;
            }
        }

        createOutput();
    }

    protected abstract void createOutput() throws BuildException;

    protected String getInput() {
        return mInput;
    }

    protected String getOutput() {
        return mOutput;
    }
}
