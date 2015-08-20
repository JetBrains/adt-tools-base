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

package com.android.tools.perflib.heap.hprof;

import java.io.IOException;

public class HprofStackTrace implements HprofRecord {
    public static final byte TAG = 0x05;

    public final int time;
    public final int stackTraceSerialNumber;   // u4
    public final int threadSerialNumber;       // u4
    public final long[] stackFrameIds;         // u4 (length) + [ID]*

    public HprofStackTrace(int time, int stackTraceSerialNumber,
            int threadSerialNumber, long[] stackFrameIds) {
        this.time = time;
        this.stackTraceSerialNumber = stackTraceSerialNumber;
        this.threadSerialNumber = threadSerialNumber;
        this.stackFrameIds = stackFrameIds;
    }

    public void write(HprofOutputStream hprof) throws IOException {
        int id = hprof.getIdSize();
        int u4 = 4;
        hprof.writeRecordHeader(TAG, time, u4 + u4 + u4 + stackFrameIds.length*id);
        hprof.writeU4(stackTraceSerialNumber);
        hprof.writeU4(threadSerialNumber);
        hprof.writeU4(stackFrameIds.length);
        for (long frameId : stackFrameIds) {
            hprof.writeId(frameId);
        }
    }
}
