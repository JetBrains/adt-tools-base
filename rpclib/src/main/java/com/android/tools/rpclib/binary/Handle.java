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
package com.android.tools.rpclib.binary;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * A binary resource handle. These are used as 20-byte-unique identifiers to an immutable object or
 * immutable data. They are often returned from RPC calls and if the client already has the object
 * or data cached locally, offer a way to avoid an unnecessary transfer of data from the server.
 */
public class Handle {
  private static final int SIZE = 20;
  @NotNull private final byte[] mValue = new byte[SIZE];
  private final int mHashCode;

  public Handle(@NotNull byte[] value) {
    assert value.length == SIZE;
    System.arraycopy(value, 0, mValue, 0, SIZE);
    mHashCode = ByteBuffer.wrap(mValue).getInt();
  }

  public Handle(@NotNull Decoder d) throws IOException {
    assert d.stream().read(mValue) == SIZE;
    mHashCode = ByteBuffer.wrap(mValue).getInt();
  }

  public void encode(@NotNull Encoder e) throws IOException {
    e.stream().write(mValue);
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof Handle)) {
      return false;
    }
    if (other == this) {
      return true;
    }
    return Arrays.equals(mValue, ((Handle)other).mValue);
  }

  @Override
  public String toString() {
    return String.format("%0" + SIZE*2 + "x", new BigInteger(mValue));
  }

  @Override
  public int hashCode() {
    return mHashCode;
  }
}
