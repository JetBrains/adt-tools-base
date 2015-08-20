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

public class HprofStaticField {

    public final long staticFieldNameStringId;     // Id
    public final byte typeOfField;     // u1
    public final long value;           // size depends on typeOfField.

    public HprofStaticField(long staticFieldNameStringId, byte typeOfField,
            long value) {
        this.staticFieldNameStringId = staticFieldNameStringId;
        this.typeOfField = typeOfField;
        this.value = value;
    }

    public void write(HprofOutputStream hprof) throws IOException {
        hprof.writeId(staticFieldNameStringId);
        hprof.writeU1(typeOfField);
        hprof.writeValue(typeOfField, value);
    }

    public int getLength(int idSize) {
        return idSize + 1 + HprofType.sizeOf(typeOfField, idSize);
    }
}
