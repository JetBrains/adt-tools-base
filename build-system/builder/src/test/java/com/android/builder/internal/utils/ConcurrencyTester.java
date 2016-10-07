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
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Queues;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Assert;

/**
 * Utility class to test concurrency.
 *
 * <p>This class is used to check whether or not a set of runnables violate a concurrency contract
 * (e.g., whether they run concurrently when they are not allowed to, or vice versa).
 *
 * <p>The following is a usage scenario of this class. Suppose we have a method that accepts an
 * "action" (of type {@link Runnable} or the like) as an argument (e.g., {@code methodUnderTest(...,
 * actionUnderTest)}) and at some point during the method's execution, the method will invoke the
 * action exactly once. When several threads are concurrently calling the method (possibly with
 * different parameter values), the method may make a contract that all the threads can execute the
 * corresponding actions concurrently, or it may make a contract that all the threads cannot execute
 * the actions concurrently. To check whether the method meets the concurrency contract, we can
 * write a test as follows.
 *
 * <pre>{@code
 * ConcurrencyTester tester = new ConcurrencyTester();
 * for (...) {
 *     IOExceptionFunction actionUnderTest = (input) -> { ... };
 *     tester.addMethodInvocationFromNewThread(
 *             (IOExceptionFunction anActionUnderTest) -> {
 *                 // ConcurrencyTester requires using anActionUnderTest instead of actionUnderTest
 *                 // when calling methodUnderTest
 *                 methodUnderTest(..., anActionUnderTest);
 *             },
 *             actionUnderTest);
 * }
 * }</pre>
 *
 * <p>Then, if the actions are allowed to run concurrently, we can make the following assertion:
 *
 * <pre>{@code
 * tester.assertThatActionsCanRunConcurrently();
 * }</pre>
 *
 * <p>If the actions are not allowed to run concurrently, we can make the following assertion:
 *
 * <pre>{@code
 * tester.assertThatActionsCannotRunConcurrently();
 * }</pre>
 */
public final class ConcurrencyTester<F, T> {

    /** The running pattern of a set of actions. */
    private enum RunningPattern {

        /** All actions run concurrently. */
        CONCURRENT,

        /** All actions run sequentially. */
        SEQUENTIAL,

        /** More than one but not all actions run concurrently. */
        MIXED
    }

    @NonNull
    private List<IOExceptionConsumer<IOExceptionFunction<F, T>>> mMethodInvocationList =
            Lists.newLinkedList();

    @NonNull private List<IOExceptionFunction<F, T>> mActionUnderTestList = Lists.newLinkedList();

    /**
     * Adds a new invocation of the method under test to this {@link ConcurrencyTester} instance.
     * The {@code ConcurrencyTester} will execute each invocation in a separate thread and check
     * whether the corresponding actions under test meet the concurrency requirement.
     *
     * @param methodInvocation the method invocation to be executed from a new thread
     * @param actionUnderTest the action for concurrency checks
     */
    public void addMethodInvocationFromNewThread(
            @NonNull IOExceptionConsumer<IOExceptionFunction<F, T>> methodInvocation,
            @NonNull IOExceptionFunction<F, T> actionUnderTest) {
        mMethodInvocationList.add(methodInvocation);
        mActionUnderTestList.add(actionUnderTest);
    }

    /**
     * Executes the method under test in separate threads and returns {@code true} if all the
     * actions ran concurrently, and {@code false} otherwise. Note that a {@code false} returned
     * value means that either the actions were not allowed to run concurrently (which violates the
     * concurrency requirement) or the actions took too long to start and accidentally ran
     * sequentially (although the latter case is possible, the implementation of this method makes
     * sure that it is unlikely to happen).
     */
    public void assertThatActionsCanRunConcurrently() {
        Preconditions.checkArgument(
                mMethodInvocationList.size() >= 2,
                "There must be at least 2 actions for concurrency checks.");
        Assert.assertTrue(
                "Two or more actions ran sequentially"
                        + " even though all the actions were expected to run concurrently.",
                executeActionsAndGetRunningPattern() == RunningPattern.CONCURRENT);
    }

    /**
     * Executes the method under test in separate threads and returns {@code true} if all the
     * actions ran sequentially, and {@code false} otherwise. Note that a {@code true} returned
     * value means that either the actions were not allowed to run concurrently (which meets the
     * concurrency requirement) or the actions took too long to start and accidentally ran
     * sequentially (although the latter case is possible, the implementation of this method makes
     * sure that it is unlikely to happen).
     */
    public void assertThatActionsCannotRunConcurrently() {
        Preconditions.checkArgument(
                mMethodInvocationList.size() >= 2,
                "There must be at least 2 actions for concurrency checks.");
        Assert.assertTrue(
                "Two or more actions ran concurrently"
                        + " even though all the actions were expected to run sequentially.",
                executeActionsAndGetRunningPattern() == RunningPattern.SEQUENTIAL);
    }

    /**
     * Executes the method under test in separate threads and returns {@code true} if one and only
     * one of the actions was executed, and {@code false} otherwise.
     */
    public void assertThatOnlyOneActionIsExecuted() {
        Preconditions.checkArgument(
                mMethodInvocationList.size() >= 2,
                "There must be at least 2 actions for concurrency checks.");

        AtomicInteger executedActions = new AtomicInteger(0);

        List<IOExceptionRunnable> runnables = Lists.newLinkedList();
        for (int i = 0; i < mMethodInvocationList.size(); i++) {
            IOExceptionConsumer<IOExceptionFunction<F, T>> methodInvocation =
                    mMethodInvocationList.get(i);
            IOExceptionFunction<F, T> actionUnderTest = mActionUnderTestList.get(i);
            runnables.add(() -> {
                methodInvocation.accept(
                        (input) -> {
                            executedActions.getAndIncrement();
                            return actionUnderTest.apply(input);
                        });
            });
        }

        Map<Thread, Optional<Throwable>> threads = executeRunnablesInThreads(runnables);
        waitForThreadsToFinish(threads);

        Assert.assertTrue(
                "Either no action or more than one action were executed"
                        + " even though only one action was expected to run.",
                executedActions.get() == 1);
    }

    /**
     * Executes the method under test in separate threads and returns the running pattern of their
     * actions.
     *
     * @return the running pattern of the actions
     */
    private RunningPattern executeActionsAndGetRunningPattern() {
        // We use blocking queue and count-down latches for the actions to communicate with the main
        // thread. When an action starts, it notifies the main thread that it has started and
        // continues immediately. When an action is going to finish, it creates a CountDownLatch,
        // sends it to the main thread, and waits for the main thread to allow it to finish.
        BlockingQueue<Thread> startedActionQueue = Queues.newLinkedBlockingQueue();
        BlockingQueue<CountDownLatch> finishRequestQueue = Queues.newLinkedBlockingQueue();

        Runnable actionStartedHandler = () -> {
            startedActionQueue.add(Thread.currentThread());
        };

        Runnable actionFinishedHandler = () -> {
            CountDownLatch finishRequest = new CountDownLatch(1);
            finishRequestQueue.add(finishRequest);
            try {
                finishRequest.await();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        };

        // Attach the event handlers to the actions
        List<IOExceptionRunnable> runnables = Lists.newLinkedList();
        for (int i = 0; i < mMethodInvocationList.size(); i++) {
            IOExceptionConsumer<IOExceptionFunction<F, T>> methodInvocation =
                    mMethodInvocationList.get(i);
            IOExceptionFunction<F, T> actionUnderTest = mActionUnderTestList.get(i);

            IOExceptionFunction<F, T> instrumentedActionUnderTest = (input) -> {
                actionStartedHandler.run();
                try {
                    return actionUnderTest.apply(input);
                } finally {
                    actionFinishedHandler.run();
                }
            };

            runnables.add(() -> {
                methodInvocation.accept(instrumentedActionUnderTest);
            });
        }

        // Execute each invocation in a separate thread
        Map<Thread, Optional<Throwable>> threads = executeRunnablesInThreads(runnables);

        int remainingActions = runnables.size();
        LinkedList<CountDownLatch> finishRequests = Lists.newLinkedList();
        int maxConcurrentActions = 0;

        // To prevent the actions from *accidentally* running sequentially, when an action is going
        // to finish, we don't let it finish immediately but try waiting for the next action to
        // start. The following loop aims to "force" the actions to run concurrently. If it
        // succeeds, it means that the actions are allowed to run concurrently. If it doesn't
        // succeed, it means that either the actions are not allowed to run concurrently, or the
        // actions take too long to start.
        while (remainingActions > 0) {
            Thread startedAction;
            try {
                // Wait for a new action to start with a timeout
                startedAction = startedActionQueue.poll(1000, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            // If a new action has started, let it run, and before it is going to finish, keep
            // waiting for more actions to start (repeat the loop)
            if (startedAction != null) {
                remainingActions--;
                CountDownLatch finishRequest;
                try {
                    finishRequest = finishRequestQueue.poll(2000, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                if (finishRequest != null) {
                    finishRequests.add(finishRequest);
                    if (finishRequests.size() > maxConcurrentActions) {
                        maxConcurrentActions = finishRequests.size();
                    }
                } else {
                    throw new RuntimeException("Actions are taking too long to run");
                }
            } else {
                // If no other action has started and there are one or more finishing actions, it
                // could be either because the finishing actions are blocking a new action to start
                // (i.e., the actions are not allowed to run concurrently), or because the actions
                // are taking too long to start. Since we cannot distinguish these two cases, we let
                // all the finishing actions finish and repeat the loop.
                if (!finishRequests.isEmpty()) {
                    while (finishRequests.size() > 0) {
                        finishRequests.removeFirst().countDown();
                    }
                } else {
                    // If no other action has started and there are no finishing actions, it means
                    // that the actions are taking too long to start.
                    throw new RuntimeException("Actions are taking too long to start");
                }
            }
        }

        // Let all the finishing actions finish
        while (finishRequests.size() > 0) {
            finishRequests.removeFirst().countDown();
        }

        // Wait for the threads to finish
        waitForThreadsToFinish(threads);

        // Determine the running pattern based on maxConcurrentActions
        assert maxConcurrentActions >= 1 && maxConcurrentActions <= runnables.size();
        if (maxConcurrentActions == 1) {
            return RunningPattern.SEQUENTIAL;
        } else if (maxConcurrentActions == runnables.size()) {
            return RunningPattern.CONCURRENT;
        } else {
            return RunningPattern.MIXED;
        }
    }

    /**
     * Executes the runnables in separate threads and returns the threads together with any
     * exceptions that were thrown during the execution of the threads.
     *
     * @param runnables the runnables to be executed
     * @return map from a thread to a {@code Throwable} (if any)
     */
    private Map<Thread, Optional<Throwable>> executeRunnablesInThreads(
            @NonNull List<IOExceptionRunnable> runnables) {
        Map<Thread, Optional<Throwable>> threads = Maps.newConcurrentMap();
        for (IOExceptionRunnable runnable : runnables) {
            Thread thread =
                    new Thread(() -> {
                        try {
                            runnable.run();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
            thread.setDaemon(true);

            threads.put(thread, Optional.empty());
            thread.setUncaughtExceptionHandler(
                    (aThread, throwable) -> {
                        threads.put(aThread, Optional.of(throwable));
                    });
        }
        for (Thread thread : threads.keySet()) {
            thread.start();
        }
        return threads;
    }

    /**
     * Waits for all the threads to finish.
     *
     * @throws RuntimeException if a timeout or interrupt occurs while waiting for the threads to
     *     finish or an exception was thrown during the execution of the threads
     */
    private void waitForThreadsToFinish(@NonNull Map<Thread, Optional<Throwable>> threads) {
        // Wait for the threads to finish
        for (Thread thread : threads.keySet()) {
            try {
                thread.join(2000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        // If a thread is still running, throw an exception
        for (Thread thread : threads.keySet()) {
            if (thread.isAlive()) {
                throw new RuntimeException("Actions are taking too long to finish");
            }
        }

        // If the threads have all terminated, throw any exceptions that occurred during the
        // execution of the threads
        for (Optional<Throwable> throwable : threads.values()) {
            if (throwable.isPresent()) {
                throw new RuntimeException(throwable.get());
            }
        }
    }
}
