/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.tools.perflib.vmtrace.viz;

import java.text.DecimalFormat;
import java.util.concurrent.TimeUnit;

public class TimeUtils {
    public static String makeHumanReadable(long time, long span, TimeUnit timeUnits) {
        String units;
        double scale;
        if (timeUnits.toSeconds(span) > 0) {
            units = "s";
            scale = 1e-9;
        } else if (timeUnits.toMillis(span) > 0) {
            units = "ms";
            scale = 1e-6;
        } else {
            units = "us";
            scale = 1e-3;
        }

        return String.format("%1$s %2$s", formatTime(timeUnits.toNanos(time), scale), units);
    }

    private static final DecimalFormat TIME_FORMATTER = new DecimalFormat("#.###");

    private static String formatTime(long nsecs, double scale) {
        return TIME_FORMATTER.format(nsecs * scale);
    }
}
