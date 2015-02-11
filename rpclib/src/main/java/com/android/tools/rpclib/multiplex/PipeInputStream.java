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
package com.android.tools.rpclib.multiplex;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.concurrent.Semaphore;

/**
 * An object that provides an {@link java.io.InputStream} interface to read data that has been written to a
 * {@link java.io.OutputStream}.
 * <p/>
 * Buffers passed to {@link java.io.OutputStream#write} on the {@link #source} stream are <b>not</b> internally copied,
 * and are assumed immutable. Mutation of any buffers passed to the {@link java.io.OutputStream#write} after the method
 * has returned will result in undefined behaviour when calling the {@link #read} methods.
 * <p/>
 * Note: This is similar to {@link java.io.PipedInputStream} and {@link java.io.PipedOutputStream}, except this
 * implementation does not use an internal ring buffer, and and does not suffer from 1 second stalls (JDK-4404700).
 */
public class PipeInputStream extends InputStream {
  private static final Item ITEM_CLOSE = new Item(null, 0, 0);
  private final OutputStream mSource;
  private final LinkedList<Item> mQueue;
  private final Semaphore mSemaphore;
  private final byte[] mByte;

  PipeInputStream() {
    mQueue = new LinkedList<Item>();
    mSemaphore = new Semaphore(0);
    mByte = new byte[1];
    mSource = new Writer();
  }

  public OutputStream getSource() {
    return mSource;
  }

  @Override
  public int read() throws IOException {
    return (read(mByte, 0, 1) > 0) ? mByte[0] : -1;
  }

  @Override
  public int read(byte b[], int off, int len) throws IOException {
    int n = 0;
    boolean closed = false;
    while (!closed && len > n) {
      try {
        mSemaphore.acquire();
        synchronized (mQueue) {
          Item item = mQueue.getFirst();
          if (item != ITEM_CLOSE) {
            n += item.read(b, off + n, len - n);
            if (item.remaining() == 0) {
              mQueue.removeFirst();
            }
            else {
              mSemaphore.release();
            }
          }
          else {
            closed = true;
          }
        }
      }
      catch (InterruptedException e) {
        break;
      }
    }
    if (n == 0 && closed) {
      return -1;
    }
    return n;
  }

  private static class Item {
    private final byte[] mData;
    private final int mCount;
    private int mOffset;

    public Item(byte[] data, int offset, int count) {
      mData = data;
      mCount = count;
      mOffset = offset;
    }

    public int read(byte[] out, int offset, int count) {
      int remaining = remaining();
      if (count > remaining) {
        count = remaining;
      }
      System.arraycopy(mData, mOffset, out, offset, count);
      mOffset += count;
      return count;
    }

    public int remaining() {
      return mCount - mOffset;
    }
  }

  private class Writer extends OutputStream {
    @Override
    public void write(int b) throws IOException {
      write(new byte[]{(byte)b}, 0, 1);
    }

    @Override
    public void write(byte b[], int off, int len) throws IOException {
      if (len > 0) {
        synchronized (mQueue) {
          mQueue.addLast(new Item(b, off, len));
        }
        mSemaphore.release();
      }
    }

    @Override
    public void close() throws IOException {
      synchronized (mQueue) {
        mQueue.addLast(ITEM_CLOSE);
        mSemaphore.release();
      }
    }
  }
}
