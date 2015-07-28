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

public class HprofPrimitiveArrayDump implements HprofDumpRecord {
    public static final byte SUBTAG = 0x23;

    public final long arrayObjectId;           // Id
    public final int stackTraceSerialNumber;   // u4
    public final byte elementType;             // u1
    public final long[] elements;              // u4 (size) + [element]*  (not packed)

    public HprofPrimitiveArrayDump(long arrayObjectId,
            int stackTraceSerialNumber, byte elementType, long[] elements) {
        this.arrayObjectId = arrayObjectId;
        this.stackTraceSerialNumber = stackTraceSerialNumber;
        this.elementType = elementType;
        this.elements = elements;
    }

    public void write(HprofOutputStream hprof) throws IOException {
        hprof.writeU1(SUBTAG);
        hprof.writeId(arrayObjectId);
        hprof.writeU4(stackTraceSerialNumber);
        hprof.writeU4(elements.length);
        hprof.writeU1(elementType);
        for (long element : elements) {
            hprof.writeValue(elementType, element);
        }
    }

    public int getLength(int idSize) {
        return 1 + idSize + 4 + 4 + 1 + elements.length * HprofType.sizeOf(elementType, idSize);
    }
}
