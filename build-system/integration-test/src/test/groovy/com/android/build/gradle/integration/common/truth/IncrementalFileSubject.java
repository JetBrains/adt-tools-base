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

package com.android.build.gradle.integration.common.truth;

import com.google.common.truth.FailureStrategy;
import com.google.common.truth.Subject;

import java.io.File;
import java.util.regex.Pattern;

/**
 * Truth support for validating whether changes to a file affects incremental tasks.
 */
public class IncrementalFileSubject extends Subject<IncrementalFileSubject, File> {

    private final String gradleOutput;

    public IncrementalFileSubject(FailureStrategy failureStrategy, File subject, String gradleOutput) {
        super(failureStrategy, subject);
        this.gradleOutput = gradleOutput;
    }

    @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
    public void hasNotBeenChanged() {
        Pattern pattern = Pattern.compile("(Input|Output) file " + getSubjectPattern());
        if (pattern.matcher(gradleOutput).find()) {
            fail("has not been changed.");
        }
    }

    @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
    public void hasBeenAdded() {
        Pattern pattern =
                Pattern.compile("Input file " + getSubjectPattern() + " has been added.");
        if (!pattern.matcher(gradleOutput).find()) {
            failWithRawMessage(
                    "Not true that a task was executed due to %s being added.",
                    getDisplaySubject());
        }
    }

    @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
    public void hasChanged() {
        Pattern pattern =
                Pattern.compile("(Input|Output) file " + getSubjectPattern() + " has changed.");
        if (!pattern.matcher(gradleOutput).find()) {
            failWithRawMessage(
                    "Not true that a task was executed due to %s being changed.",
                    getDisplaySubject());
        }
    }

    @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
    public void hasBeenRemoved() {
        Pattern pattern =
                Pattern.compile("(Input|Output) file " + getSubjectPattern()
                        + " has been removed.");
        if (!pattern.matcher(gradleOutput).find()) {
            failWithRawMessage(
                    "Not true that a task was executed due to %s being removed.",
                    getDisplaySubject());
        }
    }

    private String getSubjectPattern() {
        return getSubject().isAbsolute() ? getSubject().getPath() : "\\S*" + getSubject().getPath();
    }

}
