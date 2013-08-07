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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    public static enum ClockType { THREAD_CPU, WALL, DUAL }

    private final int mVersion;
    private final boolean mDataFileOverflow;
    private final ClockType mClockType;
    private final String mVm;
    private final Map<String, String> mTraceProperties;

    /** Map from thread ids to thread names. */
    private final SparseArray<String> mThreads;

    /** Map from method id to method info. */
    private final Map<Long,MethodInfo> mMethods;

    /** Map from thread id to the top level call in that thread */
    private final SparseArray<Call> mCalls;

    private VmTraceData(Builder b) {
        mVersion = b.mVersion;
        mDataFileOverflow = b.mDataFileOverflow;
        mClockType = b.mClockType;
        mVm = b.mVm;
        mTraceProperties = b.mProperties;
        mThreads = b.mThreads;
        mMethods = b.mMethods;
        mCalls = b.mTopLevelCalls;
    }

    public int getVersion() {
        return mVersion;
    }

    public boolean isDataFileOverflow() {
        return mDataFileOverflow;
    }

    public ClockType getClockType() {
        return mClockType;
    }

    public String getVm() {
        return mVm;
    }

    public Map<String, String> getTraceProperties() {
        return mTraceProperties;
    }

    public SparseArray<String> getThreads() {
        return mThreads;
    }

    public Map<Long,MethodInfo> getMethods() {
        return mMethods;
    }

    public MethodInfo getMethod(long methodId) {
        return mMethods.get(methodId);
    }

    public Call getTopLevelCall(int threadId) {
        return mCalls.get(threadId);
    }

    public static class Builder {
        private static final boolean DEBUG = false;

        private int mVersion;
        private boolean mDataFileOverflow;
        private ClockType mClockType = ClockType.THREAD_CPU;
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

        public void setClockType(ClockType clockType) {
            mClockType = clockType;
        }

        public ClockType getClockType() {
            return mClockType;
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
            MethodInfo methodInfo = mMethods.get(methodId);

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
                System.out.println(
                        methodId + ": " + methodAction + ": thread: " + mThreads.get(threadId)
                                + ", method: "
                                + methodInfo.className + "/" + methodInfo.methodName + ":"
                                + methodInfo.signature);
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
