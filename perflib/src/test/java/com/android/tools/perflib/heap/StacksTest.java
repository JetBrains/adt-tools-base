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

public class StacksTest extends TestCase {

    public void testStackTraces() throws IOException {
        // Set up a heap dump with 3 stack frames, 2 stack traces, one class
        // with two instances, where each instance is allocated from a different
        // stack. Then test we can access the stack traces and frames for the
        // instances.
        HprofStringBuilder strings = new HprofStringBuilder(0);
        List<HprofRecord> records = new ArrayList<HprofRecord>();
        List<HprofDumpRecord> dump = new ArrayList<HprofDumpRecord>();

        final int fooClassSerialNumber = 1;
        records.add(new HprofLoadClass(0, 0, fooClassSerialNumber, 0,
                    strings.get("Foo")));
        dump.add(new HprofClassDump(fooClassSerialNumber, 0, 0, 0, 0, 0, 0, 0, 0,
                    new HprofConstant[0], new HprofStaticField[0],
                    new HprofInstanceField[0]));

        records.add(new HprofStackFrame(0, 1, strings.get("method1"),
                    strings.get("method1(int)"), strings.get("Foo.java"),
                    fooClassSerialNumber, 13));
        records.add(new HprofStackFrame(0, 2, strings.get("method2"),
                    strings.get("method2(double)"), strings.get("Foo.java"),
                    fooClassSerialNumber, 23));
        records.add(new HprofStackFrame(0, 3, strings.get("method3"),
                    strings.get("method3(long)"), strings.get("Foo.java"),
                    fooClassSerialNumber, 33));
        records.add(new HprofStackTrace(0, 0x51, 1, new long[]{1, 3}));
        records.add(new HprofStackTrace(0, 0x52, 1, new long[]{2}));

        dump.add(new HprofHeapDumpInfo(0xA, strings.get("heapA")));
        dump.add(new HprofInstanceDump(0xA1, 0x51, fooClassSerialNumber, new byte[0]));

        dump.add(new HprofHeapDumpInfo(0xB, strings.get("heapB")));
        dump.add(new HprofInstanceDump(0xB2, 0x52, fooClassSerialNumber, new byte[0]));
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

        Instance a1 = snapshot.findInstance(0xA1);
        StackTrace a1stack = a1.getStack();
        assertEquals(0x51 , a1stack.getSerialNumber());
        StackFrame[] a1frames = a1stack.getFrames();
        assertEquals(2, a1frames.length);
        assertEquals("method1", a1frames[0].getMethodName());
        assertEquals("method1(int)", a1frames[0].getSignature());
        assertEquals("Foo.java", a1frames[0].getFilename());
        assertEquals(13, a1frames[0].getLineNumber());
        assertEquals("method3", a1frames[1].getMethodName());
        assertEquals("method3(long)", a1frames[1].getSignature());
        assertEquals("Foo.java", a1frames[1].getFilename());
        assertEquals(33, a1frames[1].getLineNumber());

        Instance b2 = snapshot.findInstance(0xB2);
        StackTrace b2stack = b2.getStack();
        assertEquals(0x52 , b2stack.getSerialNumber());
        StackFrame[] b2frames = b2stack.getFrames();
        assertEquals(1, b2frames.length);
        assertEquals("method2", b2frames[0].getMethodName());
        assertEquals("method2(double)", b2frames[0].getSignature());
        assertEquals("Foo.java", b2frames[0].getFilename());
        assertEquals(23, b2frames[0].getLineNumber());
    }
}
