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
import com.android.tools.perflib.heap.io.HprofBuffer;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.UnsignedBytes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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

    //  The immediate dominator of this instance, or null if not reachable from any GC roots.
    @Nullable
    private Instance mImmediateDominator;

    //  The retained size of this object, indexed by heap (default, image, app, zygote).
    //  Intuitively, this represents the amount of memory that could be reclaimed in each heap if
    //  the instance were removed.
    //  To save space, we only keep a primitive array here following the order in mSnapshot.mHeaps.
    private long[] mRetainedSizes;

    //  List of all objects that hold a live reference to this object
    private final ArrayList<Instance> mReferences = new ArrayList<Instance>();

    Instance(long id, @NonNull StackTrace stackTrace) {
        mId = id;
        mStack = stackTrace;
    }

    public long getId() {
        return mId;
    }

    public abstract void accept(Visitor visitor);

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

    //  Add to the list of objects that have a hard reference to this Instance
    public void addReference(Instance reference) {
        mReferences.add(reference);
    }

    @NonNull
    public ArrayList<Instance> getReferences() {
        return mReferences;
    }

    @Nullable
    protected Object readValue(@NonNull Type type) {
        switch (type) {
            case OBJECT:
                long id = readId();
                Instance result = mHeap.mSnapshot.findReference(id);
                if (result != null) {
                    result.addReference(this);
                }
                return result;
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
        switch (Type.OBJECT.getSize()) {
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

    protected int readUnsignedByte(){
        return UnsignedBytes.toInt(getBuffer().readByte());
    }

    protected int readUnsignedShort() {
        return getBuffer().readShort() & 0xffff;
    }

    protected HprofBuffer getBuffer() {
        return mHeap.mSnapshot.mBuffer;
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
}
