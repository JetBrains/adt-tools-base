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

package com.android.build.gradle.profiling

import groovy.json.JsonBuilder
import groovy.transform.CompileStatic
import org.gradle.BuildListener
import org.gradle.BuildResult
import org.gradle.api.Project
import org.gradle.api.ProjectEvaluationListener
import org.gradle.api.ProjectState
import org.gradle.api.Task
import org.gradle.api.artifacts.DependencyResolutionListener
import org.gradle.api.artifacts.ResolvableDependencies
import org.gradle.api.execution.TaskExecutionGraph
import org.gradle.api.execution.TaskExecutionGraphListener
import org.gradle.api.execution.TaskExecutionListener
import org.gradle.api.initialization.Settings
import org.gradle.api.invocation.Gradle
import org.gradle.api.tasks.TaskState
import org.gradle.initialization.BuildCompletionListener

/**
 * A simple build profiler that listens to the Gradle build and outputs Chrome trace data.
 *
 * <p>Here's an example of how to enable it from a Gradle init script (-I):
 * <pre><code>
 * import com.android.build.gradle.profiling.ProfilingBuildListener
 * gradle.addListener(new ProfilingBuildListener("build.trace"))
 * </code></pre>
 *
 * <p>See <a href="https://docs.google.com/document/d/1CvAClvFfyA5R-PhYUmn5OOQtYMH4h6I0nSsKchNAySU/edit">
 *     Chrome trace event format<a>
 */
@CompileStatic
public class ProfilingBuildListener implements BuildListener, TaskExecutionListener,
        TaskExecutionGraphListener, ProjectEvaluationListener, DependencyResolutionListener,
        BuildCompletionListener {

    PrintWriter traceWriter;

    public ProfilingBuildListener(String traceFile) {
        traceWriter = new PrintWriter(new FileOutputStream(new File(traceFile)));
        traceWriter.append('[')
    }

    @Override
    void buildStarted(Gradle gradle) {
    }

    @Override
    void settingsEvaluated(Settings settings) {
    }

    @Override
    void projectsLoaded(Gradle gradle) {
    }

    @Override
    void projectsEvaluated(Gradle gradle) {
    }

    @Override
    void buildFinished(BuildResult buildResult) {
    }

    @Override
    void graphPopulated(TaskExecutionGraph taskExecutionGraph) {
    }

    @Override
    void beforeExecute(Task task) {
        long now = System.currentTimeMillis();
        task.convention.add("__started", now);

        writeStart(task.name)
    }

    @Override
    void afterExecute(Task task, TaskState taskState) {
        long start = (long) task.convention.getByName("__started")
        long now = System.currentTimeMillis()

        def args = [
                project: task.project.name,
                description: task.description,
                didWork: taskState.didWork,
                failure: taskState.getFailure(),
        ]
        writeEnd(task.name, now - start, args)
    }

    @Override
    void completed() {
        def builder = new JsonBuilder([
                cat: 'gradle',
                name: 'DONE',
                pid: 0,
                tid: Thread.currentThread().name,
                ts: System.currentTimeMillis() * 1000,
                ph: 'I'
        ])
        writeOut(builder)
        traceWriter.append(']')
        traceWriter.close()
    }

    @Override
    void beforeResolve(ResolvableDependencies resolvableDependencies) {
        writeStart(resolvableDependencies.name)
    }

    @Override
    void afterResolve(ResolvableDependencies resolvableDependencies) {
        def args = [
                path: resolvableDependencies.path,
                description: 'Resolving ' + resolvableDependencies.name,
                resultSize: resolvableDependencies.resolutionResult.allDependencies.size(),
        ]

        writeEnd(resolvableDependencies.name, 0, args)
    }

    @Override
    void beforeEvaluate(Project project) {
        long now = System.currentTimeMillis();
        project.convention.add("__started", now);

        writeStart(project.name)
    }

    @Override
    void afterEvaluate(Project project, ProjectState projectState) {
        long start = (long) project.convention.getByName("__started")
        long now = System.currentTimeMillis()

        def args = [
                project: project.path,
                description: project.description,
                executed: projectState.executed,
                failure: projectState.failure,
        ]
        writeEnd(project.name, now - start, args)
    }

    /**
     * Writes the start of an event with the current timestamp and thread.
     */
    public void writeStart(String eventName) {
        long now = System.currentTimeMillis();
        def builder = new JsonBuilder([
                cat: 'gradle',
                name: eventName,
                pid: 0,
                tid: Thread.currentThread().name,
                ts: now * 1000,
                ph: 'B'
        ])
        writeOut(builder)
    }

    /**
     * Writes the end of an event with the current timestamp, adding total duration (wall-clock time)
     * and a user-specified property map.
     */
    public void writeEnd(String eventName, long duration, Object properties) {
        long now = System.currentTimeMillis();
        def builder = new JsonBuilder([
                cat: 'gradle',
                name: eventName,
                pid: 0,
                tid: Thread.currentThread().name,
                ts: now * 1000,
                ph: 'E',
                dur: duration,
                args: properties
        ])
        writeOut(builder)
    }

    private synchronized void writeOut(JsonBuilder builder) {
        builder.writeTo(traceWriter)
        traceWriter.append(',')
    }
}
