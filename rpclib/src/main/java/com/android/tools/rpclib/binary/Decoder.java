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
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;

/**
 * A decoder of various RPC primitive types.
 */
public class Decoder {
  @NotNull private final Map<Integer, BinaryObject> decoded;
  @NotNull private final InputStream in;
  @NotNull private final byte[] buf;

  public Decoder(@NotNull InputStream in) {
    this.decoded = new HashMap<Integer, BinaryObject>();
    this.in = in;
    this.buf = new byte[8];
  }

  public void read(byte[] buf, int count) throws IOException {
    int off = 0;
    while (off < count) {
      off += in.read(buf, off, count - off);
    }
  }

  private void read(int count) throws IOException {
    read(this.buf, count);
  }

  public boolean bool() throws IOException {
    read(1);
    return buf[0] != 0;
  }

  public byte int8() throws IOException {
    read(1);
    return buf[0];
  }

  public short uint8() throws IOException {
    return (short)(int8() & 0xff);
  }

  public short int16() throws IOException {
    read(2);
    int i = 0;
    i |= (buf[0] & 0xff) << 0;
    i |= (buf[1] & 0xff) << 8;
    return (short)i;
  }

  public int uint16() throws IOException {
    return int16() & 0xffff;
  }

  public int int32() throws IOException {
    read(4);
    int i = 0;
    i |= (buf[0] & 0xff) << 0;
    i |= (buf[1] & 0xff) << 8;
    i |= (buf[2] & 0xff) << 16;
    i |= (buf[3] & 0xff) << 24;
    return i;
  }

  public long uint32() throws IOException {
    return int32() & 0xffffffffL;
  }

  public long int64() throws IOException {
    read(8);
    long i = 0;
    i |= (buf[0] & 0xffL) << 0;
    i |= (buf[1] & 0xffL) << 8;
    i |= (buf[2] & 0xffL) << 16;
    i |= (buf[3] & 0xffL) << 24;
    i |= (buf[4] & 0xffL) << 32;
    i |= (buf[5] & 0xffL) << 40;
    i |= (buf[6] & 0xffL) << 48;
    i |= (buf[7] & 0xffL) << 56;
    return i;
  }

  public long uint64() throws IOException {
    return int64();
  }

  public float float32() throws IOException {
    return Float.intBitsToFloat(int32());
  }

  public double float64() throws IOException {
    return Double.longBitsToDouble(int64());
  }

  public String string() throws IOException {
    int size = int32();
    byte[] bytes = new byte[size];
    for (int i = 0; i < size; i++) {
      bytes[i] = int8();
    }
    try {
      return new String(bytes, "UTF-8");
    }
    catch (UnsupportedEncodingException e) {
      throw new RuntimeException(e); // Should never happen
    }
  }

  @Nullable
  public BinaryObject object() throws IOException {
    int key = uint16();

    if (key == BinaryObject.NULL_ID) {
      return null;
    }

    BinaryObject obj = decoded.get(key);
    if (obj != null) {
      return obj;
    }

    ObjectTypeID type = new ObjectTypeID(this);
    BinaryObjectCreator creator = ObjectTypeID.lookup(type);
    if (creator == null) {
      throw new RuntimeException("Unknown type id encountered");
    }
    obj = creator.create();
    obj.decode(this);

    decoded.put(key, obj);
    return obj;
  }

  public InputStream stream() {
    return in;
  }
}
