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

package com.android.tools.chunkio;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UTFDataFormatException;
import java.util.Deque;
import java.util.LinkedList;

/**
 * A ranged input stream is a decorator for a {@link DataInputStream} that
 * limits the number of bytes that can be read. It also provides the ability
 * to consume the remaining available bytes from the source stream.
 */
@SuppressWarnings("WeakerAccess")
public final class RangedInputStream extends InputStream implements DataInput {
    private final byte[] scratch = new byte[8];

    private final CountingInputStream source;

    private StreamState currentState;
    private final Deque<StreamState> stack = new LinkedList<>();

    private static class StreamState {
        final long byteCount;
        long readCount;

        StreamState(long byteCount) {
            this.byteCount = byteCount;
        }
    }

    private class CountingInputStream extends InputStream {
        private final InputStream source;

        private CountingInputStream(InputStream in) {
            source = in;
        }

        @Override
        public int available() throws IOException {
            if (currentState.byteCount < 0) return super.available();
            return (int) (currentState.byteCount - currentState.readCount);
        }

        @Override
        public int read() throws IOException {
            if (currentState.byteCount >= 0 &&
                currentState.readCount >= currentState.byteCount) {
                return -1;
            }

            int read = source.read();
            if (read >= 0) currentState.readCount++;

            return read;
        }
    }

    /**
     * Creates a new ranged input stream.
     *
     * @param in The stream to decorate
     */
    public RangedInputStream(InputStream in) {
        source = new CountingInputStream(in);
        currentState = new StreamState(-1);
        stack.offerFirst(currentState);
    }

    @SuppressWarnings("unused")
    public void pushRange(long byteCount) {
        currentState = new StreamState(byteCount);
        stack.offerFirst(currentState);
    }

    @SuppressWarnings("unused")
    public void popRange() throws IOException {
        StreamState previous = stack.pollFirst();
        consume();

        currentState = stack.peekFirst();
        currentState.readCount += previous.byteCount;
    }

    /**
     * Consumes (skips) the remaining available bytes in this stream.
     */
    @SuppressWarnings("unused")
    public void consume() throws IOException {
        if (currentState.byteCount > 0) {
            int count = available();
            if (count > 0) {
                ChunkUtils.skip(this, count);
            }
        }
    }

    @Override
    public int read() throws IOException {
        return source.read();
    }

    @Override
    public int available() throws IOException {
        return source.available();
    }

    @SuppressWarnings("NullableProblems")
    @Override
    public int read(byte[] bytes) throws IOException {
        return source.read(bytes);
    }

    @SuppressWarnings("NullableProblems")
    @Override
    public int read(byte[] bytes, int offset, int length) throws IOException {
        return source.read(bytes, offset, length);
    }

    @Override
    public long skip(long l) throws IOException {
        return source.skip(l);
    }

    @Override
    public void close() throws IOException {
        source.close();
    }

    @Override
    public synchronized void mark(int i) {
        source.mark(i);
    }

    @Override
    public synchronized void reset() throws IOException {
        source.reset();
    }

    @Override
    public boolean markSupported() {
        return source.markSupported();
    }

    @SuppressWarnings("NullableProblems")
    @Override
    public final void readFully(byte[] dst) throws IOException {
        readFully(dst, 0, dst.length);
    }

    @SuppressWarnings("NullableProblems")
    @Override
    public final void readFully(byte[] dst, int offset, int byteCount) throws IOException {
        if (byteCount == 0) return;
        if (dst == null) throw new NullPointerException("dst == null");

        checkOffsetAndCount(dst.length, offset, byteCount);

        while (byteCount > 0) {
            int bytesRead = source.read(dst, offset, byteCount);
            if (bytesRead < 0) throw new EOFException();
            offset += bytesRead;
            byteCount -= bytesRead;
        }
    }

    private static void checkOffsetAndCount(int arrayLength, int offset, int count) {
        if ((offset | count) < 0 || offset > arrayLength || arrayLength - offset < count) {
            throw new ArrayIndexOutOfBoundsException(offset);
        }
    }

    @Override
    public int skipBytes(int count) throws IOException {
        long skip;
        int skipped = 0;

        while (skipped < count && (skip = source.skip(count - skipped)) != 0) {
            skipped += skip;
        }

        return skipped;
    }

    @Override
    public final boolean readBoolean() throws IOException {
        int temp = source.read();
        if (temp < 0) throw new EOFException();
        return temp != 0;
    }

    @Override
    public final byte readByte() throws IOException {
        int temp = source.read();
        if (temp < 0) throw new EOFException();
        return (byte) temp;
    }

    @Override
    public int readUnsignedByte() throws IOException {
        int temp = source.read();
        if (temp < 0) throw new EOFException();
        return temp;
    }

    @Override
    public final char readChar() throws IOException {
        return (char) readShort();
    }

    @Override
    public final double readDouble() throws IOException {
        return Double.longBitsToDouble(readLong());
    }

    @Override
    public final float readFloat() throws IOException {
        return Float.intBitsToFloat(readInt());
    }

    @Override
    public int readUnsignedShort() throws IOException {
        return ((int) readShort()) & 0xffff;
    }

    @Override
    public final short readShort() throws IOException {
        return (short) ((source.read() << 8) | (source.read() & 0xff));
    }

    @Override
    public int readInt() throws IOException {
        return (((source.read() & 0xff) << 24) |
                ((source.read() & 0xff) << 16) |
                ((source.read() & 0xff) <<  8) |
                ((source.read() & 0xff)      ));
    }

    @Override
    public long readLong() throws IOException {
        readFully(scratch, 0, 8);
        int h = ((scratch[0] & 0xff) << 24) |
                ((scratch[1] & 0xff) << 16) |
                ((scratch[2] & 0xff) <<  8) |
                ((scratch[3] & 0xff)      );
        int l = ((scratch[4] & 0xff) << 24) |
                ((scratch[5] & 0xff) << 16) |
                ((scratch[6] & 0xff) <<  8) |
                ((scratch[7] & 0xff)      );
        return (((long) h) << 32L) | ((long) l) & 0xffffffffL;
    }

    @Override
    public String readLine() throws IOException {
        throw new IOException("readLine() is deprecated");
    }

    @SuppressWarnings("NullableProblems")
    @Override
    public String readUTF() throws IOException {
        int utfSize = readUnsignedShort();
        byte[] buf = new byte[utfSize];
        readFully(buf, 0, utfSize);
        return decode(buf, new char[utfSize], 0, utfSize);
    }

    private static String decode(byte[] in, char[] out, int offset, int utfSize)
            throws UTFDataFormatException {

        int count = 0, s = 0, a;
        while (count < utfSize) {
            if ((out[s] = (char) in[offset + count++]) < '\u0080') {
                s++;
            } else if (((a = out[s]) & 0xe0) == 0xc0) {
                if (count >= utfSize) {
                    throw new UTFDataFormatException("bad second byte at " + count);
                }
                int b = in[offset + count++];
                if ((b & 0xC0) != 0x80) {
                    throw new UTFDataFormatException("bad second byte at " + (count - 1));
                }
                out[s++] = (char) (((a & 0x1F) << 6) | (b & 0x3F));
            } else if ((a & 0xf0) == 0xe0) {
                if (count + 1 >= utfSize) {
                    throw new UTFDataFormatException("bad third byte at " + (count + 1));
                }
                int b = in[offset + count++];
                int c = in[offset + count++];
                if (((b & 0xC0) != 0x80) || ((c & 0xC0) != 0x80)) {
                    throw new UTFDataFormatException("bad second or third byte at " + (count - 2));
                }
                out[s++] = (char) (((a & 0x0F) << 12) | ((b & 0x3F) << 6) | (c & 0x3F));
            } else {
                throw new UTFDataFormatException("bad byte at " + (count - 1));
            }
        }

        return new String(out, 0, s);
    }
}
