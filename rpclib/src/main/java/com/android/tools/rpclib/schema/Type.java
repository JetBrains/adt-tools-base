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
    private String mName = null;

    @NotNull
    public final String getName() {
        if (mName == null) {
            StringBuilder out = new StringBuilder();
            name(out);
            mName = out.toString();
        }
        return mName;
    }

    public abstract void encodeValue(@NotNull Encoder e, Object value) throws IOException;

    public abstract Object decodeValue(@NotNull Decoder d) throws IOException;

    public abstract void encode(@NotNull Encoder e) throws IOException;

    public static Type decode(@NotNull Decoder d) throws IOException {
        byte v = d.uint8();
        TypeTag tag = new TypeTag((byte)(v & 0xf));
        v = (byte)((v >> 4) & 0xf);
        switch (tag.value) {
            case TypeTag.PrimitiveTag:
                return new Primitive(d, new Method(v));
            case TypeTag.StructTag:
                return new Struct(d);
            case TypeTag.PointerTag:
                return new Pointer(d);
            case TypeTag.InterfaceTag:
                return new Interface(d);
            case TypeTag.VariantTag:
                return new Variant(d);
            case TypeTag.AnyTag:
                return new AnyType(d);
            case TypeTag.SliceTag:
                return new Slice(d);
            case TypeTag.ArrayTag:
                return new Array(d);
            case TypeTag.MapTag:
                return new Map(d);
            default:
                throw new IOException("Decode unknown type " + tag);
        }
    }

    abstract void name(StringBuilder out);

    public abstract void signature(StringBuilder out);

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Type)) return false;
        return (getName().equals(((Type)o).getName()));
    }

    @Override
    public int hashCode() {
        return getName().hashCode();
    }
}