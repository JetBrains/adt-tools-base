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
import com.android.tools.perflib.heap.Instance;
import com.android.tools.perflib.heap.NonRecursiveVisitor;
import com.android.tools.perflib.heap.RootObj;
import com.android.tools.perflib.heap.Snapshot;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import java.util.List;

import gnu.trove.TLongHashSet;

public class TopologicalSort {

    @NonNull
    public static ImmutableList<Instance> compute(@NonNull Iterable<RootObj> roots) {
        TopologicalSortVisitor visitor = new TopologicalSortVisitor();
        visitor.doVisit(roots);
        ImmutableList<Instance> instances = visitor.getOrderedInstances();

        // We add the special sentinel node as the single root of the object graph, to ensure the
        // dominator algorithm terminates when having to choose between two GC roots.
        Snapshot.SENTINEL_ROOT.setTopologicalOrder(0);

        // Set localIDs in the range 1..keys.size(). This simplifies the algorithm & data structures
        // for dominator computation.
        int currentIndex = 0;
        for (Instance node : instances) {
            node.setTopologicalOrder(++currentIndex);
        }

        return instances;
    }


    /**
     * Topological sort visitor computing a post-order traversal of the graph.
     *
     * We use the classic iterative three-color marking algorithm in order to correctly compute the
     * finishing time for each node. Nodes in decreasing order of their finishing time satisfy the
     * topological order property, i.e. any node appears before its successors.
     */
    private static class TopologicalSortVisitor extends NonRecursiveVisitor {

        // Marks nodes that have been fully visited and popped off the stack.
        private final TLongHashSet mVisited = new TLongHashSet();

        private final List<Instance> mPostorder = Lists.newArrayList();

        @Override
        public void doVisit(Iterable<? extends Instance> startNodes) {
            for (Instance node : startNodes) {
                node.accept(this);
            }
            while (!mStack.isEmpty()) {
                Instance node = mStack.peek();
                if (mSeen.add(node.getId())) {
                    node.accept(this);
                } else {
                    mStack.pop();
                    if (mVisited.add(node.getId())) {
                        mPostorder.add(node);
                    }
                }
            }
        }

        ImmutableList<Instance> getOrderedInstances() {
            return ImmutableList.copyOf(Lists.reverse(mPostorder));
        }
    }
}
