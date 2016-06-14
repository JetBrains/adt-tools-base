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

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Future used by the {@link VirtualTimeScheduler} to track the actions scheduled on it.
 * Allows for introspection of the scheduled action through various helper methods.
 */
public class VirtualTimeFuture<T> implements ScheduledFuture<T> {

    private static final AtomicLong sCounter = new AtomicLong();

    private final VirtualTimeScheduler mScheduler;
    private final Runnable mRunnable;
    private final Callable<T> mCallable;
    private final long mOffset;
    private final VirtualTimeRepeatKind mRepeatKind;
    private final CountDownLatch mIsDone = new CountDownLatch(1);
    private final long mId;

    // long (and double) variables are not atomic unless volatile
    private volatile long mTick;
    private volatile long mTimeoutTick = -1;
    private boolean mIsCancelled;
    private T mValue;
    private Exception mException;

    VirtualTimeFuture(
            VirtualTimeScheduler virtualTimeScheduler,
            Runnable runnable,
            long tick,
            long offset,
            VirtualTimeRepeatKind repeatKind) {
        this.mScheduler = virtualTimeScheduler;
        this.mRunnable = runnable;
        this.mTick = tick;
        this.mCallable = null;
        this.mOffset = offset;
        this.mRepeatKind = repeatKind;
        mId = sCounter.getAndIncrement();
    }

    VirtualTimeFuture(
            VirtualTimeScheduler virtualTimeScheduler,
            Callable<T> callable,
            long tick,
            long offset,
            VirtualTimeRepeatKind repeatKind) {
        this.mScheduler = virtualTimeScheduler;
        this.mTick = tick;
        this.mCallable = callable;
        this.mRunnable = null;
        this.mOffset = offset;
        this.mRepeatKind = repeatKind;
        mId = sCounter.getAndIncrement();
    }

    @Override
    public long getDelay(TimeUnit unit) {
        long diff = mTick - mScheduler.getCurrentTimeNanos();
        return unit.convert(diff, TimeUnit.NANOSECONDS);
    }

    @Override
    public int compareTo(Delayed o) {
        if (o == null) {
            return Integer.MAX_VALUE;
        }
        if (this.equals(o)) {
            return 0;
        }
        return -1;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        mIsCancelled = mScheduler.cancel(this);
        mIsDone.countDown();
        return mIsCancelled;
    }

    @Override
    public boolean isCancelled() {
        return mIsCancelled;
    }

    @Override
    public boolean isDone() {
        return mIsDone.getCount() == 0;
    }

    @Override
    public T get() throws InterruptedException, ExecutionException {
        if (mTimeoutTick >= 0 && mScheduler.getCurrentTimeNanos() >= mTimeoutTick) {
            cancel(false);
        }
        mIsDone.await();
        if (mIsCancelled) {
            throw new CancellationException();
        }
        if (mException != null) {
            throw new ExecutionException(mException);
        }
        return mValue;
    }

    @Override
    public T get(long timeout, TimeUnit unit)
        // NOTE: get uses virtual time to wait, but does block the current thread
        // as it does not return a future.
            throws InterruptedException, ExecutionException, TimeoutException {
        if (mTimeoutTick >= 0 && mScheduler.getCurrentTimeNanos() >= mTimeoutTick) {
            cancel(false);
        }
        long end = unit.toNanos(timeout) + mScheduler.getCurrentTimeNanos();
        while (!isDone() && mScheduler.getCurrentTimeNanos() < end) {
            Thread.sleep(0);
        }
        if (!isDone()) {
            throw new TimeoutException();
        }
        if (mIsCancelled) {
            throw new CancellationException();
        }
        if (mException != null) {
            throw new ExecutionException(mException);
        }
        return mValue;
    }

    /**
     * Gets the virtual time in nano seconds that this future is scheduled to execute at.
     */
    public long getTick() {
        return mTick;
    }

    /**
     * Helper for the VirtualTimeScheduler to update the time this future is executed at (for repeated actions).
     */
    void setmTick(long mTick) {
        this.mTick = mTick;
    }

    /**
     * Gets the Runnable this future is created for.
     */
    public Runnable getRunnable() {
        return mRunnable;
    }

    /**
     * Gets the Callable this future is created for.
     */
    public Callable<T> getCallable() {
        return mCallable;
    }

    /**
     * Gets the VirtualTimeScheduler this future is associated with.
     */
    public VirtualTimeScheduler getOwningScheduler() {
        return mScheduler;
    }

    /**
     * Gets the Offset between invocations of the action behind this future, for repeated actions only.
     */
    public long getOffset() {
        return mOffset;
    }

    /**
     * Describes if this future is repeated and if so how.
     */
    public VirtualTimeRepeatKind getRepeatKind() {
        return mRepeatKind;
    }

    /**
     * Helper used by the scheduler to run the action behind this future.
     */
    void run() {
        if (mRepeatKind == VirtualTimeRepeatKind.RATE) {
            mScheduler.reschedule(this, mOffset);
        }
        try {
            if (mRunnable != null) {
                mRunnable.run();
            } else {
                mValue = mCallable.call();
            }
            if (mRepeatKind == VirtualTimeRepeatKind.NONE) {
                mIsDone.countDown();
            }
        } catch (Exception e) {
            mException = e;
            mIsDone.countDown();
        }
        if (mRepeatKind == VirtualTimeRepeatKind.DELAY) {
            mScheduler.reschedule(this, mOffset);
        }
    }

    /**
     * Helper used by the scheduler's Comparator to distinguish/order futures scheduled for the same time.
     */
    public long getId() {
        return mId;
    }

    /**
     * Helper used by @{link VirtualTimeScheduler.invokeAll} method to timeout futures.
     */
    void setTimeoutTick(long timeoutTick) {
        this.mTimeoutTick = timeoutTick;
    }
}
