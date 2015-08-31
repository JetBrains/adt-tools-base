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

import com.android.annotations.concurrency.GuardedBy;
import com.android.tools.rpclib.binary.Decoder;
import com.android.tools.rpclib.binary.Encoder;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;

public class Multiplexer {
  @NotNull private static final Logger LOG = Logger.getInstance(Multiplexer.class);
  private final Decoder mDecoder;
  private final Encoder mEncoder;
  private final NewChannelListener mNewChannelListener;
  private final Channel.EventHandler mChannelEventHandler;
  private final Sender mSender;
  private final AtomicLong mNextChannelId;
  @GuardedBy("mChannelMap") private final Map<Long, Channel> mChannelMap;

  public Multiplexer(@NotNull InputStream in, @NotNull OutputStream out, int mtu,
                     @NotNull ExecutorService executorService,
                     @Nullable NewChannelListener newChannelListener) {
    mDecoder = new Decoder(in);
    mEncoder = new Encoder(out);
    mNewChannelListener = newChannelListener;
    mChannelEventHandler = new ChannelEventHandler();
    mSender = new Sender(mtu, executorService);
    mChannelMap = new HashMap<Long, Channel>();
    mNextChannelId = new AtomicLong(0);
    executorService.execute(new Receiver());
  }

  public Channel openChannel() throws IOException {
    final long id = mNextChannelId.getAndIncrement();
    Channel channel = newChannel(id);
    mSender.sendOpenChannel(id);
    return channel;
  }

  private Channel newChannel(final long id) throws IOException {
    Long key = Long.valueOf(id);
    Channel channel = new Channel(id, mChannelEventHandler);

    synchronized (mChannelMap) {
      if (mChannelMap.isEmpty()) {
        mSender.begin(mEncoder);
      }
      mChannelMap.put(key, channel);
    }

    return channel;
  }

  private void deleteChannel(long id) {
    Long key = Long.valueOf(id);
    synchronized (mChannelMap) {
      if (mChannelMap.containsKey(key)) {
        // TODO: Mark channel closed.
        mChannelMap.remove(key);
        if (mChannelMap.isEmpty()) {
          mSender.end();
        }
      }
      else {
        // This can happen when both ends close simultaneously.
        LOG.info("Attempting to close unknown channel " + id);
      }
    }
  }

  private Channel getChannel(long id) {
    Long key = Long.valueOf(id);
    Channel channel;
    synchronized (mChannelMap) {
      channel = mChannelMap.get(key);
    }
    return channel;
  }

  private void closeAllChannels() {
    synchronized (mChannelMap) {
      for (Channel c : mChannelMap.values()) {
        try {
          c.close();
        }
        catch (IOException ignored) {}
      }
      mChannelMap.clear();
    }
  }

  private class ChannelEventHandler implements Channel.EventHandler {
    @Override
    public void closeChannel(long id) throws IOException {
      mSender.sendCloseChannel(id);
      deleteChannel(id);
    }

    @Override
    public void writeChannel(long id, byte[] b, int off, int len) throws IOException {
      mSender.sendData(id, b, off, len);
    }
  }

  private class Receiver extends Thread {
    Receiver() {
      super("rpclib.multiplex Receiver");
    }

    @Override
    public void run() {
      try {
        while (true) {
          short msgType = mDecoder.uint8();
          long id = ~(mDecoder.uint32() & 0xffffffff);
          switch (msgType) {
            case Message.OPEN_CHANNEL: {
              Channel channel = newChannel(id);
              if (mNewChannelListener != null) {
                mNewChannelListener.onNewChannel(channel);
              }
              break;
            }
            case Message.CLOSE_CHANNEL: {
              Channel channel = getChannel(id);
              if (channel != null) {
                channel.closeNoEvent();
                deleteChannel(id);
              }
              break;
            }
            case Message.DATA: {
              int count = mDecoder.uint32();
              byte[] buf = new byte[count];
              for (int offset = 0; offset < count;) {
                offset += mDecoder.stream().read(buf, offset, count-offset);
              }
              Channel channel = getChannel(id);
              if (channel != null) {
                channel.receive(buf);
              }
              else {
                // Likely this channel was closed this side, and we're receiving data
                // that should be dropped on the floor.
                LOG.info("Received data on unknown channel " + id);
              }
              break;
            }
            default:
              throw new UnsupportedOperationException("Unknown msgType: " + msgType);
          }
        }
      }
      catch (IOException e) {
        LOG.info(e);
      }
      catch (UnsupportedOperationException e) {
        LOG.error(e);
      }
      finally {
        closeAllChannels();
      }
    }
  }

}
