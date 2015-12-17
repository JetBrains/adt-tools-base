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

public final class Slice extends Type {

    String mAlias;

    Type mValueType;

    public Slice(@NotNull Decoder d) throws IOException {
        mValueType = Type.decode(d);
        mAlias = d.nonCompactString();
    }

    public Slice(String alias, Type valueType) {
        mAlias = alias;
        mValueType = valueType;
    }

    public String getAlias() {
        return mAlias;
    }

    public Type getValueType() {
        return mValueType;
    }

    @Override
    public void encodeValue(@NotNull Encoder e, Object value) throws IOException {
        assert (value instanceof Object[]);
        Object[] array = (Object[]) value;
        e.uint32(array.length);
        for (Object v : array) {
            mValueType.encodeValue(e, v);
        }
    }

    @Override
    public Object decodeValue(@NotNull Decoder d) throws IOException {
        Object[] array = new Object[d.uint32()];
        for (int i = 0; i < array.length; i++) {
            array[i] = mValueType.decodeValue(d);
        }
        return array;
    }

    @Override
    public void encode(@NotNull Encoder e) throws IOException {
        TypeTag.sliceTag().encode(e);
        mValueType.encode(e);
        e.nonCompactString(mAlias);
    }

    @Override
    void name(StringBuilder out) {
        out.append("slice<");
        mValueType.name(out);
        out.append('>');
    }

    @Override
    public void signature(StringBuilder out) {
        out.append("[]");
        mValueType.signature(out);
    }
}
