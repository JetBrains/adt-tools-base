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

import java.nio.ByteBuffer;

public class InMemoryBuffer implements HprofBuffer {

    private final ByteBuffer mBuffer;

    public InMemoryBuffer(int capacity) {
        mBuffer = ByteBuffer.allocateDirect(capacity);
    }

    public ByteBuffer getDirectBuffer() {
        return mBuffer;
    }

    @Override
    public byte readByte() {
        return mBuffer.get();
    }

    @Override
    public void read(byte[] b) {
        mBuffer.get(b);
    }

    @Override
    public void readSubSequence(byte[] b, int sourceStart, int sourceEnd) {
        ((ByteBuffer)mBuffer.slice().position(sourceStart)).get(b);
    }

    @Override
    public char readChar() {
        return mBuffer.getChar();
    }

    @Override
    public short readShort() {
        return mBuffer.getShort();
    }

    @Override
    public int readInt() {
        return mBuffer.getInt();
    }

    @Override
    public long readLong() {
        return mBuffer.getLong();
    }

    @Override
    public float readFloat() {
        return mBuffer.getFloat();
    }

    @Override
    public double readDouble() {
        return mBuffer.getDouble();
    }

    @Override
    public void setPosition(long position) {
        mBuffer.position((int) position);
    }

    @Override
    public long position() {
        return mBuffer.position();
    }

    @Override
    public boolean hasRemaining() {
        return mBuffer.hasRemaining();
    }

    @Override
    public long remaining() {
        return mBuffer.remaining();
    }
}
