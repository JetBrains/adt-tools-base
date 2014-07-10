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
import java.util.HashSet;
import java.util.Set;

public abstract class Instance {

    long mId;

    //  Id of the ClassObj of which this object is an instance
    ClassObj mClass;

    //  The stack in which this object was allocated
    StackTrace mStack;

    //  The heap in which this object was allocated (app, zygote, etc)
    Heap mHeap;

    //  The size of this object
    int mSize;

    //  List of all objects that hold a live reference to this object
    private ArrayList<Instance> mReferences;

    /*
     * Some operations require gathering all the objects in a given section
     * of the object graph.  If non-null, the filter is applied to each
     * node in the graph to determine if it should be added to the result
     * set.
     */
    public abstract void visit(Set<Instance> resultSet, Filter filter);

    public ClassObj getClassObj() {
        return mClass;
    }

    public void setClass(ClassObj aClass) {
        assert mClass == null;
        mClass = aClass;
        aClass.addInstance(this);
    }

    public final int getCompositeSize() {
        HashSet<Instance> set = new HashSet<Instance>();

        visit(set, null);

        int size = 0;

        for (Instance instance : set) {
            size += instance.getSize();
        }

        return size;
    }

    //  Returns the instrinsic size of a given object
    public int getSize() {
        return mSize;
    }

    public void setSize(int size) {
        mSize = size;
    }

    public abstract String getTypeName();

    public void setHeap(Heap heap) {
        mHeap = heap;
    }

    //  Add to the list of objects that have a hard reference to this Instance
    public void addReference(Instance reference) {
        if (mReferences == null) {
            mReferences = new ArrayList<Instance>();
        }

        mReferences.add(reference);
    }

    public ArrayList<Instance> getReferences() {
        if (mReferences == null) {
            mReferences = new ArrayList<Instance>();
        }

        return mReferences;
    }

    public interface Filter {

        public boolean accept(Instance instance);
    }
}
