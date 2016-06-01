/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Eclipse Public License, Version 1.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.eclipse.org/org/documents/epl-v10.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.testutils;

import junit.framework.TestCase;
import org.junit.Assert;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tests for {@link VirtualTimeScheduler} and {@link VirtualTimeFuture}.
 */
public class VirtualTimeSchedulerTest extends TestCase {
    public void testInitialValues() {
        // Ensure the scheduler's initial values are sensible.
        VirtualTimeScheduler virtualTimeScheduler = new VirtualTimeScheduler();
        Assert.assertEquals(0, virtualTimeScheduler.getCurrentTimeNanos());
        Assert.assertEquals(0, virtualTimeScheduler.getActionsQueued());
        Assert.assertEquals(0, virtualTimeScheduler.getActionsExecuted());
        Assert.assertEquals(0, virtualTimeScheduler.getFurthestScheduledTimeNanos());
    }

    public void testRunnableBasics() {
        VirtualTimeScheduler virtualTimeScheduler = new VirtualTimeScheduler();

        long before = new Date().getTime();
        AtomicBoolean ran = new AtomicBoolean(false);
        // Schedule a job far in the virtual future.
        Runnable job = () -> ran.set(true);
        virtualTimeScheduler.schedule(job, 1, TimeUnit.DAYS);
        // Assert time hasn't changed and nothing has been executed,
        // but the scheduler knows something is about to happen in the future.
        Assert.assertEquals(0, virtualTimeScheduler.getCurrentTimeNanos());
        Assert.assertEquals(0, virtualTimeScheduler.getActionsExecuted());
        Assert.assertEquals(1, virtualTimeScheduler.getActionsQueued());
        Assert.assertEquals(
                TimeUnit.DAYS.toNanos(1), virtualTimeScheduler.getFurthestScheduledTimeNanos());
        Assert.assertFalse(ran.get());

        // Check the details of the scheduled action
        VirtualTimeFuture<?> future = virtualTimeScheduler.getQueue().peek();
        Assert.assertNotNull(future);
        Assert.assertEquals(job, future.getRunnable());
        Assert.assertNull(future.getCallable());
        Assert.assertEquals(TimeUnit.DAYS.toNanos(1), future.getTick());
        Assert.assertEquals(VirtualTimeRepeatKind.NONE, future.getRepeatKind());
        Assert.assertEquals(TimeUnit.DAYS.toNanos(1), future.getDelay(TimeUnit.NANOSECONDS));
        Assert.assertEquals(-1, future.getOffset());
        Assert.assertEquals(virtualTimeScheduler, future.getOwningScheduler());

        // Advance the scheduler, but not enough to trigger execution
        long actionsExecuted = virtualTimeScheduler.advanceBy(1, TimeUnit.HOURS);
        Assert.assertEquals(0, actionsExecuted);
        Assert.assertEquals(0, virtualTimeScheduler.getActionsExecuted());
        Assert.assertEquals(TimeUnit.HOURS.toNanos(1), virtualTimeScheduler.getCurrentTimeNanos());
        Assert.assertEquals(1, virtualTimeScheduler.getActionsQueued());
        Assert.assertEquals(
                TimeUnit.DAYS.toNanos(1), virtualTimeScheduler.getFurthestScheduledTimeNanos());
        Assert.assertFalse(ran.get());

        Assert.assertEquals(TimeUnit.HOURS.toNanos(23), future.getDelay(TimeUnit.NANOSECONDS));

        // Advance the scheduler past the time for the scheduled action.
        actionsExecuted = virtualTimeScheduler.advanceBy(1, TimeUnit.DAYS);
        Assert.assertEquals(1, actionsExecuted);
        Assert.assertEquals(1, virtualTimeScheduler.getActionsExecuted());
        Assert.assertEquals(TimeUnit.HOURS.toNanos(25), virtualTimeScheduler.getCurrentTimeNanos());
        Assert.assertEquals(0, virtualTimeScheduler.getActionsQueued());
        Assert.assertEquals(
                TimeUnit.DAYS.toNanos(1), virtualTimeScheduler.getFurthestScheduledTimeNanos());
        Assert.assertTrue(ran.get());

        Assert.assertEquals(TimeUnit.HOURS.toNanos(-1), future.getDelay(TimeUnit.NANOSECONDS));

        long after = new Date().getTime();
        long diff = after - before;
        // Assert the time to execute this is significant less than the day it would take to execute in real time
        // we use 10 seconds as a upper bound to avoid flakiness.
        Assert.assertTrue(diff < TimeUnit.SECONDS.toNanos(10));
    }

    public void testCallableBasics() throws ExecutionException, InterruptedException {
        VirtualTimeScheduler virtualTimeScheduler = new VirtualTimeScheduler();
        Callable<Boolean> job = () -> true;

        ScheduledFuture<Boolean> result = virtualTimeScheduler.schedule(job, 1, TimeUnit.DAYS);
        Assert.assertFalse(result.isDone());
        Assert.assertFalse(result.isCancelled());

        virtualTimeScheduler.advanceBy(1, TimeUnit.DAYS);
        Assert.assertTrue(result.isDone());
        Assert.assertFalse(result.isCancelled());
        Assert.assertTrue(result.get());
    }

    public void testCallableCancel()
            throws ExecutionException, InterruptedException, TimeoutException {
        VirtualTimeScheduler virtualTimeScheduler = new VirtualTimeScheduler();

        // Create a set of jobs that have results & side effects.
        List<Integer> sideEffects = new ArrayList<>();
        Callable<Integer> job1 = createCallable(1, sideEffects);
        Callable<Integer> job2 = createCallable(1, sideEffects);
        ScheduledFuture<Integer> result1 = virtualTimeScheduler.schedule(job1, 1, TimeUnit.HOURS);
        ScheduledFuture<Integer> result2 = virtualTimeScheduler.schedule(job2, 2, TimeUnit.HOURS);

        // Make sure one job is run.
        virtualTimeScheduler.advanceBy(1, TimeUnit.HOURS);
        // Try to cancel that job, which should be a noop.
        Assert.assertFalse(result1.cancel(false));
        Assert.assertFalse(result1.isCancelled());
        Assert.assertTrue(result1.isDone());
        Assert.assertEquals(1, (int) result1.get());

        // Try to cancel the second job, which hasn't run yet so cancelling should work
        Assert.assertTrue(result2.cancel(false));
        Assert.assertTrue(result2.isCancelled());
        Assert.assertTrue(result2.isDone());

        // Ensure cancelling leads to the right behavior for get.
        try {
            result2.get();
            Assert.fail();
        } catch (CancellationException e) {
            Assert.assertEquals(0, virtualTimeScheduler.getActionsQueued());
        }
        try {
            result2.get(1, TimeUnit.HOURS);
            Assert.fail();
        } catch (CancellationException e) {
            Assert.assertEquals(0, virtualTimeScheduler.getActionsQueued());
        }
    }

    public void testCallableException() throws InterruptedException, TimeoutException {
        VirtualTimeScheduler virtualTimeScheduler = new VirtualTimeScheduler();

        // A job that throws an exception when run.
        Callable<Boolean> job =
                () -> {
                    throw new RuntimeException("Failure");
                };

        // Get the jobs Future and check initial values make sense.
        ScheduledFuture<Boolean> result = virtualTimeScheduler.schedule(job, 1, TimeUnit.DAYS);
        Assert.assertFalse(result.isDone());
        Assert.assertFalse(result.isCancelled());

        // Run the job and ensure it has ran.
        virtualTimeScheduler.advanceBy(1, TimeUnit.DAYS);
        Assert.assertTrue(result.isDone());
        Assert.assertFalse(result.isCancelled());

        // Ensure get behavior surfaces the Exception.
        try {
            result.get();
            Assert.fail();
        } catch (ExecutionException e) {
            Assert.assertTrue(e.getCause() instanceof RuntimeException);
        }
        try {
            result.get(1, TimeUnit.HOURS);
            Assert.fail();
        } catch (ExecutionException e) {
            Assert.assertTrue(e.getCause() instanceof RuntimeException);
        }
    }

    public void testCallableBlock() throws ExecutionException, InterruptedException {
        VirtualTimeScheduler virtualTimeScheduler = new VirtualTimeScheduler();

        // Create a job to block for result on.
        Callable<Boolean> job = () -> true;

        // Schedule the job and get its future.
        ScheduledFuture<Boolean> result = virtualTimeScheduler.schedule(job, 1, TimeUnit.DAYS);
        Assert.assertFalse(result.isDone());
        Assert.assertFalse(result.isCancelled());

        // Run the job
        virtualTimeScheduler.advanceBy(1, TimeUnit.DAYS);
        Assert.assertTrue(result.isDone());
        Assert.assertFalse(result.isCancelled());
        // Ensure the get behaves as expected.
        Assert.assertTrue(result.get());
    }

    public void testCallableBlockTimeout() throws InterruptedException {
        VirtualTimeScheduler virtualTimeScheduler = new VirtualTimeScheduler();

        // Create a job to timeout.
        Callable<Boolean> job = () -> true;

        // Schedule the job.
        ScheduledFuture<Boolean> result = virtualTimeScheduler.schedule(job, 1, TimeUnit.DAYS);
        Assert.assertFalse(result.isDone());
        Assert.assertFalse(result.isCancelled());

        // Create a thread to block+timeout on.
        CountDownLatch threadStarted = new CountDownLatch(1);
        CountDownLatch completion = new CountDownLatch(1);
        Thread t =
                new Thread(
                        () -> {
                            try {
                                threadStarted.countDown();
                                result.get(1, TimeUnit.HOURS);
                                Assert.fail();
                            } catch (InterruptedException e) {
                                Assert.fail();
                            } catch (ExecutionException e) {
                                Assert.fail();
                            } catch (TimeoutException e) {
                                Assert.assertEquals(
                                        TimeUnit.HOURS.toNanos(1),
                                        virtualTimeScheduler.getCurrentTimeNanos());
                            }
                            completion.countDown();
                        });
        t.start();
        threadStarted.await();
        // Sleep so the get request can be queued..
        Thread.sleep(100);
        // Advance the scheduler to trigger the timeout.
        virtualTimeScheduler.advanceBy(1, TimeUnit.HOURS);
        completion.await();
    }

    /**
     * Helper to create callables to pass to the scheduler.
     */
    private Callable<Integer> createCallable(int number, List<Integer> sideEffects) {
        return () -> {
            sideEffects.add(number);
            return number;
        };
    }

    /**
     * Helper to create runnables to pass to the scheduler.
     */
    private Runnable createRunnable(int number, List<Integer> sideEffects) {
        return () -> sideEffects.add(number);
    }

    public void testMultipleJobsAtSameTime() throws ExecutionException, InterruptedException {
        VirtualTimeScheduler virtualTimeScheduler = new VirtualTimeScheduler();
        List<Integer> sideEffects = new ArrayList<>();
        // Create a bunch of jobs
        Callable<Integer> job1 = createCallable(1, sideEffects);
        Callable<Integer> job2 = createCallable(2, sideEffects);
        Callable<Integer> job3 = createCallable(3, sideEffects);

        // Schedule some jobs at the same time.
        ScheduledFuture<Integer> result1 = virtualTimeScheduler.schedule(job1, 1, TimeUnit.HOURS);
        ScheduledFuture<Integer> result2 = virtualTimeScheduler.schedule(job2, 1, TimeUnit.HOURS);
        ScheduledFuture<Integer> result3 = virtualTimeScheduler.schedule(job3, 2, TimeUnit.HOURS);

        // Schedule the jobs that are executed at the same time.
        virtualTimeScheduler.advanceBy(90, TimeUnit.MINUTES);

        // Check results
        Assert.assertTrue(result1.isDone());
        Assert.assertFalse(result1.isCancelled());
        Assert.assertEquals(1, (int) result1.get());

        Assert.assertTrue(result2.isDone());
        Assert.assertFalse(result2.isCancelled());
        Assert.assertEquals(2, (int) result2.get());

        Assert.assertFalse(result3.isDone());
        Assert.assertFalse(result3.isCancelled());

        // Ensure jobs are executed in a predictable order.
        Assert.assertArrayEquals(new Integer[] {1, 2}, sideEffects.toArray(new Integer[] {}));
    }

    public void testFixedDelay() {
        VirtualTimeScheduler virtualTimeScheduler = new VirtualTimeScheduler();
        List<Long> ticks = new ArrayList<>();

        // Schedule a job with a fixed delay
        ScheduledFuture<?> future =
                virtualTimeScheduler.scheduleWithFixedDelay(
                        new Runnable() {
                            @Override
                            public void run() {
                                ticks.add(virtualTimeScheduler.getCurrentTimeNanos());
                                // Side effect of moving the scheduler ahead in time so our repeat execution gets timeshifted.
                                virtualTimeScheduler.advanceBy(1, TimeUnit.MINUTES);
                            }
                        },
                        2,
                        1,
                        TimeUnit.HOURS);

        // Schedule the initial action
        virtualTimeScheduler.advanceBy(1, TimeUnit.HOURS);

        // Ensure the repeat action got timeshifted.
        Assert.assertEquals(60, future.getDelay(TimeUnit.MINUTES));

        virtualTimeScheduler.advanceBy(1, TimeUnit.HOURS);

        // Verify the next action is scheduled when we expect.
        Assert.assertEquals(
                TimeUnit.MINUTES.toNanos(121), virtualTimeScheduler.getCurrentTimeNanos());

        // Ensure the action got run and more actions are scheduled.
        Assert.assertEquals(1, virtualTimeScheduler.getActionsExecuted());
        Assert.assertEquals(1, virtualTimeScheduler.getActionsQueued());
        Assert.assertEquals(60, future.getDelay(TimeUnit.MINUTES));
        Assert.assertFalse(future.isDone());
    }

    public void testFixedRate() {
        VirtualTimeScheduler virtualTimeScheduler = new VirtualTimeScheduler();
        List<Long> ticks = new ArrayList<>();
        // Schedule a job at a fixed rate
        ScheduledFuture<?> future =
                virtualTimeScheduler.scheduleAtFixedRate(
                        new Runnable() {
                            @Override
                            public void run() {
                                ticks.add(virtualTimeScheduler.getCurrentTimeNanos());
                                // Side effect of moving the scheduler ahead in time and ensure this doesn't affect the next
                                // scheduling of this action.
                                virtualTimeScheduler.advanceBy(1, TimeUnit.MINUTES);
                            }
                        },
                        2,
                        1,
                        TimeUnit.HOURS);

        // Schedule the initial action
        virtualTimeScheduler.advanceBy(1, TimeUnit.HOURS);

        // Verify the next action is scheduled when we expect.
        Assert.assertEquals(60, future.getDelay(TimeUnit.MINUTES));
        virtualTimeScheduler.advanceBy(1, TimeUnit.HOURS);

        // Ensure the action got run and more actions are scheduled.
        Assert.assertEquals(
                TimeUnit.MINUTES.toNanos(121), virtualTimeScheduler.getCurrentTimeNanos());
        Assert.assertEquals(1, virtualTimeScheduler.getActionsExecuted());
        Assert.assertEquals(1, virtualTimeScheduler.getActionsQueued());
        Assert.assertEquals(59, future.getDelay(TimeUnit.MINUTES));
        Assert.assertFalse(future.isDone());
    }

    public void testInvokeAll() throws InterruptedException, ExecutionException {
        VirtualTimeScheduler virtualTimeScheduler = new VirtualTimeScheduler();

        // Create a set of jobs.
        List<Integer> sideEffects = new ArrayList<>();
        Callable<Integer> job1 = createCallable(1, sideEffects);
        Callable<Integer> job2 = createCallable(2, sideEffects);
        Callable<Integer> job3 = createCallable(3, sideEffects);

        List<Callable<Integer>> jobs = new ArrayList<>();
        jobs.add(job1);
        jobs.add(job2);
        jobs.add(job3);

        // schedule all jobs together.
        List<Future<Integer>> results = virtualTimeScheduler.invokeAll(jobs);

        // Ensure the jobs are scheduled as expected.
        Assert.assertEquals(0, virtualTimeScheduler.getActionsExecuted());
        Assert.assertEquals(3, virtualTimeScheduler.getActionsQueued());

        // Run all jobs and ensure they were all run.
        virtualTimeScheduler.advanceBy(0);
        Assert.assertEquals(3, virtualTimeScheduler.getActionsExecuted());
        Assert.assertEquals(0, virtualTimeScheduler.getActionsQueued());

        Assert.assertTrue(results.get(0).isDone());
        Assert.assertTrue(results.get(1).isDone());
        Assert.assertTrue(results.get(2).isDone());

        Assert.assertEquals(1, (int) results.get(0).get());
        Assert.assertEquals(2, (int) results.get(1).get());
        Assert.assertEquals(3, (int) results.get(2).get());

        // Ensure jobs were run in a predictable order.
        Assert.assertArrayEquals(new Integer[] {1, 2, 3}, sideEffects.toArray(new Integer[] {}));
    }

    public void testInvokeAllTimeout() throws InterruptedException, ExecutionException {
        VirtualTimeScheduler virtualTimeScheduler = new VirtualTimeScheduler();
        List<Integer> sideEffects = new ArrayList<>();

        // Create a set of jobs.
        Callable<Integer> job1 = createCallable(1, sideEffects);
        Callable<Integer> job2 = createCallable(2, sideEffects);
        Callable<Integer> job3 = createCallable(3, sideEffects);

        List<Callable<Integer>> jobs = new ArrayList<>();
        jobs.add(job1);
        jobs.add(job2);
        jobs.add(job3);
        // schedule all jobs together with a timeout.
        List<Future<Integer>> results = virtualTimeScheduler.invokeAll(jobs, 1, TimeUnit.MINUTES);

        // Ensure the jobs are scheduled as expected.
        Assert.assertEquals(0, virtualTimeScheduler.getActionsExecuted());
        Assert.assertEquals(3, virtualTimeScheduler.getActionsQueued());

        // Run all jobs and ensure they were all run.
        virtualTimeScheduler.advanceBy(0);
        Assert.assertEquals(3, virtualTimeScheduler.getActionsExecuted());
        Assert.assertEquals(0, virtualTimeScheduler.getActionsQueued());

        Assert.assertTrue(results.get(0).isDone());
        Assert.assertTrue(results.get(1).isDone());
        Assert.assertTrue(results.get(2).isDone());

        Assert.assertEquals(1, (int) results.get(0).get());
        Assert.assertEquals(2, (int) results.get(1).get());
        Assert.assertEquals(3, (int) results.get(2).get());

        // Ensure jobs were run in a predictable order.
        Assert.assertArrayEquals(new Integer[] {1, 2, 3}, sideEffects.toArray(new Integer[] {}));
    }

    public void testInvokeAny() throws InterruptedException, ExecutionException {
        VirtualTimeScheduler virtualTimeScheduler = new VirtualTimeScheduler();
        List<Integer> sideEffects = new ArrayList<>();

        // Create a set of jobs.
        Callable<Integer> job1 = createCallable(1, sideEffects);
        Callable<Integer> job2 = createCallable(2, sideEffects);
        Callable<Integer> job3 = createCallable(3, sideEffects);

        CountDownLatch cdl = new CountDownLatch(1);
        // Create a thread to move the scheduler forward as invokeAny blocks as it doesn't return a future.
        Thread t =
                new Thread(
                        () -> {
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException e) {
                                Assert.fail();
                            }
                            virtualTimeScheduler.advanceBy(0);
                            cdl.countDown();
                        });
        t.start();
        Collection<Callable<Integer>> jobs = new ArrayList<>();
        jobs.add(job1);
        jobs.add(job2);
        jobs.add(job3);
        // invokeAny of the jobs, blocks until one job successfully returns.
        Integer result = virtualTimeScheduler.invokeAny(jobs);
        cdl.await();
        Assert.assertEquals(1, virtualTimeScheduler.getActionsExecuted());
        Assert.assertEquals(0, virtualTimeScheduler.getActionsQueued());
        Assert.assertEquals(1, (int) result);

        // Ensure the side effects ordered are as expected.
        Assert.assertArrayEquals(new Integer[] {1}, sideEffects.toArray(new Integer[] {}));
    }

    public void testInvokeAnyException() throws InterruptedException, ExecutionException {
        VirtualTimeScheduler virtualTimeScheduler = new VirtualTimeScheduler();
        List<Integer> sideEffects = new ArrayList<>();

        // Create a set of jobs of which the first throws an exception
        Callable<Integer> job1 =
                () -> {
                    sideEffects.add(1);
                    throw new RuntimeException("Runtime");
                };

        Callable<Integer> job2 = createCallable(2, sideEffects);
        Callable<Integer> job3 = createCallable(3, sideEffects);

        CountDownLatch cdl = new CountDownLatch(1);
        // Create a thread to move the scheduler forward as invokeAny blocks as it doesn't return a future.
        Thread t =
                new Thread(
                        () -> {
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException e) {
                                Assert.fail();
                            }
                            virtualTimeScheduler.advanceBy(0);
                            cdl.countDown();
                        });
        t.start();
        Collection<Callable<Integer>> jobs = new ArrayList<>();
        jobs.add(job1);
        jobs.add(job2);
        jobs.add(job3);
        // invokeAny of the jobs, blocks until one job successfully returns.
        Integer result = virtualTimeScheduler.invokeAny(jobs);
        cdl.await();
        Assert.assertEquals(1, virtualTimeScheduler.getActionsExecuted());
        Assert.assertEquals(0, virtualTimeScheduler.getActionsQueued());
        Assert.assertEquals(2, (int) result);

        // Ensure the side effects ordered are as expected.
        Assert.assertArrayEquals(new Integer[] {1, 2}, sideEffects.toArray(new Integer[] {}));
    }

    public void testShutdown() {
        VirtualTimeScheduler virtualTimeScheduler = new VirtualTimeScheduler();
        List<Integer> sideEffects = new ArrayList<>();

        // Schedule a few jobs
        virtualTimeScheduler.schedule(createCallable(1, sideEffects), 1, TimeUnit.HOURS);
        virtualTimeScheduler.schedule(createCallable(2, sideEffects), 2, TimeUnit.HOURS);

        // Shutdown the scheduler.
        virtualTimeScheduler.shutdown();
        try {
            // Try to schedule more jobs, this should fail.
            virtualTimeScheduler.schedule(createCallable(3, sideEffects), 3, TimeUnit.HOURS);
            Assert.fail();
        } catch (RejectedExecutionException e) {
            Assert.assertEquals(2, virtualTimeScheduler.getActionsQueued());
        }
        // Ensure scheduler is shutdown but not yet terminated.
        Assert.assertTrue(virtualTimeScheduler.isShutdown());
        Assert.assertFalse(virtualTimeScheduler.isTerminated());

        // Move time forward to see existing jobs still being executed.
        virtualTimeScheduler.advanceBy(1, TimeUnit.HOURS);
        Assert.assertEquals(1, virtualTimeScheduler.getActionsQueued());
        Assert.assertTrue(virtualTimeScheduler.isShutdown());
        Assert.assertFalse(virtualTimeScheduler.isTerminated());

        // Move time forward to empty out scheduler.
        virtualTimeScheduler.advanceBy(1, TimeUnit.HOURS);
        Assert.assertEquals(0, virtualTimeScheduler.getActionsQueued());
        Assert.assertTrue(virtualTimeScheduler.isShutdown());
        Assert.assertTrue(virtualTimeScheduler.isTerminated());

        // Ensure the side effects ordered are as expected.
        Assert.assertArrayEquals(new Integer[] {1, 2}, sideEffects.toArray(new Integer[] {}));
    }

    public void testShutdownEmpty() {
        // Shutting down a scheduler without any queued job should terminate the scheduler immediately.
        VirtualTimeScheduler virtualTimeScheduler = new VirtualTimeScheduler();
        virtualTimeScheduler.shutdown();
        Assert.assertTrue(virtualTimeScheduler.isShutdown());
        Assert.assertTrue(virtualTimeScheduler.isTerminated());
    }

    public void testShutdownNow() throws InterruptedException {
        VirtualTimeScheduler virtualTimeScheduler = new VirtualTimeScheduler();
        List<Integer> sideEffects = new ArrayList<>();

        // Create a set of jobs.
        Runnable job1 = createRunnable(1, sideEffects);
        Runnable job2 = createRunnable(2, sideEffects);
        Runnable job3 = createRunnable(3, sideEffects);
        Runnable job4 = createRunnable(4, sideEffects);

        // Schedule some of the jobs.
        virtualTimeScheduler.schedule(job1, 1, TimeUnit.HOURS);
        virtualTimeScheduler.schedule(job2, 2, TimeUnit.HOURS);
        virtualTimeScheduler.schedule(job3, 3, TimeUnit.HOURS);

        // Complete the first job.
        virtualTimeScheduler.advanceBy(1, TimeUnit.HOURS);
        Assert.assertEquals(2, virtualTimeScheduler.getActionsQueued());
        Assert.assertFalse(virtualTimeScheduler.isShutdown());
        Assert.assertFalse(virtualTimeScheduler.isTerminated());

        // Shutdown the scheduler, retrieve list of still scheduled jobs.
        List<Runnable> runnables = virtualTimeScheduler.shutdownNow();

        try {
            // Scheduling new jobs should fail.
            virtualTimeScheduler.schedule(job4, 4, TimeUnit.HOURS);
            Assert.fail();
        } catch (RejectedExecutionException e) {
            Assert.assertEquals(2, virtualTimeScheduler.getActionsQueued());
        }
        Assert.assertTrue(virtualTimeScheduler.isShutdown());
        Assert.assertFalse(virtualTimeScheduler.isTerminated());

        CountDownLatch waiters = new CountDownLatch(2);
        // Try waiting for termination for too short of a time.
        Thread waiterTimout =
                new Thread(
                        () -> {
                            try {
                                Assert.assertFalse(
                                        virtualTimeScheduler.awaitTermination(1, TimeUnit.MINUTES));
                            } catch (InterruptedException e) {
                                Assert.fail();
                            }
                            waiters.countDown();
                        });
        waiterTimout.start();

        // Try waiting for termination for longer than needed.
        Thread waiterComplete =
                new Thread(
                        () -> {
                            try {
                                Assert.assertTrue(
                                        virtualTimeScheduler.awaitTermination(1, TimeUnit.DAYS));
                            } catch (InterruptedException e) {
                                Assert.fail();
                            }
                            waiters.countDown();
                        });
        waiterComplete.start();

        // Wait for the threads to be started.
        Thread.sleep(100);

        // Move forward, but not enough to deplete the scheduler.
        virtualTimeScheduler.advanceBy(1, TimeUnit.HOURS);
        Assert.assertEquals(1, virtualTimeScheduler.getActionsQueued());
        Assert.assertTrue(virtualTimeScheduler.isShutdown());
        Assert.assertFalse(virtualTimeScheduler.isTerminated());

        // Move forward, enough to deplete the scheduler.
        virtualTimeScheduler.advanceBy(1, TimeUnit.HOURS);
        Assert.assertEquals(0, virtualTimeScheduler.getActionsQueued());
        Assert.assertTrue(virtualTimeScheduler.isShutdown());
        Assert.assertTrue(virtualTimeScheduler.isTerminated());

        waiters.await();

        // Ensure list of runnables is as expected.
        Assert.assertArrayEquals(new Runnable[] {job2, job3}, runnables.toArray(new Runnable[] {}));

        // Ensure list of side effects is as expected.
        Assert.assertArrayEquals(new Integer[] {1, 2, 3}, sideEffects.toArray(new Integer[] {}));
    }

    public void testVirtualTimeFutureCompare()
            throws InterruptedException, ExecutionException, TimeoutException {
        VirtualTimeScheduler virtualTimeScheduler = new VirtualTimeScheduler();

        // Create a few jobs with side effects.
        AtomicBoolean result1 = new AtomicBoolean(false);
        Future<Integer> future1 = virtualTimeScheduler.<Integer>submit(() -> result1.set(true), 3);

        AtomicBoolean result2 = new AtomicBoolean(false);
        Future<Integer> future2 = virtualTimeScheduler.<Integer>submit(() -> result2.set(true), 5);

        // Run the scheduled jobs.
        virtualTimeScheduler.advanceBy(0);

        Assert.assertTrue(result1.get());
        Assert.assertTrue(result2.get());

        // Ensure the VirtualTimeFutures compare behavior.
        Assert.assertTrue(future1 instanceof VirtualTimeFuture);
        VirtualTimeFuture<Integer> vtf1 = (VirtualTimeFuture<Integer>) future1;
        Assert.assertTrue(future2 instanceof VirtualTimeFuture);
        VirtualTimeFuture<Integer> vtf2 = (VirtualTimeFuture<Integer>) future2;
        Assert.assertEquals(0, vtf1.compareTo(vtf1));
        Assert.assertEquals(-1, vtf1.compareTo(vtf2));

        // Ensure the jobs result is as expected.
        Assert.assertEquals(3, (int) future1.get(1, TimeUnit.MINUTES));
        Assert.assertEquals(5, (int) future2.get(1, TimeUnit.MINUTES));
    }
}
