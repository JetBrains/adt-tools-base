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

public class HprofStartThread implements HprofRecord {
    public static final byte TAG = 0x0A;

    public final int time;
    public final int threadSerialNumber;        // u4
    public final long threadObjectId;           // ID
    public final int stackTraceSerialNumber;    // u4
    public final long threadNameStringId;       // ID
    public final long threadGroupNameId;        // ID
    public final long threadParentGroupNameId;  // ID

    public HprofStartThread(int time, int threadSerialNumber,
            long threadObjectId, int stackTraceSerialNumber, long threadNameStringId,
            long threadGroupNameId, long threadParentGroupNameId) {
        this.time = time;
        this.threadSerialNumber = threadSerialNumber;
        this.threadObjectId = threadObjectId;
        this.stackTraceSerialNumber = stackTraceSerialNumber;
        this.threadNameStringId = threadNameStringId;
        this.threadGroupNameId = threadGroupNameId;
        this.threadParentGroupNameId = threadParentGroupNameId;
    }

    public void write(HprofOutputStream hprof) throws IOException {
        int id = hprof.getIdSize();
        hprof.writeRecordHeader(TAG, time, 4 + id + 4 + id + id + id);
        hprof.writeU4(threadSerialNumber);
        hprof.writeId(threadObjectId);
        hprof.writeU4(stackTraceSerialNumber);
        hprof.writeId(threadNameStringId);
        hprof.writeId(threadGroupNameId);
        hprof.writeId(threadParentGroupNameId);
    }
}
