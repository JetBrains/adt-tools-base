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
import com.android.ddmlib.IDevice;
import com.android.ddmlib.Log;
import com.android.ddmlib.logcat.LogCatFilter;
import com.android.ddmlib.logcat.LogCatListener;
import com.android.ddmlib.logcat.LogCatMessage;
import com.android.ddmlib.logcat.LogCatReceiverTask;
import com.google.common.collect.Lists;

import org.junit.rules.ExternalResource;

import java.util.Collections;
import java.util.List;


public class Logcat extends ExternalResource {

    private LogCatReceiverTask mLogCatReceiverTask;

    private Thread mThread;

    private List<LogCatMessage> mLogCatMessages;


    public static Logcat create() {
        return new Logcat();
    }

    private Logcat() {

    }

    @Override
    protected void after() {
        if (mLogCatReceiverTask != null) {
            mLogCatReceiverTask.stop();
            try {
                mThread.join();
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

    private void start(@NonNull IDevice device, @Nullable final LogCatFilter filter) {
        mLogCatMessages = Lists.newArrayList();
        mLogCatReceiverTask = new LogCatReceiverTask(device);
        mLogCatReceiverTask.addLogCatListener(new LogCatListener() {
            @Override
            public void log(List<LogCatMessage> msgList) {
                for (LogCatMessage message : msgList) {
                    if (filter == null || filter.matches(message)) {
                        mLogCatMessages.add(message);
                    }
                }
            }
        });
        mThread = new Thread(mLogCatReceiverTask);
        mThread.setDaemon(true);
        mThread.start();
    }

    public List<LogCatMessage> getLogCatMessages() {
        return Collections.unmodifiableList(mLogCatMessages);
    }

    public void clear() {
        mLogCatMessages.clear();
    }
}
