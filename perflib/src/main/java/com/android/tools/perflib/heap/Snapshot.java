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

import com.android.tools.perflib.heap.analysis.Dominators;
import com.google.common.collect.Iterables;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/*
 * A snapshot of all of the heaps, and related meta-data, for the runtime at a given instant.
 *
 * There are three possible heaps: default, app and zygote. GC roots are always reported in the
 * default heap, and they are simply references to objects living in the zygote or the app heap.
 * During parsing of the HPROF file HEAP_DUMP_INFO chunks change which heap is being referenced.
 */
public class Snapshot {

    //  Special root object used in dominator computation for objects reachable via multiple roots.
    public static final Instance SENTINEL_ROOT = new RootObj(RootType.UNKNOWN);

    private static final int DEFAULT_HEAP_ID = 0;

    HashMap<Integer, Heap> mHeaps;

    Heap mCurrentHeap;

    private Map<Instance, Instance> mDominatorMap;

    public Snapshot() {
        mHeaps = new HashMap<Integer, Heap>();
        setToDefaultHeap();
    }

    public Heap setToDefaultHeap() {
        return setHeapTo(DEFAULT_HEAP_ID, "default");
    }

    public Heap setHeapTo(int id, String name) {
        Heap heap = mHeaps.get(id);

        if (heap == null) {
            heap = new Heap(name);
            heap.mSnapshot = this;
            mHeaps.put(id, heap);
        }

        mCurrentHeap = heap;

        return mCurrentHeap;
    }

    public Heap getHeap(int id) {
        return mHeaps.get(id);
    }

    public Collection<Heap> getHeaps() {
        return mHeaps.values();
    }

    public Heap getHeap(String name) {
        for (Heap heap : mHeaps.values()) {
            if (heap.mName.equals(name)) {
                return heap;
            }
        }

        return null;
    }

    public Iterable<RootObj> getGCRoots() {
        // Roots are always in the default heap.
        return mHeaps.get(DEFAULT_HEAP_ID).mRoots;
    }

    public final void addStackFrame(StackFrame theFrame) {
        mCurrentHeap.addStackFrame(theFrame);
    }

    public final StackFrame getStackFrame(long id) {
        return mCurrentHeap.getStackFrame(id);
    }

    public final void addStackTrace(StackTrace theTrace) {
        mCurrentHeap.addStackTrace(theTrace);
    }

    public final StackTrace getStackTrace(int traceSerialNumber) {
        return mCurrentHeap.getStackTrace(traceSerialNumber);
    }

    public final StackTrace getStackTraceAtDepth(int traceSerialNumber,
            int depth) {
        return mCurrentHeap.getStackTraceAtDepth(traceSerialNumber, depth);
    }

    public final void addRoot(RootObj root) {
        mCurrentHeap.addRoot(root);
        root.setHeap(mCurrentHeap);
    }

    public final void addThread(ThreadObj thread, int serialNumber) {
        mCurrentHeap.addThread(thread, serialNumber);
    }

    public final ThreadObj getThread(int serialNumber) {
        return mCurrentHeap.getThread(serialNumber);
    }

    public final void addInstance(long id, Instance instance) {
        mCurrentHeap.addInstance(id, instance);
        instance.setHeap(mCurrentHeap);
    }

    public final void addClass(long id, ClassObj theClass) {
        mCurrentHeap.addClass(id, theClass);
        theClass.setHeap(mCurrentHeap);
    }

    public final Instance findReference(long id) {
        for (Heap heap : mHeaps.values()) {
            Instance instance = heap.getInstance(id);

            if (instance != null) {
                return instance;
            }
        }

        //  Couldn't find an instance of a class, look for a class object
        return findClass(id);
    }

    public final ClassObj findClass(long id) {
        for (Heap heap : mHeaps.values()) {
            ClassObj theClass = heap.getClass(id);

            if (theClass != null) {
                return theClass;
            }
        }

        return null;
    }

    public final ClassObj findClass(String name) {
        for (Heap heap : mHeaps.values()) {
            ClassObj theClass = heap.getClass(name);

            if (theClass != null) {
                return theClass;
            }
        }

        return null;
    }

    public Map<Instance, Instance> computeDominatorMap() {
        if (mDominatorMap == null) {
            mDominatorMap = Dominators.getDominatorMap(this);
        }
        return mDominatorMap;
    }

    /**
     * Kicks off the computation of dominators and retained sizes.
     */
    public void computeRetainedSizes() {
        // Initialize retained sizes for all classes and objects, including unreachable ones.
        for (Heap heap : mHeaps.values()) {
            for (Instance instance : Iterables.concat(heap.getClasses(), heap.getInstances())) {
                instance.setRetainedSize(instance.mHeap, instance.getSize());
            }
        }
        computeDominatorMap();
        for (Instance node : mDominatorMap.keySet()) {
            // Add the size of the current node to the retained size of every dominator up to the
            // root, in the same heap.
            for (Instance dom = mDominatorMap.get(node); dom != SENTINEL_ROOT;
                    dom = mDominatorMap.get(dom)) {
                dom.setRetainedSize(node.mHeap, dom.getRetainedSize(node.mHeap) + node.getSize());
            }
        }
    }

    public final void dumpInstanceCounts() {
        for (Heap heap : mHeaps.values()) {
            System.out.println(
                    "+------------------ instance counts for heap: " + heap.mName);
            heap.dumpInstanceCounts();
        }
    }

    public final void dumpSizes() {
        for (Heap heap : mHeaps.values()) {
            System.out.println(
                    "+------------------ sizes for heap: " + heap.mName);
            heap.dumpSizes();
        }
    }

    public final void dumpSubclasses() {
        for (Heap heap : mHeaps.values()) {
            System.out.println(
                    "+------------------ subclasses for heap: " + heap.mName);
            heap.dumpSubclasses();
        }
    }
}
