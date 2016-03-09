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

package com.android.build.gradle.integration.common.utils;

import com.google.common.base.Throwables;

import org.gradle.api.tasks.TaskExecutionException;
import org.gradle.internal.serialize.PlaceholderException;
import org.gradle.tooling.GradleConnectionException;

/**
 * Helper code for dealing with exceptions returned from the tooling API.
 */
public class GradleExceptionsHelper {
    private GradleExceptionsHelper() {}

    /**
     * Gets the message printed at the bottom of the console output, after a task has failed.
     */
    public static String getTaskFailureMessage(GradleConnectionException e) {
        for (Throwable throwable : Throwables.getCausalChain(e)) {
            // Because of different class loaders involved, we are forced to do stringly-typed
            // programming.
            if (throwable.getClass().getName().equals(PlaceholderException.class.getName())) {
                if (throwable.toString().startsWith(TaskExecutionException.class.getName())) {
                    return throwable.getCause().getMessage();
                }
            }
        }

        throw new AssertionError(
                String.format(
                        "Exception was not caused by a task failure: \n%s",
                        Throwables.getStackTraceAsString(e)));
    }
}
