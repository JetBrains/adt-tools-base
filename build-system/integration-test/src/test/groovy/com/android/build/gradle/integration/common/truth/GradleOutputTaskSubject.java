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

import com.android.annotations.NonNull;
import com.google.common.truth.FailureStrategy;
import com.google.common.truth.Subject;

import java.util.List;

/**
 * Truth subject to verify execution of a Gradle task base on the stdout produced by Gradle.
 */
public class GradleOutputTaskSubject extends Subject<GradleOutputTaskSubject, String> {

    static class TaskInfo {
        // TODO: add other information about a task (e.g. whether a task is UP-TO-DATE) here.
        private final String taskName;

        public TaskInfo(String taskName) {
            this.taskName = taskName;
        }

        public String getTaskName() {
            return taskName;
        }
    }

    @NonNull
    private final List<TaskInfo> tasks;

    public GradleOutputTaskSubject(
            @NonNull FailureStrategy failureStrategy,
            @NonNull String taskName,
            @NonNull List<TaskInfo> taskInfo) {
        super(failureStrategy, taskName);
        this.tasks = taskInfo;
    }

    public void wasExecuted() {
        getTaskIndex(getSubject(), getDisplaySubject());
    }

    public void ranBefore(String task) {
        if (getTaskIndex(getSubject(), getDisplaySubject()) >= getTaskIndex(task)) {
            fail("was executed before", task);
        }
    }

    public void ranAfter(String task) {
        if (getTaskIndex(getSubject(), getDisplaySubject()) <= getTaskIndex(task)) {
            fail("was executed after", task);
        }
    }

    private int getTaskIndex(String taskName) {
        return getTaskIndex(taskName, "<" + taskName + ">");
    }

    private int getTaskIndex(String taskName, String displayName) {
        int index = findTaskIndex(taskName);
        if (index == -1) {
            failWithRawMessage("Not true that %s was executed", displayName);
        }
        return index;
    }

    private int findTaskIndex(String taskName) {
        int index = -1;
        for (int i = 0; i < tasks.size(); i++) {
            if (tasks.get(i).getTaskName().equals(taskName)) {
                index = i;
                break;
            }
        }
        return index;
    }
}
