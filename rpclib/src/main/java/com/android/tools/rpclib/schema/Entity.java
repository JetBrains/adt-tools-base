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
import java.util.Arrays;

public final class Entity {
    private String mPackage;
    private String mIdentity;
    private String mVersion;
    private String mDisplay;
    private boolean mExported;
    private Field[] mFields;
    private BinaryObject[] mMetadata;

    public Entity(String pkg, String identity, String version, String display) {
        mPackage = pkg;
        mIdentity = identity;
        mVersion = version;
        mDisplay = display;
        mFields = new Field[]{};
    }

    public Entity(@NotNull Decoder d, boolean compact) throws IOException {
        mPackage = d.string();
        mIdentity = d.string();
        mVersion = d.string();
        if (!compact) {
            mDisplay = d.string();
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

    public String getPackage() {
        return mPackage;
    }

    public String getName() {
        return mDisplay == "" ? mIdentity : mDisplay;
    }

    public boolean getExported() {
        return mExported;
    }

    public Field[] getFields() {
        return mFields;
    }
    public void setFields(Field[] fields) {
        mFields = fields;
    }

    public BinaryObject[] getMetadata() {
        return mMetadata;
    }

    public void encode(@NotNull Encoder e, boolean compact) throws IOException {
        e.string(mPackage);
        e.string(mIdentity);
        e.string(mVersion);
        if (!compact) {
            e.string(mDisplay);
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Entity entity = (Entity)o;
        if (!mPackage.equals(entity.mPackage)) return false;
        if (!mIdentity.equals(entity.mIdentity)) return false;
        if (mVersion != null ? !mVersion.equals(entity.mVersion) : entity.mVersion != null) return false;
        return Arrays.equals(mFields, entity.mFields);
    }

    @Override
    public int hashCode() {
        int result = mPackage.hashCode();
        result = 31 * result + mIdentity.hashCode();
        result = 31 * result + (mVersion != null ? mVersion.hashCode() : 0);
        return result;
    }
}
