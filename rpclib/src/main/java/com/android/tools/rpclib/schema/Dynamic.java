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


import com.android.tools.rpclib.binary.*;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class Dynamic implements BinaryObject, Render.ToComponent  {
  private Klass mKlass;
  private Object[] mFields;
  public Dynamic(Klass klass) {
    mKlass = klass;
  }

  public SchemaClass type() {
    return mKlass.mType;
  }

  public static void register(SchemaClass type) {
    Namespace.register(type.getTypeID(), new Klass(type));
  }

  public int getFieldCount() {
    return mFields.length;
  }

  public Field getFieldInfo(int index) {
    return mKlass.mType.getFields()[index];
  }

  public Object getFieldValue(int index) {
    return mFields[index];
  }

  @Override
  public void render(@NotNull SimpleColoredComponent component, SimpleTextAttributes defaultAttributes) {
    component.append("{", SimpleTextAttributes.GRAY_ATTRIBUTES);
    for (int index = 0; index < mFields.length; ++index) {
      if (index > 0) {
        component.append(",", SimpleTextAttributes.GRAY_ATTRIBUTES);
      }
      mKlass.mType.getFields()[index].getType().render(mFields[index], component, defaultAttributes);
    }
    component.append("}", SimpleTextAttributes.GRAY_ATTRIBUTES);
  }

    @NotNull
  @Override
  public Klass klass() {
    return mKlass;
  }

  public static class Klass implements BinaryClass {
    private SchemaClass mType;

    Klass(SchemaClass type) {
      mType = type;
    }
    @Override @NotNull
    public BinaryID id() { return mType.getTypeID(); }

    @Override @NotNull
    public BinaryObject create() { return new Dynamic(this); }

    @Override
    public void encode(@NotNull Encoder e, BinaryObject obj) throws IOException {
      Dynamic o = (Dynamic)obj;
      assert(o.mKlass == this);
      for (int i = 0; i < mType.getFields().length; i++) {
        Field field = mType.getFields()[i];
        Object value = o.mFields[i];
        field.getType().encodeValue(e, value);
      }
    }

    @Override
    public void decode(@NotNull Decoder d, BinaryObject obj) throws IOException {
      Dynamic o = (Dynamic)obj;
      o.mFields = new Object[mType.getFields().length];
      for (int i = 0; i < mType.getFields().length; i++) {
        Field field = mType.getFields()[i];
        o.mFields[i] = field.getType().decodeValue(d);
      }
    }
  }
}
