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

import com.android.tools.rpclib.any.Box;
import com.android.tools.rpclib.binary.Decoder;
import com.android.tools.rpclib.binary.Encoder;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public final class AnyType extends Type {

    public AnyType() {
    }

    public AnyType(@NotNull Decoder d) throws IOException {
    }

    @Override
    public void encodeValue(@NotNull Encoder e, Object value) throws IOException {
        e.variant(Box.wrap(value));
    }

    @Override
    public Object decodeValue(@NotNull Decoder d) throws IOException {
        Box boxed = (Box) d.variant();
        if (boxed == null) {
            return null;
        }
        return boxed.unwrap();
    }

    @Override
    public void encode(@NotNull Encoder e) throws IOException {
        TypeTag.anyTag().encode(e);
    }

    @Override
    void name(StringBuilder out) {
        out.append("any");
    }

    @Override
    public void signature(StringBuilder out) {
        out.append('~');
    }
}
