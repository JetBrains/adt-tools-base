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

public class Simple {
  public final int value;

  public Simple(int value) {
    this.value = value;
  }

  public void encode(@NotNull Encoder e) throws IOException {
    e.uint32(value);
  }

  public static Simple decode(@NotNull Decoder d) throws IOException {
    return new Simple(d.uint32());
  }

  @Override
  public String toString() {
    return String.valueOf(value);
  }
}
