/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.tools.perflib.heap.analysis;

import com.android.tools.perflib.heap.ArrayInstance;
import com.android.tools.perflib.heap.ClassInstance;
import com.android.tools.perflib.heap.ClassObj;
import com.android.tools.perflib.heap.Heap;
import com.android.tools.perflib.heap.RootObj;
import com.android.tools.perflib.heap.RootType;
import com.android.tools.perflib.heap.State;
import com.android.tools.perflib.heap.Type;
import com.android.tools.perflib.heap.Value;

import junit.framework.TestCase;

/**
 * There are two testing scenarios we want to cover here: basic connectivity between different
 * Instance types, and the visitor's ability to deal with cycles, diamonds, etc. in the graph. For
 * the latter we create heaps that are only concerned with connectivity between nodes.
 */
public class VisitorsTest extends TestCase {

    private final ClassObj mDummyClass = new ClassObj(0, null, "dummy");

    private Heap mHeap;

    @Override
    public void setUp() throws Exception {
        mHeap = new State().setToDefaultHeap();
        mDummyClass.setHeap(mHeap);
        mDummyClass.setSize(20);
        mHeap.addClass(0, mDummyClass);
    }

    public void testSimpleStaticFieldsGraph() {
        ClassInstance object1 = new ClassInstance(1, null);
        object1.setHeap(mHeap);
        object1.setClass(mDummyClass);
        mHeap.addInstance(1, object1);

        ClassInstance object2 = new ClassInstance(2, null);
        object2.setClass(mDummyClass);
        object2.setHeap(mHeap);
        mHeap.addInstance(2, object2);

        ClassObj clazz = new ClassObj(13, null, "FooBar");
        Value value1 = new Value(clazz);
        value1.setValue(object1);
        clazz.addStaticField(Type.OBJECT, "foo", value1);
        Value value2 = new Value(clazz);
        value2.setValue(object2);
        clazz.addStaticField(Type.OBJECT, "bar", value2);
        clazz.setSize(10);
        clazz.setHeap(mHeap);
        mHeap.addClass(13, clazz);

        RootObj root = new RootObj(RootType.SYSTEM_CLASS, 13);
        root.setHeap(mHeap);
        mHeap.addRoot(root);

        // Size of root is 2 x sizeof(mDummyClass) + sizeof(clazz)
        assertEquals(50, root.getCompositeSize());
    }

    public void testSimpleArray() {
        ClassInstance object = new ClassInstance(1, null);
        object.setHeap(mHeap);
        object.setClass(mDummyClass);
        mHeap.addInstance(1, object);

        ArrayInstance array = new ArrayInstance(2, null, Type.OBJECT);
        Value value = new Value(array);
        value.setValue(object);
        array.setValues(new Value[] {value, value, value});
        array.setHeap(mHeap);
        mHeap.addInstance(2, array);

        RootObj root = new RootObj(RootType.JAVA_LOCAL, 2);
        root.setHeap(mHeap);
        mHeap.addRoot(root);

        // Size of root is sizeof(object) + 3 x sizeof(pointer to object)
        assertEquals(32, root.getCompositeSize());
    }

    public void testBasicDiamond() {
        Heap heap = new HeapBuilder(4)
                .addReference(1, 2)
                .addReference(1, 3)
                .addReference(2, 4)
                .addReference(3, 4)
                .addRoot(1)
                .getHeap();

        assertEquals(10, heap.getInstance(1).getCompositeSize());
        assertEquals(6, heap.getInstance(2).getCompositeSize());
        assertEquals(7, heap.getInstance(3).getCompositeSize());
        assertEquals(4, heap.getInstance(4).getCompositeSize());
    }

    public void testBasicCycle() {
        Heap heap = new HeapBuilder(3)
                .addReference(1, 2)
                .addReference(2, 3)
                .addReference(3, 1)
                .addRoot(1)
                .getHeap();

        // The composite size is a sum over all nodes participating in the cycle.
        assertEquals(6, heap.getInstance(1).getCompositeSize());
        assertEquals(6, heap.getInstance(2).getCompositeSize());
        assertEquals(6, heap.getInstance(3).getCompositeSize());
    }
}
