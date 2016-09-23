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

package com.android.build.gradle.integration.common.truth;

import static com.google.common.base.Preconditions.checkState;

import com.android.annotations.NonNull;
import com.google.common.truth.FailureStrategy;
import com.google.common.truth.SubjectFactory;

import org.gradle.execution.taskgraph.TaskInfo;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Factory to create GradleOutputTaskSubject.
 */
public class GradleOutputTaskSubjectFactory extends SubjectFactory<GradleOutputTaskSubject, String> {

    @NonNull
    private final List<GradleOutputTaskSubject.TaskInfo> taskInfo;

    public GradleOutputTaskSubjectFactory(@NonNull String gradleOutput) {
        List<String> lines = Arrays.stream(gradleOutput.split("\n"))
                .map(String::trim)
                .collect(Collectors.toList());

        Pattern pattern = Pattern.compile("Tasks to be executed: \\[(.*)\\]");
        Optional<String> taskLine = lines.stream()
                .map(pattern::matcher)
                .filter(Matcher::matches)
                .map(match -> match.group(1))
                .findFirst();
        if (!taskLine.isPresent()) {
            throw new RuntimeException("Unable to determine task lists from Gradle output");
        }

        taskInfo = Arrays.stream(taskLine.get().split(", "))
                .map(task -> {
                    Matcher m = Pattern.compile("task \'(.*)\'").matcher(task);
                    checkState(m.matches());
                    return m.group(1);
                })
                .map(GradleOutputTaskSubject.TaskInfo::new)
                .collect(Collectors.toList());
    }

    @Override
    public GradleOutputTaskSubject getSubject(
            @NonNull FailureStrategy failureStrategy,
            @NonNull String subject) {
        return new GradleOutputTaskSubject(failureStrategy, subject, taskInfo);
    }
}
