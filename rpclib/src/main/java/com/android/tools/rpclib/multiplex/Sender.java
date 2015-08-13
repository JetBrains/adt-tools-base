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

import com.android.tools.rpclib.binary.Encoder;
import gnu.trove.TLongObjectHashMap;
import gnu.trove.TLongObjectIterator;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;

class Sender {
  private static final int MAX_PENDING_SEND_COUNT = 1024;
  private static final SendItem NOP_ITEM = new SendNop();
  private final int mMtu;
  @NotNull private ExecutorService mExecutorService;
  @NotNull private final LinkedBlockingQueue<SendItem> mPendingItems;
  private Worker mWorker;

  Sender(int mtu, @NotNull ExecutorService executorService) {
    mMtu = mtu;
    mExecutorService = executorService;
    mPendingItems = new LinkedBlockingQueue<SendItem>(MAX_PENDING_SEND_COUNT);
  }

  void begin(Encoder out) {
    mWorker = new Worker(out);
    mExecutorService.execute(mWorker);
  }

  void end() {
    try {
      synchronized (mWorker) {
        mWorker.setRunning(false);
        mPendingItems.add(NOP_ITEM); // Unblock the sender
        while (!mWorker.isStopped()) {
          mWorker.wait();
        }
        mWorker = null;
      }
    }
    catch (InterruptedException e) {
    }
  }

  void sendData(long channel, byte b[], int off, int len) throws IOException {
    send(new SendData(channel, b, off, len));
  }

  void sendOpenChannel(long channel) throws IOException {
    send(new OpenChannel(channel));
  }

  void sendCloseChannel(long channel) throws IOException {
    send(new CloseChannel(channel));
  }

  private void send(SendItem item) throws IOException {
    if (mWorker == null) {
      throw new RuntimeException("Attempting to send item when sender is not running");
    }
    mPendingItems.add(item);
    item.sync();
  }

  private static abstract class SendItem {
    final long mChannel;
    private boolean mDone;
    private IOException mException;

    SendItem(long channel) {
      mChannel = channel;
    }

    /**
     * Encodes the item to the provided {@link Encoder}, unblocking any calls to {@link #sync}.
     *
     * @return true if the item was fully sent, or false if there is more to send.
     */
    final boolean send(Encoder e) {
      try {
        return encode(e);
      }
      catch (IOException exception) {
        synchronized (this) {
          mException = exception;
        }
        return true;
      }
      finally {
        synchronized (this) {
          mDone = true;
          notifyAll();
        }
      }
    }

    /**
     * Waits for {@link #send} to be called, re-throwing an {@link java.io.IOException} if there was an exception
     * thrown while sending the item.
     */
    final void sync() throws IOException {
      synchronized (this) {
        while (!mDone) {
          try {
            this.wait();
          }
          catch (InterruptedException e) {
          }
        }
        if (mException != null) {
          throw mException;
        }
      }
    }


    /** @return true if the item was fully sent, or false if there is more to send. */
    protected abstract boolean encode(Encoder e) throws IOException;
  }

  private static class OpenChannel extends SendItem {
    OpenChannel(long channel) {
      super(channel);
    }

    @Override
    protected boolean encode(Encoder e) throws IOException {
      e.uint8(Message.OPEN_CHANNEL);
      e.uint32(mChannel);
      return true;
    }
  }

  private static class CloseChannel extends SendItem {
    CloseChannel(long channel) {
      super(channel);
    }

    @Override
    protected boolean encode(Encoder e) throws IOException {
      e.uint8(Message.CLOSE_CHANNEL);
      e.uint32(mChannel);
      return true;
    }
  }

  /** SendNop encodes nothing, and is simply used to unblock the sender in {@link #end}. */
  private static class SendNop extends SendItem {
    SendNop() {
      super(0);
    }

    @Override
    protected boolean encode(Encoder e) {
      return true;
    }
  }

  private final class Worker extends Thread {
    private final Encoder mEncoder;
    private boolean mIsRunning;
    private boolean mIsStopped;

    Worker(Encoder encoder) {
      super("rpclib.multiplex Sender");
      mEncoder = encoder;
      mIsRunning = true;
    }

    public boolean isStopped() {
      return mIsStopped;
    }

    public void setRunning(boolean running) {
      mIsRunning = running;
    }

    @Override
    public void run() {
      SendMap map = new SendMap();
      try {
        while (mIsRunning) {
          SendItem item;
          if (map.size() == 0) {
            // If there's nothing being worked on, block until we have something.
            item = mPendingItems.take();
          }
          else {
            // If we're busy, grab more work only if there's something there.
            item = mPendingItems.poll();
          }
          if (item != null) {
            map.add(item);
            map.flush(mEncoder);
          }
        }
        // Drain map
        while (map.size() > 0) {
          map.flush(mEncoder);
        }
        // Signal that this thread is done
        synchronized (this) {
          mIsStopped = true;
          notifyAll();
        }
      }
      catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
  }

  private class SendData extends SendItem {
    final byte[] mData;
    int mOffset;
    int mLength;

    SendData(long channel, byte[] data, int off, int len) {
      super(channel);
      mData = data;
      mOffset = off;
      mLength = len;
    }

    @Override
    protected boolean encode(Encoder e) throws IOException {
      e.uint8(Message.DATA);
      e.uint32(mChannel);
      int c = Math.min(mLength, mMtu);
      e.uint32(c);
      e.stream().write(mData, mOffset, c);
      mOffset += c;
      mLength -= c;
      return mLength == 0;
    }
  }

  private class SendMap {
    @NotNull private final TLongObjectHashMap<Queue<SendItem>> mQueues =
      new TLongObjectHashMap<Queue<SendItem>>();

    public int size() {
      return mQueues.size();
    }

    public void add(SendItem item) {
      long channel = item.mChannel;
      Queue<SendItem> queue = mQueues.get(channel);
      if (queue == null) {
        queue = new ArrayDeque<SendItem>();
        mQueues.put(channel, queue);
      }
      queue.add(item);
    }

    public void flush(Encoder e) {
      TLongObjectIterator<Queue<SendItem>> it = mQueues.iterator();
      for (int i = mQueues.size(); i-- > 0; ) {
        it.advance();
        Queue<SendItem> queue = it.value();
        SendItem item = queue.peek();
        if (item.send(e)) {
          // Item has been fully encoded.
          queue.remove();
          if (queue.poll() == null) {
            it.remove();
          }
        }
      }
    }
  }

}
