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

package com.android.tools.perflib.heap;

import com.android.tools.perflib.heap.hprof.*;
import com.android.tools.perflib.heap.io.InMemoryBuffer;

import junit.framework.TestCase;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class ArrayInstanceTest extends TestCase {

    public void testCharArrayInstance() throws IOException {
        // Set up a heap dump with an array of characters:
        //  {'a', 'b', 'c', 'd'}.
        HprofStringBuilder strings = new HprofStringBuilder(0);
        List<HprofRecord> records = new ArrayList<HprofRecord>();
        List<HprofDumpRecord> dump = new ArrayList<HprofDumpRecord>();

        long chars[] = new long[]{'a', 'b', 'c', 'd'};
        dump.add(new HprofPrimitiveArrayDump(0xA, 0, HprofType.TYPE_CHAR, chars));

        records.add(new HprofHeapDump(0, dump.toArray(new HprofDumpRecord[0])));

        // TODO: When perflib can handle the case where strings are referred to
        // before they are defined, just add the string records to the records
        // list.
        List<HprofRecord> actualRecords = new ArrayList<HprofRecord>();
        actualRecords.addAll(strings.getStringRecords());
        actualRecords.addAll(records);

        Snapshot snapshot = null;
        Hprof hprof = new Hprof("JAVA PROFILE 1.0.3", 2, new Date(), actualRecords);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        hprof.write(os);
        InMemoryBuffer buffer = new InMemoryBuffer(os.toByteArray());
        snapshot = Snapshot.createSnapshot(buffer);

        ArrayInstance a = (ArrayInstance)snapshot.findInstance(0xA);
        assertEquals(4, a.getLength());
        assertEquals(Type.CHAR, a.getArrayType());

        assertArrayEquals(new char[]{'a', 'b', 'c', 'd'}, a.asCharArray(0, 4));
        assertArrayEquals(new char[]{'b', 'c'}, a.asCharArray(1, 2));
        assertArrayEquals(new char[]{}, a.asCharArray(1, 0));
    }

    private static void assertArrayEquals(char[] a, char[] b) {
        assertEquals(a.length, b.length);
        for (int i = 0; i < a.length; i++) {
          assertEquals(a[i], b[i]);
        }
    }
}
