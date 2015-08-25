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

import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;

public final class Render {
  public interface ToComponent {
    void render(@NotNull SimpleColoredComponent component, SimpleTextAttributes defaultAttributes);
  }

  public static void object(@NotNull Object value, @NotNull SimpleColoredComponent component, SimpleTextAttributes defaultAttributes) {
    if (value instanceof ToComponent) {
      ((ToComponent)value).render(component, defaultAttributes);
    } else {
      component.append(value.toString(), defaultAttributes);
    }
  }

  private static final int MAX_DISPLAY = 3;

  public static void array(@NotNull Object value, @NotNull Type valueType, @NotNull SimpleColoredComponent component, SimpleTextAttributes defaultAttributes) {
    assert (value instanceof Object[]);
    Object[] array = (Object[])value;
    int count = Math.min(array.length, MAX_DISPLAY);
    component.append("[", SimpleTextAttributes.GRAY_ATTRIBUTES);
    for (int index = 0; index < count; ++index) {
      if (index > 0) {
        component.append(",", SimpleTextAttributes.GRAY_ATTRIBUTES);
      }
      valueType.render(array[index], component, defaultAttributes);
    }
    if (count < array.length) {
      component.append("...", SimpleTextAttributes.GRAY_ATTRIBUTES);
    }
  }
}
