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

import com.android.tools.perflib.heap.ClassInstance;
import com.android.tools.perflib.heap.ClassObj;
import com.android.tools.perflib.heap.Field;
import com.android.tools.perflib.heap.RootObj;
import com.android.tools.perflib.heap.RootType;
import com.android.tools.perflib.heap.Snapshot;
import com.android.tools.perflib.heap.Type;
import com.android.tools.perflib.heap.Value;

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

    public SnapshotBuilder(int numNodes) {
        mSnapshot = new Snapshot();
        mSnapshot.setHeapTo(13, "testHeap");

        mNodes = new ClassInstance[numNodes + 1];
        for (int i = 1; i <= numNodes; i++) {
            ClassObj clazz = new ClassObj(100 + i, null, "Class" + i);
            clazz.setInstanceSize(i);

            mNodes[i] = new ClassInstance(i, null);
            mNodes[i].setClass(clazz);
            mSnapshot.addInstance(i, mNodes[i]);
        }
    }

    public SnapshotBuilder addReference(int nodeFrom, int nodeTo) {
        Value link = new Value(mNodes[nodeFrom]);
        link.setValue(mNodes[nodeTo]);
        mNodes[nodeFrom].addField(new Field(Type.OBJECT, "f" + nodeTo), link);
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
