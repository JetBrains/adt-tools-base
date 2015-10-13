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

import com.android.tools.rpclib.schema.Dynamic;
import com.android.tools.rpclib.schema.Entity;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;

/**
 * A decoder of various RPC primitive types.
 * The encoding format is documented at the following link:
 * https://android.googlesource.com/platform/tools/gpu/+/master/binary/doc.go
 */
public class Decoder {
  @NotNull private final TIntObjectHashMap<Entity> mEntities;
  @NotNull private final TIntObjectHashMap<BinaryObject> mObjects;
  @NotNull private final InputStream mInputStream;
  @NotNull private final byte[] mBuffer;
  @NotNull private final EncodingControl mControl = new EncodingControl();

  public Decoder(@NotNull InputStream in) {
    mEntities = new TIntObjectHashMap<Entity>();
    mObjects = new TIntObjectHashMap<BinaryObject>();
    mInputStream = in;
    mBuffer = new byte[9];
    mEntities.put(0, null);
    mObjects.put(0, null);
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


  private long intv() throws IOException {
    long uv = uintv();
    long v = uv >>> 1;
    if ((uv & 1) != 0) {
      v = ~v;
    }
    return v;
  }

  private long uintv() throws IOException {
    read(1);
    int count = 0;
    while (((0x80 >> count) & mBuffer[0]) != 0) count++;
    long v = mBuffer[0] & (0xff >> count);
    if (count == 0) {
      return v;
    }
    read(count);
    for (int i = 0; i < count; i++) {
      v = (v << 8) | (mBuffer[i] & 0xffL);
    }
    return v;
  }

  public short int16() throws IOException {
    return (short)intv();
  }

  public short uint16() throws IOException {
    return (short)uintv();
  }

  public int int32() throws IOException {
    return (int)intv();
  }

  public int uint32() throws IOException {
    return (int)uintv();
  }

  public long int64() throws IOException {
    return intv();
  }

  public long uint64() throws IOException {
    return uintv();
  }

  public float float32() throws IOException {
    int bits = (int)uintv();
    int shuffled = ((bits & 0x000000ff) <<  24) |
                   ((bits & 0x0000ff00) <<   8) |
                   ((bits & 0x00ff0000) >>   8) |
                   ((bits & 0xff000000) >>> 24);
    return Float.intBitsToFloat(shuffled);
  }

  public double float64() throws IOException {
    long bits = uintv();
    long shuffled = ((bits & 0x00000000000000ffL) <<  56) |
                    ((bits & 0x000000000000ff00L) <<  40) |
                    ((bits & 0x0000000000ff0000L) <<  24) |
                    ((bits & 0x00000000ff000000L) <<   8) |
                    ((bits & 0x000000ff00000000L) >>   8) |
                    ((bits & 0x0000ff0000000000L) >>  24) |
                    ((bits & 0x00ff000000000000L) >>  40) |
                    ((bits & 0xff00000000000000L) >>> 56);
    return Double.longBitsToDouble(shuffled);
  }

  public String string() throws IOException {
    int size = uint32();
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

  public String nonCompactString() throws IOException {
    return (mControl.mode != EncodingControl.Compact) ? string() : "";
  }

  private int readSid() throws IOException {
    int v = uint32();
    if (v == 1) { // encoded sid 0 is a special marker
      // read control block.
      mControl.decode(this);
      // read the real sid
      v = uint32();
    }
    return v;
  }

  public Entity entity() throws IOException {
    int v = readSid();
    int sid = v >> 1;
    if ((v & 1) != 0) {
      Entity entity = new Entity();
      mEntities.put(sid, entity);
      entity.decode(this);
      return entity;
    }
    if (!mEntities.containsKey(sid)) {
      throw new RuntimeException("Unknown entity: " + sid);
    }
    return mEntities.get(sid);
  }

  public void value(@NotNull BinaryObject obj) throws IOException {
    obj.klass().decode(this, obj);
  }

  @Nullable
  public BinaryObject variant() throws IOException {
    Entity entity = entity();
    if (entity == null) {
      return  null;
    }
    BinaryClass c = Namespace.lookup(entity);
    if (c == null) {
      c = Dynamic.register(entity);
    }
    BinaryObject obj = c.create();
    c.decode(this, obj);
    return obj;
  }

  @Nullable
  public BinaryObject object() throws IOException {
    int v = readSid();
    int sid = v >> 1;
    if ((v & 1) != 0) {
      BinaryObject obj = variant();
      mObjects.put(sid, obj);
      return obj;
    }
    if (!mObjects.containsKey(sid)) {
      throw new RuntimeException("Unknown object: " + sid);
    }
    return mObjects.get(sid);
  }

  public InputStream stream() {
    return mInputStream;
  }

  public int getMode() {
    return mControl.mode;
  }
}
