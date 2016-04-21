/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle.integration.common.fixture;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;

import org.gradle.tooling.GradleConnectionException;

import java.io.ByteArrayOutputStream;

/**
 * The result from running a build.
 *
 * <p>See {@link GradleTestProject#executor()} and {@link RunGradleTasks}.
 */
public class GradleBuildResult {

    @NonNull
    private final ByteArrayOutputStream stdout;

    @NonNull
    private final ByteArrayOutputStream stderr;

    @Nullable
    private final GradleConnectionException exception;

    public GradleBuildResult(
            @NonNull ByteArrayOutputStream stdout,
            @NonNull ByteArrayOutputStream stderr,
            @Nullable GradleConnectionException exception) {
        this.stdout = stdout;
        this.stderr = stderr;
        this.exception = exception;
    }

    /**
     * Returns the exception from the build, null if the build succeeded.
     */
    @Nullable
    public GradleConnectionException getException() {
        return exception;
    }

    public String getStdout() {
        return stdout.toString();
    }

    @NonNull
    public String getStderr() {
        return stderr.toString();
    }

}
