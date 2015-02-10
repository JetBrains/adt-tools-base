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
import com.google.common.collect.ImmutableList;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Facility to record block execution time on a single thread. Threads should not be spawned during
 * the block execution as its processing will not be recorded as of the parent's execution time.
 *
 * // TODO : provide facilities to create a new ThreadRecorder using a parent so the slave threads
 * can be connected to the parent's task.
 */
public class ThreadRecorder {

    private static final Logger logger = Logger.getLogger(ThreadRecorder.class.getName());


    public static Recorder get() {
        return recorder.get();
    }

    private static final ThreadLocal<Recorder> recorder = new ThreadLocal<Recorder>() {
        @Override
        protected Recorder initialValue() {

            class PartialRecord {
                final ExecutionType executionType;
                final long recordId;
                final long parentRecordId;
                final long startTimeInMs;

                final List<Recorder.Property> extraArgs;

                PartialRecord(ExecutionType executionType,
                        long recordId,
                        long parentId,
                        long startTimeInMs,
                        List<Recorder.Property> extraArgs) {
                    this.executionType = executionType;
                    this.recordId = recordId;
                    this.parentRecordId = parentId;
                    this.startTimeInMs = startTimeInMs;
                    this.extraArgs = extraArgs;
                }
            }

            final long threadId = Thread.currentThread().getId();
            final Deque<PartialRecord> stackOfRecords = new ArrayDeque<PartialRecord>();
            final AtomicLong recordId = new AtomicLong(0); 
                    

            return new Recorder() {
                @Override
                public <T> T record(@NonNull ExecutionType executionType,
                        @NonNull Block<T> block, Property... properties) {

                    long thisRecordId = recordId.incrementAndGet();
                    
                    // am I a child ?
                    PartialRecord parentRecord = stackOfRecords.peek();
                    long parentRecordId = parentRecord == null ? 0 : parentRecord.recordId;

                    List<Recorder.Property> propertyList = properties == null
                            ? ImmutableList.<Recorder.Property>of()
                            : ImmutableList.copyOf(properties);

                    long startTimeInMs = System.currentTimeMillis();

                    PartialRecord partialRecord = new PartialRecord(executionType,
                            thisRecordId, parentRecordId,
                            startTimeInMs, propertyList);

                    stackOfRecords.push(partialRecord);
                    try {
                        return block.call();
                    } catch (Exception e) {
                        block.handleException(e);
                    } finally {
                        PartialRecord currentRecord = stackOfRecords.pop();
                        // check that our current record is the one we are expecting.
                        if (currentRecord.executionType != executionType ||
                                currentRecord.startTimeInMs != startTimeInMs) {
                            // records got messed up, probably some threading issues...
                            logger.log(Level.SEVERE, "messed up !");
                        }
                        ProcessRecorder.get().writeRecord(
                                new ExecutionRecord(currentRecord.recordId,
                                        currentRecord.parentRecordId,
                                        currentRecord.startTimeInMs,
                                        System.currentTimeMillis() - currentRecord.startTimeInMs,
                                        currentRecord.executionType,
                                        currentRecord.extraArgs));
                    }
                    // we always return null when an exception occurred and was not rethrown.
                    return null;
                }
            };
        }
    };

}
