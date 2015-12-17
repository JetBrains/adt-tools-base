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

import com.android.tools.rpclib.schema.Entity;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 *
 */
public class Namespace {
  private static Map<String, BinaryClass> registry = new HashMap<String, BinaryClass>();

  @NotNull private static final Logger LOG = Logger.getInstance(Namespace.class);
  public static void register(BinaryClass creator) {
    registry.put(creator.entity().signature(), creator);
  }

  public static BinaryClass lookup(Entity entity) {
    return registry.get(entity.signature());
  }
}
