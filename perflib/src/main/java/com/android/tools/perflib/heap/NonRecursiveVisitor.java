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

package com.android.tools.perflib.heap;

import com.android.annotations.NonNull;

import java.util.ArrayDeque;
import java.util.Deque;

import gnu.trove.TLongHashSet;

/**
 * Non-recursive depth-first visitor, managing its own stack.
 */
public class NonRecursiveVisitor implements Visitor {

    protected final Deque<Instance> mStack = new ArrayDeque<Instance>();

    // Marks nodes that have been visited.
    protected final TLongHashSet mSeen = new TLongHashSet();

    protected void defaultAction(Instance instance) {
    }

    @Override
    public void visitRootObj(@NonNull RootObj root) {
        defaultAction(root);
    }

    @Override
    public void visitArrayInstance(@NonNull ArrayInstance instance) {
        defaultAction(instance);
    }

    @Override
    public void visitClassInstance(@NonNull ClassInstance instance) {
        defaultAction(instance);
    }

    @Override
    public void visitClassObj(@NonNull ClassObj instance) {
        defaultAction(instance);
    }

    @Override
    public void visitLater(@NonNull Instance instance) {
        mStack.push(instance);
    }

    public void doVisit(Iterable<? extends Instance> startNodes) {
        for (Instance node : startNodes) {
            if (node instanceof RootObj) {
                // RootObj nodes don't have their own id, they should be visited right away.
                node.accept(this);
            } else {
                visitLater(node);
            }
        }
        while (!mStack.isEmpty()) {
            Instance node = mStack.pop();
            if (mSeen.add(node.getId())) {
                node.accept(this);
            }
        }
    }
}
