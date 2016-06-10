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

package com.android.builder.profile;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.wireless.android.sdk.stats.AndroidStudioStats;
import com.google.wireless.android.sdk.stats.AndroidStudioStats.GradleBuildProfileSpan.ExecutionType;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Facility to record block execution time on a single thread. Threads should not be spawned during
 * the block execution as its processing will not be recorded as of the parent's execution time.
 *
 * // TODO : provide facilities to create a new ThreadRecorder using a parent so the slave threads
 * can be connected to the parent's task.
 */
public class ThreadRecorder implements Recorder {

    private static final Logger logger = Logger.getLogger(ThreadRecorder.class.getName());

    // Dummy implementation that records nothing but comply to the overall recording contracts.
    protected static final Recorder dummyRecorder = new Recorder() {

        @Nullable
        @Override
        public <T> T record(@NonNull ExecutionType executionType, @NonNull String project,
                @Nullable String variant, @NonNull Block<T> block) {
            try {
                return block.call();
            } catch (Exception e) {
                block.handleException(e);
            }
            return null;
        }

        @Nullable
        @Override
        public <T> T record(
                @NonNull ExecutionType executionType,
                @Nullable AndroidStudioStats.GradleTransformExecution transform,
                @NonNull String project, @Nullable String variant, @NonNull Block<T> block) {
            return record(executionType, project, variant, block);
        }

        @Override
        public long allocationRecordId() {
            return 0;
        }


        @Override
        public void closeRecord(@NonNull String project, @Nullable String variant,
                @NonNull AndroidStudioStats.GradleBuildProfileSpan.Builder executionRecord) {
        }
    };

    private static final Recorder recorder = new ThreadRecorder();


    public static Recorder get() {
        return ProcessRecorderFactory.getFactory().isInitialized() ? recorder : dummyRecorder;
    }

    /**
     * Do not put anything else than JDK classes in the ThreadLocal as it prevents that class
     * and therefore the plugin classloader to be gc'ed leading to OOM or PermGen issues.
     */
    protected final ThreadLocal<Deque<Long>> recordStacks =
            new ThreadLocal<Deque<Long>>() {
        @Override
        protected Deque<Long> initialValue() {
            return new ArrayDeque<>();
        }
    };


    @Override
    public long allocationRecordId() {
        long recordId = ProcessRecorder.allocateRecordId();
        recordStacks.get().push(recordId);
        return recordId;
    }

    @Override
    public void closeRecord(
            @NonNull String project,
            @Nullable String variant,
            @NonNull AndroidStudioStats.GradleBuildProfileSpan.Builder executionRecord) {
        if (recordStacks.get().pop() != executionRecord.getId()) {
            logger.severe("Internal Error : mixed records in profiling stack");
        }
        ProcessRecorder.get().writeRecord(project, variant, executionRecord);
    }


    @Nullable
    @Override
    public <T> T record(
            @NonNull ExecutionType executionType,
            @NonNull String project, @Nullable String variant, @NonNull Block<T> block) {
        return record(executionType, null, project, variant, block);
    }

    @Nullable
    @Override
    public <T> T record(
            @NonNull ExecutionType executionType,
            @Nullable AndroidStudioStats.GradleTransformExecution transform,
            @NonNull String project,
            @Nullable String variant,
            @NonNull Block<T> block) {

        long thisRecordId = ProcessRecorder.allocateRecordId();

        // am I a child ?
        @Nullable
        Long parentId = recordStacks.get().peek();

        long startTimeInMs = System.currentTimeMillis();

        final AndroidStudioStats.GradleBuildProfileSpan.Builder currentRecord =
                AndroidStudioStats.GradleBuildProfileSpan.newBuilder()
                        .setId(thisRecordId)
                        .setType(executionType)
                        .setStartTimeInMs(startTimeInMs);

        if (transform != null) {
            currentRecord.setTransform(transform);
        }

        if (parentId != null) {
            currentRecord.setParentId(parentId);
        }

        recordStacks.get().push(thisRecordId);
        try {
            return block.call();
        } catch (Exception e) {
            block.handleException(e);
        } finally {
            // pop this record from the stack.
            if (recordStacks.get().pop() != currentRecord.getId()) {
                logger.log(Level.SEVERE, "Profiler stack corrupted");
            }
            currentRecord.setDurationInMs(
                    System.currentTimeMillis() - currentRecord.getStartTimeInMs());
            ProcessRecorder.get().writeRecord(project, variant, currentRecord);
        }
        // we always return null when an exception occurred and was not rethrown.
        return null;
    }
}
