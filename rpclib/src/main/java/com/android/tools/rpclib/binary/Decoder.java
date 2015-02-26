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

import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;

/**
 * A decoder of various RPC primitive types.
 */
public class Decoder {
  @NotNull private final TIntObjectHashMap<BinaryObject> mDecodedMap;
  @NotNull private final InputStream mInputStream;
  @NotNull private final byte[] mBuffer;

  public Decoder(@NotNull InputStream in) {
    mDecodedMap = new TIntObjectHashMap<BinaryObject>();
    mInputStream = in;
    mBuffer = new byte[8];
  }

  public void read(byte[] buf, int count) throws IOException {
    int off = 0;
    while (off < count) {
      off += mInputStream.read(buf, off, count - off);
    }
  }

  private void read(int count) throws IOException {
    read(mBuffer, count);
  }

  public boolean bool() throws IOException {
    read(1);
    return mBuffer[0] != 0;
  }

  public byte int8() throws IOException {
    read(1);
    return mBuffer[0];
  }

  public byte uint8() throws IOException {
    return int8();
  }

  public short int16() throws IOException {
    read(2);
    int i = 0;
    i |= (mBuffer[0] & 0xff);
    i |= (mBuffer[1] & 0xff) << 8;
    return (short)i;
  }

  public short uint16() throws IOException {
    return int16();
  }

  public int int32() throws IOException {
    read(4);
    int i = 0;
    i |= (mBuffer[0] & 0xff);
    i |= (mBuffer[1] & 0xff) << 8;
    i |= (mBuffer[2] & 0xff) << 16;
    i |= (mBuffer[3] & 0xff) << 24;
    return i;
  }

  public int uint32() throws IOException {
    return int32();
  }

  public long int64() throws IOException {
    read(8);
    long i = 0;
    i |= (mBuffer[0] & 0xffL);
    i |= (mBuffer[1] & 0xffL) << 8;
    i |= (mBuffer[2] & 0xffL) << 16;
    i |= (mBuffer[3] & 0xffL) << 24;
    i |= (mBuffer[4] & 0xffL) << 32;
    i |= (mBuffer[5] & 0xffL) << 40;
    i |= (mBuffer[6] & 0xffL) << 48;
    i |= (mBuffer[7] & 0xffL) << 56;
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
    int key = uint16() & 0xffff;

    if (key == BinaryObject.NULL_ID) {
      return null;
    }

    BinaryObject obj = mDecodedMap.get(key);
    if (obj != null) {
      return obj;
    }

    ObjectTypeID type = new ObjectTypeID(this);
    BinaryObjectCreator creator = ObjectTypeID.lookup(type);
    if (creator == null) {
      throw new RuntimeException("Unknown type id encountered: " + type);
    }
    obj = creator.create();
    obj.decode(this);

    mDecodedMap.put(key, obj);
    return obj;
  }

  public InputStream stream() {
    return mInputStream;
  }
}
