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

public final class Entity {
    private BinaryID mTypeID;
    private String mPackage;
    private String mName;
    private String mIdentity;
    private String mVersion;
    private boolean mExported;
    private Field[] mFields;
    private BinaryObject[] mMetadata;

    public Entity(@NotNull Decoder d, boolean compact) throws IOException {
        mPackage = d.string();
        mIdentity = d.string();
        mVersion = d.string();
        if (!compact) {
            mName = d.string();
            mTypeID = d.id();
            mExported = d.bool();
        }
        mFields = new Field[d.uint32()];
        for (int i = 0; i < mFields.length; i++) {
            mFields[i] = new Field();
            mFields[i].mType = Type.decode(d, compact);
            if (!compact) {
                mFields[i].mDeclared = d.string();
            }
        }
        if (!compact) {
            mMetadata = new BinaryObject[d.uint32()];
            for (int i = 0; i < mMetadata.length; i++) {
                mMetadata[i] = d.object();
            }
        }
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

    public void encode(@NotNull Encoder e, boolean compact) throws IOException {
        e.string(mPackage);
        e.string(mIdentity);
        e.string(mVersion);
        if (!compact) {
            e.string(mName);
            e.id(mTypeID);
            e.bool(mExported);
        }
        e.uint32(mFields.length);
        for (Field field : mFields) {
            field.mType.encode(e, compact);
            if (!compact) {
                e.string(field.mDeclared);
            }
        }
        if (!compact) {
            e.uint32(mMetadata.length);
            for (BinaryObject meta : mMetadata) {
                e.object(meta);
            }
        }
    }
}
