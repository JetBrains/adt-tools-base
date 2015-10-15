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

package com.android.tools.perflib.captures;

import com.android.annotations.NonNull;
import com.android.annotations.VisibleForTesting;
import com.android.tools.perflib.captures.DataBuffer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import sun.nio.ch.DirectBuffer;

public class MemoryMappedFileBuffer implements DataBuffer {

    // Default chunk size is 1 << 30, or 1,073,741,824 bytes.
    private static final int DEFAULT_SIZE = 1 << 30;

    // Eliminate wrapped, multi-byte reads across chunks in most cases.
    private static final int DEFAULT_PADDING = 1024;

    private final int mBufferSize;

    private final int mPadding;

    @NonNull
    private final ByteBuffer[] mByteBuffers;

    private final long mLength;

    private long mCurrentPosition;

    @VisibleForTesting
    public MemoryMappedFileBuffer(@NonNull File f, int bufferSize,
            int padding) throws IOException {
        mBufferSize = bufferSize;
        mPadding = padding;
        mLength = f.length();
        int shards = (int) (mLength / mBufferSize) + 1;
        mByteBuffers = new ByteBuffer[shards];

        FileInputStream inputStream = new FileInputStream(f);
        try {
            long offset = 0;
            for (int i = 0; i < shards; i++) {
                long size = Math.min(mLength - offset, mBufferSize + mPadding);
                mByteBuffers[i] = inputStream.getChannel()
                        .map(FileChannel.MapMode.READ_ONLY, offset, size);
                mByteBuffers[i].order(HPROF_BYTE_ORDER);
                offset += mBufferSize;
            }
            mCurrentPosition = 0;
        } finally {
            inputStream.close();
        }
    }

    /**
     * Creates a buffer by memory-mapping file {@param f}.
     *
     * It may be a good idea to dispose() the buffer if no longer needed. A garbage collection isn't
     * guaranteed to free up the resources, and in a long-running 32-bit JVM there's the risk of
     * exhausting the address space this way. On Windows, mmap locks the file, preventing it from
     * being deleted. See {@link http://bugs.java.com/bugdatabase/view_bug.do?bug_id=4715154}.
     */
    public MemoryMappedFileBuffer(@NonNull File f) throws IOException {
        this(f, DEFAULT_SIZE, DEFAULT_PADDING);
    }

    /**
     * Attempts to unmap the buffer. It is the caller's responsibility to ensure there are no other
     * accesses to this buffer, otherwise this can result in a crash and kill the JVM.
     */
    @Override
    public void dispose() {
        try {
            for (int i = 0; i < mByteBuffers.length; i++) {
                ((DirectBuffer) mByteBuffers[i]).cleaner().clean();
            }
        } catch (Exception ex) {
            // ignore, this is a best effort attempt.
        }
    }

    @Override
    public byte readByte() {
        byte result = mByteBuffers[getIndex()].get(getOffset());
        mCurrentPosition++;
        return result;
    }

    @Override
    public void append(@NonNull byte[] data) {
        // Do nothing since this is not a streaming data buffer.
    }

    @Override
    public void read(@NonNull byte[] b) {
        int index = getIndex();
        mByteBuffers[index].position(getOffset());
        if (b.length <= mByteBuffers[index].remaining()) {
            mByteBuffers[index].get(b, 0, b.length);
        } else {
            // Wrapped read
            int split = mBufferSize - mByteBuffers[index].position();
            mByteBuffers[index].get(b, 0, split);
            mByteBuffers[index + 1].position(0);
            mByteBuffers[index + 1].get(b, split, b.length - split);
        }
        mCurrentPosition += b.length;
    }

    @Override
    public void readSubSequence(@NonNull byte[] b, int sourceStart, int length) {
        assert length < mLength;

        mCurrentPosition += sourceStart;

        int index = getIndex();
        mByteBuffers[index].position(getOffset());
        if (b.length <= mByteBuffers[index].remaining()) {
            mByteBuffers[index].get(b, 0, b.length);
        } else {
            int split = mBufferSize - mByteBuffers[index].position();
            mByteBuffers[index].get(b, 0, split);

            int start = split;
            int remainingMaxLength = Math.min(length - start, b.length - start);
            int remainingShardCount = (remainingMaxLength + mBufferSize - 1) / mBufferSize;
            for (int i = 0; i < remainingShardCount; ++i) {
                int maxToRead = Math.min(remainingMaxLength, mBufferSize);
                mByteBuffers[index + 1 + i].position(0);
                mByteBuffers[index + 1 + i].get(b, start, maxToRead);
                start += maxToRead;
                remainingMaxLength -= maxToRead;
            }
        }

        mCurrentPosition += Math.min(b.length, length);
    }

    @Override
    public char readChar() {
        char result = mByteBuffers[getIndex()].getChar(getOffset());
        mCurrentPosition += 2;
        return result;
    }

    @Override
    public short readShort() {
        short result = mByteBuffers[getIndex()].getShort(getOffset());
        mCurrentPosition += 2;
        return result;
    }

    @Override
    public int readInt() {
        int result = mByteBuffers[getIndex()].getInt(getOffset());
        mCurrentPosition += 4;
        return result;
    }

    @Override
    public long readLong() {
        long result = mByteBuffers[getIndex()].getLong(getOffset());
        mCurrentPosition += 8;
        return result;
    }

    @Override
    public float readFloat() {
        float result = mByteBuffers[getIndex()].getFloat(getOffset());
        mCurrentPosition += 4;
        return result;
    }

    @Override
    public double readDouble() {
        double result = mByteBuffers[getIndex()].getDouble(getOffset());
        mCurrentPosition += 8;
        return result;
    }

    @Override
    public void setPosition(long position) {
        mCurrentPosition = position;
    }

    @Override
    public long position() {
        return mCurrentPosition;
    }

    @Override
    public boolean hasRemaining() {
        return mCurrentPosition < mLength;
    }

    @Override
    public long remaining() {
        return mLength - mCurrentPosition;
    }

    private int getIndex() {
        return (int) (mCurrentPosition / mBufferSize);
    }

    private int getOffset() {
        return (int) (mCurrentPosition % mBufferSize);
    }
}
