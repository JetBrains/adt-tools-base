/*
 * Copyright (C) 2008 Google Inc.
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

import com.android.annotations.NonNull;
import com.android.tools.perflib.captures.DataBuffer;
import com.google.common.primitives.UnsignedBytes;
import com.google.common.primitives.UnsignedInts;

import java.io.EOFException;
import java.io.IOException;

import gnu.trove.TLongObjectHashMap;

class HprofParser {

    private static final int STRING_IN_UTF8 = 0x01;

    private static final int LOAD_CLASS = 0x02;

    @SuppressWarnings("UnusedDeclaration")
    private static final int UNLOAD_CLASS = 0x03;

    private static final int STACK_FRAME = 0x04;

    private static final int STACK_TRACE = 0x05;

    @SuppressWarnings("UnusedDeclaration")
    private static final int ALLOC_SITES = 0x06;

    @SuppressWarnings("UnusedDeclaration")
    private static final int HEAP_SUMMARY = 0x07;

    @SuppressWarnings("UnusedDeclaration")
    private static final int START_THREAD = 0x0a;

    @SuppressWarnings("UnusedDeclaration")
    private static final int END_THREAD = 0x0b;

    private static final int HEAP_DUMP = 0x0c;

    private static final int HEAP_DUMP_SEGMENT = 0x1c;

    @SuppressWarnings("UnusedDeclaration")
    private static final int HEAP_DUMP_END = 0x2c;

    @SuppressWarnings("UnusedDeclaration")
    private static final int CPU_SAMPLES = 0x0d;

    @SuppressWarnings("UnusedDeclaration")
    private static final int CONTROL_SETTINGS = 0x0e;

    private static final int ROOT_UNKNOWN = 0xff;

    private static final int ROOT_JNI_GLOBAL = 0x01;

    private static final int ROOT_JNI_LOCAL = 0x02;

    private static final int ROOT_JAVA_FRAME = 0x03;

    private static final int ROOT_NATIVE_STACK = 0x04;

    private static final int ROOT_STICKY_CLASS = 0x05;

    private static final int ROOT_THREAD_BLOCK = 0x06;

    private static final int ROOT_MONITOR_USED = 0x07;

    private static final int ROOT_THREAD_OBJECT = 0x08;

    private static final int ROOT_CLASS_DUMP = 0x20;

    private static final int ROOT_INSTANCE_DUMP = 0x21;

    private static final int ROOT_OBJECT_ARRAY_DUMP = 0x22;

    private static final int ROOT_PRIMITIVE_ARRAY_DUMP = 0x23;

    /**
     * Android format addition
     *
     * Specifies information about which heap certain objects came from. When a sub-tag of this type
     * appears in a HPROF_HEAP_DUMP or HPROF_HEAP_DUMP_SEGMENT record, entries that follow it will
     * be associated with the specified heap.  The HEAP_DUMP_INFO data is reset at the end of the
     * HEAP_DUMP[_SEGMENT].  Multiple HEAP_DUMP_INFO entries may appear in a single
     * HEAP_DUMP[_SEGMENT].
     *
     * Format: u1: Tag value (0xFE) u4: heap ID ID: heap name string ID
     */
    private static final int ROOT_HEAP_DUMP_INFO = 0xfe;

    private static final int ROOT_INTERNED_STRING = 0x89;

    private static final int ROOT_FINALIZING = 0x8a;

    private static final int ROOT_DEBUGGER = 0x8b;

    private static final int ROOT_REFERENCE_CLEANUP = 0x8c;

    private static final int ROOT_VM_INTERNAL = 0x8d;

    private static final int ROOT_JNI_MONITOR = 0x8e;

    private static final int ROOT_UNREACHABLE = 0x90;

    private static final int ROOT_PRIMITIVE_ARRAY_NODATA = 0xc3;

    @NonNull
    private final DataBuffer mInput;

    int mIdSize;

    Snapshot mSnapshot;

    /*
     * These are only needed while parsing so are not kept as part of the
     * heap data.
     */
    @NonNull
    TLongObjectHashMap<String> mStrings = new TLongObjectHashMap<String>();

    @NonNull
    TLongObjectHashMap<String> mClassNames = new TLongObjectHashMap<String>();

    static void parseBuffer(@NonNull Snapshot snapshot, @NonNull DataBuffer buffer) {
        new HprofParser(snapshot, buffer).parse();
    }

    private HprofParser(@NonNull Snapshot snapshot, @NonNull DataBuffer buffer) {
        mInput = buffer;
        mSnapshot = snapshot;
    }

    private void parse() {
        try {
            try {
                readNullTerminatedString();  // Version, ignored for now.

                mIdSize = mInput.readInt();
                mSnapshot.setIdSize(mIdSize);

                mInput.readLong();  // Timestamp, ignored for now.

                while (mInput.hasRemaining()) {
                    int tag = readUnsignedByte();
                    mInput.readInt(); // Ignored: timestamp
                    long length = readUnsignedInt();

                    switch (tag) {
                        case STRING_IN_UTF8:
                            // String length is limited by Int.MAX_VALUE anyway.
                            loadString((int) length - mIdSize);
                            break;

                        case LOAD_CLASS:
                            loadClass();
                            break;

                        case STACK_FRAME:
                            loadStackFrame();
                            break;

                        case STACK_TRACE:
                            loadStackTrace();
                            break;

                        case HEAP_DUMP:
                            loadHeapDump(length);
                            mSnapshot.setToDefaultHeap();
                            break;

                        case HEAP_DUMP_SEGMENT:
                            loadHeapDump(length);
                            mSnapshot.setToDefaultHeap();
                            break;

                        default:
                            skipFully(length);
                    }

                }
            } catch (EOFException eof) {
                //  this is fine
            }
            mSnapshot.resolveClasses();
            mSnapshot.resolveReferences();
            // TODO: enable this after the dominators computation is also optimized.
            // mSnapshot.computeRetainedSizes();
        } catch (Exception e) {
            e.printStackTrace();
        }

        mClassNames.clear();
        mStrings.clear();
    }

    @NonNull
    private String readNullTerminatedString() throws IOException {
        StringBuilder s = new StringBuilder();
        for (byte c = mInput.readByte(); c != 0; c = mInput.readByte()) {
            s.append((char) c);
        }
        return s.toString();
    }

    private long readId() throws IOException {
        // As long as we don't interpret IDs, reading signed values here is fine.
        switch (mIdSize) {
            case 1:
                return mInput.readByte();
            case 2:
                return mInput.readShort();
            case 4:
                return mInput.readInt();
            case 8:
                return mInput.readLong();
        }

        throw new IllegalArgumentException("ID Length must be 1, 2, 4, or 8");
    }

    @NonNull
    private String readUTF8(int length) throws IOException {
        byte[] b = new byte[length];

        mInput.read(b);

        return new String(b, "utf-8");
    }

    private int readUnsignedByte() throws IOException {
        return UnsignedBytes.toInt(mInput.readByte());
    }

    private int readUnsignedShort() throws IOException {
        return mInput.readShort() & 0xffff;
    }

    private long readUnsignedInt() throws IOException {
        return UnsignedInts.toLong(mInput.readInt());
    }

    private void loadString(int length) throws IOException {
        long id = readId();
        String string = readUTF8(length);

        mStrings.put(id, string);
    }

    private void loadClass() throws IOException {
        mInput.readInt();  // Ignored: Class serial number.
        long id = readId();
        mInput.readInt(); // Ignored: Stack trace serial number.
        String name = mStrings.get(readId());

        mClassNames.put(id, name);
    }

    private void loadStackFrame() throws IOException {
        long id = readId();
        String methodName = mStrings.get(readId());
        String methodSignature = mStrings.get(readId());
        String sourceFile = mStrings.get(readId());
        int serial = mInput.readInt();
        int lineNumber = mInput.readInt();

        StackFrame frame = new StackFrame(id, methodName, methodSignature,
                sourceFile, serial, lineNumber);

        mSnapshot.addStackFrame(frame);
    }

    private void loadStackTrace() throws IOException {
        int serialNumber = mInput.readInt();
        int threadSerialNumber = mInput.readInt();
        final int numFrames = mInput.readInt();
        StackFrame[] frames = new StackFrame[numFrames];

        for (int i = 0; i < numFrames; i++) {
            frames[i] = mSnapshot.getStackFrame(readId());
        }

        StackTrace trace = new StackTrace(serialNumber, threadSerialNumber, frames);

        mSnapshot.addStackTrace(trace);
    }

    private void loadHeapDump(long length) throws IOException {
        while (length > 0) {
            int tag = readUnsignedByte();
            length--;

            switch (tag) {
                case ROOT_UNKNOWN:
                    length -= loadBasicObj(RootType.UNKNOWN);
                    break;

                case ROOT_JNI_GLOBAL:
                    length -= loadBasicObj(RootType.NATIVE_STATIC);
                    readId();   //  ignored
                    length -= mIdSize;
                    break;

                case ROOT_JNI_LOCAL:
                    length -= loadJniLocal();
                    break;

                case ROOT_JAVA_FRAME:
                    length -= loadJavaFrame();
                    break;

                case ROOT_NATIVE_STACK:
                    length -= loadNativeStack();
                    break;

                case ROOT_STICKY_CLASS:
                    length -= loadBasicObj(RootType.SYSTEM_CLASS);
                    break;

                case ROOT_THREAD_BLOCK:
                    length -= loadThreadBlock();
                    break;

                case ROOT_MONITOR_USED:
                    length -= loadBasicObj(RootType.BUSY_MONITOR);
                    break;

                case ROOT_THREAD_OBJECT:
                    length -= loadThreadObject();
                    break;

                case ROOT_CLASS_DUMP:
                    length -= loadClassDump();
                    break;

                case ROOT_INSTANCE_DUMP:
                    length -= loadInstanceDump();
                    break;

                case ROOT_OBJECT_ARRAY_DUMP:
                    length -= loadObjectArrayDump();
                    break;

                case ROOT_PRIMITIVE_ARRAY_DUMP:
                    length -= loadPrimitiveArrayDump();
                    break;

                case ROOT_PRIMITIVE_ARRAY_NODATA:
                    System.err.println("+--- PRIMITIVE ARRAY NODATA DUMP");
                    length -= loadPrimitiveArrayDump();

                    throw new IllegalArgumentException(
                            "Don't know how to load a nodata array");

                case ROOT_HEAP_DUMP_INFO:
                    int heapId = mInput.readInt();
                    long heapNameId = readId();
                    String heapName = mStrings.get(heapNameId);

                    mSnapshot.setHeapTo(heapId, heapName);
                    length -= 4 + mIdSize;
                    break;

                case ROOT_INTERNED_STRING:
                    length -= loadBasicObj(RootType.INTERNED_STRING);
                    break;

                case ROOT_FINALIZING:
                    length -= loadBasicObj(RootType.FINALIZING);
                    break;

                case ROOT_DEBUGGER:
                    length -= loadBasicObj(RootType.DEBUGGER);
                    break;

                case ROOT_REFERENCE_CLEANUP:
                    length -= loadBasicObj(RootType.REFERENCE_CLEANUP);
                    break;

                case ROOT_VM_INTERNAL:
                    length -= loadBasicObj(RootType.VM_INTERNAL);
                    break;

                case ROOT_JNI_MONITOR:
                    length -= loadJniMonitor();
                    break;

                case ROOT_UNREACHABLE:
                    length -= loadBasicObj(RootType.UNREACHABLE);
                    break;

                default:
                    throw new IllegalArgumentException(
                            "loadHeapDump loop with unknown tag " + tag
                                    + " with " + mInput.remaining()
                                    + " bytes possibly remaining");
            }
        }
    }

    private int loadJniLocal() throws IOException {
        long id = readId();
        int threadSerialNumber = mInput.readInt();
        int stackFrameNumber = mInput.readInt();
        ThreadObj thread = mSnapshot.getThread(threadSerialNumber);
        StackTrace trace = mSnapshot.getStackTraceAtDepth(thread.mStackTrace, stackFrameNumber);
        RootObj root = new RootObj(RootType.NATIVE_LOCAL, id, threadSerialNumber, trace);

        mSnapshot.addRoot(root);

        return mIdSize + 4 + 4;
    }

    private int loadJavaFrame() throws IOException {
        long id = readId();
        int threadSerialNumber = mInput.readInt();
        int stackFrameNumber = mInput.readInt();
        ThreadObj thread = mSnapshot.getThread(threadSerialNumber);
        StackTrace trace = mSnapshot.getStackTraceAtDepth(thread.mStackTrace, stackFrameNumber);
        RootObj root = new RootObj(RootType.JAVA_LOCAL, id, threadSerialNumber, trace);

        mSnapshot.addRoot(root);

        return mIdSize + 4 + 4;
    }

    private int loadNativeStack() throws IOException {
        long id = readId();
        int threadSerialNumber = mInput.readInt();
        ThreadObj thread = mSnapshot.getThread(threadSerialNumber);
        StackTrace trace = mSnapshot.getStackTrace(thread.mStackTrace);
        RootObj root = new RootObj(RootType.NATIVE_STACK, id, threadSerialNumber, trace);

        mSnapshot.addRoot(root);

        return mIdSize + 4;
    }

    private int loadBasicObj(RootType type) throws IOException {
        long id = readId();
        RootObj root = new RootObj(type, id);

        mSnapshot.addRoot(root);

        return mIdSize;
    }

    private int loadThreadBlock() throws IOException {
        long id = readId();
        int threadSerialNumber = mInput.readInt();
        ThreadObj thread = mSnapshot.getThread(threadSerialNumber);
        StackTrace stack = mSnapshot.getStackTrace(thread.mStackTrace);
        RootObj root = new RootObj(RootType.THREAD_BLOCK, id, threadSerialNumber, stack);

        mSnapshot.addRoot(root);

        return mIdSize + 4;
    }

    private int loadThreadObject() throws IOException {
        long id = readId();
        int threadSerialNumber = mInput.readInt();
        int stackSerialNumber = mInput.readInt();
        ThreadObj thread = new ThreadObj(id, stackSerialNumber);

        mSnapshot.addThread(thread, threadSerialNumber);

        return mIdSize + 4 + 4;
    }

    private int loadClassDump() throws IOException {
        final long id = readId();
        int stackSerialNumber = mInput.readInt();
        StackTrace stack = mSnapshot.getStackTrace(stackSerialNumber);
        final long superClassId = readId();
        final long classLoaderId = readId();
        readId(); // Ignored: Signeres ID.
        readId(); // Ignored: Protection domain ID.
        readId(); // RESERVED.
        readId(); // RESERVED.
        int instanceSize = mInput.readInt();

        int bytesRead = (7 * mIdSize) + 4 + 4;

        //  Skip over the constant pool
        int numEntries = readUnsignedShort();
        bytesRead += 2;

        for (int i = 0; i < numEntries; i++) {
            readUnsignedShort();
            bytesRead += 2 + skipValue();
        }

        final ClassObj theClass = new ClassObj(id, stack, mClassNames.get(id), mInput.position());
        theClass.setSuperClassId(superClassId);
        theClass.setClassLoaderId(classLoaderId);

        //  Skip over static fields
        numEntries = readUnsignedShort();
        bytesRead += 2;

        Field[] staticFields = new Field[numEntries];

        for (int i = 0; i < numEntries; i++) {
            String name = mStrings.get(readId());
            Type type = Type.getType(mInput.readByte());

            staticFields[i] = new Field(type, name);
            skipFully(mSnapshot.getTypeSize(type));

            bytesRead += mIdSize + 1 + mSnapshot.getTypeSize(type);
        }

        theClass.setStaticFields(staticFields);

        //  Instance fields
        numEntries = readUnsignedShort();
        bytesRead += 2;

        Field[] fields = new Field[numEntries];

        for (int i = 0; i < numEntries; i++) {
            String name = mStrings.get(readId());
            Type type = Type.getType(readUnsignedByte());

            fields[i] = new Field(type, name);

            bytesRead += mIdSize + 1;
        }

        theClass.setFields(fields);
        theClass.setInstanceSize(instanceSize);

        mSnapshot.addClass(id, theClass);

        return bytesRead;
    }

    private int loadInstanceDump() throws IOException {
        long id = readId();
        int stackId = mInput.readInt();
        StackTrace stack = mSnapshot.getStackTrace(stackId);
        long classId = readId();
        int remaining = mInput.readInt();

        long position = mInput.position();
        ClassInstance instance = new ClassInstance(id, stack, position);
        instance.setClassId(classId);
        mSnapshot.addInstance(id, instance);

        skipFully(remaining);
        return mIdSize + 4 + mIdSize + 4 + remaining;
    }

    private int loadObjectArrayDump() throws IOException {
        final long id = readId();
        int stackId = mInput.readInt();
        StackTrace stack = mSnapshot.getStackTrace(stackId);
        int numElements = mInput.readInt();
        long classId = readId();
        ArrayInstance array =
                new ArrayInstance(id, stack, Type.OBJECT, numElements, mInput.position());
        array.setClassId(classId);
        mSnapshot.addInstance(id, array);

        int remaining = numElements * mIdSize;
        skipFully(remaining);
        return mIdSize + 4 + 4 + mIdSize + remaining;
    }

    private int loadPrimitiveArrayDump() throws IOException {
        long id = readId();
        int stackId = mInput.readInt();
        StackTrace stack = mSnapshot.getStackTrace(stackId);
        int numElements = mInput.readInt();
        Type type = Type.getType(readUnsignedByte());
        int size = mSnapshot.getTypeSize(type);
        ArrayInstance array = new ArrayInstance(id, stack, type, numElements, mInput.position());
        mSnapshot.addInstance(id, array);

        int remaining = numElements * size;
        skipFully(remaining);
        return mIdSize + 4 + 4 + 1 + remaining;
    }

    private int loadJniMonitor() throws IOException {
        long id = readId();
        int threadSerialNumber = mInput.readInt();
        int stackDepth = mInput.readInt();
        ThreadObj thread = mSnapshot.getThread(threadSerialNumber);
        StackTrace trace = mSnapshot.getStackTraceAtDepth(thread.mStackTrace, stackDepth);
        RootObj root = new RootObj(RootType.NATIVE_MONITOR, id, threadSerialNumber, trace);

        mSnapshot.addRoot(root);

        return mIdSize + 4 + 4;
    }

    private int skipValue() throws IOException {
        Type type = Type.getType(readUnsignedByte());
        int size = mSnapshot.getTypeSize(type);

        skipFully(size);

        return size + 1;
    }

    private void skipFully(long numBytes) throws IOException {
        mInput.setPosition(mInput.position() + numBytes);
    }
}
