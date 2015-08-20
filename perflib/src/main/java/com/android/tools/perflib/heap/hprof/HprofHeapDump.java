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

public class HprofHeapDump implements HprofRecord {
    public static final byte TAG = 0x0C;

    public final int time;
    public final HprofDumpRecord[] records;

    public HprofHeapDump(int time, HprofDumpRecord[] records) {
        this.time = time;
        this.records = records;
    }

    public void write(HprofOutputStream hprof) throws IOException {
        int idSize = hprof.getIdSize();
        int len = 0;
        for (HprofDumpRecord record : records) {
            len += record.getLength(idSize);
        }
        hprof.writeRecordHeader(TAG, time, len);

        for (HprofDumpRecord record : records) {
            record.write(hprof);
        }
    }
}
