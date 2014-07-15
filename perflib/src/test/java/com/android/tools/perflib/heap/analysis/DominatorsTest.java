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

import com.android.tools.perflib.heap.HprofParser;
import com.android.tools.perflib.heap.Instance;
import com.android.tools.perflib.heap.Snapshot;
import com.google.common.io.Closeables;

import junit.framework.TestCase;

import java.io.DataInputStream;
import java.util.Map;

public class DominatorsTest extends TestCase {

    private Snapshot mSnapshot;
    private Map<Instance, Instance> mDominators;

    public void testSimpleGraph() {
        mSnapshot = new SnapshotBuilder(6)
                .addReference(1, 2)
                .addReference(1, 3)
                .addReference(2, 4)
                .addReference(2, 6)
                .addReference(3, 4)
                .addReference(3, 5)
                .addReference(4, 6)
                .addRoot(1)
                .getSnapshot();

        mDominators = Dominators.getDominatorMap(mSnapshot);
        assertEquals(6, mDominators.size());
        assertDominates(1, 2);
        assertDominates(1, 3);
        assertDominates(1, 4);
        assertDominates(1, 6);
        assertDominates(3, 5);
    }

    public void testCyclicGraph() {
        mSnapshot = new SnapshotBuilder(4)
                .addReference(1, 2)
                .addReference(1, 3)
                .addReference(1, 4)
                .addReference(2, 3)
                .addReference(3, 4)
                .addReference(4, 2)
                .addRoot(1)
                .getSnapshot();

        mDominators = Dominators.getDominatorMap(mSnapshot);
        assertEquals(4, mDominators.size());
        assertDominates(1, 2);
        assertDominates(1, 3);
        assertDominates(1, 4);
    }

    public void testMultipleRoots() {
        mSnapshot = new SnapshotBuilder(5)
                .addReference(1, 3)
                .addReference(2, 4)
                .addReference(3, 5)
                .addReference(4, 5)
                .addRoot(1)
                .addRoot(2)
                .getSnapshot();

        mDominators = Dominators.getDominatorMap(mSnapshot);
        assertEquals(5, mDominators.size());
        assertDominates(1, 3);
        assertDominates(2, 4);
        // Node 5 is reachable via both roots, neither of which can be the sole dominator.
        assertEquals(mSnapshot.SENTINEL_ROOT, mDominators.get(mSnapshot.findReference(5)));
    }

    public void testSampleHprof() throws Exception {
        DataInputStream dis = new DataInputStream(
                ClassLoader.getSystemResourceAsStream("dialer.android-hprof"));
        try {
            Snapshot snapshot = (new HprofParser(dis)).parse();
            Map<Instance, Instance> dominators = Dominators.getDominatorMap(snapshot);

            // TODO: Double-check this data
            assertEquals(29598, dominators.size());

            // An object reachable via two GC roots, a JNI global and a Thread.
            Instance instance = snapshot.findReference(0xB0EDFFA0L);
            assertEquals(Snapshot.SENTINEL_ROOT, dominators.get(instance));
        } finally {
            Closeables.close(dis, false);
        }
    }

    /** Asserts that nodeA dominates nodeB in mHeap. */
    private void assertDominates(int nodeA, int nodeB) {
        assertEquals(mSnapshot.findReference(nodeA),
                mDominators.get(mSnapshot.findReference(nodeB)));
    }
}
