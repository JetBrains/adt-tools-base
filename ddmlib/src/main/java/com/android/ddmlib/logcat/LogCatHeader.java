/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.ddmlib.logcat;

import com.android.annotations.NonNull;
import com.android.ddmlib.Log.LogLevel;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Data class for message header information which gets reported by logcat.
 */
public final class LogCatHeader {

    // Parser that matches the format of the time reported by logcat
    private static final SimpleDateFormat TIME_PARSER = new SimpleDateFormat("MM-dd hh:mm:ss.SSS");

    @NonNull
    private final LogLevel mLogLevel;

    @NonNull
    private final String mPid;

    @NonNull
    private final String mTid;

    @NonNull
    private final String mAppName;

    @NonNull
    private final String mTag;

    @NonNull
    private final String mTime;

    /**
     * Construct an immutable log message object.
     */
    public LogCatHeader(@NonNull LogLevel logLevel,
            @NonNull String pid,
            @NonNull String tid,
            @NonNull String appName,
            @NonNull String tag,
            @NonNull String time) {
        mLogLevel = logLevel;
        mPid = pid;
        mAppName = appName;
        mTag = tag;
        mTime = time;

        long tidValue;
        try {
            // Thread id's may be in hex on some platforms.
            // Decode and store them in radix 10.
            tidValue = Long.decode(tid.trim());
        } catch (NumberFormatException e) {
            tidValue = -1;
        }

        mTid = Long.toString(tidValue);
    }

    @NonNull
    public LogLevel getLogLevel() {
        return mLogLevel;
    }

    @NonNull
    public String getPid() {
        return mPid;
    }

    @NonNull
    public String getTid() {
        return mTid;
    }

    @NonNull
    public String getAppName() {
        return mAppName;
    }

    @NonNull
    public String getTag() {
        return mTag;
    }

    @NonNull
    public String getTime() {
        return mTime;
    }

    /**
     * Returns the header time as a {@link Date} instance, which is more convenient for comparing
     * one header's time with another.
     */
    @NonNull
    public Date getTimeAsDate() {
        try {
            return TIME_PARSER.parse(mTime);
        } catch (ParseException e) {
            throw new RuntimeException(String.format("Could not convert \"%s\" to %s", mTime,
                    Date.class.getSimpleName()), e);
        }
    }


    @Override
    public String toString() {
        return String.format("%s: %s/%s(%s)", mTime, mLogLevel.getPriorityLetter(), mTag, mPid);
    }
}
