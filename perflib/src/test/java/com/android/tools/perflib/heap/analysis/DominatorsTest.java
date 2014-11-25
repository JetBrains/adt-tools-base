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

import com.android.tools.perflib.heap.ClassObj;
import com.android.tools.perflib.heap.HprofParser;
import com.android.tools.perflib.heap.Instance;
import com.android.tools.perflib.heap.Snapshot;
import com.android.tools.perflib.heap.io.MemoryMappedFileBuffer;

import junit.framework.TestCase;

import java.io.File;

public class DominatorsTest extends TestCase {

    private Snapshot mSnapshot;

    public void testSimpleGraph() {
        mSnapshot = new SnapshotBuilder(6)
                .addReferences(1, 2, 3)
                .addReferences(2, 4, 6)
                .addReferences(3, 4, 5)
                .addReferences(4, 6)
                .addRoot(1)
                .getSnapshot();

        mSnapshot.computeDominators();

        assertEquals(6, mSnapshot.getReachableInstances().size());
        assertDominates(1, 2);
        assertDominates(1, 3);
        assertDominates(1, 4);
        assertDominates(1, 6);
        assertDominates(3, 5);
    }

    public void testCyclicGraph() {
        mSnapshot = new SnapshotBuilder(4)
                .addReferences(1, 2, 3, 4)
                .addReferences(2, 3)
                .addReferences(3, 4)
                .addReferences(4, 2)
                .addRoot(1)
                .getSnapshot();

        mSnapshot.computeDominators();

        assertEquals(4, mSnapshot.getReachableInstances().size());
        assertDominates(1, 2);
        assertDominates(1, 3);
        assertDominates(1, 4);
    }

    public void testMultipleRoots() {
        mSnapshot = new SnapshotBuilder(6)
                .addReferences(1, 3)
                .addReferences(2, 4)
                .addReferences(3, 5)
                .addReferences(4, 5)
                .addReferences(5, 6)
                .addRoot(1)
                .addRoot(2)
                .getSnapshot();

        mSnapshot.computeDominators();

        assertEquals(6, mSnapshot.getReachableInstances().size());
        assertDominates(1, 3);
        assertDominates(2, 4);
        // Node 5 is reachable via both roots, neither of which can be the sole dominator.
        assertEquals(mSnapshot.SENTINEL_ROOT,
                mSnapshot.findReference(5).getImmediateDominator());
        assertDominates(5, 6);
    }

    public void testDoublyLinkedList() {
        // Node 1 points to a doubly-linked list 2-3-4-5-6-7-8-9.
        mSnapshot = new SnapshotBuilder(9)
                .addReferences(1, 2)
                .addReferences(2, 3, 9)
                .addReferences(3, 2, 4)
                .addReferences(4, 3, 5)
                .addReferences(5, 4, 6)
                .addReferences(6, 5, 7)
                .addReferences(7, 6, 8)
                .addReferences(8, 7, 9)
                .addReferences(9, 2, 8)
                .addRoot(1)
                .getSnapshot();

        mSnapshot.computeDominators();

        assertEquals(45, mSnapshot.findReference(1).getRetainedSize(1));
        assertEquals(44, mSnapshot.findReference(2).getRetainedSize(1));
        for (int i = 3; i <= 9; i++) {
            assertEquals(i, mSnapshot.findReference(i).getRetainedSize(1));
        }
    }

    public void testSampleHprof() throws Exception {
        File file = new File(ClassLoader.getSystemResource("dialer.android-hprof").getFile());
        mSnapshot = (new HprofParser(new MemoryMappedFileBuffer(file))).parse();
        mSnapshot.computeDominators();

        // TODO: investigate the unreachable objects: there are 43687 objects in total.
        assertEquals(42911, mSnapshot.getReachableInstances().size());

        // An object reachable via two GC roots, a JNI global and a Thread.
        Instance instance = mSnapshot.findReference(0xB0EDFFA0);
        assertEquals(Snapshot.SENTINEL_ROOT, instance.getImmediateDominator());

        int appIndex = mSnapshot.getHeapIndex(mSnapshot.getHeap("app"));
        int zygoteIndex = mSnapshot.getHeapIndex(mSnapshot.getHeap("zygote"));

        // The largest object in our sample hprof belongs to the zygote
        ClassObj htmlParser = mSnapshot.findClass("android.text.Html$HtmlParser");
        assertEquals(116492, htmlParser.getRetainedSize(zygoteIndex));
        assertEquals(0, htmlParser.getRetainedSize(appIndex));

        // One of the bigger objects in the app heap
        ClassObj activityThread = mSnapshot.findClass("android.app.ActivityThread");
        assertEquals(813, activityThread.getRetainedSize(zygoteIndex));
        assertEquals(576, activityThread.getRetainedSize(appIndex));
    }

    /**
     * Asserts that nodeA dominates nodeB in mHeap.
     */
    private void assertDominates(int nodeA, int nodeB) {
        assertEquals(mSnapshot.findReference(nodeA),
                mSnapshot.findReference(nodeB).getImmediateDominator());
    }
}
