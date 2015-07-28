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

/**
 * This record is specific to Android.
 */
public class HprofHeapDumpInfo implements HprofDumpRecord {
    public static final byte SUBTAG = (byte)0xfe;

    // Heap types:
    public static final int HEAP_DEFAULT = 0;
    public static final int HEAP_ZYGOTE = 0x5A;   // 'Z'
    public static final int HEAP_APP = 0x41;      // 'A'
    public static final int HEAP_IMAGE = 0x49;    // 'I'

    public final int heapType;           // u4
    public final long heapNameStringId;   // Id

    public HprofHeapDumpInfo(int heapType, long heapNameStringId) {
        this.heapType = heapType;
        this.heapNameStringId = heapNameStringId;
    }

    public void write(HprofOutputStream hprof) throws IOException {
        hprof.writeU1(SUBTAG);
        hprof.writeU4(heapType);
        hprof.writeId(heapNameStringId);
    }

    public int getLength(int idSize) {
        return 1 + 4 + idSize;
    }
}
