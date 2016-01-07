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
package com.android.tools.rpclib.schema;

import com.android.tools.rpclib.binary.Decoder;
import com.android.tools.rpclib.binary.Encoder;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public final class ConstantSet {
  private Type mType;
  private Constant[] mEntries;
  private HashMap<Object, List<Constant>> byValue;
  private HashMap<String, List<Constant>> byStrValue;

  private static final HashMap<Type, ConstantSet> mRegistry = new HashMap<Type, ConstantSet>();

  public static void register(ConstantSet set) {
    mRegistry.put(set.getType(), set);
  }

  public static ConstantSet lookup(Type type) {
    return mRegistry.get(type);
  }

  // Constructs a default-initialized {@link ConstantSet}.
  public ConstantSet(@NotNull Decoder d) throws IOException {
    mType = Type.decode(d);
    mEntries = new Constant[d.uint32()];
    for (int i = 0; i < mEntries.length; i++) {
      mEntries[i] = new Constant();
      mEntries[i].mName = d.string();
      mEntries[i].mValue = mType.decodeValue(d);
    }
  }

  public Type getType() {
    return mType;
  }

  public Constant[] getEntries() {
    return mEntries;
  }

  private HashMap<Object, List<Constant>> byValue() {
    if (byValue == null) {
      // build a map from value (as Object) to List of possible constants.
      byValue = new HashMap<Object, List<Constant>>();
      for (Constant constant : mEntries) {
        List<Constant> list;
        if (!byValue.containsKey(constant.getValue())) {
          list = new ArrayList<Constant>();
          byValue.put(constant.getValue(), list);
        } else {
          list = byValue.get(constant.getValue());
        }
        list.add(constant);
      }
    }
    return byValue;
  }

  private HashMap<String, List<Constant>> byStrValue() {
    if (byStrValue == null) {
      // build a map from value (as String) to List of possible constants.
      byStrValue = new HashMap<String, List<Constant>>();
      for (Constant constant : mEntries) {
        List<Constant> list;
        String strVal = constant.getValue().toString();
        if (!byStrValue.containsKey(strVal)) {
          list = new ArrayList<Constant>();
          byStrValue.put(strVal, list);
        } else {
          list = byStrValue.get(strVal);
        }
        list.add(constant);
      }
    }
    return byStrValue;
  }

  public List<Constant> getByValue(Object value) {
    if (mEntries.length == 0) {
      return null;
    }
    if (mEntries[0].getClass() == value.getClass()) {
      // Prefer an exact match, when possible.
      return byValue().get(value);
    } else {
      return byStrValue().get(value.toString());
    }
  }
  public void encode(@NotNull Encoder e) throws IOException {
    mType.encode(e);
    for (Constant mEntry : mEntries) {
      e.string(mEntry.mName);
      mType.encodeValue(e, mEntry.mValue);
    }
  }
}
