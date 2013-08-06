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

import com.android.annotations.NonNull;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Call {
    private final long mMethodId;

    private final int mEntryThreadTime;
    private final int mEntryGlobalTime;
    private final int mExitGlobalTime;
    private final int mExitThreadTime;

    private final int mDepth;

    private final List<Call> mCallees;

    private Call(Builder builder) {
        mMethodId = builder.mMethodId;

        mEntryThreadTime = builder.mEntryThreadTime;
        mEntryGlobalTime = builder.mEntryGlobalTime;
        mExitThreadTime = builder.mExitThreadTime;
        mExitGlobalTime = builder.mExitGlobalTime;

        mDepth = builder.mDepth;
        if (builder.mCallees == null) {
            mCallees = Collections.emptyList();
        } else {
            List<Call> callees = new ArrayList<Call>(builder.mCallees.size());
            for (Builder b : builder.mCallees) {
                b.setStackDepth(mDepth + 1);
                callees.add(b.build());
            }
            mCallees = new ImmutableList.Builder<Call>().addAll(callees).build();
        }
    }

    public long getMethodId() {
        return mMethodId;
    }

    @NonNull
    public List<Call> getCallees() {
        return mCallees;
    }

    public int getDepth() {
        return mDepth;
    }

    public int getEntryThreadTime() {
        return mEntryThreadTime;
    }

    public int getExitThreadTime() {
        return mExitThreadTime;
    }

    public static class Builder {
        private final long mMethodId;

        private int mEntryThreadTime;
        private int mEntryGlobalTime;
        private int mExitGlobalTime;
        private int mExitThreadTime;

        private int mDepth = 0;

        private List<Builder> mCallees = null;

        public Builder(long methodId) {
            mMethodId = methodId;
        }

        public long getMethodId() {
            return mMethodId;
        }

        public void setMethodEntryTime(int threadTime, int globalTime) {
            mEntryThreadTime = threadTime;
            mEntryGlobalTime = globalTime;
        }

        public void setMethodExitTime(int threadTime, int globalTime) {
            mExitThreadTime = threadTime;
            mExitGlobalTime = globalTime;
        }

        public void addCallee(Builder c) {
            if (mCallees == null) {
                mCallees = new ArrayList<Builder>();
            }
            mCallees.add(c);
        }

        public void setStackDepth(int depth) {
            mDepth = depth;
        }

        public Call build() {
            return new Call(this);
        }
    }

    /**
     * Formats this call and all its call hierarchy using the given {@link com.android.tools.perflib.vmtrace.Call.Formatter} to
     * print the details for each method.
     */
    public String format(Formatter f) {
        StringBuilder sb = new StringBuilder(100);
        printCallHierarchy(sb, f);
        return sb.toString();
    }

    public interface Formatter {
        String format(Call c);
    }

    private static Formatter METHOD_ID_FORMATTER = new Formatter() {
        @Override
        public String format(Call c) {
            return Long.toString(c.getMethodId());
        }
    };

    @Override
    public String toString() {
        return format(METHOD_ID_FORMATTER);
    }

    private void printCallHierarchy(@NonNull StringBuilder sb, Formatter formatter) {
        sb.append(" -> ");
        sb.append(formatter.format(this));

        List<Call> callees = getCallees();

        int lineStart = sb.lastIndexOf("\n");
        int depth = sb.length() - (lineStart + 1);

        for (int i = 0; i < callees.size(); i++) {
            if (i != 0) {
                sb.append("\n");
                sb.append(Strings.repeat(" ", depth));
            }

            Call callee = callees.get(i);
            callee.printCallHierarchy(sb, formatter);
        }
    }
}
