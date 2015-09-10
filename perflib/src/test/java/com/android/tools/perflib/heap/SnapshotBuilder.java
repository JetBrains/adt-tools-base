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
import com.android.tools.perflib.heap.hprof.*;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Utility for creating Snapshot objects to be used in tests.
 *
 * As the main concern here is graph connectivity, we only initialize the app heap, creating
 * ClassInstance objects with id in [1..numNodes], each instance pointing to a unique ClassObj.
 * The class ids range in [101..100+numNodes] and their size is set to match the id of their object
 * instance. The default heap holds the roots.
 */
public class SnapshotBuilder {
    public static final int SOFT_REFERENCE_ID = 99;

    public static final int SOFT_AND_HARD_REFERENCE_ID = 98;

    private int mNextAvailableSoftReferenceNodeId;

    private int mNextAvailableSoftAndHardReferenceNodeId;

    private int mNumNodes;
    private int mNumSoftNodes;
    private int mNumSoftAndHardNodes;
    private int mMaxTotalNodes;

    // Map from node id to the list of nodes it references.
    private List<Integer>[] mReferences;

    private List<Integer> mRoots;

    public SnapshotBuilder(int numNodes) {
        this(numNodes, 0, 0);
    }

    public SnapshotBuilder(int numNodes, int numSoftNodes, int numSoftAndHardNodes) {
        mNextAvailableSoftReferenceNodeId = numNodes + 1;
        mNextAvailableSoftAndHardReferenceNodeId = numNodes + numSoftNodes + 1;
        mNumNodes = numNodes;
        mNumSoftNodes = numSoftNodes;
        mNumSoftAndHardNodes = numSoftAndHardNodes;
        mMaxTotalNodes = numNodes + numSoftNodes + numSoftAndHardNodes;
        mReferences = (List<Integer>[])new List[mMaxTotalNodes+1];
        for (int i = 0; i < mReferences.length; i++) {
            mReferences[i] = new ArrayList<Integer>();
        }
        mRoots = new ArrayList<Integer>();
    }

    public SnapshotBuilder addReferences(int nodeFrom, int... nodesTo) {
        assertEquals(mReferences[nodeFrom].size(), 0);
        for (int i = 0; i < nodesTo.length; i++) {
            mReferences[nodeFrom].add(nodesTo[i]);
        }
        return this;
    }

    /**
     * Inserts a soft reference instance between <code>nodeFrom</code> to <code>nodeTo</code>.
     *
     * @param nodeFrom the parent node
     * @param nodeTo the child node
     * @return this
     */
    public SnapshotBuilder insertSoftReference(int nodeFrom, int nodeToSoftReference) {
        int softReferenceId = mNextAvailableSoftReferenceNodeId++;
        assert softReferenceId <= mNumNodes + mNumSoftNodes;
        mReferences[nodeFrom].add(softReferenceId);
        mReferences[softReferenceId].add(nodeToSoftReference);
        return this;
    }

    public SnapshotBuilder insertSoftAndHardReference(int nodeFrom,
            int nodeToSoftReference, int nodeToHardReference) {
        int softReferenceId = mNextAvailableSoftAndHardReferenceNodeId++;
        assert softReferenceId <= mMaxTotalNodes;
        mReferences[nodeFrom].add(softReferenceId);
        mReferences[softReferenceId].add(nodeToSoftReference);
        mReferences[softReferenceId].add(nodeToHardReference);
        return this;
    }

    public SnapshotBuilder addRoot(int node) {
        mRoots.add(node);
        return this;
    }

    public Snapshot build() {
        HprofStringBuilder strings = new HprofStringBuilder(0);
        List<HprofRecord> records = new ArrayList<HprofRecord>();
        List<HprofDumpRecord> dump = new ArrayList<HprofDumpRecord>();
        byte objType = HprofType.TYPE_OBJECT;

        // Roots go in the default heap. Add those first.
        for (Integer id : mRoots) {
            dump.add(new HprofRootUnknown(id));
        }

        // Everything else goes in "testHeap" with id 13.
        dump.add(new HprofHeapDumpInfo(13, strings.get("testHeap")));

        // The SoftReference class
        records.add(new HprofLoadClass( 0, 0, SOFT_REFERENCE_ID, 0,
                    strings.get("java.lang.ref.Reference")));
        dump.add(new HprofClassDump(SOFT_REFERENCE_ID, 0, 0, 0, 0, 0, 0, 0, 0,
                    new HprofConstant[0], new HprofStaticField[0],
                    new HprofInstanceField[]{
                        new HprofInstanceField(strings.get("referent"), objType)}));

        // The SoftAndHardReference class
        records.add(new HprofLoadClass(0, 1, SOFT_AND_HARD_REFERENCE_ID, 0,
                    strings.get("SoftAndHardReference")));
        dump.add(new HprofClassDump(SOFT_AND_HARD_REFERENCE_ID, 0, 0, 0, 0, 0, 0, 0, 0,
                    new HprofConstant[0], new HprofStaticField[0],
                    new HprofInstanceField[]{
                        new HprofInstanceField(strings.get("referent"), objType),
                        new HprofInstanceField(strings.get("hardReference"), objType)}));

        // Regular nodes and their classes
        for (int i = 1; i <= mNumNodes; i++) {
            HprofInstanceField[] fields = new HprofInstanceField[mReferences[i].size()];
            ByteArrayDataOutput values = ByteStreams.newDataOutput();
            for (int j = 0; j < fields.length; j++) {
                fields[j] = new HprofInstanceField(strings.get("field" + j), objType);
                values.writeShort(mReferences[i].get(j));
            }

            // Use same name classes on different loaders to extend test coverage
            records.add(new HprofLoadClass(0, 0, 100+i, 0, strings.get("Class" + (i/2))));
            dump.add(new HprofClassDump(100+i, 0, 0, i%2, 0, 0, 0, 0, i,
                        new HprofConstant[0], new HprofStaticField[0], fields));

            dump.add(new HprofInstanceDump(i, 0, 100+i, values.toByteArray()));
        }

        // Soft reference nodes.
        for (int i = mNumNodes + 1; i <= mNumNodes + mNumSoftNodes; ++i) {
            assertEquals(1, mReferences[i].size());
            ByteArrayDataOutput values = ByteStreams.newDataOutput();
            values.writeShort(mReferences[i].get(0));
            dump.add(new HprofInstanceDump(i, 0, SOFT_REFERENCE_ID, values.toByteArray()));
        }

        // Soft and hard reference nodes.
        for (int i = mNumNodes + mNumSoftNodes + 1; i <= mMaxTotalNodes; ++i) {
            assertEquals(2, mReferences[i].size());
            ByteArrayDataOutput values = ByteStreams.newDataOutput();
            values.writeShort(mReferences[i].get(0));
            values.writeShort(mReferences[i].get(1));
            dump.add(new HprofInstanceDump(i, 0, SOFT_AND_HARD_REFERENCE_ID, values.toByteArray()));
        }

        records.add(new HprofHeapDump(0, dump.toArray(new HprofDumpRecord[0])));

        // TODO: Should perflib handle the case where strings are referred to
        // before they are defined?
        List<HprofRecord> actualRecords = new ArrayList<HprofRecord>();
        actualRecords.addAll(strings.getStringRecords());
        actualRecords.addAll(records);

        Snapshot snapshot = null;
        try {
            Hprof hprof = new Hprof("JAVA PROFILE 1.0.3", 2, new Date(), actualRecords);
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            hprof.write(os);
            InMemoryBuffer buffer = new InMemoryBuffer(os.toByteArray());
            snapshot = Snapshot.createSnapshot(buffer);
        } catch (IOException e) {
            fail("IOException when writing to byte output stream: " + e);
        }

        // TODO: Should the parser be setting isSoftReference, not the builder?
        for (Heap heap : snapshot.getHeaps()) {
            ClassObj softClass = heap.getClass(SOFT_REFERENCE_ID);
            if (softClass != null) {
                softClass.setIsSoftReference();
            }

            ClassObj softAndHardClass = heap.getClass(SOFT_AND_HARD_REFERENCE_ID);
            if (softAndHardClass != null) {
                softAndHardClass.setIsSoftReference();
            }
        }
        return snapshot;
    }
}
