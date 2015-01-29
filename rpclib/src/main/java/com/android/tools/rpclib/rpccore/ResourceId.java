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
package com.android.tools.rpclib.rpccore;

import com.android.tools.rpclib.binary.Decoder;
import com.android.tools.rpclib.binary.Encoder;

import java.io.IOException;

/**
 * An identifier of a resource. These are often returned by RPC calls where
 * retrieving the data can be a lengthy process. For each {@link ResourceId}
 * type there will be a corresponding Resolve RPC function that fetches the
 * resource data from the server.
 */
public class ResourceId {
  final public String value;

  public ResourceId(String value) {
    this.value = value;
  }

  public ResourceId(Decoder d) throws IOException {
    this.value = d.string();
  }

  public void encode(Encoder e) throws IOException {
    e.string(this.value);
  }
}
