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

package com.android.build.gradle.internal.scope;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.TaskFactory;

import org.gradle.api.Action;
import org.gradle.api.Task;

import java.util.ArrayList;
import java.util.List;

/**
 * Handle for a {@link Task} that may not yet been created.
 * For tasks created using CollectionBuilder<Task>, they are usually not actually created until
 * Gradle decides those tasks needs to be executed.  This class contains information about those
 * tasks and allow dependencies to be specified.
 */
public class AndroidTask<T extends Task> {
    @NonNull
    private String name;
    @NonNull
    private final Class<T> taskType;
    @Nullable
    private T task;
    @NonNull
    private final List<AndroidTask<? extends Task>> upstreamTasks;
    @NonNull
    private final List<AndroidTask<? extends Task>> downstreamTasks;

    public AndroidTask(@NonNull String name, @NonNull Class<T> taskType) {
        this.name = name;
        this.taskType = taskType;
        upstreamTasks = new ArrayList<AndroidTask<? extends Task>>();
        downstreamTasks = new ArrayList<AndroidTask<? extends Task>>();
    }

    /**
     * The name of Task this represents.
     */
    @NonNull
    public String getName() {
        return name;
    }

    /**
     * The type of Task this represents.
     */
    @NonNull
    public Class<T> getTaskType() {
        return taskType;
    }

    /**
     * Return all the AndroidTask this depends on.
     */
    @NonNull
    public List<AndroidTask<? extends Task>> getUpstreamTasks() {
        return upstreamTasks;
    }

    /**
     * Return all the AndroidTask that depends on this.
     */
    @NonNull
    public List<AndroidTask<? extends Task>> getDownstreamTasks() {
        return downstreamTasks;
    }

    /**
     * Add dependency on another AndroidTask.
     * @param taskFactory TaskFactory used to configure the task for dependencies.
     * @param other The task that this depends on.
     */
    public void dependsOn(final TaskFactory taskFactory, final AndroidTask<? extends Task> other) {
        taskFactory.named(name, new Action<Task>() {
            @Override
            public void execute(Task task) {
                task.dependsOn(other.name);
            }
        });
        upstreamTasks.add(other);
        other.addDependent(this);
    }

    /**
     * Add dependency on another object.
     * This method adds dependencies on any objects accepted by {@link Task#dependsOn} and is
     * needed for compatibility until all tasks are trasitioned to AndroidTask.
     * @param taskFactory TaskFactory used to configure the task for dependencies.
     * @param other Objects accepted by {@link Task#dependsOn}.
     */
    public void dependsOn(final TaskFactory taskFactory, final Object... other) {
        taskFactory.named(name, new Action<Task>() {
            @Override
            public void execute(Task task) {
                task.dependsOn(other);
            }
        });
    }

    private void addDependent(AndroidTask<? extends Task> tAndroidTask) {
        downstreamTasks.add(tAndroidTask);
    }

    /**
     * Add a configration action for this task.
     * @param taskFactory TaskFactory used to configure the task.
     * @param configAction An Action to be executed.
     */
    public void configure(TaskFactory taskFactory, Action<? super Task> configAction) {
        taskFactory.named(name, configAction);
    }
}
