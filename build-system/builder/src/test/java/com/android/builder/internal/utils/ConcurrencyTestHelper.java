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

package com.android.builder.internal.utils;

import com.android.annotations.NonNull;
import com.google.common.collect.Lists;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Utility class to test concurrency.
 *
 * <p>In some cases, runnables are allowed to run concurrently, and in other cases, they are not
 * allowed to run concurrently. This class is used to check whether a set of runnables violate this
 * requirement.
 *
 * <p>The following is a usage scenario of this class. Suppose Foo is a class that implements the
 * Runnable interface. It also allows external listeners to capture the events when Foo.run() has
 * started executing and when it is going to finish (by providing methods
 * Foo.addExecutedListener(executedListener) and foo.addFinishingListeners(finishingListener), and
 * calling these listeners at the beginning and end of method Foo.run().
 *
 * <p>When the runnables are not allowed to run concurrently, we can have a test as follows.
 * <pre>{@code
 * ConcurrencyTestHelper helper = new ConcurrencyTestHelper(false); // canRunConcurrently = false
 * List<Thread> threads = Lists.newLinkedList();
 * List<Foo> fooRunnables = ... // List of runnables where concurrent execution is not allowed
 * for (Foo foo : fooRunnables) {
 *     foo.addExecutedListener(() -> {
 *         helper.threadExecuted();
 *     });
 *     foo.addFinishingListener(() -> {
 *         helper.waitToBeAllowedToFinish();
 *     });
 *     threads.add(new Thread(foo));
 * }
 *
 * helper.startThreads(threads);
 * helper.waitForThreadsToExecute();
 *
 * // Make sure that only one thread has executed so far
 * assertEquals(1, helper.getExecutedThreadCount());
 *
 * helper.allowThreadsToFinish();
 * helper.waitForThreadsToFinish();
 * }</pre>
 *
 * <p>When the runnables are allowed to run concurrently, we can have a test as follows.
 * <pre>{@code
 * ConcurrencyTestHelper helper = new ConcurrencyTestHelper(true); // canRunConcurrently = true
 * List<Thread> threads = Lists.newLinkedList();
 * List<Foo> fooRunnables = ... // List of runnables where concurrent execution is allowed
 * for (Foo foo : fooRunnables) {
 *     foo.addExecutedListener(() -> {
 *         helper.threadExecuted();
 *     });
 *     foo.addFinishingListener(() -> {
 *         helper.waitToBeAllowedToFinish();
 *     });
 *     threads.add(new Thread(foo));
 * }
 *
 * helper.startThreads(threads);
 * helper.waitForThreadsToExecute();
 *
 * // Make sure that more than one thread have executed so far
 * assertTrue(helper.getExecutedThreadCount() > 1);
 *
 * helper.allowThreadsToFinish();
 * helper.waitForThreadsToFinish();
 * }</pre>
 */
public class ConcurrencyTestHelper {

    private boolean mCanRunConcurrently;

    @NonNull private List<Thread> mThreads;

    @NonNull private AtomicInteger mExecutedThreadCount;

    @NonNull private CountDownLatch mThreadExecutedLatch;

    @NonNull private CountDownLatch mAllowThreadsToFinishLatch;

    /**
     * Creates a {@code ConcurrencyTestHelper} instance.
     *
     * @param canRunConcurrently set to {@code true} if the threads are allowed to run concurrently,
     *     {@code false} otherwise
     */
    public ConcurrencyTestHelper(boolean canRunConcurrently) {
        this.mCanRunConcurrently = canRunConcurrently;
    }

    /** Starts all the threads. */
    public void startThreads(@NonNull List<Thread> threads) {
        mThreads = Lists.newLinkedList(threads);
        mExecutedThreadCount = new AtomicInteger(0);
        mThreadExecutedLatch = new CountDownLatch(mCanRunConcurrently ? mThreads.size() : 1);
        mAllowThreadsToFinishLatch = new CountDownLatch(1);
        for (Thread thread : mThreads) {
            thread.start();
        }
    }

    /** Notifies ConcurrencyTestHelper that the current thread has executed. */
    public void threadExecuted() {
        mExecutedThreadCount.incrementAndGet();
        mThreadExecutedLatch.countDown();
    }

    /** Waits for ConcurrencyTestHelper to allow the current thread to finish. */
    public void waitToBeAllowedToFinish() {
        try {
            mAllowThreadsToFinishLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Waits for one or more threads to execute. If the threads are allowed to run concurrently,
     * this method will wait for all the threads to execute; otherwise, it will wait for at least
     * one thread to execute.
     */
    public void waitForThreadsToExecute() {
        if (mCanRunConcurrently) {
            // Wait for all the threads to execute, or return after a timeout. (Note that we
            // should give enough timeout such that when it happens, it is not because the
            // threads were not allocated time to run, but because they are not able to run in
            // parallel, which we will later detect as a test failure.)
            try {
                mThreadExecutedLatch.await(2 * mThreads.size(), TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        } else {
            // Wait for at least one thread to execute
            try {
                mThreadExecutedLatch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /** Returns the number of executed threads so far. */
    public int getExecutedThreadCount() {
        return mExecutedThreadCount.get();
    }

    /** Allows all the threads to finish. */
    public void allowThreadsToFinish() {
        mAllowThreadsToFinishLatch.countDown();
    }

    /** Waits for all the threads to finish. */
    public void waitForThreadsToFinish() {
        for (Thread thread : mThreads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
