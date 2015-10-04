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

public final class Array extends Type {

    String mAlias;

    Type mValueType;

    int mSize;

    public Array(String alias, Type type, int size) {
        mAlias = alias;
        mValueType = type;
        mSize = size;
    }

    public Array(@NotNull Decoder d, boolean compact) throws IOException {
        mSize = d.uint32();
        mValueType = decode(d, compact);
        if (!compact) {
            mAlias = d.string();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Array)) return false;
        Array array = (Array)o;
        if (mSize != array.mSize) return false;
        return mValueType.equals(array.mValueType);
    }

    @Override
    public int hashCode() {
        return 31 * mValueType.hashCode() + mSize;
    }

    @NotNull
    @Override
    public String getName() {
        return "array<" + mValueType.getName() + ">";
    }

    public String getAlias() {
        return mAlias;
    }

    public Type getValueType() {
        return mValueType;
    }

    public int getSize() {
        return mSize;
    }

    @Override
    public void encodeValue(@NotNull Encoder e, Object value) throws IOException {
        assert (value instanceof Object[]);
        Object[] array = (Object[]) value;
        for (int i = 0; i < mSize; i++) {
            mValueType.encodeValue(e, array[i]);
        }
    }

    @Override
    public Object decodeValue(@NotNull Decoder d) throws IOException {
        Object[] array = new Object[mSize];
        for (int i = 0; i < mSize; i++) {
            array[i] = mValueType.decodeValue(d);
        }
        return array;
    }

    @Override
    public void encode(@NotNull Encoder e, boolean compact) throws IOException {
        TypeTag.arrayTag().encode(e);
        e.uint32(mSize);
        mValueType.encode(e, compact);
        if (!compact) {
            e.string(mAlias);
        }
    }
}
