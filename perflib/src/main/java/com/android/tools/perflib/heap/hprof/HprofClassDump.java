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

public class HprofClassDump implements HprofDumpRecord {
    public static final byte SUBTAG = 0x20;

    public final long classObjectId;                  // Id
    public final int stackTraceSerialNumber;          // u4
    public final long superClassObjectId;             // Id
    public final long classLoaderObjectId;            // Id
    public final long signersObjectId;                // Id
    public final long protectionDomainObjectId;       // Id
    public final long reserved1;                      // Id
    public final long reserved2;                      // Id
    public final int instanceSize;                    // u4
    public final HprofConstant[] constantPool;        // u2 + [ConstantPool]*
    public final HprofStaticField[] staticFields;     // u2 + [StaticField]*
    public final HprofInstanceField[] instanceFields; // u2 + [InstanceField]*

    public HprofClassDump(long classObjectId, int stackTraceSerialNumber,
            long superClassObjectId, long classLoaderObjectId,
            long signersObjectId, long protectionDomainObjectId,
            long reserved1, long reserved2, int instanceSize,
            HprofConstant[] constantPool, HprofStaticField[] staticFields,
            HprofInstanceField[] instanceFields) {
        this.classObjectId = classObjectId;
        this.stackTraceSerialNumber = stackTraceSerialNumber;
        this.superClassObjectId = superClassObjectId;
        this.classLoaderObjectId = classLoaderObjectId;
        this.signersObjectId = signersObjectId;
        this.protectionDomainObjectId = protectionDomainObjectId;
        this.reserved1 = reserved1;
        this.reserved2 = reserved2;
        this.instanceSize = instanceSize;
        this.constantPool = constantPool;
        this.staticFields = staticFields;
        this.instanceFields = instanceFields;
    }

    public void write(HprofOutputStream hprof) throws IOException {
        hprof.writeU1(SUBTAG);
        hprof.writeId(classObjectId);
        hprof.writeU4(stackTraceSerialNumber);
        hprof.writeId(superClassObjectId);
        hprof.writeId(classLoaderObjectId);
        hprof.writeId(signersObjectId);
        hprof.writeId(protectionDomainObjectId);
        hprof.writeId(reserved1);
        hprof.writeId(reserved2);
        hprof.writeU4(instanceSize);
        hprof.writeU2((short)constantPool.length);
        for (HprofConstant constant : constantPool) {
            constant.write(hprof);
        }
        hprof.writeU2((short)staticFields.length);
        for (HprofStaticField field : staticFields) {
            field.write(hprof);
        }
        hprof.writeU2((short)instanceFields.length);
        for (HprofInstanceField field : instanceFields) {
            field.write(hprof);
        }
    }

    public int getLength(int idSize) {
        int length = 1 + 7*idSize + 2 * 4 + 3*2;
        for (HprofConstant constant : constantPool) {
            length += constant.getLength(idSize);
        }
        for (HprofStaticField field : staticFields) {
            length += field.getLength(idSize);
        }
        for (HprofInstanceField field : instanceFields) {
            length += field.getLength(idSize);
        }
        return length;
    }
}
