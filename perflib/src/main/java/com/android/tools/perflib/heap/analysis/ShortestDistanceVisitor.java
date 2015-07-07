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
import com.android.tools.perflib.heap.NonRecursiveVisitor;

import java.util.Comparator;
import java.util.PriorityQueue;

public class ShortestDistanceVisitor extends NonRecursiveVisitor {
    private PriorityQueue<Instance> mPriorityQueue = new PriorityQueue<Instance>(1024, new Comparator<Instance>() {
        @Override
        public int compare(Instance o1, Instance o2) {
            return o1.getDistanceToGcRoot() - o2.getDistanceToGcRoot();
        }
    });
    private Instance mPreviousInstance = null;
    private int mVisitDistance = 0;

    @Override
    public void visitLater(Instance parent, @NonNull Instance child) {
        if (mVisitDistance < child.getDistanceToGcRoot() &&
                (parent == null ||
                     child.getSoftReferences() == null ||
                     !child.getSoftReferences().contains(parent) ||
                     child.getIsSoftReference())) {
            child.setDistanceToGcRoot(mVisitDistance);
            child.setNextInstanceToGcRoot(mPreviousInstance);
            mPriorityQueue.add(child);
        }
    }

    @Override
    public void doVisit(Iterable<? extends Instance> startNodes) {
        // root nodes are instances that share the same id as the node they point to.
        // This means that we cannot mark them as visited here or they would be marking
        // the actual root instance
        // TODO RootObj should not be Instance objects
        for (Instance node : startNodes) {
            node.accept(this);
        }

        while (!mPriorityQueue.isEmpty()) {
            Instance node = mPriorityQueue.poll();
            mVisitDistance = node.getDistanceToGcRoot() + 1;
            mPreviousInstance = node;
            node.accept(this);
        }
    }
}
