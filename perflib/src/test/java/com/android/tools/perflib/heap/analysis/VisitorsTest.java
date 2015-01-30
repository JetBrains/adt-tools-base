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

import com.android.annotations.NonNull;
import com.android.tools.perflib.heap.ArrayInstance;
import com.android.tools.perflib.heap.ClassInstance;
import com.android.tools.perflib.heap.ClassObj;
import com.android.tools.perflib.heap.Field;
import com.android.tools.perflib.heap.Instance;
import com.android.tools.perflib.heap.RootObj;
import com.android.tools.perflib.heap.RootType;
import com.android.tools.perflib.heap.Snapshot;
import com.android.tools.perflib.heap.Type;
import com.android.tools.perflib.heap.io.InMemoryBuffer;
import com.google.common.collect.Maps;

import junit.framework.TestCase;

import java.util.List;
import java.util.Map;

/**
 * There are two testing scenarios we want to cover here: basic connectivity between different
 * Instance types, and the visitor's ability to deal with cycles, diamonds, etc. in the graph. For
 * the latter we create heaps that are only concerned with connectivity between nodes.
 */
public class VisitorsTest extends TestCase {

    private final ClassObj mDummyClass = new ClassObj(42, null, "dummy", 0);

    private Snapshot mSnapshot;

    @Override
    public void setUp() throws Exception {
        mSnapshot = new Snapshot(new InMemoryBuffer(10));
        mSnapshot.setHeapTo(13, "testHeap");
        mDummyClass.setFields(new Field[0]);
        mSnapshot.addClass(42, mDummyClass);
    }

    public void testSimpleStaticFieldsGraph() {
        Type.setIdSize(4);
        final ClassInstance object1 = new ClassInstance(1, null, 0);
        object1.setClassId(42);
        object1.setSize(20);
        mSnapshot.addInstance(1, object1);

        final ClassInstance object2 = new ClassInstance(2, null, 0);
        object2.setClassId(42);
        object2.setSize(20);
        mSnapshot.addInstance(2, object2);

        ClassObj clazz = new ClassObj(13, null, "FooBar", 0) {
            @NonNull
            @Override
            public Map<Field, Object> getStaticFieldValues() {
                Map<Field, Object> result = Maps.newHashMap();
                result.put(new Field(Type.OBJECT, "foo"), object1);
                result.put(new Field(Type.OBJECT, "bar"), object2);
                return result;
            }
        };
        clazz.setSize(10);
        mSnapshot.addClass(13, clazz);

        mSnapshot.setToDefaultHeap();
        RootObj root = new RootObj(RootType.SYSTEM_CLASS, 13);
        mSnapshot.addRoot(root);

        // Size of root is 2 x sizeof(mDummyClass) + sizeof(clazz)
        assertEquals(50, root.getCompositeSize());
    }

    public void testSimpleArray() {
        Type.setIdSize(4);
        final ClassInstance object = new ClassInstance(1, null, 0);
        object.setClassId(42);
        object.setSize(20);
        mSnapshot.addInstance(1, object);

        ArrayInstance array = new ArrayInstance(2, null, Type.OBJECT, 3, 0) {
            @NonNull
            @Override
            public Object[] getValues() {
                return new Object[] {object, object, object};
            }
        };
        mSnapshot.addInstance(2, array);

        mSnapshot.setToDefaultHeap();
        RootObj root = new RootObj(RootType.JAVA_LOCAL, 2);
        mSnapshot.addRoot(root);

        // Size of root is sizeof(object) + 3 x sizeof(pointer to object)
        assertEquals(32, root.getCompositeSize());
    }

    public void testBasicDiamond() {
        Snapshot snapshot = new SnapshotBuilder(4)
                .addReferences(1, 2, 3)
                .addReferences(2, 4)
                .addReferences(3, 4)
                .addRoot(1)
                .getSnapshot();

        assertEquals(10, snapshot.findReference(1).getCompositeSize());
        assertEquals(6, snapshot.findReference(2).getCompositeSize());
        assertEquals(7, snapshot.findReference(3).getCompositeSize());
        assertEquals(4, snapshot.findReference(4).getCompositeSize());
    }

    public void testBasicCycle() {
        Snapshot snapshot = new SnapshotBuilder(3)
                .addReferences(1, 2)
                .addReferences(2, 3)
                .addReferences(3, 1)
                .addRoot(1)
                .getSnapshot();

        // The composite size is a sum over all nodes participating in the cycle.
        assertEquals(6, snapshot.findReference(1).getCompositeSize());
        assertEquals(6, snapshot.findReference(2).getCompositeSize());
        assertEquals(6, snapshot.findReference(3).getCompositeSize());
    }

    public void testTopSortSimpleGraph() {
        Snapshot snapshot = new SnapshotBuilder(6)
                .addReferences(1, 2, 3)
                .addReferences(2, 4, 6)
                .addReferences(3, 4, 5)
                .addReferences(4, 6)
                .addRoot(1)
                .getSnapshot();

        List<Instance> topSort = TopologicalSort.compute(snapshot.getGCRoots());
        assertEquals(6, topSort.size());
        // Make sure finishing times are computed correctly. A visitor simply collecting nodes as
        // they are expanded will not yield the correct order. The correct invariant for a DAG is:
        // for each directed edge (u,v), topsort(u) < topsort(v).
        assertTrue(snapshot.findReference(1).getTopologicalOrder() <
                snapshot.findReference(2).getTopologicalOrder());
        assertTrue(snapshot.findReference(1).getTopologicalOrder() <
                snapshot.findReference(3).getTopologicalOrder());
        assertTrue(snapshot.findReference(2).getTopologicalOrder() <
                snapshot.findReference(4).getTopologicalOrder());
        assertTrue(snapshot.findReference(2).getTopologicalOrder() <
                snapshot.findReference(6).getTopologicalOrder());
        assertTrue(snapshot.findReference(3).getTopologicalOrder() <
                snapshot.findReference(4).getTopologicalOrder());
        assertTrue(snapshot.findReference(3).getTopologicalOrder() <
                snapshot.findReference(5).getTopologicalOrder());
        assertTrue(snapshot.findReference(4).getTopologicalOrder() <
                snapshot.findReference(6).getTopologicalOrder());
    }
}
