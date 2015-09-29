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

import com.android.tools.rpclib.binary.*;
import com.intellij.util.containers.HashMap;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public final class ConstantSet implements BinaryObject {

    private Type mType;

    private Constant[] mEntries;

    private static final HashMap<Type, ConstantSet> mRegistry = new HashMap<Type, ConstantSet>();

    public static void register(ConstantSet set) {
        mRegistry.put(set.getType(), set);
    }

    public static ConstantSet lookup(Type type) {
        return mRegistry.get(type);
    }

    // Constructs a default-initialized {@link ConstantSet}.
    public ConstantSet() {
    }

    public Type getType() {
        return mType;
    }

    public Constant[] getEntries() {
        return mEntries;
    }

    @Override
    @NotNull
    public BinaryClass klass() {
        return Klass.INSTANCE;
    }

    private static final byte[] IDBytes = {40, -113, 108, -120, 49, -47, 4, 82, -73, 90, 37, -125,
            1, 78, -102, 124, 83, 3, 50, -98,};

    public static final BinaryID ID = new BinaryID(IDBytes);

    static {
        Namespace.register(ID, Klass.INSTANCE);
    }

    public static void register() {
    }

    public enum Klass implements BinaryClass {
        INSTANCE;

        @Override
        @NotNull
        public BinaryID id() {
            return ID;
        }

        @Override
        @NotNull
        public BinaryObject create() {
            return new ConstantSet();
        }

        @Override
        public void encode(@NotNull Encoder e, BinaryObject obj) throws IOException {
            ConstantSet o = (ConstantSet) obj;
            o.mType.encode(e);
            for (int i = 0; i < o.mEntries.length; i++) {
                e.string(o.mEntries[i].mName);
                o.mType.encodeValue(e, o.mEntries[i].mValue);
            }
        }

        @Override
        public void decode(@NotNull Decoder d, BinaryObject obj) throws IOException {
            ConstantSet o = (ConstantSet) obj;
            o.mType = Type.decode(d);
            o.mEntries = new Constant[d.uint32()];
            for (int i = 0; i < o.mEntries.length; i++) {
                o.mEntries[i] = new Constant();
                o.mEntries[i].mName = d.string();
                o.mEntries[i].mValue = o.mType.decodeValue(d);
            }
        }
    }
}
