/*
 * Copyright (C) 2011 The Android Open Source Project
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
import com.android.annotations.Nullable;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.Log.LogLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class to parse raw output of {@code adb logcat -v long} to {@link LogCatMessage} objects.
 */
public final class LogCatMessageParser {

    /**
     * Pattern for logcat -v long header ([ MM-DD HH:MM:SS.mmm PID:TID LEVEL/TAG ])
     * Ex: [ 08-18 16:39:11.760  2977: 2988 D/PhoneInterfaceManager ]
     * Group 1: Date + Time
     * Group 2: PID
     * Group 3: TID (hex on some systems!)
     * Group 4: Log Level character
     * Group 5: Tag
     */
    private static final Pattern sLogHeaderPattern = Pattern.compile(
            "^\\[\\s(\\d\\d-\\d\\d\\s\\d\\d:\\d\\d:\\d\\d\\.\\d+)"
                    + "\\s+(\\d*):\\s*(\\S+)\\s([VDIWEAF])/(.*[^\\s])\\s+\\]$");

    @Nullable
    LogCatHeader mPrevHeader;

    /**
     * Parse a header line into a {@link LogCatHeader} object, or {@code null} if the input line
     * doesn't match the expected format.
     *
     * @param line   raw text that should be the header line from logcat -v long
     * @param device device from which these log messages have been received
     * @return a {@link LogCatHeader} which represents the passed in text
     */
    @Nullable
    public LogCatHeader processLogHeader(@NonNull String line, @Nullable IDevice device) {
        Matcher matcher = sLogHeaderPattern.matcher(line);
        if (!matcher.matches()) {
            return null;
        }

        int pid = -1;
        try {
            pid = Integer.parseInt(matcher.group(2));
        } catch (NumberFormatException ignored) {
        }

        int tid = -1;
        try {
            // Thread id's may be in hex on some platforms.
            // Decode and store them in radix 10.
            tid = Integer.decode(matcher.group(3));
        } catch (NumberFormatException ignored) {
        }

        String pkgName = null;
        if (device != null && pid != -1) {
            pkgName = device.getClientName(pid);
        }
        if (pkgName == null || pkgName.isEmpty()) {
            pkgName = "?"; //$NON-NLS-1$
        }

        LogLevel logLevel = LogLevel.getByLetterString(matcher.group(4));
        if (logLevel == null && matcher.group(4).equals("F")) {
            logLevel = LogLevel.ASSERT;
        }
        if (logLevel == null) {
            // Should never happen but warn seems like a decent default just in case
            logLevel = LogLevel.WARN;
        }

        mPrevHeader = new LogCatHeader(logLevel, pid, tid, pkgName,
                matcher.group(5), LogCatTimestamp.fromString(matcher.group(1)));

        return mPrevHeader;
    }

    /**
     * Parse a list of strings into {@link LogCatMessage} objects. This method maintains state from
     * previous calls regarding the last seen header of logcat messages.
     *
     * @param lines  list of raw strings obtained from logcat -v long
     * @param device device from which these log messages have been received
     * @return list of LogMessage objects parsed from the input
     * @throws IllegalStateException if given text before ever parsing a header
     */
    @NonNull
    public List<LogCatMessage> processLogLines(@NonNull String[] lines, @Nullable IDevice device) {
        List<LogCatMessage> messages = new ArrayList<LogCatMessage>(lines.length);

        for (String line : lines) {
            if (line.isEmpty()) {
                continue;
            }

            if (processLogHeader(line, device) == null) {
                // If not a header line, this is a message line
                if (mPrevHeader == null) {
                    throw new IllegalStateException(
                            "No logcat header processed yet, failed to parse line: " + line);
                }
                messages.add(new LogCatMessage(mPrevHeader, line));
            }
        }

        return messages;
    }
}
