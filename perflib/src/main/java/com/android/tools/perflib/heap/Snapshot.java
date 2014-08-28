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
import com.android.tools.perflib.heap.analysis.Dominators;
import com.android.tools.perflib.heap.io.HprofBuffer;
import com.google.common.collect.Iterables;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

/*
 * A snapshot of all of the heaps, and related meta-data, for the runtime at a given instant.
 *
 * There are three possible heaps: default, app and zygote. GC roots are always reported in the
 * default heap, and they are simply references to objects living in the zygote or the app heap.
 * During parsing of the HPROF file HEAP_DUMP_INFO chunks change which heap is being referenced.
 */
public class Snapshot {

    private static final String JAVA_LANG_CLASS = "java.lang.Class";

    //  Special root object used in dominator computation for objects reachable via multiple roots.
    public static final Instance SENTINEL_ROOT = new RootObj(RootType.UNKNOWN);

    private static final int DEFAULT_HEAP_ID = 0;

    @NonNull
    final HprofBuffer mBuffer;

    @NonNull
    ArrayList<Heap> mHeaps = new ArrayList<Heap>();

    @NonNull
    Heap mCurrentHeap;

    private Map<Instance, Instance> mDominatorMap;

    public Snapshot(@NonNull HprofBuffer buffer) {
        mBuffer = buffer;
        setToDefaultHeap();
    }

    @NonNull
    public Heap setToDefaultHeap() {
        return setHeapTo(DEFAULT_HEAP_ID, "default");
    }

    @NonNull
    public Heap setHeapTo(int id, @NonNull String name) {
        Heap heap = getHeap(id);

        if (heap == null) {
            heap = new Heap(id, name);
            heap.mSnapshot = this;
            mHeaps.add(heap);
        }

        mCurrentHeap = heap;

        return mCurrentHeap;
    }

    @Nullable
    public Heap getHeap(int id) {
        //noinspection ForLoopReplaceableByForEach
        for (int i = 0; i < mHeaps.size(); i++) {
            if (mHeaps.get(i).getId() == id) {
                return mHeaps.get(i);
            }
        }
        return null;
    }

    @Nullable
    public Heap getHeap(@NonNull String name) {
        //noinspection ForLoopReplaceableByForEach
        for (int i = 0; i < mHeaps.size(); i++) {
            if (name.equals(mHeaps.get(i).getName())) {
                return mHeaps.get(i);
            }
        }
        return null;
    }

    @NonNull
    public Collection<Heap> getHeaps() {
        return mHeaps;
    }

    @NonNull
    public Collection<RootObj> getGCRoots() {
        // Roots are always in the default heap.
        return mHeaps.get(DEFAULT_HEAP_ID).mRoots;
    }

    public final void addStackFrame(@NonNull StackFrame theFrame) {
        mCurrentHeap.addStackFrame(theFrame);
    }

    public final StackFrame getStackFrame(long id) {
        return mCurrentHeap.getStackFrame(id);
    }

    public final void addStackTrace(@NonNull StackTrace theTrace) {
        mCurrentHeap.addStackTrace(theTrace);
    }

    public final StackTrace getStackTrace(int traceSerialNumber) {
        return mCurrentHeap.getStackTrace(traceSerialNumber);
    }

    public final StackTrace getStackTraceAtDepth(int traceSerialNumber,
            int depth) {
        return mCurrentHeap.getStackTraceAtDepth(traceSerialNumber, depth);
    }

    public final void addRoot(@NonNull RootObj root) {
        mCurrentHeap.addRoot(root);
        root.setHeap(mCurrentHeap);
    }

    public final void addThread(ThreadObj thread, int serialNumber) {
        mCurrentHeap.addThread(thread, serialNumber);
    }

    public final ThreadObj getThread(int serialNumber) {
        return mCurrentHeap.getThread(serialNumber);
    }

    public final void addInstance(long id, @NonNull Instance instance) {
        mCurrentHeap.addInstance(id, instance);
        instance.setHeap(mCurrentHeap);
    }

    public final void addClass(long id, @NonNull ClassObj theClass) {
        mCurrentHeap.addClass(id, theClass);
        theClass.setHeap(mCurrentHeap);
    }

    @Nullable
    public final Instance findReference(long id) {
        //noinspection ForLoopReplaceableByForEach
        for (int i = 0; i < mHeaps.size(); i++) {
            Instance instance = mHeaps.get(i).getInstance(id);

            if (instance != null) {
                return instance;
            }
        }

        //  Couldn't find an instance of a class, look for a class object
        return findClass(id);
    }

    @Nullable
    public final ClassObj findClass(long id) {
        //noinspection ForLoopReplaceableByForEach
        for (int i = 0; i < mHeaps.size(); i++) {
            ClassObj theClass = mHeaps.get(i).getClass(id);

            if (theClass != null) {
                return theClass;
            }
        }

        return null;
    }

    @Nullable
    public final ClassObj findClass(String name) {
        //noinspection ForLoopReplaceableByForEach
        for (int i = 0; i < mHeaps.size(); i++) {
            ClassObj theClass = mHeaps.get(i).getClass(name);

            if (theClass != null) {
                return theClass;
            }
        }

        return null;
    }

    public void resolveClasses() {
        ClassObj clazz = findClass(JAVA_LANG_CLASS);
        int javaLangClassSize = clazz != null ? clazz.getInstanceSize() : 0;

        for (Heap heap : mHeaps) {
            for (ClassObj classObj : heap.getClasses()) {
                ClassObj superClass = classObj.getSuperClassObj();
                if (superClass != null) {
                    superClass.addSubclass(classObj);
                }
                // We under-approximate the size of the class by including the size of Class.class
                // and the size of static fields, and omitting padding, vtable and imtable sizes.
                int classSize = javaLangClassSize;

                for (Field f : classObj.mStaticFields) {
                    classSize += f.getType().getSize();
                }
                classObj.setSize(classSize);
            }
            for (Instance instance : heap.getInstances()) {
                ClassObj classObj = instance.getClassObj();
                if (classObj != null) {
                    classObj.addInstance(instance);
                }
            }
        }
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
        for (Heap heap : mHeaps) {
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
        for (Heap heap : mHeaps) {
            System.out.println(
                    "+------------------ instance counts for heap: " + heap.getName());
            heap.dumpInstanceCounts();
        }
    }

    public final void dumpSizes() {
        for (Heap heap : mHeaps) {
            System.out.println(
                    "+------------------ sizes for heap: " + heap.getName());
            heap.dumpSizes();
        }
    }

    public final void dumpSubclasses() {
        for (Heap heap : mHeaps) {
            System.out.println(
                    "+------------------ subclasses for heap: " + heap.getName());
            heap.dumpSubclasses();
        }
    }
}
