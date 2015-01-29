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

import com.android.tools.rpclib.binary.Decoder;
import com.android.tools.rpclib.binary.Encoder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class Multiplexer {
  private final Decoder in;
  private final Encoder out;
  private final int mtu;
  private final NewChannelListener newChannelListener;
  private final Channel.EventHandler channelEventHandler;
  private final Sender sender;
  private final Map<Long, Channel> channels;
  private final AtomicLong nextChannelId;

  public Multiplexer(@NotNull InputStream in, @NotNull OutputStream out, int mtu, @Nullable NewChannelListener newChannelListener) {
    this.in = new Decoder(in);
    this.out = new Encoder(out);
    this.mtu = mtu;
    this.newChannelListener = newChannelListener;
    this.channelEventHandler = new ChannelEventHandler();
    this.sender = new Sender(mtu);
    this.channels = new HashMap<Long, Channel>();
    this.nextChannelId = new AtomicLong(0);
    new Receiver().start();
  }

  public Channel openChannel() throws IOException {
    final long id = nextChannelId.getAndIncrement();
    Channel channel = newChannel(id);
    sender.sendOpenChannel(id);
    return channel;
  }

  private Channel newChannel(final long id) throws IOException {
    Channel channel = new Channel(id, channelEventHandler);

    synchronized (channels) {
      if (channels.size() == 0) {
        sender.begin(out);
      }
      channels.put(id, channel);
    }

    return channel;
  }

  private void deleteChannel(long id) {
    synchronized (channels) {
      if (channels.containsKey(id)) {
        // TODO: Mark channel closed.
        channels.remove(id);
        if (channels.size() == 0) {
          sender.end();
        }
      }
      else {
        // Attempting to close an unknown channel.
        // This can happen when both ends close simultaneously.
      }
    }
  }

  private Channel getChannel(long id) {
    Channel channel;
    synchronized (channels) {
      channel = channels.get(id);
    }
    return channel;
  }

  private void closeAllChannels() {
    synchronized (channels) {
      for (Channel c : channels.values()) {
        try {
          c.close();
        }
        catch (IOException e) {
        }
      }
      channels.clear();
    }
  }

  private class ChannelEventHandler implements Channel.EventHandler {
    @Override
    public void closeChannel(long id) throws IOException {
      sender.sendCloseChannel(id);
      deleteChannel(id);
    }

    @Override
    public void writeChannel(long id, byte[] b, int off, int len) throws IOException {
      sender.sendData(id, b, off, len);
    }
  }

  private class Receiver extends Thread {
    Receiver() {
      super("Multiplex receiver");
    }

    @Override
    public void run() {
      try {
        while (true) {
          short msgType = in.uint8();
          long id = in.uint32() ^ 0xffffffffL;
          switch (msgType) {
            case Message.OPEN_CHANNEL: {
              Channel channel = newChannel(id);
              newChannelListener.onNewChannel(channel);
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
              int count = (int)in.uint32();
              byte[] buf = new byte[count];
              in.stream().read(buf);
              Channel channel = getChannel(id);
              if (channel != null) {
                channel.receive(buf);
              }
              else {
                // Likely this channel was closed this side, and we're receiving data
                // that should be dropped on the floor.
              }
              break;
            }
            default:
              return; // Throw exception?
          }
        }
      }
      catch (IOException e) {
        // Maybe log?
      }
      finally {
        closeAllChannels();
      }
    }
  }

}
