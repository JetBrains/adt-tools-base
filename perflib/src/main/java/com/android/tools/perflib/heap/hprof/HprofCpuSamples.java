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

public class HprofCpuSamples implements HprofRecord {
    public static final byte TAG = 0x0D;

    public final int time;
    public final int totalNumberOfSamples;    // u4
    public final HprofCpuSample[] samples;    // u4 (length) + [CpuSample]*

    public HprofCpuSamples(int time, int totalNumberOfSamples,
            HprofCpuSample[] samples) {
        this.time = time;
        this.totalNumberOfSamples = totalNumberOfSamples;
        this.samples = samples;
    }

    public void write(HprofOutputStream hprof) throws IOException {
        hprof.writeRecordHeader(TAG, time, 4 + 4 + samples.length*HprofCpuSample.LENGTH);
        hprof.writeU4(totalNumberOfSamples);
        hprof.writeU4(samples.length);
        for (HprofCpuSample sample : samples) {
            sample.write(hprof);
        }
    }
}
