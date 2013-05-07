/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.ide.common.internal;

import com.google.common.collect.Lists;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * A utility wrapper around a {@link CompletionService} using an ThreadPoolExecutor so that it
 * is possible to wait on all the tasks.
 *
 * Tasks are submitted as {@link Callable} with {@link #execute(java.util.concurrent.Callable)}.
 *
 * After executing all tasks, it is possible to wait on them with
 * {@link #waitForTasksWithQuickFail()}, or {@link #waitForAllTasks()}.
 *
 * This class is not Thread safe!
 */
public class WaitableExecutor<T> {

    private final CompletionService<T> mCompletionService;

    private int mCount = 0;

    public WaitableExecutor(int nThreads) {
        if (nThreads < 1) {
            mCompletionService = new ExecutorCompletionService<T>(
                    Executors.newCachedThreadPool());
        } else {
            mCompletionService = new ExecutorCompletionService<T>(
                    Executors.newFixedThreadPool(nThreads));
        }
    }

    public WaitableExecutor() {
        this(0);
    }

    /**
     * Submits a Callable for execution.
     *
     * @param runnable the callable to run.
     */
    public void execute(Callable<T> runnable) {
        mCompletionService.submit(runnable);
        mCount++;
    }

    /**
     * Waits for all tasks to be executed. If a tasks throws an exception, it will be thrown from
     * this method inside the ExecutionException, preventing access to the result of the other
     * threads.
     *
     * If you want to get the results of all tasks (result and/or exception), use
     * {@link #waitForAllTasks()}
     *
     * @return a list of all the return values from the tasks.
     *
     * @throws InterruptedException if this thread was interrupted. Not if the tasks were interrupted.
     * @throws ExecutionException if a task fails with an interruption. The cause if the original exception.
     */
    public List<T> waitForTasksWithQuickFail() throws InterruptedException, ExecutionException {
        List<T> results = Lists.newArrayListWithCapacity(mCount);
        for (int i = 0 ; i < mCount ; i++) {
            Future<T> result = mCompletionService.take();
            // Get the result from the task. If the task threw an exception just throw it too.
            results.add(result.get());
        }

        return results;
    }

    public static final class TaskResult<T> {
        public T value;
        public Throwable exception;

        static <T> TaskResult<T> withValue(T value) {
            TaskResult<T> result = new TaskResult<T>(null);
            result.value = value;
            return result;
        }

        TaskResult(Throwable cause) {
            exception = cause;
        }
    }

    /**
     * Waits for all tasks to be executed, and returns a {@link TaskResult} for each, containing
     * either the result or the exception thrown by the task.
     *
     * If a task is cancelled (and it threw InterruptedException) then the result for the task
     * is *not* included.
     *
     * @return a list of all the return values from the tasks.
     *
     * @throws InterruptedException if this thread was interrupted. Not if the tasks were interrupted.
     */
    public List<TaskResult<T>> waitForAllTasks() throws InterruptedException {
        List<TaskResult<T>> results = Lists.newArrayListWithCapacity(mCount);
        for (int i = 0 ; i < mCount ; i++) {
            Future<T> task = mCompletionService.take();
            // Get the result from the task.
            try {
                results.add(TaskResult.withValue(task.get()));
            } catch (ExecutionException e) {
                // the original exception thrown by the task is the cause of this one.
                Throwable cause = e.getCause();

                //noinspection StatementWithEmptyBody
                if (cause instanceof InterruptedException) {
                    // if the task was cancelled we probably don't care about its result.
                } else {
                    // there was an error.
                    results.add(new TaskResult<T>(cause));
                }
            }
        }

        return results;
    }

    /**
     * Cancel all remaining tasks.
     */
    public void cancelAllTasks() {
        while (true) {
            Future<T> task = mCompletionService.poll();
            if (task != null) {
                task.cancel(true /*mayInterruptIfRunning*/);
            } else {
                break;
            }
        }
    }
}
