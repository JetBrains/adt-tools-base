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

public abstract class Type {

    @NotNull
    public abstract String getName();

    public abstract void encodeValue(@NotNull Encoder e, Object value) throws IOException;

    public abstract Object decodeValue(@NotNull Decoder d) throws IOException;

    public abstract void encode(@NotNull Encoder e, boolean compact) throws IOException;

    public static Type decode(@NotNull Decoder d, boolean compact) throws IOException {
        TypeTag tag = TypeTag.decode(d);
        switch (tag.value) {
            case TypeTag.PrimitiveTag:
                return new Primitive(d, compact);
            case TypeTag.StructTag:
                return new Struct(d, compact);
            case TypeTag.PointerTag:
                return new Pointer(d, compact);
            case TypeTag.InterfaceTag:
                return new Interface(d, compact);
            case TypeTag.VariantTag:
                return new Variant(d, compact);
            case TypeTag.AnyTag:
                return new AnyType(d, compact);
            case TypeTag.SliceTag:
                return new Slice(d, compact);
            case TypeTag.ArrayTag:
                return new Array(d, compact);
            case TypeTag.MapTag:
                return new Map(d, compact);
            default:
                throw new IOException("Decode unknown type " + tag);
        }
    }
}
