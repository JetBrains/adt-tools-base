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
    private Field[] mFields;
    private BinaryObject[] mMetadata;
    private String mSignature;

    public Entity(String pkg, String identity, String version, String display) {
        mPackage = pkg;
        mIdentity = identity;
        mVersion = version;
        mDisplay = display;
        mFields = new Field[]{};
    }

    public Entity() {}

    public void decode(@NotNull Decoder d) throws IOException {
        mPackage = d.string();
        mIdentity = d.string();
        mVersion = d.string();
        mDisplay = d.nonCompactString();
        mFields = new Field[d.uint32()];
        for (int i = 0; i < mFields.length; i++) {
            mFields[i] = new Field();
            mFields[i].mType = Type.decode(d);
            mFields[i].mDeclared = d.nonCompactString();
        }
        if (d.getMode() != EncodingControl.Compact) {
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

    public Field[] getFields() {
        return mFields;
    }
    public void setFields(Field[] fields) {
        mFields = fields;
    }

    public BinaryObject[] getMetadata() {
        return mMetadata;
    }

    public void encode(@NotNull Encoder e) throws IOException {
        e.string(mPackage);
        e.string(mIdentity);
        e.string(mVersion);
        e.nonCompactString(mDisplay);
        e.uint32(mFields.length);
        for (Field field : mFields) {
            field.mType.encode(e);
            e.nonCompactString(field.mDeclared);
        }
        if (e.getMode() != EncodingControl.Compact) {
            e.uint32(mMetadata.length);
            for (BinaryObject meta : mMetadata) {
                e.object(meta);
            }
        }
    }

    public String signature() {
        if (mSignature == null) {
            StringBuilder out = new StringBuilder();
            out.append(mPackage).append('.').append(mIdentity);
            if (mVersion.length() > 0) {
                out.append('@').append(mVersion);
            }
            out.append('{');
            for (int index = 0; index < mFields.length; ++index) {
                if (index > 0) {
                    out.append(',');
                }
                mFields[index].getType().signature(out);
            }
            out.append('}');
            mSignature = out.toString();
        }
        return mSignature;
    }
}
