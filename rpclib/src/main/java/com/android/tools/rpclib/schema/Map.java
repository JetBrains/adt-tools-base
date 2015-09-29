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
import com.intellij.util.containers.HashMap;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public final class Map extends Type {

    String mAlias;

    Type mKeyType;

    Type mValueType;

    public Map(@NotNull Decoder d) throws IOException {
        mAlias = d.string();
        mKeyType = decode(d);
        mValueType = decode(d);
    }

    @NotNull
    @Override
    public String getName() {
        return "map<" + mKeyType.getName() + "," + mValueType.getName() + ">";
    }

    public String getAlias() {
        return mAlias;
    }

    public Type getKeyType() {
        return mKeyType;
    }

    @Override
    public void encodeValue(@NotNull Encoder e, Object value) throws IOException {
        assert (value instanceof java.util.Map);
        java.util.Map<?, ?> map = (java.util.Map) value;
        e.uint32(map.size());
        for (java.util.Map.Entry entry : map.entrySet()) {
            mKeyType.encodeValue(e, entry.getKey());
            mValueType.encodeValue(e, entry.getValue());
        }
    }

    @Override
    public Object decodeValue(@NotNull Decoder d) throws IOException {
        int size = (int) d.uint32();
        HashMap<Object, Object> map = new HashMap<Object, Object>();
        for (int i = 0; i < size; i++) {
            map.put(mKeyType.decodeValue(d), mValueType.decodeValue(d));
        }
        return map;
    }

    public Type getValueType() {
        return mValueType;
    }

    @Override
    public void encode(@NotNull Encoder e) throws IOException {
        TypeTag.mapTag().encode(e);
        e.string(mAlias);
        mKeyType.encode(e);
        mValueType.encode(e);
    }
}
