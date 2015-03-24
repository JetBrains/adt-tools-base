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
import com.android.annotations.VisibleForTesting;

import java.util.HashMap;
import java.util.Map;

public class ClassInstance extends Instance {

    private final long mValuesOffset;

    public ClassInstance(long id, @NonNull StackTrace stack, long valuesOffset) {
        super(id, stack);
        mValuesOffset = valuesOffset;
    }

    @VisibleForTesting
    Object getField(Type type, String name) {
        return getValues().get(new Field(type, name));
    }

    @NonNull
    public Map<Field, Object> getValues() {
        Map<Field, Object> result = new HashMap<Field, Object>();

        ClassObj clazz = getClassObj();
        getBuffer().setPosition(mValuesOffset);
        while (clazz != null) {
            for (Field field : clazz.getFields()) {
                result.put(field, readValue(field.getType()));
            }
            clazz = clazz.getSuperClassObj();
        }
        return result;
    }

    @Override
    public final void accept(@NonNull Visitor visitor) {
        visitor.visitClassInstance(this);
        for (Object value : getValues().values()) {
            if (value instanceof Instance) {
                visitor.visitLater((Instance) value);
            }
        }
    }

    public final String toString() {
        return String.format("%s@0x%08x", getClassObj().getClassName(), mId);
    }
}
