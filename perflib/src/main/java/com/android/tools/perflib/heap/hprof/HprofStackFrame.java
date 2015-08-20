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

public class HprofStackFrame implements HprofRecord {
    public static final byte TAG = 0x04;

    // The following special values can be used for lineNumber.
    public static final int NO_LINE_INFO = 0;
    public static final int UNKNOWN_LOCATION = -1;
    public static final int COMPILED_METHOD = -2;
    public static final int NATIVE_METHOD = -3;

    public final int time;
    public final long stackFrameId;             // ID
    public final long methodNameStringId;       // ID
    public final long methodSignatureStringId;  // ID
    public final long sourceFileNameStringId;   // ID
    public final int classSerialNumber;         // u4
    public final int lineNumber;                // u4

    public HprofStackFrame(int time, long stackFrameId, long methodNameStringId,
            long methodSignatureStringId, long sourceFileNameStringId,
            int classSerialNumber, int lineNumber) {
        this.time = time;
        this.stackFrameId = stackFrameId;
        this.methodNameStringId = methodNameStringId;
        this.methodSignatureStringId = methodSignatureStringId;
        this.sourceFileNameStringId = sourceFileNameStringId;
        this.classSerialNumber = classSerialNumber;
        this.lineNumber = lineNumber;
    }

    public void write(HprofOutputStream hprof) throws IOException {
        int id = hprof.getIdSize();
        int u4 = 4;
        hprof.writeRecordHeader(TAG, time, id + id + id + id + u4 + u4);
        hprof.writeId(stackFrameId);
        hprof.writeId(methodNameStringId);
        hprof.writeId(methodSignatureStringId);
        hprof.writeId(sourceFileNameStringId);
        hprof.writeU4(classSerialNumber);
        hprof.writeU4(lineNumber);
    }
}
