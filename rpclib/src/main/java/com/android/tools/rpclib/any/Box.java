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
package com.android.tools.rpclib.any;

import com.android.tools.rpclib.binary.BinaryObject;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

public abstract class Box implements BinaryObject {
  public abstract Object unwrap();

  public static Box wrap(Object value) {
    if (value instanceof BinaryObject) {
      return new ObjectBox().setValue((BinaryObject)value);
    }
    if (value instanceof Boolean) {
      return new Bool().setValue((Boolean)value);
    }
    // TODO: signed/unsigned variants are indistinguishable in java
    if (value instanceof Byte) {
      return new Uint8().setValue((Byte)value);
    }
    if (value instanceof Short) {
      return new Uint16().setValue((Short)value);
    }
    if (value instanceof Integer) {
      return new Uint32().setValue((Integer)value);
    }
    if (value instanceof Long) {
      return new Uint64().setValue((Long)value);
    }
    if (value instanceof Float) {
      return new Float32().setValue((Float)value);
    }
    if (value instanceof Double) {
      return new Float64().setValue((Double)value);
    }
    if (value instanceof String) {
      return new StringBox().setValue((String)value);
    }
    // TODO: slice types
    throw new NotImplementedException();
  }
}
