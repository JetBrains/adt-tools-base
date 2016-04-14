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
import com.android.annotations.Nullable;
import com.android.tools.perflib.heap.Heap;
import com.android.tools.perflib.heap.Instance;
import com.android.tools.perflib.heap.RootObj;
import com.android.tools.perflib.heap.Snapshot;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import java.util.*;

import gnu.trove.TIntStack;
import gnu.trove.TObjectProcedure;

/**
 * Computes dominators based on the union-find data structure with path compression and linking by
 * size. Using description found in: http://adambuchsbaum.com/papers/dom-toplas.pdf which is based
 * on a copy of the paper available at:
 * http://www.cc.gatech.edu/~harrold/6340/cs6340_fall2009/Readings/lengauer91jul.pdf
 */
public final class LinkEvalDominators extends DominatorsBase {
    @NonNull
    private ArrayList<LinkEvalNode> mNodes;

    @NonNull
    private LinkEval mLinkEval;

    private volatile int mSemiDominatorProgress = 0;

    private volatile int mDominatorProgress = 0;

    public LinkEvalDominators(@NonNull Snapshot snapshot) {
        super(snapshot);

        final Map<Instance, LinkEvalNode> instanceNodeMap = new HashMap<Instance, LinkEvalNode>();
        TObjectProcedure<Instance> mapProcedure = new TObjectProcedure<Instance>() {
            @Override
            public boolean execute(Instance instance) {
                LinkEvalNode node = new LinkEvalNode(instance);
                instanceNodeMap.put(instance, node);
                return true;
            }
        };
        for (Heap heap : mSnapshot.getHeaps()) {
            for (Instance instance : heap.getClasses()) {
                mapProcedure.execute(instance);
            }
            heap.forEachInstance(mapProcedure);
        }

        for (LinkEvalNode node : instanceNodeMap.values()) {
            node.finalize(instanceNodeMap);
        }

        Collection<RootObj> roots = snapshot.getGCRoots();
        Set<Instance> filteredRootInstances = new HashSet<Instance>(roots.size());
        for (RootObj root : roots) {
            Instance referredInstance = root.getReferredInstance();
            if (referredInstance != null) {
                filteredRootInstances.add(referredInstance);
            }
        }
        Instance[] gcRootInstances = filteredRootInstances
                .toArray(new Instance[filteredRootInstances.size()]);

        // Manually construct the sentinel root.
        LinkEvalNode[] rootNodes = new LinkEvalNode[gcRootInstances.length];
        for (int i = 0; i < rootNodes.length; ++i) {
            rootNodes[i] = instanceNodeMap.get(gcRootInstances[i]);
        }
        LinkEvalNode sentinelRootNode = new SentinelNode(Snapshot.SENTINEL_ROOT, instanceNodeMap,
                rootNodes);
        instanceNodeMap.put(Snapshot.SENTINEL_ROOT, sentinelRootNode);
        for (LinkEvalNode rootNode : rootNodes) {
            rootNode.setParent(sentinelRootNode);
            // Stuff the sentinel root into the back references of root object Nodes.
            LinkEvalNode[] backReferences = rootNode.getBackReferences();
            LinkEvalNode[] augmentedBackReferences = new LinkEvalNode[backReferences.length + 1];
            System.arraycopy(backReferences, 0, augmentedBackReferences, 1, backReferences.length);
            augmentedBackReferences[0] = sentinelRootNode;
            rootNode.setBackReferences(augmentedBackReferences);
        }

        mNodes = new ArrayList<LinkEvalNode>();
        depthFirstSearch(sentinelRootNode);
        mNodes.trimToSize();

        mLinkEval = new LinkEval();
    }

    @NonNull
    @Override
    public ComputationProgress getComputationProgress() {
        String progressMessage;
        double progress;
        if (mSemiDominatorProgress < mNodes.size()) {
            progressMessage = String
                    .format("Calculating semi-dominators %d/%d", mSemiDominatorProgress,
                            mNodes.size());
            progress = 0.5 * (double) mSemiDominatorProgress / (double) mNodes.size();
        } else {
            progressMessage = String
                    .format("Calculating immediate dominators %d/%d", mDominatorProgress,
                            mNodes.size());
            progress = 0.5 + 0.5 * (double) mDominatorProgress / (double) mNodes.size();
        }
        mCurrentProgress.setMessage(progressMessage);
        mCurrentProgress.setProgress(progress);
        return mCurrentProgress;
    }

    @Override
    public void computeDominators() {
        for (int i = mNodes.size() - 1; i > 0; --i, mSemiDominatorProgress = mNodes.size() - i) {
            LinkEvalNode currentNode = mNodes.get(i);

            // Step 2 of paper.
            for (LinkEvalNode predecessor : currentNode.getBackReferences()) {
                LinkEvalNode u = mLinkEval.eval(predecessor);
                if (u.getSemiDominator().getTopologicalOrder() < currentNode.getSemiDominator()
                        .getTopologicalOrder()) {
                    currentNode.setSemiDominator(u.getSemiDominator());
                }
            }

            currentNode.getSemiDominator().getDominates().add(currentNode);
            LinkEvalNode parent = currentNode.getParent();
            assert parent != null;
            LinkEval.link(parent, currentNode);

            // Step 3 of paper.
            // Manual recursion-to-loop conversion of the following code:
            //
            //for (Iterator<TarjanNode> it = parent.getDominates().iterator(); it.hasNext();) {
            //    TarjanNode dominatedNode = it.next();
            //    it.remove();
            //    TarjanNode u = LinkEval.eval(dominatedNode);
            //    dominatedNode.setImmediateDominator(
            //        u.getSemiDominator().getTopologicalOrder() <
            //            dominatedNode.getSemiDominator().getTopologicalOrder() ? u : parent);
            //}

            for (LinkEvalNode node : parent.getDominates()) {
                LinkEvalNode u = mLinkEval.eval(node);
                node.setImmediateDominator(
                        u.getSemiDominator().getTopologicalOrder() < node.getSemiDominator()
                                .getTopologicalOrder() ? u : parent);
            }
            parent.getDominates().clear(); // Bulk remove (slightly different from paper).
            parent.getDominates().trimToSize();
        }

        // Step 4 of paper.
        for (int i = 1; i < mNodes.size(); ++i) {
            LinkEvalNode currentNode = mNodes.get(i);
            if (currentNode.getImmediateDominator() != currentNode.getSemiDominator()) {
                LinkEvalNode dominator = currentNode.getImmediateDominator();
                assert dominator != null;
                assert dominator.getImmediateDominator() != null;
                currentNode.setImmediateDominator(dominator.getImmediateDominator());
            }
            mDominatorProgress = i;
        }
    }

    /**
     * Depth-first search in loop form, since the recursive version blows the stack.
     */
    private void depthFirstSearch(@NonNull LinkEvalNode root) {
        // Manual recursion-to-loop conversion of the following code:
        //
        // private int depthFirstSearch(int topologicalOrder, @NonNull LinkEvalNode currentNode) {
        //    currentNode.setTopologicalOrder(topologicalOrder);
        //    ++topologicalOrder;
        //    mNodes.add(currentNode);
        //    currentNode.setSemiDominator(currentNode);
        //    for (LinkEvalNode forwardReference : currentNode.getForwardReferences()) {
        //        if (forwardReference.getSemiDominator() == null) {
        //            forwardReference.setParent(currentNode);
        //            topologicalOrder = depthFirstSearch(topologicalOrder, forwardReference);
        //        }
        //    }
        //    return topologicalOrder;
        //}

        Stack<LinkEvalNode> nodeStack = new Stack<LinkEvalNode>();
        TIntStack childOffsetStack = new TIntStack();
        int topologicalOrder = 0;

        LinkEvalNode currentNode;
        int currentChildOffset;

        nodeStack.push(root);
        childOffsetStack.push(0);

        while (!nodeStack.empty()) {
            currentNode = nodeStack.pop();
            currentChildOffset = childOffsetStack.pop();

            if (currentNode.getSemiDominator() == null) {
                currentNode.setTopologicalOrder(topologicalOrder++);
                currentNode.setSemiDominator(currentNode);
                mNodes.add(currentNode);
            }

            LinkEvalNode[] forwardReferences = currentNode.getForwardReferences();
            while (currentChildOffset < forwardReferences.length) {
                LinkEvalNode successor = forwardReferences[currentChildOffset];
                if (successor.getSemiDominator() == null) {
                    successor.setParent(currentNode);
                    nodeStack.push(currentNode);
                    childOffsetStack.push(currentChildOffset + 1);
                    nodeStack.push(successor);
                    childOffsetStack.push(0);
                    break;
                }
                ++currentChildOffset;
            }
        }
    }

    protected static class LinkEval {
        @NonNull
        private List<LinkEvalNode> mCompressArray = new ArrayList<LinkEvalNode>();

        public static void link(@NonNull LinkEvalNode ancestor, @NonNull LinkEvalNode child) {
            child.setAncestor(ancestor);
        }

        //private static void compress(@NonNull LinkEvalNode node) {
        //  assert node.getAncestor() != null;
        //  if (node.getAncestor().getAncestor() != null) {
        //    compressBackup(node.getAncestor());
        //    if (node.getAncestor().getLabel().getSemiDominator().getTopologicalOrder() <
        //        node.getLabel().getSemiDominator().getTopologicalOrder()) {
        //      node.setLabel(node.getAncestor().getLabel());
        //    }
        //    node.setAncestor(node.getAncestor().getAncestor());
        //  }
        //}

        private void compress(@NonNull LinkEvalNode node) {
            assert mCompressArray.isEmpty();
            assert node.getAncestor() != null;
            while (node.getAncestor().getAncestor() != null) {
                mCompressArray.add(node);
                node = node.getAncestor();
                assert node.getAncestor() != null;
            }

            for (LinkEvalNode toCompress : Lists.reverse(mCompressArray)) {
                LinkEvalNode ancestor = toCompress.getAncestor();
                assert ancestor != null;
                if (ancestor.getLabel().getSemiDominator().getTopologicalOrder() <
                        toCompress.getLabel().getSemiDominator().getTopologicalOrder()) {
                    toCompress.setLabel(ancestor.getLabel());
                }
                toCompress.setAncestor(ancestor.getAncestor());
            }
            mCompressArray.clear();
        }

        public LinkEvalNode eval(@NonNull LinkEvalNode node) {
            if (node.getAncestor() == null) {
                return node;
            } else {
                compress(node);
                return node.getLabel();
            }
        }
    }

    protected static class LinkEvalNode {
        // A map from Node to Instances to map data within Instances to Nodes.
        protected Map<Instance, LinkEvalNode> mInstanceLookup;

        @NonNull
        protected Instance mInstance;

        @NonNull
        protected LinkEvalNode[] mForwardReferences;

        @NonNull
        protected LinkEvalNode[] mBackReferences;

        // The semi-dominator of this node.
        protected LinkEvalNode mSemiDominator;

        // The parent node in the DFS spanning tree.
        @Nullable
        protected LinkEvalNode mParent;

        @Nullable
        protected LinkEvalNode mAncestor;

        @NonNull
        protected LinkEvalNode mLabel;

        // The nodes for which this node is the semi-dominator of.
        protected ArrayList<LinkEvalNode> mSemisDominated;

        public LinkEvalNode(@NonNull Instance instance) {
            mInstance = instance;
            mInstance.setTopologicalOrder(0);
            mSemiDominator = null;
            mParent = null;
            mAncestor = null;
            mLabel = this;
            mSemisDominated = new ArrayList<LinkEvalNode>(1);
        }

        public final Instance getInstance() {
            return mInstance;
        }

        public LinkEvalNode[] getForwardReferences() {
            return mForwardReferences;
        }

        public LinkEvalNode[] getBackReferences() {
            return mBackReferences;
        }

        public void setBackReferences(@NonNull LinkEvalNode[] backReferences) {
            mBackReferences = backReferences;
        }

        public void finalize(@NonNull Map<Instance, LinkEvalNode> instanceLookup) {
            mInstanceLookup = instanceLookup;
            mForwardReferences = new LinkEvalNode[mInstance.getHardForwardReferences().size()];

            int i = 0;
            for (Instance instance : mInstance.getHardForwardReferences()) {
                mForwardReferences[i++] = instanceLookup.get(instance);
            }

            // Filter reverse reference list for unreachable nodes.
            List<LinkEvalNode> backReferenceInstances = new ArrayList<LinkEvalNode>(
                    mInstance.getHardReverseReferences().size());
            for (Instance instance : mInstance.getHardReverseReferences()) {
                if (instance.isReachable()) {
                    backReferenceInstances.add(instanceLookup.get(instance));
                }
            }
            mBackReferences = backReferenceInstances
                    .toArray(new LinkEvalNode[backReferenceInstances.size()]);
        }

        public final void setImmediateDominator(@NonNull LinkEvalNode node) {
            mInstance.setImmediateDominator(node.getInstance());
        }

        @Nullable
        public final LinkEvalNode getImmediateDominator() {
            return mInstanceLookup.get(mInstance.getImmediateDominator());
        }

        public final int getTopologicalOrder() {
            return mInstance.getTopologicalOrder();
        }

        public void setSemiDominator(@NonNull LinkEvalNode node) {
            mSemiDominator = node;
        }

        public LinkEvalNode getSemiDominator() {
            return mSemiDominator;
        }

        @Nullable
        public LinkEvalNode getParent() {
            return mParent;
        }

        public void setParent(@NonNull LinkEvalNode parent) {
            mParent = parent;
        }

        @Nullable
        public LinkEvalNode getAncestor() {
            return mAncestor;
        }

        public void setAncestor(@NonNull LinkEvalNode ancestor) {
            mAncestor = ancestor;
        }

        @NonNull
        public LinkEvalNode getLabel() {
            return mLabel;
        }

        public void setLabel(@NonNull LinkEvalNode node) {
            mLabel = node;
        }

        public void setTopologicalOrder(int order) {
            mInstance.setTopologicalOrder(order);
            mSemiDominator = this;
        }

        public ArrayList<LinkEvalNode> getDominates() {
            return mSemisDominated;
        }
    }

    protected static class SentinelNode extends LinkEvalNode {

        public SentinelNode(@NonNull Instance instance,
                @NonNull Map<Instance, LinkEvalNode> instanceLookup,
                @NonNull LinkEvalNode[] roots) {
            super(instance);
            mInstanceLookup = instanceLookup;
            mForwardReferences = roots;
            mBackReferences = new LinkEvalNode[0];
        }

        @Override
        public final void finalize(@NonNull Map<Instance, LinkEvalNode> instanceLookup) {
            throw new RuntimeException("This method should not be called.");
        }
    }
}
