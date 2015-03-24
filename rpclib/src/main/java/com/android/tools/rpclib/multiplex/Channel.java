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

import org.jetbrains.annotations.NotNull;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class Channel implements Closeable {
  private final OutputStream mOutputStream;
  private final InputStream mInputStream;
  private final PipeInputStream mPipeInputStream;
  private final long mId;
  private final EventHandler mEventHandler;
  private boolean mIsClosed;

  public Channel(long id, @NotNull EventHandler events) throws IOException {
    PipeInputStream in = new PipeInputStream();

    mId = id;
    mEventHandler = events;
    mInputStream = in;
    mOutputStream = new Output();
    mPipeInputStream = in;
  }

  public InputStream getInputStream() {
    return mInputStream;
  }

  public OutputStream getOutputStream() {
    return mOutputStream;
  }

  @Override
  public synchronized void close() throws IOException {
    if (!mIsClosed) {
      mIsClosed = true;
      mEventHandler.closeChannel(mId);
      mInputStream.close();
      mOutputStream.close();
    }
  }

  void receive(byte[] data) throws IOException {
    mPipeInputStream.getSource().write(data);
  }

  synchronized void closeNoEvent() throws IOException {
    if (!mIsClosed) {
      mIsClosed = true;
      mInputStream.close();
      mOutputStream.close();
    }
  }

  interface EventHandler {
    void closeChannel(long id) throws IOException;
    void writeChannel(long id, byte b[], int off, int len) throws IOException;
  }

  private class Output extends OutputStream {
    @Override
    public void write(int b) throws IOException, UnsupportedOperationException {
      write(new byte[]{(byte)b}, 0, 1);
      // We really, really should not be writing out single bytes. For now, throw an exception.
      throw new UnsupportedOperationException("Use write(byte[], int, int) instead of writing single bytes!");
    }

    @Override
    public void write(byte b[], int off, int len) throws IOException {
      mEventHandler.writeChannel(mId, b, off, len);
    }

    @Override
    public void close() throws IOException {
      Channel.this.close();
    }
  }
}
