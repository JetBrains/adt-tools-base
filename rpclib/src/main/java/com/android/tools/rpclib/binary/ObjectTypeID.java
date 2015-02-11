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

import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * An object used to hold the registry of type id to {@link BinaryObjectCreator}s.
 * </p>
 * See: {@link BinaryObject}
 */
public class ObjectTypeID {
  private static final int SIZE = 20;
  private static Map<ObjectTypeID, BinaryObjectCreator> registry = new HashMap<ObjectTypeID, BinaryObjectCreator>();
  private byte[] mValue;
  private int mHash;

  public ObjectTypeID(byte[] value) {
    mValue = value;
    mHash = ByteBuffer.wrap(mValue).getInt();
    assert value.length == SIZE;
  }

  public ObjectTypeID(Decoder d) throws IOException {
    mValue = new byte[SIZE];
    d.read(mValue, SIZE);
    mHash = ByteBuffer.wrap(mValue).getInt();
  }

  public static void register(ObjectTypeID id, BinaryObjectCreator creator) {
    registry.put(id, creator);
  }

  public static BinaryObjectCreator lookup(ObjectTypeID id) {
    return registry.get(id);
  }

  public void encode(Encoder e) throws IOException {
    e.stream().write(mValue, 0, SIZE);
  }

  @Override
  public boolean equals(Object other) {
    if (other == null) return false;
    if (other == this) return true;
    if (!(other instanceof ObjectTypeID)) return false;
    return Arrays.equals(mValue, ((ObjectTypeID)other).mValue);
  }

  @Override
  public int hashCode() {
    return mHash;
  }

  @Override
  public String toString() {
    return String.format("%020x", new BigInteger(mValue));
  }
}
