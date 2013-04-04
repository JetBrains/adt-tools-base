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
 * After executing all tasks, it is possible to wait on them with {@link #waitForTasks()}.
 */
public class WaitableExecutor<T> {

    private final CompletionService<T> mCompletionService;
    private int mCount = 0;

    public WaitableExecutor() {
        mCompletionService = new ExecutorCompletionService<T>(Executors.newCachedThreadPool());
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
     * Waits for all tasks to be executed. If a tasks through an exception, it will be thrown here.
     *
     * @return a list of all the return values from the tasks.
     *
     * @throws InterruptedException
     * @throws ExecutionException
     */
    public List<T> waitForTasks() throws InterruptedException, ExecutionException {
        List<T> results = Lists.newArrayListWithCapacity(mCount);
        for (int i = 0 ; i < mCount ; i++) {
            Future<T> result = mCompletionService.take();
            results.add(result.get());
        }

        return results;
    }
}
