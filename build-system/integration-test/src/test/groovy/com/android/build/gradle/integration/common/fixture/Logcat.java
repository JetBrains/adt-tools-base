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

package com.android.build.gradle.integration.common.fixture;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.integration.common.utils.DeviceHelper;
import com.android.builder.tasks.BooleanLatch;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.Log;
import com.android.ddmlib.logcat.LogCatFilter;
import com.android.ddmlib.logcat.LogCatListener;
import com.android.ddmlib.logcat.LogCatMessage;
import com.android.ddmlib.logcat.LogCatReceiverTask;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.common.collect.EvictingQueue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;


public class Logcat implements TestRule {

    @Nullable
    private LogCatReceiverTask mLogCatReceiverTask;

    @Nullable
    private Thread mThread;

    @Nullable
    private List<LogCatMessage> mFilteredLogCatMessages;

    @Nullable
    private EvictingQueue<LogCatMessage> mLogCatMessages;

    public static Logcat create() {
        return new Logcat();
    }

    private Logcat() {

    }

    @Override
    public Statement apply(final Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                boolean failed = false;
                try {
                    base.evaluate();
                } catch (Throwable e) {
                    failed = true;
                    throw e;
                } finally {
                    after();
                    if (failed) {
                        printLogCatMessages();
                    }
                }
            }
        };
    }

    protected synchronized void after() {
        if (mLogCatReceiverTask != null) {
            mLogCatReceiverTask.stop();
            try {
                if (mThread != null) {
                    mThread.join();
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Start collecting logcat.
     *
     * @param device The device to connect to.
     * @param tagFilter the logcat message tag filter.
     *                  Only record messages with tags that match this regular expression.
     */
    public void start(@NonNull IDevice device, @NonNull String tagFilter) {
        start(device, new LogCatFilter("", tagFilter, "", "", "", Log.LogLevel.VERBOSE));
    }

    private synchronized void start(@NonNull IDevice device, @Nullable final LogCatFilter filter) {
        mLogCatMessages = EvictingQueue.create(400);
        mFilteredLogCatMessages = Lists.newArrayList();
        mLogCatReceiverTask = new LogCatReceiverTask(device);
        mLogCatReceiverTask.addLogCatListener(new LogCatListener() {
            @Override
            public void log(List<LogCatMessage> msgList) {
                for (LogCatMessage message : msgList) {
                    mLogCatMessages.add(message);
                    if (filter == null || filter.matches(message)) {
                        mFilteredLogCatMessages.add(message);
                    }
                }
            }
        });
        mThread = new Thread(mLogCatReceiverTask);
        mThread.setDaemon(true);
        mThread.start();
    }


    /** Start listening for a logcat message. See {@link MessageListener}. */
    @NonNull
    public MessageListener listenForMessage(@NonNull final String messageText) {
        return new MessageListener(messageText,
                Preconditions.checkNotNull(mLogCatReceiverTask, "Call start() first!")).start();
    }

    /**
     * Listens for a logcat message with the exact content given.<br />
     *
     * Tests will want to wait for a device to be ready, a common idiom is likely to be:
     * <ol>
     *     <li>Test calls {@link #listenForMessage(String)} to start listening for the logcat
     *         message.</li>
     *     <li>Test performs some action (e.g. adb shell am start) which should result in the
     *         message being output.</li>
     *     <li>Test calls {@link #await(long, TimeUnit)} which blocks until the logcat message is
     *         received or indicates a timeout by throwing a {@link TimeoutException}.</li>
     * </ol>
     */
    public static class MessageListener {
        @NonNull
        private final String mMessageText;

        @NonNull
        private final LogCatReceiverTask mLogCatReceiverTask;

        @Nullable
        private LogCatListener mListener;

        private final BooleanLatch messageFound = new BooleanLatch();

        MessageListener(
                @NonNull String messageText,
                @NonNull LogCatReceiverTask logCatReceiverTask) {
            mMessageText = messageText;
            mLogCatReceiverTask = logCatReceiverTask;
        }

        MessageListener start() {
            LogCatListener listener = new LogCatListener() {
                @Override
                public void log(List<LogCatMessage> msgList) {
                    for (LogCatMessage message: msgList) {
                        if (mMessageText.equals(message.getMessage())) {
                            messageFound.signal();
                        }
                    }
                }
            };

            mLogCatReceiverTask.addLogCatListener(listener);
            return this;
        }

        public void await(long waitTime, @NonNull TimeUnit waitTimeUnit)
                throws InterruptedException, TimeoutException {
            try {
                boolean found = messageFound.await(waitTimeUnit.toNanos(waitTime));

                if (!found) {
                    throw new TimeoutException(
                            "Expected logcat message '" + mMessageText + "' never received.");
                }
            } finally {
                mLogCatReceiverTask.removeLogCatListener(mListener);
            }
        }

        public void await() throws InterruptedException, TimeoutException {
            await(DeviceHelper.DEFAULT_ADB_TIMEOUT_MSEC, TimeUnit.MILLISECONDS);
        }
    }

    public List<LogCatMessage> getFilteredLogCatMessages() {
        return ImmutableList.copyOf(
                Preconditions.checkNotNull(mFilteredLogCatMessages, "Call start() first!"));
    }

    private void printLogCatMessages() {
        if (mLogCatMessages != null) {
            System.out.println("------------ Logcat Messages ------------\n"
                    + Joiner.on('\n').join(mLogCatMessages) + "\n"
                    + "---------- End Logcat Messages ----------\n");
        }
    }

    public void clearFiltered() {
        Preconditions.checkNotNull(mFilteredLogCatMessages, "Call start() first!").clear();
    }
}
