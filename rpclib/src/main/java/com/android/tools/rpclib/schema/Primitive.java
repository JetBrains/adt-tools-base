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

import com.android.tools.rpclib.binary.BinaryID;
import com.android.tools.rpclib.binary.Decoder;
import com.android.tools.rpclib.binary.Encoder;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public final class Primitive extends Type {

    String mName;

    Method mMethod;

    public Primitive(String name, byte method) {
        mName = name;
        mMethod = new Method(method);
    }

    public Primitive(@NotNull Decoder d, Method method) throws IOException {
        mMethod = method;
        mName = d.nonCompactString();
    }

    @Override
    public String toString() {
        return mName + "(" + mMethod + ")";
    }

    public Method getMethod() {
        return mMethod;
    }

    @Override
    public void encodeValue(@NotNull Encoder e, Object value) throws IOException {
        switch (mMethod.value) {
            case Method.Bool:
                e.bool((Boolean) value);
                break;
            case Method.Int8:
                e.int8((Byte) value);
                break;
            case Method.Uint8:
                e.uint8((Short) value);
                break;
            case Method.Int16:
                e.int16((Short) value);
                break;
            case Method.Uint16:
                e.uint16((Integer) value);
                break;
            case Method.Int32:
                e.int32((Integer) value);
                break;
            case Method.Uint32:
                e.uint32((Long) value);
                break;
            case Method.Int64:
                e.int64((Long) value);
                break;
            case Method.Uint64:
                e.uint64((Long) value);
                break;
            case Method.Float32:
                e.float32((Float) value);
                break;
            case Method.Float64:
                e.float64((Double) value);
                break;
            case Method.String:
                e.string((String) value);
                break;
            default:
                throw new IOException("Invalid primitive method in encode");
        }
    }

    @Override
    public Object decodeValue(@NotNull Decoder d) throws IOException {
        switch (mMethod.value) {
            case Method.Bool:
                return d.bool();
            case Method.Int8:
                return d.int8();
            case Method.Uint8:
                return d.uint8();
            case Method.Int16:
                return d.int16();
            case Method.Uint16:
                return d.uint16();
            case Method.Int32:
                return d.int32();
            case Method.Uint32:
                return d.uint32();
            case Method.Int64:
                return d.int64();
            case Method.Uint64:
                return d.uint64();
            case Method.Float32:
                return d.float32();
            case Method.Float64:
                return d.float64();
            case Method.String:
                return d.string();
            default:
                throw new IOException("Invalid primitive method in decode");
        }
    }

    @Override
    public void encode(@NotNull Encoder e) throws IOException {
        //noinspection PointlessBitwiseExpression
        e.uint8((short)(TypeTag.PrimitiveTag | ( mMethod.value << 4)));
        e.nonCompactString(mName);
    }

    @Override
    void name(StringBuilder out) {
        out.append(mName);
    }

    @Override
    public void signature(StringBuilder out) {
        out.append(mMethod);
    }
}
