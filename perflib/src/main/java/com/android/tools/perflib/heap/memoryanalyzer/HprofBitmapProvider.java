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
package com.android.tools.perflib.heap.memoryanalyzer;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ddmlib.BitmapDecoder;
import com.android.tools.perflib.heap.ArrayInstance;
import com.android.tools.perflib.heap.ClassInstance;
import com.android.tools.perflib.heap.Field;
import com.android.tools.perflib.heap.Instance;
import com.android.tools.perflib.heap.Type;

import java.awt.Dimension;

public class HprofBitmapProvider implements BitmapDecoder.BitmapDataProvider {

    private ArrayInstance mBuffer = null;

    private boolean mMutable = false;

    private int mWidth = -1;

    private int mHeight = -1;

    public HprofBitmapProvider(@NonNull Instance instance) {
        ClassInstance resolvedInstance = getBitmapClassInstance(instance);
        if (resolvedInstance == null) {
            throw new RuntimeException("Can not resolve Bitmap instance");
        }

        Integer width = null;
        Integer height = null;
        Boolean mutable = null;

        for (ClassInstance.FieldValue field : resolvedInstance.getValues()) {
            Object bitmapValue = field.getValue();
            String bitmapFieldName = field.getField().getName();
            if ("mBuffer".equals(bitmapFieldName) && (bitmapValue instanceof ArrayInstance)) {
                ArrayInstance arrayInstance = (ArrayInstance) bitmapValue;
                if (arrayInstance.getArrayType() == Type.BYTE) {
                    mBuffer = arrayInstance;
                }
            } else if ("mIsMutable".equals(bitmapFieldName) && (bitmapValue instanceof Boolean)) {
                mutable = (Boolean) bitmapValue;
            } else if ("mWidth".equals(bitmapFieldName) && (bitmapValue instanceof Integer)) {
                width = (Integer) bitmapValue;
            } else if ("mHeight".equals(bitmapFieldName) && (bitmapValue instanceof Integer)) {
                height = (Integer) bitmapValue;
            }
        }

        if (mBuffer == null || mBuffer.getArrayType() != Type.BYTE || mutable == null
                || width == null || height == null) {
            throw new RuntimeException("Unable to resolve bitmap instance member variables");
        }

        mMutable = mutable;
        mWidth = width;
        mHeight = height;
    }

    public static boolean canGetBitmapFromInstance(@NonNull Instance value) {
        if (!(value instanceof ClassInstance)) {
            return false;
        }

        String className = value.getClassObj().getClassName();
        return BitmapDecoder.BITMAP_FQCN.equals(className) || BitmapDecoder.BITMAP_DRAWABLE_FQCN
                .equals(className);
    }

    @Nullable
    @Override
    public String getBitmapConfigName() throws Exception {
        int area = mWidth * mHeight;
        int pixelSize = mBuffer.getLength() / area;

        if ((!mMutable && ((mBuffer.getLength() % area) != 0)) ||
                (mMutable && area > mBuffer.getLength())) {
            return null;
        }

        switch (pixelSize) {
            case 4:
                return "\"ARGB_8888\"";
            case 2:
                return "\"RGB_565\"";
            default:
                return "\"ALPHA_8\"";
        }
    }

    @Nullable
    @Override
    public Dimension getDimension() throws Exception {
        return mWidth < 0 || mHeight < 0 ? null : new Dimension(mWidth, mHeight);
    }

    @Override
    public boolean downsizeBitmap(@NonNull Dimension newSize) throws Exception {
        return true;
    }

    @Nullable
    @Override
    public byte[] getPixelBytes(@NonNull Dimension size) throws Exception {
        return mBuffer.asRawByteArray(0, mBuffer.getLength());
    }

    @Nullable
    private static ClassInstance getBitmapClassInstance(@NonNull Instance instance) {
        if (!(instance instanceof ClassInstance)) {
            return null;
        }

        ClassInstance selectedObject = (ClassInstance) instance;
        String className = instance.getClassObj().getClassName();
        if (BitmapDecoder.BITMAP_FQCN.equals(className)) {
            return selectedObject;
        } else if (BitmapDecoder.BITMAP_DRAWABLE_FQCN.equals(className)) {
            return getBitmapFromDrawable(selectedObject);
        }

        return null;
    }

    @Nullable
    private static ClassInstance getBitmapFromDrawable(@NonNull ClassInstance instance) {
        ClassInstance bitmapState = getBitmapStateFromBitmapDrawable(instance);
        if (bitmapState == null) {
            return null;
        }

        for (ClassInstance.FieldValue fieldValue : bitmapState.getValues()) {
            Field field = fieldValue.getField();
            Object value = fieldValue.getValue();
            if ("mBitmap".equals(field.getName()) && (value instanceof ClassInstance)) {
                ClassInstance result = (ClassInstance) value;
                String className = result.getClassObj().getClassName();
                if (BitmapDecoder.BITMAP_FQCN.equals(className)) {
                    return (ClassInstance) value;
                }
            }
        }

        return null;
    }

    @Nullable
    private static ClassInstance getBitmapStateFromBitmapDrawable(
            @NonNull ClassInstance bitmapDrawable) {
        for (ClassInstance.FieldValue field : bitmapDrawable.getValues()) {
            String fieldName = field.getField().getName();
            Object fieldValue = field.getValue();
            if ("mBitmapState".equals(fieldName) && (fieldValue instanceof ClassInstance)) {
                ClassInstance result = (ClassInstance) fieldValue;
                String className = result.getClassObj().getClassName();
                if ((BitmapDecoder.BITMAP_DRAWABLE_FQCN + "$BitmapState").equals(className)) {
                    return result;
                }
            }
        }
        return null;
    }
}