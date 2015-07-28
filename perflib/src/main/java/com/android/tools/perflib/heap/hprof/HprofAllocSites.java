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

public class HprofAllocSites implements HprofRecord {
    public static final byte TAG = 0x06;

    // Bit mask flags:
    public static final short INCREMENTAL_VS_COMPLETE = 0x1;
    public static final short SORTED_BY_ALLOCATION_VS_LINE = 0x2;
    public static final short FORCE_GC = 0x4;

    public final int time;
    public final short bitMaskFlags;            // u2
    public final int cutoffRatio;               // u4
    public final int totalLiveBytes;            // u4
    public final int totalLiveInstances;        // u4
    public final long totalBytesAllocated;      // u8
    public final long totalInstancesAllocated;  // u8
    public final HprofAllocSite[] sites;        // u4 (length) + [AllocSite]*

    public HprofAllocSites(int time, short bitMaskFlags, int cutoffRatio,
            int totalLiveBytes, int totalLiveInstances, long totalBytesAllocated,
            long totalInstancesAllocated, HprofAllocSite[] sites) {
        this.time = time;
        this.bitMaskFlags = bitMaskFlags;
        this.cutoffRatio = cutoffRatio;
        this.totalLiveBytes = totalLiveBytes;
        this.totalLiveInstances = totalLiveInstances;
        this.totalBytesAllocated = totalBytesAllocated;
        this.totalInstancesAllocated = totalInstancesAllocated;
        this.sites = sites;
    }

    public void write(HprofOutputStream hprof) throws IOException {
        hprof.writeRecordHeader(TAG, time,
                2 + 4 + 4 + 4 + 8 + 8 + 4 + sites.length*HprofAllocSite.LENGTH);
        hprof.writeU2(bitMaskFlags);
        hprof.writeU4(cutoffRatio);
        hprof.writeU4(totalLiveBytes);
        hprof.writeU4(totalLiveInstances);
        hprof.writeU8(totalBytesAllocated);
        hprof.writeU8(totalInstancesAllocated);
        hprof.writeU4(sites.length);
        for (HprofAllocSite site : sites) {
            site.write(hprof);
        }
    }
}
