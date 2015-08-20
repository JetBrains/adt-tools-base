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

public class HprofConstant {

    public final short constantPoolIndex;   // u2
    public final byte typeOfEntry;          // u1
    public final long value;                // size depends on typeOfEntry.

    public HprofConstant(short constantPoolIndex, byte typeOfEntry, long value) {
        this.constantPoolIndex = constantPoolIndex;
        this.typeOfEntry = typeOfEntry;
        this.value = value;
    }

    public void write(HprofOutputStream hprof) throws IOException {
        hprof.writeU2(constantPoolIndex);
        hprof.writeU1(typeOfEntry);
        hprof.writeValue(typeOfEntry, value);
    }

    public int getLength(int idSize) {
        return 2 + 1 + HprofType.sizeOf(typeOfEntry, idSize);
    }
}
