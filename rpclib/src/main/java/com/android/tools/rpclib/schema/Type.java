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


import com.android.tools.rpclib.binary.BinaryObject;
import com.android.tools.rpclib.binary.Decoder;
import com.android.tools.rpclib.binary.Encoder;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public abstract class Type implements BinaryObject {
  private ConstantSet myConstants;

  @NotNull
  public static Type wrap(BinaryObject object) {
    return (Type)object;
  }

  @NotNull
  public BinaryObject unwrap() {
    return this;
  }

  public abstract void encodeValue(@NotNull Encoder e, Object value) throws IOException;

  public abstract Object decodeValue(@NotNull Decoder d) throws IOException;

  public abstract void render(@NotNull Object value, @NotNull SimpleColoredComponent component);

  private static final int MAX_DISPLAY = 3;

  public static void renderArray(@NotNull Object value, @NotNull Type valueType, @NotNull SimpleColoredComponent component) {
    assert (value instanceof Object[]);
    Object[] array = (Object[])value;
    int count = Math.min(array.length, MAX_DISPLAY);
    component.append("[", SimpleTextAttributes.GRAY_ATTRIBUTES);
    for (int index = 0; index < count; ++index) {
      if (index > 0) {
        component.append(",", SimpleTextAttributes.GRAY_ATTRIBUTES);
      }
      valueType.render(array[index], component);
    }
    if (count < array.length) {
      component.append("...", SimpleTextAttributes.GRAY_ATTRIBUTES);
    }
  }
  public static void renderObject(@NotNull Object value, @NotNull SimpleColoredComponent component) {
    assert (value instanceof BinaryObject);
    if (value instanceof Renderable) {
      ((Renderable)value).render(component);
    } else {
      component.append(value.toString(), SimpleTextAttributes.SYNTHETIC_ATTRIBUTES);
    }
  }

  public interface Renderable {
    void render(@NotNull SimpleColoredComponent component);
  }
}
