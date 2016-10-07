
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

package com.android.build.gradle.internal.profile;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.tasks.DefaultAndroidTask;
import com.android.builder.profile.Recorder;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CaseFormat;
import com.google.wireless.android.sdk.stats.AndroidStudioStats;
import com.google.wireless.android.sdk.stats.AndroidStudioStats.GradleBuildProfileSpan;
import com.google.wireless.android.sdk.stats.AndroidStudioStats.GradleBuildProfileSpan.ExecutionType;

import org.gradle.api.Task;
import org.gradle.api.execution.TaskExecutionListener;
import org.gradle.api.tasks.TaskState;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of the {@link TaskExecutionListener} that records the execution span of
 * tasks execution and records such spans using the {@link Recorder} facilities.
 */
public class RecordingBuildListener implements TaskExecutionListener {

    @NonNull
    private final Recorder mRecorder;
    // map of outstanding tasks executing, keyed by their name.
    @NonNull
    private final Map<String, GradleBuildProfileSpan.Builder> mTaskRecords =
            new ConcurrentHashMap<>();

    RecordingBuildListener(@NonNull Recorder recorder) {
        mRecorder = recorder;
    }

    @Override
    public void beforeExecute(@NonNull Task task) {
        GradleBuildProfileSpan.Builder builder = GradleBuildProfileSpan.newBuilder();
        builder.setType(ExecutionType.TASK_EXECUTION);
        builder.setId(mRecorder.allocationRecordId());
        builder.setStartTimeInMs(System.currentTimeMillis());

        mTaskRecords.put(task.getName(), builder);
    }

    @Override
    public void afterExecute(@NonNull Task task, @NonNull TaskState taskState) {
        GradleBuildProfileSpan.Builder record = mTaskRecords.get(task.getName());

        record.setDurationInMs(System.currentTimeMillis() - record.getStartTimeInMs());

        //noinspection ThrowableResultOfMethodCallIgnored Just logging the failure.
        record.setTask(
                AndroidStudioStats.GradleTaskExecution.newBuilder()
                        .setType(getExecutionType(task.getClass()))
                        .setDidWork(taskState.getDidWork())
                        .setSkipped(taskState.getSkipped())
                        .setUpToDate(taskState.getUpToDate())
                        .setFailed(taskState.getFailure() != null));

        mRecorder.closeRecord(task.getProject().getPath(), getVariantName(task), record);
    }

    @VisibleForTesting
    @NonNull
    static AndroidStudioStats.GradleTaskExecution.Type getExecutionType(@NonNull Class<?> taskClass) {
        // find the right ExecutionType.
        String taskImpl = taskClass.getSimpleName();
        if (taskImpl.endsWith("_Decorated")) {
            taskImpl = taskImpl.substring(0, taskImpl.length() - "_Decorated".length());
        }
        String potentialExecutionTypeName =
                CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_UNDERSCORE, taskImpl);
        try {
            return AndroidStudioStats.GradleTaskExecution.Type.valueOf(potentialExecutionTypeName);
        } catch (IllegalArgumentException ignored) {
            return AndroidStudioStats.GradleTaskExecution.Type.UNKNOWN_TASK_TYPE;
        }
    }

    @Nullable
    private static String getVariantName(@NonNull Task task) {
        if (!(task instanceof DefaultAndroidTask)) {
            return null;
        }
        String variantName = ((DefaultAndroidTask) task).getVariantName();
        if (variantName == null) {
            throw new IllegalStateException("Task with type " + task.getClass().getName() +
                    " does not include a variantName");
        }
        if (variantName.isEmpty()) {
            return null;
        }
        return variantName;
    }
}
