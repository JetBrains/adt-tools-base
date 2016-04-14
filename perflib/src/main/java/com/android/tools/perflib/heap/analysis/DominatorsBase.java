/*
 * Copyright (C) 2015 The Android Open Source Project
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

import com.android.annotations.NonNull;
import com.android.tools.perflib.heap.Heap;
import com.android.tools.perflib.heap.Instance;
import com.android.tools.perflib.heap.Snapshot;
import com.google.common.collect.Iterables;
import gnu.trove.TObjectProcedure;

import java.util.List;

public abstract class DominatorsBase {
    @NonNull
    protected volatile ComputationProgress mCurrentProgress = new ComputationProgress(
            "Starting dominator computation", 0.0);

    protected Snapshot mSnapshot;

    @NonNull
    protected List<Instance> mTopSort;

    protected DominatorsBase(@NonNull Snapshot snapshot) {
        mSnapshot = snapshot;
        assert mSnapshot.getTopologicalOrdering() != null;
        mTopSort = mSnapshot.getTopologicalOrdering();

        // Initialize retained sizes for all classes and objects, including unreachable ones.
        for (Heap heap : mSnapshot.getHeaps()) {
            for (Instance instance : heap.getClasses()) {
                instance.resetRetainedSize();
            }
            heap.forEachInstance(new TObjectProcedure<Instance>() {
                @Override
                public boolean execute(Instance instance) {
                    instance.resetRetainedSize();
                    return true;
                }
            });
        }
    }

    public void dispose() {
        mSnapshot = null;
    }

    @NonNull
    public abstract ComputationProgress getComputationProgress();

    /**
     * Kicks off the computation of dominators.
     */
    public abstract void computeDominators();

    /**
     * Computes retained sizes of instances. Only call this AFTER dominator computation.
     */
    public void computeRetainedSizes() {
        // We only update the retained sizes of objects in the dominator tree (i.e. reachable).
        for (Instance node : mSnapshot.getReachableInstances()) {
            int heapIndex = mSnapshot.getHeapIndex(node.getHeap());
            // Add the size of the current node to the retained size of every dominator up to the
            // root, in the same heap.
            for (Instance dom = node.getImmediateDominator(); dom != Snapshot.SENTINEL_ROOT;
                    dom = dom.getImmediateDominator()) {
                assert dom != null;
                dom.addRetainedSize(heapIndex, node.getSize());
            }
        }
    }
}
