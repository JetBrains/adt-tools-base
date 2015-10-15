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

import com.google.common.base.Charsets;
import java.io.IOException;

public class HprofString implements HprofRecord {
    public static final byte TAG = 0x01;
    public final int time;
    public final long id;           // ID
    public final String string;     // [u1]*

    public HprofString(int time, long id, String string) {
        this.time = time;
        this.id = id;
        this.string = string;
    }

    public void write(HprofOutputStream hprof) throws IOException {
        byte[] bytes = string.getBytes(Charsets.UTF_8);
        hprof.writeRecordHeader(TAG, time, hprof.getIdSize() + bytes.length);
        hprof.writeId(id);
        hprof.write(bytes);
    }
}
