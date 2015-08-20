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

public class HprofAllocSite {
    public static final int LENGTH = 1 + 6*4;
    public final byte arrayIndicator;             // u1
    public final int classSerialNumber;           // u4
    public final int stackTraceSerialNumber;      // u4
    public final int numberOfLiveBytes;           // u4
    public final int numberOfLiveInstances;       // u4
    public final int numberOfBytesAllocated;      // u4
    public final int numberOfInstancesAllocated;  // u4

    public HprofAllocSite(byte arrayIndicator, int classSerialNumber,
            int stackTraceSerialNumber, int numberOfLiveBytes,
            int numberOfLiveInstances, int numberOfBytesAllocated,
            int numberOfInstancesAllocated) {
        this.arrayIndicator = arrayIndicator;
        this.classSerialNumber = classSerialNumber;
        this.stackTraceSerialNumber = stackTraceSerialNumber;
        this.numberOfLiveBytes = numberOfLiveBytes;
        this.numberOfLiveInstances = numberOfLiveInstances;
        this.numberOfBytesAllocated = numberOfBytesAllocated;
        this.numberOfInstancesAllocated = numberOfInstancesAllocated;
    }

    public void write(HprofOutputStream hprof) throws IOException {
        hprof.writeU1(arrayIndicator);
        hprof.writeU4(classSerialNumber);
        hprof.writeU4(stackTraceSerialNumber);
        hprof.writeU4(numberOfLiveBytes);
        hprof.writeU4(numberOfLiveInstances);
        hprof.writeU4(numberOfBytesAllocated);
        hprof.writeU4(numberOfInstancesAllocated);
    }
}
