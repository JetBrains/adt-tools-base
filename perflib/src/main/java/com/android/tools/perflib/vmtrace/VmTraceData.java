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

import com.android.utils.SparseArray;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * The {@link VmTraceData} class stores all the information from a Dalvik method trace file.
 * Specifically, it provides:
 *  <ul>
 *      <li>A mapping from thread ids to thread names.</li>
 *      <li>A mapping from method ids to {@link MethodInfo}</li>
 *      <li>A mapping from each thread to the top level call on that thread.</li>
 *  </ul>
 */
public class VmTraceData {
    public enum VmClockType { THREAD_CPU, WALL, DUAL }

    private final int mVersion;
    private final boolean mDataFileOverflow;
    private final VmClockType mVmClockType;
    private final String mVm;
    private final Map<String, String> mTraceProperties;

    /** Map from method id to method info. */
    private final Map<Long,MethodInfo> mMethods;

    /** Map from thread name to thread info. */
    private final Map<String, ThreadInfo> mThreadInfo;

    private VmTraceData(Builder b) {
        mVersion = b.mVersion;
        mDataFileOverflow = b.mDataFileOverflow;
        mVmClockType = b.mVmClockType;
        mVm = b.mVm;
        mTraceProperties = b.mProperties;
        mMethods = b.mMethods;

        mThreadInfo = Maps.newHashMapWithExpectedSize(b.mThreads.size());
        for (int i = 0; i < b.mThreads.size(); i++) {
            int id = b.mThreads.keyAt(i);
            String name = b.mThreads.valueAt(i);

            ThreadInfo info = mThreadInfo.get(name);
            if (info != null) {
                // there is alread a thread with the same name
                name = String.format("%1$s-%2$d", name, id);
            }

            info = new ThreadInfo(id, name, b.mTopLevelCalls.get(id));
            mThreadInfo.put(name, info);
        }
    }

    public int getVersion() {
        return mVersion;
    }

    public boolean isDataFileOverflow() {
        return mDataFileOverflow;
    }

    public VmClockType getVmClockType() {
        return mVmClockType;
    }

    public String getVm() {
        return mVm;
    }

    public Map<String, String> getTraceProperties() {
        return mTraceProperties;
    }

    public static TimeUnit getDefaultTimeUnits() {
        // The traces from the VM currently use microseconds.
        // TODO: figure out if this can be obtained/inferred from the trace itself
        return TimeUnit.MICROSECONDS;
    }

    public Collection<ThreadInfo> getThreads() {
        return mThreadInfo.values();
    }

    public List<ThreadInfo> getThreads(boolean excludeThreadsWithNoActivity) {
        Collection<ThreadInfo> allThreads = getThreads();
        if (!excludeThreadsWithNoActivity) {
            return ImmutableList.copyOf(allThreads);
        }

        return Lists.newArrayList(Iterables.filter(allThreads, new Predicate<ThreadInfo>() {
            @Override
            public boolean apply(
                    com.android.tools.perflib.vmtrace.ThreadInfo input) {
                return input.getTopLevelCall() != null;
            }
        }));
    }

    public ThreadInfo getThread(String name) {
        return mThreadInfo.get(name);
    }

    public Map<Long,MethodInfo> getMethods() {
        return mMethods;
    }

    public MethodInfo getMethod(long methodId) {
        return mMethods.get(methodId);
    }

    /** Returns the duration of this call as a percentage of the duration of the top level call. */
    public double getDurationPercentage(Call call, ThreadInfo thread, ClockType clockType,
            boolean inclusiveTime) {
        MethodInfo methodInfo = getMethod(call.getMethodId());
        TimeSelector selector = TimeSelector.create(clockType, inclusiveTime);
        long methodTime = selector.get(methodInfo, thread, TimeUnit.NANOSECONDS);
        return getDurationPercentage(methodTime, thread, clockType);
    }

    /**
     * Returns the given duration as a percentage of the duration of the top level call
     * in given thread.
     */
    public double getDurationPercentage(long methodTime, ThreadInfo thread, ClockType clockType) {
        Call topCall = getThread(thread.getName()).getTopLevelCall();
        if (topCall == null) {
            return 100.;
        }

        MethodInfo topInfo = getMethod(topCall.getMethodId());

        // always use inclusive time to obtain the top level's time when computing percentages
        TimeSelector selector = TimeSelector.create(clockType, true);
        long topLevelTime = selector.get(topInfo, thread, TimeUnit.NANOSECONDS);

        return (double) methodTime/topLevelTime * 100;
    }

    public SearchResult searchFor(String pattern, ThreadInfo thread) {
        pattern = pattern.toLowerCase(Locale.US);

        Set<MethodInfo> methods = new HashSet<MethodInfo>();
        Set<Call> calls = new HashSet<Call>();

        Call topLevelCall = getThread(thread.getName()).getTopLevelCall();
        if (topLevelCall == null) {
            // no matches
            return new SearchResult(methods, calls);
        }

        // Find all methods matching given pattern called on given thread
        for (MethodInfo method: getMethods().values()) {
            String fullName = method.getFullName().toLowerCase(Locale.US);
            if (fullName.contains(pattern)) { // method name matches
                long inclusiveTime = method.getProfileData()
                        .getInclusiveTime(thread, ClockType.GLOBAL, TimeUnit.NANOSECONDS);
                if (inclusiveTime > 0) {
                    // method was called in this thread
                    methods.add(method);
                }
            }
        }

        // Find all invocations of the matched methods
        Iterator<Call> iterator = topLevelCall.getCallHierarchyIterator();
        while (iterator.hasNext()) {
            Call c = iterator.next();
            MethodInfo method = getMethod(c.getMethodId());
            if (methods.contains(method)) {
                calls.add(c);
            }
        }

        return new SearchResult(methods, calls);
    }

    public static class Builder {
        private static final boolean DEBUG = false;

        private int mVersion;
        private boolean mDataFileOverflow;
        private VmClockType mVmClockType = VmClockType.THREAD_CPU;
        private String mVm = "";
        private final Map<String, String> mProperties = new HashMap<String, String>(10);

        /** Map from thread ids to thread names. */
        private final SparseArray<String> mThreads = new SparseArray<String>(10);

        /** Map from method id to method info. */
        private final Map<Long,MethodInfo> mMethods = new HashMap<Long, MethodInfo>(100);

        /** Map from thread id to per thread stack call reconstructor. */
        private final SparseArray<CallStackReconstructor> mStackReconstructors
                = new SparseArray<CallStackReconstructor>(10);

        /** Map from thread id to the top level call for that thread. */
        private final SparseArray<Call> mTopLevelCalls = new SparseArray<Call>(10);

        public void setVersion(int version) {
            mVersion = version;
        }

        public int getVersion() {
            return mVersion;
        }

        public void setDataFileOverflow(boolean dataFileOverflow) {
            mDataFileOverflow = dataFileOverflow;
        }

        public void setVmClockType(VmClockType vmClockType) {
            mVmClockType = vmClockType;
        }

        public VmClockType getVmClockType() {
            return mVmClockType;
        }

        public void setProperty(String key, String value) {
            mProperties.put(key, value);
        }

        public void setVm(String vm) {
            mVm = vm;
        }

        public void addThread(int id, String name) {
            mThreads.put(id, name);
        }

        public void addMethod(long id, MethodInfo info) {
            mMethods.put(id, info);
        }

        public void addMethodAction(int threadId, long methodId, TraceAction methodAction,
                int threadTime, int globalTime) {
            // create thread info if it doesn't exist
            if (mThreads.get(threadId) == null) {
                mThreads.put(threadId, String.format("Thread id: %1$d", threadId));
            }

            // create method info if it doesn't exist
            if (mMethods.get(methodId) == null) {
                MethodInfo info = new MethodInfo(methodId, "unknown", "unknown", "unknown",
                        "unknown", -1);
                mMethods.put(methodId, info);
            }

            if (DEBUG) {
                MethodInfo methodInfo = mMethods.get(methodId);
                System.out.printf("Thread %1$30s: (%2$8x) %3$-40s %4$20s\n",
                        mThreads.get(threadId), methodId, methodInfo.getShortName(), methodAction);
            }

            CallStackReconstructor reconstructor = mStackReconstructors.get(threadId);
            if (reconstructor == null) {
                long topLevelCallId = createUniqueMethodIdForThread(threadId);
                reconstructor = new CallStackReconstructor(topLevelCallId);
                mStackReconstructors.put(threadId, reconstructor);
            }

            reconstructor.addTraceAction(methodId, methodAction, threadTime, globalTime);
        }

        private long createUniqueMethodIdForThread(int threadId) {
            long id = Long.MAX_VALUE - mThreads.indexOfKey(threadId);
            assert mMethods.get(id) == null :
                    "Unexpected error while attempting to create a unique key - key already exists";
            MethodInfo info = new MethodInfo(id, mThreads.get(threadId), "", "", "", 0);
            mMethods.put(id, info);
            return id;
        }

        public VmTraceData build() {
            for (int i = 0; i < mStackReconstructors.size(); i++) {
                int threadId = mStackReconstructors.keyAt(i);
                CallStackReconstructor reconstructor = mStackReconstructors.valueAt(i);
                mTopLevelCalls.put(threadId, reconstructor.getTopLevel());
            }

            return new VmTraceData(this);
        }
    }
}
