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

public class HprofLoadClass implements HprofRecord {
    public static final byte TAG = 0x02;
    public final int time;
    public final int classSerialNumber;          // u4
    public final long classObjectId;             // ID
    public final int stackTraceSerialNumber;     // u4
    public final long classNameStringId;         // ID

    public HprofLoadClass(int time, int classSerialNumber, long classObjectId, 
            int stackTraceSerialNumber, long classNameStringId) {
        this.time = time;
        this.classSerialNumber = classSerialNumber;
        this.classObjectId = classObjectId;
        this.stackTraceSerialNumber = stackTraceSerialNumber;
        this.classNameStringId = classNameStringId;
    }

    public void write(HprofOutputStream hprof) throws IOException {
        int id = hprof.getIdSize();
        hprof.writeRecordHeader(TAG, time, 4 + id + 4 + id);
        hprof.writeU4(classSerialNumber);
        hprof.writeId(classObjectId);
        hprof.writeU4(stackTraceSerialNumber);
        hprof.writeId(classNameStringId);
    }
}
