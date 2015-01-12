/*
 * Copyright (C) 2014 The Android Open Source Project
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
import com.android.ide.common.process.JavaProcessExecutor
import com.android.ide.common.process.JavaProcessInfo
import com.android.ide.common.process.ProcessOutput
import com.android.ide.common.process.ProcessOutputHandler
import com.android.ide.common.process.ProcessResult
import groovy.transform.CompileStatic
import org.gradle.api.Project
import org.gradle.process.ExecResult
import org.gradle.process.JavaExecSpec

/**
 * Implementation of JavaProcessExecutor that uses Gradle's mechanism to execute external java
 * processes.
 */
@CompileStatic
public class GradleJavaProcessExecutor implements JavaProcessExecutor {

    @NonNull
    private final Project project;

    public GradleJavaProcessExecutor(@NonNull Project project) {
        this.project = project
    }

    @NonNull
    @Override
    public ProcessResult execute(
            @NonNull JavaProcessInfo javaProcessInfo,
            @NonNull ProcessOutputHandler processOutputHandler) {
        ProcessOutput output = processOutputHandler.createOutput()

        ExecResult result = project.javaexec(getJavaExecClosure(javaProcessInfo, output))

        processOutputHandler.handleOutput(output);

        return new GradleProcessResult(result)
    }

    @NonNull
    private static Closure getJavaExecClosure(
            @NonNull final JavaProcessInfo javaProcessInfo,
            @NonNull final ProcessOutput processOutput) {
        return { JavaExecSpec javaExecSpec ->
            javaExecSpec.classpath(new File(javaProcessInfo.getClasspath()))
            javaExecSpec.setMain(javaProcessInfo.getMainClass())
            javaExecSpec.args(javaProcessInfo.getArgs())
            javaExecSpec.jvmArgs(javaProcessInfo.getJvmArgs())
            javaExecSpec.environment(javaProcessInfo.getEnvironment())
            javaExecSpec.setStandardOutput(processOutput.getStandardOutput())
            javaExecSpec.setErrorOutput(processOutput.getErrorOutput())

            // we want the caller to be able to do its own thing.
            javaExecSpec.setIgnoreExitValue(true)
        }
    }
}
