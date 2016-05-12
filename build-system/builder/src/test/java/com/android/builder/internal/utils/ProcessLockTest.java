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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test cases for {@link ProcessLock}.
 */
public class ProcessLockTest {

    @Test
    public void testMultiThreadsSameKey() {
        int threadCount = 5;
        String[] keys = {"foo", "foo", "foo", "foo", "foobar".substring(0, 3)};

        for (boolean withInterProcessLocking : new boolean[]{true, false}) {
            CountDownLatch threadFinishingLatch = new CountDownLatch(1);
            CountDownLatch threadFinishedLatch = new CountDownLatch(1);
            AtomicInteger executedThreadCount = new AtomicInteger(0);

            IOExceptionRunnable[] runnables = new IOExceptionRunnable[threadCount];
            for (int i = 0; i < threadCount; i++) {
                final int ii = i;
                runnables[i] = () -> {
                    ProcessLock.doLocked(
                            keys[ii],
                            () -> {
                                executedThreadCount.incrementAndGet();
                                // Notify the main thread that it is about to finish
                                threadFinishingLatch.countDown();
                                // Wait for the main thread to allow it to finish
                                try {
                                    threadFinishedLatch.await();
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                            },
                            withInterProcessLocking);
                };
            }

            // Execute the runnables
            for (IOExceptionRunnable runnable : runnables) {
                new Thread(() -> {
                    try {
                        runnable.run();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }).start();
            }

            // Wait for notification from one of the threads that it is about to finish
            try {
                threadFinishingLatch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // Make sure that only one thread has executed so far. (Because we use the same key, the
            // threads are not allowed to run in parallel.)
            assertEquals(1, executedThreadCount.get());

            // Allow all the threads to finish
            threadFinishedLatch.countDown();
        }
    }

    @Test
    public void testMultiThreadsDifferentKeys() {
        int threadCount = 5;
        String[] keys = {"foo1", "foo2", "foo3", "foo4", "foo5"};

        for (boolean withInterProcessLocking : new boolean[]{true, false}) {
            CountDownLatch threadFinishingLatch = new CountDownLatch(threadCount);
            CountDownLatch threadFinishedLatch = new CountDownLatch(1);
            AtomicInteger executedThreadCount = new AtomicInteger(0);

            IOExceptionRunnable[] runnables = new IOExceptionRunnable[threadCount];
            for (int i = 0; i < threadCount; i++) {
                final int ii = i;
                runnables[i] = () -> {
                    ProcessLock.doLocked(
                            keys[ii],
                            () -> {
                                executedThreadCount.incrementAndGet();
                                // Notify the main thread that it is about to finish
                                threadFinishingLatch.countDown();
                                // Wait for the main thread to allow it to finish
                                try {
                                    threadFinishedLatch.await();
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                            },
                            withInterProcessLocking);
                };
            }

            // Execute the runnables
            for (IOExceptionRunnable runnable : runnables) {
                new Thread(() -> {
                    try {
                        runnable.run();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }).start();
            }

            // Wait for notifications from all the threads that they are about to finish, or return
            // after a timeout. (Note: We should give enough timeout such that when it happens, it
            // is not because the threads were not allocated time to run, but because they are not
            // able to run in parallel, which we will detect a test failure.)
            try {
                threadFinishingLatch.await(2 * threadCount, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // Make sure that more than one thread have executed so far. (Because we use distinct
            // keys, the threads are expected to run in parallel.)
            assertTrue(executedThreadCount.get() > 1);

            // Allow all the threads to finish
            threadFinishedLatch.countDown();
        }
    }

    @Test
    public void testComplexKey() throws IOException {
        AtomicInteger executionCount = new AtomicInteger(0);

        ProcessLock.doLocked(
                "foo`-=[]\\;',./~!@#$%^&*()_+{}|:\"<>?",
                () -> { executionCount.incrementAndGet(); },
                false);

        assertEquals(1, executionCount.get());
    }

}