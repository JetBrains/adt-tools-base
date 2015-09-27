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

import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public final class Entity implements BinaryObject {
    private BinaryID mTypeID;
    private String mPackage;
    private String mName;
    private String mIdentity;
    private String mVersion;
    private boolean mExported;
    private Field[] mFields;

    private BinaryObject[] mMetadata;

    // Constructs a default-initialized {@link Entity}.
    public Entity() {
    }


    public BinaryID getTypeID() {
        return mTypeID;
    }

    public String getPackage() {
        return mPackage;
    }

    public String getName() {
        return mName;
    }

    public boolean getExported() {
        return mExported;
    }

    public Field[] getFields() {
        return mFields;
    }

    public BinaryObject[] getMetadata() {
        return mMetadata;
    }

    @Override
    @NotNull
    public BinaryClass klass() {
        return Klass.INSTANCE;
    }

    private static final byte[] IDBytes = {-15, -85, -82, -49, -61, 35, -8, 101, -95, -21, -32, 58,
            -95, -82, -77, -85, 119, -80, 87, -17,};

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
            return new Entity();
        }

        @Override
        public void encode(@NotNull Encoder e, BinaryObject obj) throws IOException {
            Entity o = (Entity) obj;
            e.id(o.mTypeID);
            e.string(o.mPackage);
            e.string(o.mName);
            e.string(o.mIdentity);
            e.string(o.mVersion);
            e.bool(o.mExported);
            e.uint32(o.mFields.length);
            for (int i = 0; i < o.mFields.length; i++) {
                e.string(o.mFields[i].mDeclared);
                o.mFields[i].mType.encode(e);
            }
            e.uint32(o.mMetadata.length);
            for (int i = 0; i < o.mMetadata.length; i++) {
                e.object(o.mMetadata[i]);
            }
        }

        @Override
        public void decode(@NotNull Decoder d, BinaryObject obj) throws IOException {
            Entity o = (Entity) obj;
            o.mTypeID = d.id();
            o.mPackage = d.string();
            o.mName = d.string();
            o.mIdentity = d.string();
            o.mVersion = d.string();
            o.mExported = d.bool();
            o.mFields = new Field[d.uint32()];
            for (int i = 0; i < o.mFields.length; i++) {
                o.mFields[i] = new Field();
                o.mFields[i].mDeclared = d.string();
                o.mFields[i].mType = Type.decode(d);
            }
            o.mMetadata = new BinaryObject[d.uint32()];
            for (int i = 0; i < o.mMetadata.length; i++) {
                o.mMetadata[i] = d.object();
            }
        }
    }
}
