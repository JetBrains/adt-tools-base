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
package com.android.build.gradle.external.gnumake;


import com.google.common.base.Joiner;

import java.util.List;

/**
 * Classification of a given command-line. Includes tool-specific interpretation of input and output
 * files.
 */
class BuildStepInfo {
    private final CommandLine command;
    private final List<String> inputs;
    private final List<String> outputs;
    // true only if this command can supply terminal input files.
    // For example, .c files specified by gcc with -c flag.
    private final boolean inputsAreSourceFiles;

    BuildStepInfo(CommandLine command, List<String> inputs, List<String> outputs) {
        this.command = command;
        this.inputs = inputs;
        this.outputs = outputs;
        this.inputsAreSourceFiles = false;
    }

    BuildStepInfo(CommandLine command, List<String> inputs, List<String> outputs,
            boolean inputsAreSourceFiles) {
        this.command = command;
        this.inputs = inputs;
        this.outputs = outputs;
        this.inputsAreSourceFiles = inputsAreSourceFiles;
    }

    String getOnlyInput() {
        if (getInputs().size() != 1) {
            throw new RuntimeException("Did not expect multiple inputs");
        }
        return getInputs().iterator().next();
    }

    @Override
    public boolean equals(Object obj) {
        BuildStepInfo that = (BuildStepInfo) obj;
        return this.command.command.equals(that.command.command)
                && this.inputs.equals(that.getInputs())
                && this.outputs.equals(that.getOutputs())
                && this.inputsAreSourceFiles == that.inputsAreSourceFiles;
    }

    @Override
    public String toString() {
        return command.command
                + " in:[" + Joiner.on(' ').join(inputs) + "]"
                + " out:[" + Joiner.on(' ').join(outputs) + "]"
                + (inputsAreSourceFiles ? "SOURCE" : "INTERMEDIATE");
    }

    CommandLine getCommand() {
        return command;
    }

    List<String> getInputs() {
        return inputs;
    }

    List<String> getOutputs() {
        return outputs;
    }

    boolean inputsAreSourceFiles() {
        return inputsAreSourceFiles;
    }
}
