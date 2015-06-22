/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.perflib.heap.io;

import java.nio.ByteOrder;

public interface HprofBuffer {
    ByteOrder HPROF_BYTE_ORDER = ByteOrder.BIG_ENDIAN;

    byte readByte();

    void read(byte[] b);

    void readSubSequence(byte[] b, int sourceStart, int sourceEnd);

    char readChar();

    short readShort();

    int readInt();

    long readLong();

    float readFloat();

    double readDouble();

    void setPosition(long position);

    long position();

    boolean hasRemaining();

    long remaining();
}
