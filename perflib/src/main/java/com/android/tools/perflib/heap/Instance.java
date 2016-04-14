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
import com.android.tools.perflib.captures.DataBuffer;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.UnsignedBytes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public abstract class Instance {
    protected final long mId;

    //  The stack in which this object was allocated
    @NonNull
    protected final StackTrace mStack;

    //  Id of the ClassObj of which this object is an instance
    long mClassId;

    //  The heap in which this object was allocated (app, zygote, etc)
    Heap mHeap;

    //  The size of this object
    int mSize;

    //  Another identifier for this Instance, that we computed during the analysis phase.
    int mTopologicalOrder;

    int mDistanceToGcRoot = Integer.MAX_VALUE;

    Instance mNextInstanceToGcRoot = null;

    //  The immediate dominator of this instance, or null if not reachable from any GC roots.
    @Nullable
    private Instance mImmediateDominator;

    //  The retained size of this object, indexed by heap (default, image, app, zygote).
    //  Intuitively, this represents the amount of memory that could be reclaimed in each heap if
    //  the instance were removed.
    //  To save space, we only keep a primitive array here following the order in mSnapshot.mHeaps.
    private long[] mRetainedSizes;

    protected final ArrayList<Instance> mHardForwardReferences = new ArrayList<Instance>();

    protected Instance mSoftForwardReference = null;

    //  List of all objects that hold a live reference to this object
    protected final ArrayList<Instance> mHardReverseReferences = new ArrayList<Instance>();

    //  List of all objects that hold a soft/weak/phantom reference to this object.
    //  Don't create an actual list until we need to.
    protected ArrayList<Instance> mSoftReverseReferences = null;

    Instance(long id, @NonNull StackTrace stackTrace) {
        mId = id;
        mStack = stackTrace;
    }

    /**
     * Resolves all forward/reverse + hard/soft references for this instance.
     */
    public abstract void resolveReferences();

    public abstract void accept(Visitor visitor);

    /**
     * Trims the variable size data to their minimal size to reduce memory usage.
     */
    public void compactMemory() {
        // mHardForwardReferences trimmed in resolveReferences();
        mHardReverseReferences.trimToSize();
        if (mSoftReverseReferences != null) {
            mSoftReverseReferences.trimToSize();
        }
    }

    public long getId() {
        return mId;
    }

    public long getUniqueId() {
        return getId() & mHeap.mSnapshot.getIdSizeMask();
    }

    public void setClassId(long classId) {
        mClassId = classId;
    }

    public ClassObj getClassObj() {
        return mHeap.mSnapshot.findClass(mClassId);
    }

    public final int getCompositeSize() {
        CompositeSizeVisitor visitor = new CompositeSizeVisitor();
        visitor.doVisit(ImmutableList.of(this));
        return visitor.getCompositeSize();
    }

    //  Returns the instrinsic size of a given object
    public int getSize() {
        return mSize;
    }

    public void setSize(int size) {
        mSize = size;
    }

    public void setHeap(Heap heap) {
        mHeap = heap;
    }

    public Heap getHeap() {
        return mHeap;
    }

    public int getTopologicalOrder() {
        return mTopologicalOrder;
    }

    public void setTopologicalOrder(int topologicalOrder) {
        mTopologicalOrder = topologicalOrder;
    }

    @Nullable
    public Instance getImmediateDominator() {
        return mImmediateDominator;
    }

    public void setImmediateDominator(@NonNull Instance dominator) {
        mImmediateDominator = dominator;
    }

    public int getDistanceToGcRoot() {
        return mDistanceToGcRoot;
    }

    public Instance getNextInstanceToGcRoot() {
        return mNextInstanceToGcRoot;
    }

    public void setDistanceToGcRoot(int newDistance) {
        assert (newDistance < mDistanceToGcRoot);
        mDistanceToGcRoot = newDistance;
    }

    public void setNextInstanceToGcRoot(Instance instance) {
        mNextInstanceToGcRoot = instance;
    }

    /**
     * Determines if this instance is reachable via hard references from any GC root.
     * The results are only valid after ShortestDistanceVisitor has been run.
     */
    public boolean isReachable() {
        return mDistanceToGcRoot != Integer.MAX_VALUE;
    }

    public void resetRetainedSize() {
        List<Heap> allHeaps = mHeap.mSnapshot.mHeaps;
        if (mRetainedSizes == null) {
            mRetainedSizes = new long[allHeaps.size()];
        } else {
            Arrays.fill(mRetainedSizes, 0);
        }
        mRetainedSizes[allHeaps.indexOf(mHeap)] = getSize();
    }

    public void addRetainedSize(int heapIndex, long size) {
        mRetainedSizes[heapIndex] += size;
    }

    public long getRetainedSize(int heapIndex) {
        return mRetainedSizes[heapIndex];
    }

    public long getTotalRetainedSize() {
        if (mRetainedSizes == null) {
            return 0;
        }

        long totalSize = 0;
        for (long mRetainedSize : mRetainedSizes) {
            totalSize += mRetainedSize;
        }
        return totalSize;
    }

    /**
     * Add to the list of objects that references this Instance.
     *
     * @param field     the named variable in #reference pointing to this instance. If the name of
     *                  the field is "referent", and #reference is a soft reference type, then
     *                  reference is counted as a soft reference instead of the usual hard
     *                  reference.
     * @param reference another instance that references this instance
     */
    public void addReverseReference(@Nullable Field field, @NonNull Instance reference) {
        if (reference.getIsSoftReference() && field != null && field.getName().equals("referent")) {
            if (mSoftReverseReferences == null) {
                mSoftReverseReferences = new ArrayList<Instance>();
            }
            mSoftReverseReferences.add(reference);
        } else {
            mHardReverseReferences.add(reference);
        }
    }

    @NonNull
    public ArrayList<Instance> getHardForwardReferences() {
        return mHardForwardReferences;
    }

    @NonNull
    public Instance getSoftForwardReference() {
        return mSoftForwardReference;
    }

    @NonNull
    public ArrayList<Instance> getHardReverseReferences() {
        return mHardReverseReferences;
    }

    @Nullable
    public ArrayList<Instance> getSoftReverseReferences() {
        return mSoftReverseReferences;
    }

    /**
     * Removes all duplicate references AND references to itself.
     */
    public void dedupeReferences() {
        Set<Instance> dedupeSet = new HashSet<Instance>(mHardReverseReferences.size());
        dedupeSet.addAll(mHardReverseReferences);
        dedupeSet.remove(this);
        mHardReverseReferences.clear();
        mHardReverseReferences.addAll(dedupeSet);
        mHardReverseReferences.trimToSize();

        if (getSoftReverseReferences() != null) {
            dedupeSet.clear();
            dedupeSet.addAll(getSoftReverseReferences());
            mSoftReverseReferences.clear();
            mSoftReverseReferences.addAll(dedupeSet);
            mSoftReverseReferences.trimToSize();
        }
    }

    /**
     * There is an underlying assumption that a class that is a soft reference will only have one
     * referent.
     *
     * @return true if the instance is a soft reference type, or false otherwise
     */
    public boolean getIsSoftReference() {
        return false;
    }

    @Nullable
    protected Object readValue(@NonNull Type type) {
        switch (type) {
            case OBJECT:
                long id = readId();
                return mHeap.mSnapshot.findInstance(id);
            case BOOLEAN:
                return getBuffer().readByte() != 0;
            case CHAR:
                return getBuffer().readChar();
            case FLOAT:
                return getBuffer().readFloat();
            case DOUBLE:
                return getBuffer().readDouble();
            case BYTE:
                return getBuffer().readByte();
            case SHORT:
                return getBuffer().readShort();
            case INT:
                return getBuffer().readInt();
            case LONG:
                return getBuffer().readLong();
        }
        return null;
    }

    protected long readId() {
        // As long as we don't interpret IDs, reading signed values here is fine.
        switch (mHeap.mSnapshot.getTypeSize(Type.OBJECT)) {
            case 1:
                return getBuffer().readByte();
            case 2:
                return getBuffer().readShort();
            case 4:
                return getBuffer().readInt();
            case 8:
                return getBuffer().readLong();
        }
        return 0;
    }

    protected int readUnsignedByte() {
        return UnsignedBytes.toInt(getBuffer().readByte());
    }

    protected int readUnsignedShort() {
        return getBuffer().readShort() & 0xffff;
    }

    protected DataBuffer getBuffer() {
        return mHeap.mSnapshot.getBuffer();
    }


    public static class CompositeSizeVisitor extends NonRecursiveVisitor {
        int mSize = 0;

        @Override
        protected void defaultAction(Instance node) {
            mSize += node.getSize();
        }

        public int getCompositeSize() {
            return mSize;
        }
    }

    public StackTrace getStack() {
        return mStack;
    }
}
