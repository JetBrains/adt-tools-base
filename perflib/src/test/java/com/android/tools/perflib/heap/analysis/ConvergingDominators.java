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
import com.android.tools.perflib.heap.Instance;
import com.android.tools.perflib.heap.RootObj;
import com.android.tools.perflib.heap.Snapshot;

/**
 * Initial implementation of dominator computation. <p> Node <i>d</i> is said to dominate node
 * <i>n</i> if every path from any of the roots to node <i>n</i> must go through <i>d</i>. The
 * <b>immediate</b> dominator of a node <i>n</i> is the dominator <i>d</i> that is closest to
 * <i>n</i>. The immediate dominance relation yields a tree called <b>dominator tree</b>, with the
 * important property that the subtree of a node corresponds to the retained object graph of that
 * particular node, i.e. the amount of memory that could be freed if the node were garbage
 * collected. <p> The full algorithm is described in
 * <a href="http://www.cs.rice.edu/~keith/EMBED/dom.pdf">
 *     http://www.cs.rice.edu/~keith/EMBED/dom.pdf</a>.
 * It's a simple iterative algorithm with worst-case complexity of O(N^2).
 */
public class ConvergingDominators extends DominatorsBase {
    private volatile int mStartingNodesCount = 0;

    private volatile int mRetiredNodes = 0;

    public ConvergingDominators(@NonNull Snapshot snapshot) {
        super(snapshot);
        mRetiredNodes = 0;
        mStartingNodesCount = mTopSort.size();

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

    @NonNull
    @Override
    public ComputationProgress getComputationProgress() {
        return new ComputationProgress(
                String.format("Calculating dominators %d/%d", mRetiredNodes, mStartingNodesCount),
                (double) mRetiredNodes / (double) mStartingNodesCount);
    }

    @Override
    public void computeDominators() {
        // We need to iterate on the dominator computation because the graph may contain cycles.
        boolean changed = true;
        while (changed) {
            changed = false;

            for (Instance node : mTopSort) {
                // Root nodes and nodes immediately dominated by the SENTINEL_ROOT are skipped.
                if (node.getImmediateDominator() != Snapshot.SENTINEL_ROOT) {
                    Instance dominator = null;

                    for (int j = 0; j < node.getHardReverseReferences().size(); j++) {
                        Instance predecessor = node.getHardReverseReferences().get(j);
                        if (predecessor.getImmediateDominator() == null) {
                            // If we don't have a dominator/approximation for predecessor, skip it
                            continue;
                        }
                        if (dominator == null) {
                            dominator = predecessor;
                        }
                        else {
                            Instance fingerA = dominator;
                            Instance fingerB = predecessor;
                            while (fingerA != fingerB) {
                                if (fingerA.getTopologicalOrder() < fingerB.getTopologicalOrder()) {
                                    assert fingerB.getTopologicalOrder() >=
                                           fingerB.getImmediateDominator().getTopologicalOrder();
                                    fingerB = fingerB.getImmediateDominator();
                                }
                                else {
                                    assert fingerA.getTopologicalOrder() >=
                                           fingerA.getImmediateDominator().getTopologicalOrder();
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
}
