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
import java.io.OutputStream;
import java.util.Date;
import java.util.List;

/**
 * A structure representing heap dump data in the hprof format.
 */
public class Hprof {
    public final String format;
    public final int idSize;
    public final Date date;
    public final List<HprofRecord> records;

    /**
     * Construct an HprofBuilder with the given format, identifier size, date
     * date, and records
     * @param format - "JAVA PROFILE 1.0.3", for example.
     * @param idSize - The size of identifiers in bytes.
     * @param date - The date of the hprof dump.
     * @param records - The list of hprof records.
     */
    public Hprof(String format, int idSize, Date date, List<HprofRecord> records) {
        this.format = format;
        this.idSize = idSize;
        this.date = date;
        this.records = records;
    }

    /**
     * Write the hprof data to the given output stream in binary format.
     * @param filename - The file to write to.
     */
    public void write(OutputStream os) throws IOException {
        HprofOutputStream hprof = new HprofOutputStream(idSize, os);
        hprof.write(format.getBytes(Charsets.US_ASCII));
        hprof.write(0);
        hprof.writeU4(idSize);

        long time = date.getTime();
        hprof.writeU4((int)(time >> 32));
        hprof.writeU4((int)(time >> 0));

        for (HprofRecord record : records) {
            record.write(hprof);
        }

        hprof.flush();
        hprof.close();
    }
}
