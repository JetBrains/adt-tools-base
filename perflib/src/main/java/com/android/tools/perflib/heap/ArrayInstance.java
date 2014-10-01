/*
 * Copyright (C) 2008 Google Inc.
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

package com.android.tools.perflib.heap;

import com.android.annotations.NonNull;

public class ArrayInstance extends Instance {

    private final Type mType;

    private final int mLength;

    private final long mValuesOffset;

    public ArrayInstance(long id, @NonNull StackTrace stack, @NonNull Type type, int length,
            long valuesOffset) {
        super(id, stack);
        mType = type;
        mLength = length;
        mValuesOffset = valuesOffset;
    }

    @NonNull
    public Object[] getValues() {
        Object[] values = new Object[mLength];

        getBuffer().setPosition(mValuesOffset);
        for (int i = 0; i < mLength; i++) {
            values[i] = readValue(mType);
        }
        return values;
    }

    @Override
    public final int getSize() {
        return mLength * mType.getSize();
    }

    @Override
    public final void accept(@NonNull Visitor visitor) {
        if (mType != Type.OBJECT) {
            return;
        }

        if (visitor.visitEnter(this)) {
            for (Object value : getValues()) {
                if (value instanceof Instance) {
                    ((Instance) value).accept(visitor);
                }
            }
            visitor.visitLeave(this);
        }
    }

    public final String toString() {
        return String.format("%s[%d]@0x%08x", mType.name(), mLength, mId);
    }
}
