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

package com.android.tools.perflib.heap;

import com.android.tools.perflib.heap.io.InMemoryBuffer;

import java.nio.ByteBuffer;

/**
 * Utility for creating Snapshot objects to be used in tests.
 *
 * As the main concern here is graph connectivity, we only initialize the app heap, creating
 * ClassInstance objects with id in [1..numNodes], each instance pointing to a unique ClassObj.
 * The class ids range in [101..100+numNodes] and their size is set to match the id of their object
 * instance. The default heap holds the roots.
 */
public class SnapshotBuilder {

    private final Snapshot mSnapshot;

    private final ClassInstance[] mNodes;

    private final int[] mOffsets;

    private final ByteBuffer mDirectBuffer;

    public SnapshotBuilder(int numNodes) {
        Type.setIdSize(2); // A good opportunity to test the handling of short IDs.
        InMemoryBuffer buffer = new InMemoryBuffer(2 * numNodes * numNodes);
        mDirectBuffer = buffer.getDirectBuffer();
        mOffsets = new int[numNodes + 1];

        mSnapshot = new Snapshot(buffer);
        mSnapshot.setHeapTo(13, "testHeap");

        mNodes = new ClassInstance[numNodes + 1];
        for (int i = 1; i <= numNodes; i++) {
            ClassObj clazz = new ClassObj(100 + i, null, "Class" + i, 0);
            clazz.setFields(new Field[0]);
            mSnapshot.addClass(100 + i, clazz);

            mOffsets[i] = 2 * (i - 1) * numNodes;
            mNodes[i] = new ClassInstance(i, null, mOffsets[i]);
            mNodes[i].setClassId(100 + i);
            mNodes[i].setSize(i);
            mSnapshot.addInstance(i, mNodes[i]);
        }
    }

    public SnapshotBuilder addReferences(int nodeFrom, int... nodesTo) {
        Field[] fields = new Field[nodesTo.length];
        for (int i = 0; i < nodesTo.length; i++) {
            mDirectBuffer.putShort(mOffsets[nodeFrom] + i * 2, (short) nodesTo[i]);
            fields[i] = new Field(Type.OBJECT, "f" + nodesTo[i]);
        }

        mNodes[nodeFrom].getClassObj().setFields(fields);
        return this;
    }

    public SnapshotBuilder addRoot(int node) {
        RootObj root = new RootObj(RootType.JAVA_LOCAL, node);
        mSnapshot.setToDefaultHeap();
        mSnapshot.addRoot(root);
        return this;
    }

    public Snapshot getSnapshot() {
        return mSnapshot;
    }
}
