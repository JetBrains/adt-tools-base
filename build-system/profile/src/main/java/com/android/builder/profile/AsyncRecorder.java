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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.logging.Logger;

/**
 * Sync recorder capable of recording asynchronous recording events.
 */
public class AsyncRecorder extends ThreadRecorder {

    private static final Logger logger = Logger.getLogger(AsyncRecorder.class.getName());

    private static final Recorder recorder = new AsyncRecorder();

    public static Recorder get() {
        return ProcessRecorderFactory.getFactory().isInitialized() ? recorder : dummyRecorder;
    }

    @Override
    public void closeRecord(ExecutionRecord executionRecord) {
        // there is no contract that allocationRecordId and closeRecord will be called in the
        // right order to maintain the stack integrity. Therefore, I used an API which makes
        // no assumption on where in the stack the allocated ID is so these Apis can be called
        // in various orders as long as allocationRecordId is called before closeRecord.
        if (!recordStacks.get().removeFirstOccurrence(executionRecord.id)) {
            logger.severe("Internal Error : mixed records in profiling stack");
        }
        ProcessRecorder.get().writeRecord(executionRecord);
    }
}
