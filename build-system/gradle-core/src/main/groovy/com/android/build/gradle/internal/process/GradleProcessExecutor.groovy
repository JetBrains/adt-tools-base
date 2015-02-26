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

package com.android.build.gradle.internal.process
import com.android.annotations.NonNull
import com.android.ide.common.process.ProcessExecutor
import com.android.ide.common.process.ProcessInfo
import com.android.ide.common.process.ProcessOutput
import com.android.ide.common.process.ProcessOutputHandler
import com.android.ide.common.process.ProcessResult
import groovy.transform.CompileStatic
import org.gradle.api.Project
import org.gradle.process.ExecResult
import org.gradle.process.ExecSpec

/**
 * Implementation of ProcessExecutor that uses Gradle's mechanism to execute external processes.
 */
@CompileStatic
public class GradleProcessExecutor implements ProcessExecutor {

    @NonNull
    private final Project project;

    public GradleProcessExecutor(@NonNull Project project) {
        this.project = project
    }

    @NonNull
    @Override
    public ProcessResult execute(
            @NonNull ProcessInfo processInfo,
            @NonNull ProcessOutputHandler processOutputHandler) {
        ProcessOutput output = processOutputHandler.createOutput()

        ExecResult result = project.exec(getExecClosure(processInfo, output))

        processOutputHandler.handleOutput(output);

        return new GradleProcessResult(result)
    }

    @NonNull
    private static Closure getExecClosure(
            @NonNull final ProcessInfo processInfo,
            @NonNull final ProcessOutput processOutput) {
        return { ExecSpec execSpec ->

            execSpec.setExecutable(processInfo.executable)
            execSpec.args(processInfo.getArgs())
            execSpec.environment(processInfo.getEnvironment())
            execSpec.setStandardOutput(processOutput.getStandardOutput())
            execSpec.setErrorOutput(processOutput.getErrorOutput())

            // we want the caller to be able to do its own thing.
            execSpec.setIgnoreExitValue(true)
        }
    }
}
