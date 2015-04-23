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
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ClassObj extends Instance implements Comparable<ClassObj> {

    @NonNull
    final String mClassName;

    private final long mStaticFieldsOffset;

    long mSuperClassId;

    long mClassLoaderId;

    Field[] mFields;

    Field[] mStaticFields;

    private int mInstanceSize;

    private int mShalowSize;

    @NonNull
    ArrayList<Instance> mInstances = new ArrayList<Instance>();

    @NonNull
    Set<ClassObj> mSubclasses = new HashSet<ClassObj>();

    public ClassObj(long id, @NonNull StackTrace stack, @NonNull String className,
            long staticFieldsOffset) {
        super(id, stack);
        mClassName = className;
        mStaticFieldsOffset = staticFieldsOffset;
    }

    public final void addSubclass(ClassObj subclass) {
        mSubclasses.add(subclass);
    }

    @NonNull
    public final Set<ClassObj> getSubclasses() {
        return mSubclasses;
    }

    public final void dumpSubclasses() {
        for (ClassObj subclass : mSubclasses) {
            System.out.println("     " + subclass.mClassName);
        }
    }

    @NonNull
    public final String toString() {
        return mClassName.replace('/', '.');
    }

    public final void addInstance(@NonNull Instance instance) {
        mShalowSize += instance.getSize();
        mInstances.add(instance);
    }

    public final void setSuperClassId(long superClass) {
        mSuperClassId = superClass;
    }

    public final void setClassLoaderId(long classLoader) {
        mClassLoaderId = classLoader;
    }

    public int getAllFieldsCount() {
        int result = 0;
        ClassObj clazz = this;
        while (clazz != null) {
            result += clazz.getFields().length;
            clazz = clazz.getSuperClassObj();
        }
        return result;
    }

    public Field[] getFields() {
        return mFields;
    }

    public void setFields(@NonNull Field[] fields) {
        mFields = fields;
    }

    public void setStaticFields(@NonNull Field[] staticFields) {
        mStaticFields = staticFields;
    }

    public void setInstanceSize(int size) {
        mInstanceSize = size;
    }

    public int getInstanceSize() {
        return mInstanceSize;
    }

    public int getShalowSize() {
        return mShalowSize;
    }

    @NonNull
    public Map<Field, Object> getStaticFieldValues() {
        Map<Field, Object> result = new HashMap<Field, Object>();
        getBuffer().setPosition(mStaticFieldsOffset);

        int numEntries = readUnsignedShort();
        for (int i = 0; i < numEntries; i++) {
            Field f = mStaticFields[i];

            readId();
            readUnsignedByte();

            Object value = readValue(f.getType());
            result.put(f, value);
        }
        return result;
    }

    public final void dump() {
        System.out.println("+----------  ClassObj dump for: " + mClassName);

        System.out.println("+-----  Static fields");
        Map<Field, Object> staticFields = getStaticFieldValues();
        for (Field field : staticFields.keySet()) {
            System.out.println(field.getName() + ": " + field.getType() + " = "
                    + staticFields.get(field));
        }

        System.out.println("+-----  Instance fields");
        for (Field field : mFields) {
            System.out.println(field.getName() + ": " + field.getType());
        }
        if (getSuperClassObj() != null) {
            getSuperClassObj().dump();
        }
    }

    @NonNull
    public final String getClassName() {
        return mClassName;
    }

    @Override
    public final void accept(@NonNull Visitor visitor) {
        visitor.visitClassObj(this);
        for (Object value : getStaticFieldValues().values()) {
            if (value instanceof Instance) {
                visitor.visitLater((Instance) value);
            }
        }
    }

    @Override
    public final int compareTo(@NonNull ClassObj o) {
        return mClassName.compareTo(o.mClassName);
    }

    public final boolean equals(Object o) {
        if (!(o instanceof ClassObj)) {
            return false;
        }

        return 0 == compareTo((ClassObj) o);
    }

    @Override
    public int hashCode() {
        return mClassName.hashCode();
    }

    @VisibleForTesting
    Object getStaticField(Type type, String name) {
        return getStaticFieldValues().get(new Field(type, name));
    }

    public ClassObj getSuperClassObj() {
        return mHeap.mSnapshot.findClass(mSuperClassId);
    }

    @Nullable
    public Instance getClassLoader() {
        return mHeap.mSnapshot.findReference(mClassLoaderId);
    }

    @NonNull
    public Collection<Instance> getInstances() {
        return mInstances;
    }
}
