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

import com.google.common.util.concurrent.SettableFuture;

import java.util.*;
import java.util.concurrent.*;

/**
 * {@link VirtualTimeScheduler} is a {@link ScheduledExecutorService} that uses a virtual notion of time.
 * Actions are queued and can be inspected. Time can be advanced on-demand and metadata on how many actions are queued
 * and have been executed can be retrieved.
 * <p>
 * This scheduler is ideally suited to use in tests as it avoids having to have tests sleep which would make the tests
 * inherently flaky.
 */
public class VirtualTimeScheduler implements ScheduledExecutorService {
    // While access to fields is atomic (due to volatile on long & doubles), this code has many methods
    // that operate on various variables at once. We synchronize any method that operates on multiple variables
    // or access members of those variables (e.g. method calls). Tasks are executed sequentially in the same thread
    // that calls #advanceBy or #advanceTo.
    private final Object mGate = new Object();

    private final PriorityQueue<VirtualTimeFuture<?>> mQueue =
            new PriorityQueue<VirtualTimeFuture<?>>(new VirtualFuturesComparator());
    // long (and double) variables are not atomic unless volatile
    private volatile long mCurrentTimeNanos = 0;
    private volatile long mFurthestScheduledTimeNanos = 0;
    private volatile long mActionsExecuted = 0;
    private boolean mIsShutdown = false;
    private boolean mIsTerminated = false;

    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        return schedule(command, unit.toNanos(delay), -1, VirtualTimeRepeatKind.NONE);
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        return schedule(callable, unit.toNanos(delay), -1, VirtualTimeRepeatKind.NONE);
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(
            Runnable command, long initialDelay, long period, TimeUnit unit) {
        return schedule(
                command,
                unit.toNanos(initialDelay),
                unit.toNanos(period),
                VirtualTimeRepeatKind.RATE);
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(
            Runnable command, long initialDelay, long delay, TimeUnit unit) {
        return schedule(
                command,
                unit.toNanos(initialDelay),
                unit.toNanos(delay),
                VirtualTimeRepeatKind.DELAY);
    }

    @Override
    public void shutdown() {
        synchronized (mGate) {
            mIsShutdown = true;
            if (mQueue.isEmpty()) {
                mIsTerminated = true;
            }
        }
    }

    @Override
    public List<Runnable> shutdownNow() {
        // TODO: do we want to wrap the Callable<T> entries in a Runnable and return?
        List<Runnable> runnables = new ArrayList<>();
        synchronized (mGate) {
            shutdown();
            for (VirtualTimeFuture<?> entry : mQueue) {
                Runnable runnable = entry.getRunnable();
                if (runnable != null) {
                    runnables.add(runnable);
                }
            }
        }
        return runnables;
    }

    @Override
    public boolean isShutdown() {
        return mIsShutdown;
    }

    @Override
    public boolean isTerminated() {
        return mIsTerminated;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        // NOTE: awaitTermination uses virtual time to wait, but does block the current thread
        // as it does not return a future.
        if (isTerminated()) {
            return true;
        }
        long end = unit.toNanos(timeout) + getCurrentTimeNanos();
        while (!isTerminated() && getCurrentTimeNanos() < end) {
            Thread.sleep(0);
        }
        return getCurrentTimeNanos() < end;
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        return schedule(task, 0, 0, VirtualTimeRepeatKind.NONE);
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        return submit(
                () -> {
                    task.run();
                    return result;
                });
    }

    @Override
    public Future<?> submit(Runnable task) {
        return schedule(task, 0, -1, VirtualTimeRepeatKind.NONE);
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
            throws InterruptedException {
        return invokeAll(tasks, -1, TimeUnit.NANOSECONDS);
    }

    @Override
    public <T> List<Future<T>> invokeAll(
            Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException {
        List<Future<T>> results = new ArrayList<>();
        // The lock is needed to ensure all jobs get scheduled & timed-out at the same tick (no invocation of
        // advanceBy/To in between submits).
        synchronized (mGate) {
            for (Callable<T> task : tasks) {
                VirtualTimeFuture<T> vft = schedule(task, 0, 0, VirtualTimeRepeatKind.NONE);
                long timeoutNanos = unit.toNanos(timeout);
                if (timeoutNanos >= 0) {
                    vft.setTimeoutTick(timeoutNanos + mCurrentTimeNanos);
                }
                results.add(vft);
            }
        }
        return results;
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
            throws InterruptedException, ExecutionException {
        // NOTE: invokeAny uses virtual time to wait, but does block the current thread
        // as it does not return a future.
        return invokeAnyAsFuture(tasks).get();
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        // NOTE: invokeAny uses virtual time to wait, but does block the current thread
        // as it does not return a future.
        return invokeAnyAsFuture(tasks).get(timeout, unit);
    }

    @Override
    public void execute(Runnable command) {
        submit(command);
    }

    /**
     * Implementation method for the various invokeAny methods above.
     * Note that this executes the tasks in serial until one successfully completes. This falls within the
     * contract of the {@link ScheduledExecutorService} interface.
     **/
    private <T> Future<T> invokeAnyAsFuture(Collection<? extends Callable<T>> tasks) {
        final SettableFuture<T> output = SettableFuture.create();
        submit(
                () -> {
                    int index = 0;
                    for (Callable<T> task : tasks) {
                        ++index;
                        try {
                            output.set(task.call());
                            return;
                        } catch (Exception e) {
                            if (index == tasks.size() - 1) {
                                output.setException(e);
                            }
                        }
                    }
                });
        return output;
    }

    /**
     * Implementation method for the various schedule/submit/invoke methods above that take a Runnable argument.
     */
    private ScheduledFuture<?> schedule(
            Runnable command, long initial, long offset, VirtualTimeRepeatKind repeatKind) {
        synchronized (mGate) {
            if (mIsShutdown) {
                throw new RejectedExecutionException("VirtualTimeScheduler has been shutdown");
            }
            long target = mCurrentTimeNanos + initial;
            VirtualTimeFuture<?> vf =
                    new VirtualTimeFuture<>(this, command, target, offset, repeatKind);
            queueAndRecordFurthest(vf);
            return vf;
        }
    }

    /**
     * Implementation method for the various schedule/submit/invoke methods above that take a Callable argument.
     */
    private <T> VirtualTimeFuture<T> schedule(
            Callable<T> command, long initial, long offset, VirtualTimeRepeatKind repeatKind) {
        synchronized (mGate) {
            if (mIsShutdown) {
                throw new RejectedExecutionException("VirtualTimeScheduler has been shutdown");
            }
            long target = mCurrentTimeNanos + initial;
            VirtualTimeFuture<T> vf =
                    new VirtualTimeFuture<T>(this, command, target, offset, repeatKind);
            queueAndRecordFurthest(vf);
            return vf;
        }
    }

    /**
     * Queues the future at the tick time specified in the future and record if it is the future scheduled furthest in the future.
     * Needs to be called while synchronized on mGate.
     */
    private void queueAndRecordFurthest(VirtualTimeFuture<?> future) {
        mQueue.add(future);
        mFurthestScheduledTimeNanos = Math.max(future.getTick(), mFurthestScheduledTimeNanos);
    }

    /**
     * advanceBy will run all actions scheduled within the interval (converted to nanos) specified from the current time.
     * @param interval time in {@code unit} to advance.
     * @param unit for {@code interval} to advance.
     * @return amount of actions executed.
     */
    public long advanceBy(long interval, TimeUnit unit) {
        return advanceBy(TimeUnit.NANOSECONDS.convert(interval, unit));
    }

    /**
     * advanceBy will run all actions scheduled within the interval specified from the current time.
     * @param intervalNanos time in nano seconds to advance.
     * @return amount of actions executed.
     */
    public long advanceBy(long intervalNanos) {
        synchronized (mGate) {
            long endTimeNanos = intervalNanos + mCurrentTimeNanos;
            return advanceTo(endTimeNanos);
        }
    }

    /**
     * advanceTo will run all actions scheduled between CurrentTimeNanos and endTimeNanos.
     * @param endTimeNanos time in nano seconds to advance to.
     * @return amount of actions executed.
     */
    public long advanceTo(long endTimeNanos) {
        long currentActions = 0;
        synchronized (mGate) {
            while (!mQueue.isEmpty()) {
                VirtualTimeFuture next = mQueue.peek();
                long tick = next.getTick();
                if (tick > endTimeNanos) {
                    break;
                }
                mCurrentTimeNanos = tick;
                mQueue.remove();
                if (!next.isCancelled()) {
                    next.run();
                    currentActions++;
                    mActionsExecuted++;
                }
            }
            if (mCurrentTimeNanos < endTimeNanos) {
                mCurrentTimeNanos = endTimeNanos;
            }
            if (mIsShutdown && mQueue.isEmpty()) {
                mIsTerminated = true;
            }
        }
        return currentActions;
    }

    /**
     * @return the total number of actions executed by this scheduler.
     */
    public long getActionsExecuted() {
        return mActionsExecuted;
    }

    /**
     * @return the number of actions currently queued on this scheduler.
     */
    public long getActionsQueued() {
        synchronized (mGate) {
            return mQueue.size();
        }
    }

    /**
     * @return the furthest time an action is/was scheduled at within this scheduler.
     */
    public long getFurthestScheduledTimeNanos() {
        return mFurthestScheduledTimeNanos;
    }

    /**
     * @return the current virtual time of this scheduler.
     */
    public long getCurrentTimeNanos() {
        return mCurrentTimeNanos;
    }

    /**
     * @return a copy of the {@link PriorityQueue} of queued futures.
     */
    public PriorityQueue<VirtualTimeFuture<?>> getQueue() {
        return new PriorityQueue<>(mQueue);
    }

    /*
     * Helper function that cancels the future from this scheduler.
     */
    boolean cancel(VirtualTimeFuture<?> virtualTimeFuture) {
        synchronized (mGate) {
            if (virtualTimeFuture.isDone()) {
                return false;
            }
            if (virtualTimeFuture.getTick() < mCurrentTimeNanos) {
                return false;
            }
            return mQueue.remove(virtualTimeFuture);
        }
    }

    /*
     * Helper function that cancels the future from this scheduler.
     */
    void reschedule(VirtualTimeFuture<?> virtualTimeFuture, long offset) {
        synchronized (mGate) {
            long tick = mCurrentTimeNanos + offset;
            virtualTimeFuture.setmTick(tick);
            queueAndRecordFurthest(virtualTimeFuture);
        }
    }

    /**
     * Comparator used in this scheduler's priority Queue to order futures by time.
     */
    private static class VirtualFuturesComparator implements Comparator<VirtualTimeFuture<?>> {
        @Override
        public int compare(VirtualTimeFuture<?> o1, VirtualTimeFuture<?> o2) {
            if (o1.equals(o2)) {
                return 0;
            }
            long tickDiff = o1.getTick() - o2.getTick();
            if (tickDiff < 0) {
                return -1;
            }
            if (tickDiff == 0) {
                return Long.compare(o1.getId(), o2.getId());
            }
            return 1;
        }
    }
}
