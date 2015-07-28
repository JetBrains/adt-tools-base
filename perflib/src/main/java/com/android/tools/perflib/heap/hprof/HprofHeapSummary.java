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

public class HprofHeapSummary implements HprofRecord {
    public static final byte TAG = 0x07;

    public final int time;
    public final int totalLiveBytes;             // u4
    public final int totalLiveInstances;         // u4
    public final long totalBytesAllocated;       // u8
    public final long totalInstancesAllocated;   // u8

    public HprofHeapSummary(int time, int totalLiveBytes,
            int totalLiveInstances, long totalBytesAllocated,
            long totalInstancesAllocated) {
        this.time = time;
        this.totalLiveBytes = totalLiveBytes;
        this.totalLiveInstances = totalLiveInstances;
        this.totalBytesAllocated = totalBytesAllocated;
        this.totalInstancesAllocated = totalInstancesAllocated;
    }

    public void write(HprofOutputStream hprof) throws IOException {
        hprof.writeRecordHeader(TAG, time, 4 + 4 + 8 + 8);
        hprof.writeU4(totalLiveBytes);
        hprof.writeU4(totalLiveInstances);
        hprof.writeU8(totalBytesAllocated);
        hprof.writeU8(totalInstancesAllocated);
    }
}
