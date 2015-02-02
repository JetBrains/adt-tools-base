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
*
* THIS WILL BE REMOVED ONCE THE CODE GENERATOR IS INTEGRATED INTO THE BUILD.
*/
package com.android.tools.rpclib.rpccore;

import com.android.tools.rpclib.binary.*;

import java.io.IOException;

class ObjectFactory {
  public static byte[] RpcErrorIDBytes = {-26, -83, -92, -61, 22, 123, 123, -22, -48, 126, 53, -93, 23, -5, -83, -29, -8, -8, 125, 64,};
  public static ObjectTypeID RpcErrorID = new ObjectTypeID(RpcErrorIDBytes);
  static {
    ObjectTypeID.register(RpcErrorID, Entries.RpcErrorEnum);
  }

  public static void encode(Encoder e, RpcError o) throws IOException {
    e.string(o.msg);
  }

  public static void decode(Decoder d, RpcError o) throws IOException {
    o.msg = d.string();
  }

  public enum Entries implements BinaryObjectCreator {
    RpcErrorEnum {
      @Override
      public BinaryObject create() {
        return new RpcError();
      }
    },
  }
}
