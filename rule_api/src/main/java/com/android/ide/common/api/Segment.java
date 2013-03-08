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

/**
 * A segment is a straight horizontal or vertical line between two points, typically an
 * edge of a node but also possibly some internal segment like a baseline or a center
 * line, and it can be offset by a margin from the node's visible bounds.
 * <p>
 * <b>NOTE: This is not a public or final API; if you rely on this be prepared
 * to adjust your code for the next tools release.</b>
 */
@Beta
public class Segment {
    /** For horizontal lines, the y coordinate; for vertical lines the x */
    public final int at;

    /** The starting coordinate along the line */
    public final int from;

    /** The ending coordinate along the line */
    public final int to;

    /** Whether the edge is a top edge, a baseline edge, a left edge, etc */
    @NonNull
    public final SegmentType edgeType;

    /**
     * Whether the edge is offset from the node by a margin or not, or whether it has no
     * margin
     */
    @NonNull
    public final MarginType marginType;

    /** The node that contains this edge */
    @Nullable
    public final INode node;

    /**
     * The id of the node. May be null (in which case id should be generated when
     * move/resize is completed
     */
    @Nullable
    public final String id;

    public Segment(int at, int from, int to, @Nullable INode node, @Nullable String id,
            @NonNull SegmentType edgeType, @NonNull MarginType marginType) {
        this.at = at;
        this.from = from;
        this.to = to;
        this.node = node;
        this.id = id;
        this.edgeType = edgeType;
        this.marginType = marginType;
    }

    @NonNull
    @Override
    public String toString() {
        String nodeStr = node == null ? "null" : node.getFqcn().substring(
                node.getFqcn().lastIndexOf(('.')) + 1);
        return "Segment [edgeType=" + edgeType + ", node=" + nodeStr + ", at=" + at + ", id=" + id
                + ", from=" + from + ", to=" + to + ", marginType=" + marginType + "]";
    }
}
