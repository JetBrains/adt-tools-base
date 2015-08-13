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

import com.android.annotations.NonNull;
import com.android.tools.perflib.heap.*;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

/**
 * Initial implementation of dominator computation.
 *
 * Node <i>d</i> is said to dominate node <i>n</i> if every path from any of the roots to node
 * <i>n</i> must go through <i>d</i>. The <b>immediate</b> dominator of a node <i>n</i> is the
 * dominator <i>d</i> that is closest to <i>n</i>. The immediate dominance relation yields a tree
 * called <b>dominator tree</b>, with the important property that the subtree of a node corresponds
 * to the retained object graph of that particular node, i.e. the amount of memory that could be
 * freed if the node were garbage collected.
 *
 * The full algorithm is described in {@see http://www.cs.rice.edu/~keith/EMBED/dom.pdf}. It's a
 * simple iterative algorithm with worst-case complexity of O(N^2).
 */
public class Dominators {

    @NonNull
    private final Snapshot mSnapshot;

    @NonNull
    private final ImmutableList<Instance> mTopSort;

    public Dominators(@NonNull Snapshot snapshot, @NonNull ImmutableList<Instance> topSort) {
        mSnapshot = snapshot;
        mTopSort = topSort;

        // Only instances reachable from the GC roots will participate in dominator computation.
        // We will omit from the analysis any other nodes which could be considered roots, i.e. with
        // no incoming references, if they are not GC roots.
        for (RootObj root : snapshot.getGCRoots()) {
            Instance ref = root.getReferredInstance();
            if (ref != null) {
                ref.setImmediateDominator(Snapshot.SENTINEL_ROOT);
            }
        }
    }

    private void computeDominators() {
        // We need to iterate on the dominator computation because the graph may contain cycles.
        // TODO: Check how long it takes to converge, and whether we need to place an upper bound.
        boolean changed = true;
        while (changed) {
            changed = false;

            for (int i = 0; i < mTopSort.size(); i++) {
                Instance node = mTopSort.get(i);
                // Root nodes and nodes immediately dominated by the SENTINEL_ROOT are skipped.
                if (node.getImmediateDominator() != Snapshot.SENTINEL_ROOT) {
                    Instance dominator = null;

                    for (int j = 0; j < node.getHardReferences().size(); j++) {
                        Instance predecessor = node.getHardReferences().get(j);
                        if (predecessor.getImmediateDominator() == null) {
                            // If we don't have a dominator/approximation for predecessor, skip it
                            continue;
                        }
                        if (dominator == null) {
                            dominator = predecessor;
                        } else {
                            Instance fingerA = dominator;
                            Instance fingerB = predecessor;
                            while (fingerA != fingerB) {
                                if (fingerA.getTopologicalOrder() < fingerB.getTopologicalOrder()) {
                                    fingerB = fingerB.getImmediateDominator();
                                } else {
                                    fingerA = fingerA.getImmediateDominator();
                                }
                            }
                            dominator = fingerA;
                        }
                    }

                    if (node.getImmediateDominator() != dominator) {
                        node.setImmediateDominator(dominator);
                        changed = true;
                    }
                }
            }
        }
    }

    /**
     * Kicks off the computation of dominators and retained sizes.
     */
    public void computeRetainedSizes() {
        // Initialize retained sizes for all classes and objects, including unreachable ones.
        for (Heap heap : mSnapshot.getHeaps()) {
            for (Instance instance : Iterables.concat(heap.getClasses(), heap.getInstances())) {
                instance.resetRetainedSize();
            }
        }
        computeDominators();
        // We only update the retained sizes of objects in the dominator tree (i.e. reachable).
        for (Instance node : mSnapshot.getReachableInstances()) {
            int heapIndex = mSnapshot.getHeapIndex(node.getHeap());
            // Add the size of the current node to the retained size of every dominator up to the
            // root, in the same heap.
            for (Instance dom = node.getImmediateDominator(); dom != Snapshot.SENTINEL_ROOT;
                    dom = dom.getImmediateDominator()) {
                dom.addRetainedSize(heapIndex, node.getSize());
            }
        }
    }
}
