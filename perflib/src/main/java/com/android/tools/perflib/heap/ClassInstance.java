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

import com.google.common.collect.Maps;

import java.util.Map;

public class ClassInstance extends Instance {

    private Map<Field, Value> mValues = Maps.newHashMap();

    public ClassInstance(long id, StackTrace stack) {
        mId = id;
        mStack = stack;
    }

    public void addField(Field field, Value value) {
        mValues.put(field, value);
    }

    public Value getField(Type type, String name) {
        return mValues.get(new Field(type, name));
    }

    @Override
    public final int getSize() {
        return mClass.getSize();
    }

    @Override
    public final void accept(Visitor visitor) {
        if (visitor.visitEnter(this)) {
            for (Value value : mValues.values()) {
                if (value.getValue() instanceof Instance) {
                    ((Instance) value.getValue()).accept(visitor);
                }
            }
            visitor.visitLeave(this);
        }
    }

    public final String toString() {
        return String.format("%s@0x%08x", getClassObj().getClassName(), mId);
    }
}
