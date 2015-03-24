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

package com.android.build.gradle.internal.profile

import com.android.builder.profile.ExecutionRecord
import com.android.builder.profile.ExecutionType
import com.android.builder.profile.Recorder
import com.android.builder.profile.ThreadRecorder
import com.google.common.base.CaseFormat
import org.gradle.api.Task
import org.gradle.api.execution.TaskExecutionListener
import org.gradle.api.tasks.TaskState

import java.util.concurrent.atomic.AtomicLong

/**
 * Implementation of the {@link TaskExecutionListener} that records the execution span of
 * tasks execution and records such spans using the {@link ThreadRecorder} facilities.
 */
class RecordingBuildListener implements TaskExecutionListener {

    final AtomicLong currentRecordID = new AtomicLong();
    final AtomicLong startTime = new AtomicLong();

    @Override
    void beforeExecute(Task task) {
        currentRecordID.set(ThreadRecorder.get().allocationRecordId());
        startTime.set(System.currentTimeMillis());
    }

    @Override
    void afterExecute(Task task, TaskState taskState) {

        // find the right ExecutionType.
        String taskImpl = task.getClass().getSimpleName();
        if (taskImpl.endsWith("_Decorated")) {
            taskImpl = taskImpl.substring(0, taskImpl.length() - "_Decorated".length());
        }
        String potentialExecutionTypeName = "TASK_" +
                CaseFormat.LOWER_CAMEL.converterTo(CaseFormat.UPPER_UNDERSCORE).
                        convert(taskImpl);
        ExecutionType executiontype;
        try {
            executiontype = ExecutionType.valueOf(potentialExecutionTypeName)
        } catch (IllegalArgumentException e) {
            executiontype = ExecutionType.GENERIC_TASK_EXECUTION
        }

        List< Recorder.Property> properties = new ArrayList<>();
        properties.add(new Recorder.Property("project", task.getProject().getName()));
        properties.add(new Recorder.Property("task", task.getName()));
        ThreadRecorder.get().closeRecord(new ExecutionRecord(
                currentRecordID.get(),
                0 /* parentId */,
                startTime.get(),
                System.currentTimeMillis() - startTime.get(),
                executiontype,
                properties))
    }
}
