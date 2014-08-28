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
import com.android.tools.perflib.heap.Heap;
import com.android.tools.perflib.heap.Instance;
import com.android.tools.perflib.heap.RootObj;
import com.android.tools.perflib.heap.Snapshot;
import com.android.tools.perflib.heap.Visitor;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import java.util.List;
import java.util.Map;
import java.util.Set;

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
    public static Map<Instance, Instance> getDominatorMap(@NonNull Snapshot snapshot) {
        Map<Instance, Instance> mDominatorMap = Maps.newHashMap();

        // Only instances reachable from the GC roots will participate in dominator computation.
        // We will omit from the analysis any other nodes which could be considered roots, i.e. with
        // no incoming references, if they are not GC roots.

        // Start with a topological sort because we want to process a node after all its parents.
        Map<Instance, Integer> topSort =
                TopologicalSortVisitor.getTopologicalSort(snapshot.getGCRoots());

        // We add the special sentinel node as the single root of the object graph, to ensure the
        // dominator algorithm terminates when having to choose between two GC roots.
        topSort.put(Snapshot.SENTINEL_ROOT, 0);
        Set<Instance> roots = Sets.newHashSet(Snapshot.SENTINEL_ROOT);
        for (RootObj root : snapshot.getGCRoots()) {
            Instance ref = root.getReferredInstance();
            if (ref != null) {
                mDominatorMap.put(ref, Snapshot.SENTINEL_ROOT);
                roots.add(ref);
            }
        }

        // We need to iterate on the dominator computation because the graph may contain cycles.
        // TODO: Check how long it takes to converge, and whether we need to place an upper bound.
        boolean changed = true;
        while (changed) {
            changed = false;

            for (Instance node : topSort.keySet()) {
                if (!roots.contains(node)) { // Root nodes are skipped
                    Instance dominator = null;

                    for (Instance predecessor : node.getReferences()) {
                        if (mDominatorMap.get(predecessor) == null) {
                            // If we don't have a dominator/approximation for predecessor, skip it
                            continue;
                        }
                        if (dominator == null) {
                            dominator = predecessor;
                        } else {
                            Instance fingerA = dominator;
                            Instance fingerB = predecessor;
                            while (!fingerA.equals(fingerB)) {
                                if (topSort.get(fingerA) < topSort.get(fingerB)) {
                                    fingerB = mDominatorMap.get(fingerB);
                                } else {
                                    fingerA = mDominatorMap.get(fingerA);
                                }
                            }
                            dominator = fingerA;
                        }
                    }

                    if (mDominatorMap.get(node) != dominator) {
                        mDominatorMap.put(node, dominator);
                        changed = true;
                    }
                }
            }
        }
        return ImmutableMap.copyOf(mDominatorMap);
    }


    private static class TopologicalSortVisitor implements Visitor {

        private final Set<Instance> mVisited = Sets.newHashSet();

        private final List<Instance> mPostorder = Lists.newArrayList();

        @Override
        public boolean visitEnter(Instance instance) {
            return mVisited.add(instance);
        }

        @Override
        public void visitLeave(Instance instance) {
            mPostorder.add(instance);
        }

        private Map<Instance, Integer> buildTopologicalSort() {
            Map<Instance, Integer> result = Maps.newLinkedHashMap();
            int currentIndex = 0;
            for (Instance node : Lists.reverse(mPostorder)) {
                result.put(node, ++currentIndex);
            }
            return result;
        }

        static Map<Instance, Integer> getTopologicalSort(@NonNull Iterable<? extends Instance> roots) {
            TopologicalSortVisitor visitor = new TopologicalSortVisitor();
            for (Instance root : roots) {
                root.accept(visitor);
            }
            return visitor.buildTopologicalSort();
        }
    }
}
