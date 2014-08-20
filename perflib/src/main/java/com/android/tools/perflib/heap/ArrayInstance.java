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

public class ArrayInstance extends Instance {

    private Type mType;

    private Value[] mValues;

    public ArrayInstance(long id, StackTrace stack, Type type) {
        mId = id;
        mStack = stack;
        mType = type;
    }

    public void setValues(Value[] values) {
        mValues = values;
    }

    public Value[] getValues() {
        return mValues;
    }

    @Override
    public final int getSize() {
        return mValues.length * mType.getSize();
    }

    @Override
    public final void accept(Visitor visitor) {
        if (mType != Type.OBJECT) {
            return;
        }

        if (visitor.visitEnter(this)) {
            for (Value value : mValues) {
                if (value.getValue() instanceof Instance) {
                    ((Instance) value.getValue()).accept(visitor);
                }
            }
            visitor.visitLeave(this);
        }
    }

    public final String toString() {
        return String.format("%s[%d]@0x%08x", mType.name(), mValues.length, mId);
    }
}
