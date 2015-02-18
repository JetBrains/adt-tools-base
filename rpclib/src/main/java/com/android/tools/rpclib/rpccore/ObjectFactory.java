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

import com.android.tools.rpclib.binary.BinaryObject;
import com.android.tools.rpclib.binary.BinaryObjectCreator;
import com.android.tools.rpclib.binary.Decoder;
import com.android.tools.rpclib.binary.Encoder;
import com.android.tools.rpclib.binary.ObjectTypeID;

import java.io.IOException;

class ObjectFactory {
    public enum Entries implements BinaryObjectCreator {
        ErrorEnum {
            @Override public BinaryObject create() {
                return new RpcError();
            }
        },
    }

    public static byte[] RpcErrorIDBytes = { 71, 28, 125, -19, 107, -92, 51, -36, 4, 63, 55, -2, -74, 94, 104, -95, 66, 106, -69, 7, };

    public static ObjectTypeID RpcErrorID = new ObjectTypeID(RpcErrorIDBytes);

    static {
        ObjectTypeID.register(RpcErrorID, Entries.ErrorEnum);
    }

    public static void encode(Encoder e, RpcError o) throws IOException {
        e.string(o.mMessage);
    }

    public static void decode(Decoder d, RpcError o) throws IOException {
        o.mMessage = d.string();
    }
}
