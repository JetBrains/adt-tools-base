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
import java.io.OutputStream;
import java.lang.IllegalStateException;

public class HprofOutputStream extends OutputStream {
    private int mIdSize;
    private OutputStream mOutputStream;

    public HprofOutputStream(int idSize, OutputStream os) {
        mIdSize = idSize;
        if (idSize != 1 && idSize != 2 && idSize != 4 && idSize != 8) {
            throw new IllegalArgumentException("Unsupproted id size: " + idSize);
        }
        mOutputStream = os;
    }

    public void close() throws IOException {
        mOutputStream.close();
    }

    public void flush() throws IOException {
        mOutputStream.flush();
    }

    public void write(byte[] b) throws IOException {
        mOutputStream.write(b);
    }

    public void write(byte[] b, int off, int len) throws IOException {
        mOutputStream.write(b, off, len);
    }

    public void write(int b) throws IOException {
        mOutputStream.write(b);
    }

    /**
     * Write a u1 to the output stream.
     */
    public void writeU1(byte data) throws IOException {
        mOutputStream.write(data);
    }

    /**
     * Write a u2 to the output stream.
     */
    public void writeU2(short data) throws IOException {
        writeU1((byte)(data >> 8));
        writeU1((byte)(data >> 0));
    }

    /**
     * Write a u4 to the output stream.
     */
    public void writeU4(int data) throws IOException {
        writeU1((byte)(data >> 24));
        writeU1((byte)(data >> 16));
        writeU1((byte)(data >>  8));
        writeU1((byte)(data >>  0));
    }

    /**
     * Write a u8 to the output stream.
     */
    public void writeU8(long data) throws IOException {
        writeU1((byte)(data >> 56));
        writeU1((byte)(data >> 48));
        writeU1((byte)(data >> 40));
        writeU1((byte)(data >> 32));
        writeU1((byte)(data >> 24));
        writeU1((byte)(data >> 16));
        writeU1((byte)(data >>  8));
        writeU1((byte)(data >>  0));
    }

    /**
     * Write an ID to the output stream.
     */
    public void writeId(long data) throws IOException {
        writeSized(mIdSize, data);
    }

    /**
     * Write a value with given type to the output stream.
     */
    public void writeValue(byte type, long data) throws IOException {
        writeSized(HprofType.sizeOf(type, mIdSize), data);
    }


    /**
     * Write a record header to the output.
     * @param tag - The record tag.
     * @param time - T number of microseconds since the hprof time stamp.
     * @param length - the number of bytes of content of the record (not
     * including the bytes for the header).
     */
    public void writeRecordHeader(byte tag, int time, int length) throws IOException {
        writeU1(tag);
        writeU4(time);
        writeU4(length);
    }

    public int getIdSize() {
        return mIdSize;
    }

    /**
     * Write data of the given size in bytes to the output stream.
     * The size must be 1, 2, 4, or 8.
     */
    private void writeSized(int size, long data) throws IOException {
        switch (size) {
            case 1: writeU1((byte)data); break;
            case 2: writeU2((short)data); break;
            case 4: writeU4((int)data); break;
            case 8: writeU8(data); break;
            default: throw new IllegalStateException("Unexpected size: " + size);
        }
    }
}
