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

import java.io.IOException;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;

class Sender {
  private static final int MAX_PENDING_SEND_COUNT = 1024;
  private static final SendItem NOP_ITEM = new SendNop();
  private final int mtu;
  private final LinkedBlockingQueue<SendItem> pending;
  private Worker worker;

  Sender(int mtu) {
    this.mtu = mtu;
    pending = new LinkedBlockingQueue(MAX_PENDING_SEND_COUNT);
  }

  void begin(Encoder out) {
    worker = new Worker(out);
    worker.start();
  }

  void end() {
    try {
      synchronized (worker) {
        worker.running = false;
        pending.add(NOP_ITEM); // Unblock the sender
        while (!worker.stopped) {
          worker.wait();
        }
        worker = null;
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
    if (worker == null) {
      throw new RuntimeException("Attempting to send item when sender is not running");
    }
    pending.add(item);
    item.sync();
  }

  private static abstract class SendItem {
    final long channel;
    private boolean done;
    private IOException exception;

    SendItem(long channel) {
      this.channel = channel;
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
          this.exception = exception;
        }
        return true;
      }
      finally {
        synchronized (this) {
          this.done = true;
          this.notifyAll();
        }
      }
    }

    /**
     * Waits for {@link #send} to be called, re-throwing an {@link java.io.IOException} if there was an exception
     * thrown while sending the item.
     */
    final void sync() throws IOException {
      synchronized (this) {
        while (!done) {
          try {
            this.wait();
          }
          catch (InterruptedException e) {
          }
        }
        if (exception != null) {
          throw exception;
        }
      }
    }


    // Returns true if the item was fully sent, or false if there is more to send.
    protected abstract boolean encode(Encoder e) throws IOException;
  }

  private static class OpenChannel extends SendItem {
    OpenChannel(long channel) {
      super(channel);
    }

    @Override
    protected boolean encode(Encoder e) throws IOException {
      e.uint8(Message.OPEN_CHANNEL);
      e.uint32(channel);
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
      e.uint32(channel);
      return true;
    }
  }

  // SendNop encodes nothing, and is simply used to unblock the sender in {@link #end}.
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
    private final Encoder encoder;
    boolean running;
    boolean stopped;

    Worker(Encoder encoder) {
      super("Multiplex sender");
      this.encoder = encoder;
      this.running = true;
    }

    @Override
    public void run() {
      SendMap map = new SendMap();
      try {
        while (running) {
          SendItem item;
          if (map.size() == 0) {
            // If there's nothing being worked on, block until we have something.
            item = pending.take();
          }
          else {
            // If we're busy, grab more work only if there's something there.
            item = pending.poll();
          }
          if (item != null) {
            map.add(item);
            map.flush(encoder);
          }
        }
        // Drain map
        while (map.size() > 0) {
          map.flush(encoder);
        }
        // Signal that this thread is done
        synchronized (this) {
          stopped = true;
          notifyAll();
        }
      }
      catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
  }

  private class SendData extends SendItem {
    final byte data[];
    int off;
    int len;

    SendData(long channel, byte data[], int off, int len) {
      super(channel);
      this.data = data;
      this.off = off;
      this.len = len;
    }

    @Override
    protected boolean encode(Encoder e) throws IOException {
      e.uint8(Message.DATA);
      e.uint32(channel);
      int c = Math.min(len, mtu);
      e.uint32(c);
      e.stream().write(data, off, c);
      off += c;
      len -= c;
      return len == 0;
    }
  }

  private class SendMap {
    private final Map<Long, Queue<SendItem>> map = new HashMap<Long, Queue<SendItem>>();

    public int size() {
      return map.size();
    }

    public void add(SendItem item) {
      long channel = item.channel;
      Queue<SendItem> queue = map.get(channel);
      if (queue == null) {
        queue = new ArrayDeque<SendItem>();
        map.put(channel, queue);
      }
      queue.add(item);
    }

    public void flush(Encoder e) {
      Iterator<Map.Entry<Long, Queue<SendItem>>> it = map.entrySet().iterator();
      while (it.hasNext()) {
        Map.Entry<Long, Queue<SendItem>> entry = it.next();
        Queue<SendItem> queue = entry.getValue();
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
