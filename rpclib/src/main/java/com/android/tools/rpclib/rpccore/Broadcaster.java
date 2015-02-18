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
package com.android.tools.rpclib.rpccore;

import com.android.tools.rpclib.binary.Decoder;
import com.android.tools.rpclib.binary.Encoder;
import com.android.tools.rpclib.multiplex.Channel;
import com.android.tools.rpclib.multiplex.Multiplexer;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;

public class Broadcaster {
  private final Multiplexer mMultiplexer;
  private final int mMtu;

  public Broadcaster(@NotNull InputStream in, @NotNull OutputStream out, int mtu,
                     @NotNull ExecutorService executorService) {
    mMultiplexer = new Multiplexer(in, out, mtu, executorService, null);
    mMtu = mtu;
  }

  private static void writeHeader(@NotNull Encoder encoder) throws IOException {
    encoder.int8((byte)'r');
    encoder.int8((byte)'p');
    encoder.int8((byte)'c');
    encoder.int8((byte)'0');
  }

  public Result Send(@NotNull Call call) throws IOException, RpcException {
    Channel channel = mMultiplexer.openChannel();

    try {
      BufferedOutputStream out = new BufferedOutputStream(channel.getOutputStream(), mMtu);
      Encoder e = new Encoder(out);
      Decoder d = new Decoder(channel.getInputStream());

      // Write the RPC header
      writeHeader(e);

      // Write the call
      e.object(call);

      // Flush the buffer
      out.flush();

      // Wait for and read the response
      Object res = d.object();

      // Check to see if the response was an error
      if (res instanceof RpcError) {
        throw new RpcException((RpcError)res);
      }

      return (Result)res;
    }
    finally {
      // Close the channel
      channel.close();
    }
  }

  static {
    // Make sure the RpcError type is properly registered.
    assert ObjectFactory.RpcErrorID != null;
  }
}
