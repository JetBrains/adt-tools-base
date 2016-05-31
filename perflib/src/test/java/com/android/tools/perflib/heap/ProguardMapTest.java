/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.tools.perflib.heap.io.InMemoryBuffer;
import com.android.tools.perflib.heap.hprof.*;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;

import junit.framework.TestCase;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class ProguardMapTest extends TestCase {
    private static final String TEST_MAP =
        "class.that.is.Empty -> a:\n"
      + "class.that.is.Empty$subclass -> b:\n"
      + "class.with.only.Fields -> c:\n"
      + "    int prim_type_field -> a\n"
      + "    int[] prim_array_type_field -> b\n"
      + "    class.that.is.Empty class_type_field -> c\n"
      + "    class.that.is.Empty[] array_type_field -> d\n"
      + "    int longObfuscatedNameField -> abc\n"
      + "class.with.Methods -> d:\n"
      + "    int some_field -> a\n"
      + "    12:23:void <clinit>() -> <clinit>\n"
      + "    42:43:void boringMethod() -> m\n"
      + "    45:48:void methodWithPrimArgs(int,float) -> m\n"
      + "    49:50:void methodWithPrimArrArgs(int[],float) -> m\n"
      + "    52:55:void methodWithClearObjArg(class.not.in.Map) -> m\n"
      + "    57:58:void methodWithClearObjArrArg(class.not.in.Map[]) -> m\n"
      + "    59:61:void methodWithObfObjArg(class.with.only.Fields) -> m\n"
      + "    64:66:class.with.only.Fields methodWithObfRes() -> n\n"
      + "    80:80:void lineObfuscatedMethod():8:8 -> o\n"
      + "    90:90:void lineObfuscatedMethod2():9 -> p\n"
      + "    120:121:void method.from.a.Superclass.supermethod() -> q\n"
      ;

    public void testProguardMap() throws IOException, ParseException {
        ProguardMap map = new ProguardMap();

        // An empty proguard map should not deobfuscate anything.
        assertEquals("foo.bar.Sludge", map.getClassName("foo.bar.Sludge"));
        assertEquals("fooBarSludge", map.getClassName("fooBarSludge"));
        assertEquals("myfield", map.getFieldName("foo.bar.Sludge", "myfield"));
        assertEquals("myfield", map.getFieldName("fooBarSludge", "myfield"));
        ProguardMap.Frame frame = map.getFrame(
                "foo.bar.Sludge", "mymethod", "(Lfoo/bar/Sludge;)V", "SourceFile.java", 123);
        assertEquals("mymethod", frame.methodName);
        assertEquals("(Lfoo/bar/Sludge;)V", frame.signature);
        assertEquals("SourceFile.java", frame.filename);
        assertEquals(123, frame.line);

        // Read in the proguard map.
        map.readFromReader(new StringReader(TEST_MAP));

        // It should still not deobfuscate things that aren't in the map
        assertEquals("foo.bar.Sludge", map.getClassName("foo.bar.Sludge"));
        assertEquals("fooBarSludge", map.getClassName("fooBarSludge"));
        assertEquals("myfield", map.getFieldName("foo.bar.Sludge", "myfield"));
        assertEquals("myfield", map.getFieldName("fooBarSludge", "myfield"));
        frame = map.getFrame("foo.bar.Sludge", "mymethod", "(Lfoo/bar/Sludge;)V",
                "SourceFile.java", 123);
        assertEquals("mymethod", frame.methodName);
        assertEquals("(Lfoo/bar/Sludge;)V", frame.signature);
        assertEquals("SourceFile.java", frame.filename);
        assertEquals(123, frame.line);

        // Test deobfuscated of class names
        assertEquals("class.that.is.Empty", map.getClassName("a"));
        assertEquals("class.that.is.Empty$subclass", map.getClassName("b"));
        assertEquals("class.with.only.Fields", map.getClassName("c"));
        assertEquals("class.with.Methods", map.getClassName("d"));

        // Test deobfuscated of methods
        assertEquals("prim_type_field", map.getFieldName("class.with.only.Fields", "a"));
        assertEquals("prim_array_type_field", map.getFieldName("class.with.only.Fields", "b"));
        assertEquals("class_type_field", map.getFieldName("class.with.only.Fields", "c"));
        assertEquals("array_type_field", map.getFieldName("class.with.only.Fields", "d"));
        assertEquals("longObfuscatedNameField", map.getFieldName("class.with.only.Fields", "abc"));
        assertEquals("some_field", map.getFieldName("class.with.Methods", "a"));

        // Test deobfuscated of frames
        frame = map.getFrame("class.with.Methods", "<clinit>", "()V", "SourceFile.java", 13);
        assertEquals("<clinit>", frame.methodName);
        assertEquals("()V", frame.signature);
        assertEquals("Methods.java", frame.filename);
        assertEquals(13, frame.line);

        frame = map.getFrame("class.with.Methods", "m", "()V", "SourceFile.java", 42);
        assertEquals("boringMethod", frame.methodName);
        assertEquals("()V", frame.signature);
        assertEquals("Methods.java", frame.filename);
        assertEquals(42, frame.line);

        frame = map.getFrame("class.with.Methods", "m", "(IF)V", "SourceFile.java", 45);
        assertEquals("methodWithPrimArgs", frame.methodName);
        assertEquals("(IF)V", frame.signature);
        assertEquals("Methods.java", frame.filename);
        assertEquals(45, frame.line);

        frame = map.getFrame("class.with.Methods", "m", "([IF)V", "SourceFile.java", 49);
        assertEquals("methodWithPrimArrArgs", frame.methodName);
        assertEquals("([IF)V", frame.signature);
        assertEquals("Methods.java", frame.filename);
        assertEquals(49, frame.line);

        frame = map.getFrame("class.with.Methods", "m", "(Lclass/not/in/Map;)V",
            "SourceFile.java", 52);
        assertEquals("methodWithClearObjArg", frame.methodName);
        assertEquals("(Lclass/not/in/Map;)V", frame.signature);
        assertEquals("Methods.java", frame.filename);
        assertEquals(52, frame.line);

        frame = map.getFrame("class.with.Methods", "m", "([Lclass/not/in/Map;)V",
            "SourceFile.java", 57);
        assertEquals("methodWithClearObjArrArg", frame.methodName);
        assertEquals("([Lclass/not/in/Map;)V", frame.signature);
        assertEquals("Methods.java", frame.filename);
        assertEquals(57, frame.line);

        frame = map.getFrame("class.with.Methods", "m", "(Lc;)V", "SourceFile.java", 59);
        assertEquals("methodWithObfObjArg", frame.methodName);
        assertEquals("(Lclass/with/only/Fields;)V", frame.signature);
        assertEquals("Methods.java", frame.filename);
        assertEquals(59, frame.line);

        frame = map.getFrame("class.with.Methods", "n", "()Lc;", "SourceFile.java", 64);
        assertEquals("methodWithObfRes", frame.methodName);
        assertEquals("()Lclass/with/only/Fields;", frame.signature);
        assertEquals("Methods.java", frame.filename);
        assertEquals(64, frame.line);

        frame = map.getFrame("class.with.Methods", "o", "()V", "SourceFile.java", 80);
        assertEquals("lineObfuscatedMethod", frame.methodName);
        assertEquals("()V", frame.signature);
        assertEquals("Methods.java", frame.filename);
        assertEquals(8, frame.line);

        frame = map.getFrame("class.with.Methods", "p", "()V", "SourceFile.java", 94);
        assertEquals("lineObfuscatedMethod2", frame.methodName);
        assertEquals("()V", frame.signature);
        assertEquals("Methods.java", frame.filename);
        assertEquals(13, frame.line);

        frame = map.getFrame("class.with.Methods", "q", "()V", "SourceFile.java", 120);
        // TODO: Should this be "supermethod", instead of
        // "method.from.a.Superclass.supermethod"?
        assertEquals("method.from.a.Superclass.supermethod", frame.methodName);
        assertEquals("()V", frame.signature);
        assertEquals("Superclass.java", frame.filename);
        assertEquals(120, frame.line);
    }

    public void testHprofParser() throws IOException, ParseException {
        // Set up a heap dump with a single stack frame, stack trace, class,
        // and instance to test deobfuscation.
        HprofStringBuilder strings = new HprofStringBuilder(0);
        List<HprofRecord> records = new ArrayList<HprofRecord>();
        List<HprofDumpRecord> dump = new ArrayList<HprofDumpRecord>();

        final int classSerialNumber = 1;
        final int classObjectId = 2;
        records.add(new HprofLoadClass(0, classSerialNumber, classObjectId, 0, strings.get("d")));
        dump.add(new HprofClassDump(classObjectId, 0, 0, 0, 0, 0, 0, 0, 4,
                    new HprofConstant[0], new HprofStaticField[0],
                    new HprofInstanceField[]{
                      new HprofInstanceField(strings.get("a"), HprofType.TYPE_INT)}));

        records.add(new HprofStackFrame(0, 1, strings.get("m"),
                    strings.get("()V"), strings.get("SourceFile.java"),
                    classSerialNumber, 43));
        records.add(new HprofStackTrace(0, 0x52, 1, new long[]{1}));

        dump.add(new HprofHeapDumpInfo(0xA, strings.get("heapA")));

        ByteArrayDataOutput values = ByteStreams.newDataOutput();
        values.writeInt(42);
        dump.add(new HprofInstanceDump(0xA1, 0x52, classObjectId, values.toByteArray()));
        records.add(new HprofHeapDump(0, dump.toArray(new HprofDumpRecord[0])));

        // TODO: When perflib can handle the case where strings are referred to
        // before they are defined, just add the string records to the records
        // list.
        List<HprofRecord> actualRecords = new ArrayList<HprofRecord>();
        actualRecords.addAll(strings.getStringRecords());
        actualRecords.addAll(records);

        Hprof hprof = new Hprof("JAVA PROFILE 1.0.3", 2, new Date(), actualRecords);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        hprof.write(os);
        InMemoryBuffer buffer = new InMemoryBuffer(os.toByteArray());

        ProguardMap map = new ProguardMap();
        map.readFromReader(new StringReader(TEST_MAP));

        Snapshot snapshot = Snapshot.createSnapshot(buffer, map);

        ClassInstance inst = (ClassInstance)snapshot.findInstance(0xA1);
        ClassObj cls = inst.getClassObj();
        assertEquals("class.with.Methods", cls.getClassName());

        Field[] fields = cls.getFields();
        assertEquals(1, fields.length);
        assertEquals("some_field", fields[0].getName());

        StackTrace stack = inst.getStack();
        StackFrame[] frames = stack.getFrames();
        assertEquals(1, frames.length);
        assertEquals("boringMethod", frames[0].getMethodName());
        assertEquals("()V", frames[0].getSignature());
        assertEquals("Methods.java", frames[0].getFilename());
        assertEquals(43, frames[0].getLineNumber());
    }
}
