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

package com.android.tools.perflib.vmtrace;

import com.google.common.collect.Maps;

import java.util.Locale;
import java.util.Map;

public class MethodInfo {
    public final long id;
    public final String className;
    public final String methodName;
    public final String signature;
    public final String srcPath;
    public final int srcLineNumber;

    /** Method stats across all threads. */
    private final MethodStats mAllThreadsStats;

    /** Method stats per thread. */
    private final Map<Integer,MethodStats> mPerThreadStats;

    private String mFullName;
    private String mShortName;

    public MethodInfo(long id, String className, String methodName, String signature,
            String srcPath, int srcLineNumber) {
        this.id = id;
        this.className = className;
        this.methodName = methodName;
        this.signature = signature;
        this.srcPath = srcPath;
        this.srcLineNumber = srcLineNumber;

        mAllThreadsStats = new MethodStats();
        mPerThreadStats = Maps.newHashMapWithExpectedSize(20);
    }

    public String getFullName() {
        if (mFullName == null) {
            mFullName = String.format(Locale.US, "%s.%s: %s", className, methodName, signature);
        }
        return mFullName;
    }

    public String getShortName() {
        if (mShortName == null) {
            mShortName = String.format(Locale.US, "%s.%s", getUnqualifiedClassName(), methodName);
        }
        return mShortName;
    }

    private String getUnqualifiedClassName() {
        String cn = className;
        int i = cn.lastIndexOf('/');
        if (i > 0) {
            cn = cn.substring(i + 1);
        }
        return cn;
    }

    public long getExclusiveTime(ThreadInfo thread, ClockType clockType) {
        MethodStats stats = mPerThreadStats.get(thread.getId());
        return stats != null ? stats.getExclusiveTime(clockType) : 0;
    }

    public long getInclusiveTime(ThreadInfo thread, ClockType clockType) {
        MethodStats stats = mPerThreadStats.get(thread.getId());
        return stats != null ? stats.getInclusiveTime(clockType) : 0;
    }

    public void addExclusiveTime(long time, ThreadInfo thread, ClockType clockType) {
        mAllThreadsStats.addExclusiveTime(time, clockType);

        MethodStats stats = getMethodStats(thread, true);
        stats.addExclusiveTime(time, clockType);
    }

    public void addInclusiveTime(long time, ThreadInfo thread, ClockType clockType) {
        mAllThreadsStats.addInclusiveTime(time, clockType);

        MethodStats stats = getMethodStats(thread, true);
        stats.addInclusiveTime(time, clockType);
    }

    private MethodStats getMethodStats(ThreadInfo thread, boolean createIfAbsent) {
        MethodStats stats = mPerThreadStats.get(thread.getId());
        if (stats == null && createIfAbsent) {
            stats = new MethodStats();
            mPerThreadStats.put(thread.getId(), stats);
        }
        return stats;
    }

    private static class MethodStats {
        private long mInclusiveThreadTime;
        private long mExclusiveThreadTime;

        private long mInclusiveGlobalTime;
        private long mExclusiveGlobalTime;

        public long getInclusiveTime(ClockType clockType) {
            return clockType == ClockType.THREAD ? mInclusiveThreadTime : mInclusiveGlobalTime;
        }

        public long getExclusiveTime(ClockType clockType) {
            return clockType == ClockType.THREAD ? mExclusiveThreadTime : mExclusiveGlobalTime;
        }

        public void addInclusiveTime(long time, ClockType clockType) {
            if (clockType == ClockType.THREAD) {
                mInclusiveThreadTime += time;
            } else {
                mInclusiveGlobalTime += time;
            }
        }

        public void addExclusiveTime(long time, ClockType clockType) {
            if (clockType == ClockType.THREAD) {
                mExclusiveThreadTime += time;
            } else {
                mExclusiveGlobalTime += time;
            }
        }
    }
}
