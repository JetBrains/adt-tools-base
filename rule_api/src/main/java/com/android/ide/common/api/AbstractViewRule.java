/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Eclipse Public License, Version 1.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.eclipse.org/org/documents/epl-v10.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.ide.common.api;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.common.annotations.Beta;

import java.util.List;

/**
 * Default implementation of an {@link IViewRule}. This is a convenience
 * implementation which makes it easier to supply designtime behavior for a
 * custom view and just override the methods you are interested in.
 * <p>
 * <b>NOTE: This is not a public or final API; if you rely on this be prepared
 * to adjust your code for the next tools release.</b>
 */
@Beta
public class AbstractViewRule implements IViewRule {
    @Override
    public boolean onInitialize(@NonNull String fqcn, @NonNull IClientRulesEngine engine) {
        return true;
    }

    @Override
    public void onDispose() {
    }

    @Override
    @Nullable
    public String getDisplayName() {
        // Default is to not override the selection display name.
        return null;
    }

    @Override
    @Nullable
    public List<String> getSelectionHint(@NonNull INode parentNode, @NonNull INode childNode) {
        return null;
    }

    @Override
    public void addLayoutActions(@NonNull List<RuleAction> actions, @NonNull INode parentNode,
            @NonNull List<? extends INode> children) {
    }

    @Override
    public void addContextMenuActions(@NonNull List<RuleAction> actions, @NonNull INode node) {
    }

    @Override
    @Nullable
    public String getDefaultActionId(@NonNull INode node) {
        return null;
    }

    @Override
    public void paintSelectionFeedback(@NonNull IGraphics graphics, @NonNull INode parentNode,
            @NonNull List<? extends INode> childNodes, @Nullable Object view) {
    }

    @Override
    @Nullable
    public DropFeedback onDropEnter(@NonNull INode targetNode, @Nullable Object targetView,
            @Nullable IDragElement[] elements) {
        return null;
    }

    @Override
    @Nullable
    public DropFeedback onDropMove(@NonNull INode targetNode, @NonNull IDragElement[] elements,
            @Nullable DropFeedback feedback, @NonNull Point p) {
        return null;
    }

    @Override
    public void onDropLeave(@NonNull INode targetNode, @NonNull IDragElement[] elements,
            @Nullable DropFeedback feedback) {
        // ignore
    }

    @Override
    public void onDropped(
            @NonNull INode targetNode,
            @NonNull IDragElement[] elements,
            @Nullable DropFeedback feedback,
            @NonNull Point p) {
        // ignore
    }


    @Override
    public void onPaste(@NonNull INode targetNode, @Nullable Object targetView,
            @NonNull IDragElement[] pastedElements) {
    }

    @Override
    public void onCreate(@NonNull INode node, @NonNull INode parent,
            @NonNull InsertType insertType) {
    }

    @Override
    public void onChildInserted(@NonNull INode child, @NonNull INode parent,
            @NonNull InsertType insertType) {
    }

    @Override
    public void onRemovingChildren(@NonNull List<INode> deleted, @NonNull INode parent,
            boolean moved) {
    }

    @Override
    @Nullable
    public DropFeedback onResizeBegin(@NonNull INode child, @NonNull INode parent,
            @Nullable SegmentType horizontalEdge,
            @Nullable SegmentType verticalEdge, @Nullable Object childView,
            @Nullable Object parentView) {
        return null;
    }

    @Override
    public void onResizeUpdate(@Nullable DropFeedback feedback, @NonNull INode child,
            @NonNull INode parent, @NonNull Rect newBounds,
            int modifierMask) {
    }

    @Override
    public void onResizeEnd(@Nullable DropFeedback feedback, @NonNull INode child,
            @NonNull INode parent, @NonNull Rect newBounds) {
    }
}
