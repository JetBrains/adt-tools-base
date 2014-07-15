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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ClassObj extends Instance implements Comparable<ClassObj> {

    String mClassName;

    ClassObj mSuperClass;

    Field[] mFields;

    Map<Field, Value> mStaticFields = new HashMap<Field, Value>();

    ArrayList<Instance> mInstances = new ArrayList<Instance>();

    Set<ClassObj> mSubclasses = new HashSet<ClassObj>();

    public ClassObj(long id, StackTrace stack, String className) {
        mId = id;
        mStack = stack;
        mClassName = className;
    }

    public final void addSubclass(ClassObj subclass) {
        mSubclasses.add(subclass);
    }

    public final Set<ClassObj> getSubclasses() {
        return mSubclasses;
    }

    public final void dumpSubclasses() {
        for (ClassObj subclass : mSubclasses) {
            System.out.println("     " + subclass.mClassName);
        }
    }

    public final String toString() {
        return mClassName.replace('/', '.');
    }

    public final void addInstance(Instance instance) {
        mInstances.add(instance);
    }

    public final void setSuperClass(ClassObj superClass) {
        mSuperClass = superClass;
        superClass.addSubclass(this);
    }

    public Field[] getFields() {
        return mFields;
    }

    public void setFields(Field[] fields) {
        mFields = fields;
    }

    public final void dump() {
        System.out.println("+----------  ClassObj dump for: " + mClassName);

        System.out.println("+-----  Static fields");
        for (Field field : mStaticFields.keySet()) {
            System.out.println(field.getName() + ": " + field.getType() + " = "
                    + mStaticFields.get(field));
        }

        System.out.println("+-----  Instance fields");
        for (Field field : mFields) {
            System.out.println(field.getName() + ": " + field.getType());
        }
        if (mSuperClass != null) {
            mSuperClass.dump();
        }
    }

    public final String getClassName() {
        return mClassName;
    }

    @Override
    public final void accept(Visitor visitor) {
        if (visitor.visitEnter(this)) {
            for (Value value : mStaticFields.values()) {
                if (value.getValue() instanceof Instance) {
                    ((Instance) value.getValue()).accept(visitor);
                }
            }
            visitor.visitLeave(this);
        }
    }

    @Override
    public final int compareTo(ClassObj o) {
        return mClassName.compareTo(o.mClassName);
    }

    public final boolean equals(Object o) {
        if (!(o instanceof ClassObj)) {
            return false;
        }

        return 0 == compareTo((ClassObj) o);
    }

    public void addStaticField(Type type, String name, Value value) {
        // TODO: Do we need to add a root for this objects?
        mStaticFields.put(new Field(type, name), value);
    }

    public Value getStaticField(Type type, String name) {
        return mStaticFields.get(new Field(type, name));
    }

    public ClassObj getSuperClassObj() {
        return mSuperClass;
    }

    public Collection<Instance> getInstances() {
        return mInstances;
    }
}
