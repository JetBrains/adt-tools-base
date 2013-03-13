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

/** A segment type describes the different roles or positions a segment can have in a node
 * <p>
 * <b>NOTE: This is not a public or final API; if you rely on this be prepared
 * to adjust your code for the next tools release.</b>
 */
@Beta
public enum SegmentType {
    /** Segment is on the left edge */
    @NonNull LEFT,
    /** Segment is on the top edge */
    @NonNull TOP,
    /** Segment is on the right edge */
    @NonNull RIGHT,
    /** Segment is on the bottom edge */
    @NonNull BOTTOM,
    /** Segment is along the baseline */
    @NonNull BASELINE,
    /** Segment is along the center vertically */
    @NonNull CENTER_VERTICAL,
    /** Segment is along the center horizontally */
    @NonNull CENTER_HORIZONTAL,
    /** Segment is on an unknown edge */
    @NonNull UNKNOWN;

    public boolean isHorizontal() {
        return this == TOP || this == BOTTOM || this == BASELINE || this == CENTER_HORIZONTAL;
    }

    /**
     * Returns the X coordinate for an edge of this type given its bounds
     *
     * @param node the node containing the edge
     * @param bounds the bounds of the node
     * @return the X coordinate for an edge of this type given its bounds
     */
    public int getX(@Nullable INode node, @NonNull Rect bounds) {
        // We pass in the bounds rather than look it up via node.getBounds() because
        // during a resize or move operation, we call this method to look up proposed
        // bounds rather than actual bounds
        switch (this) {
            case RIGHT:
                return bounds.x + bounds.w;
            case TOP:
            case BOTTOM:
            case CENTER_VERTICAL:
                return bounds.x + bounds.w / 2;
            case UNKNOWN:
                assert false;
                return bounds.x;
            case LEFT:
            case BASELINE:
            default:
                return bounds.x;
        }
    }

    /**
     * Returns the Y coordinate for an edge of this type given its bounds
     *
     * @param node the node containing the edge
     * @param bounds the bounds of the node
     * @return the Y coordinate for an edge of this type given its bounds
     */
    public int getY(@Nullable INode node, @NonNull Rect bounds) {
        switch (this) {
            case TOP:
                return bounds.y;
            case BOTTOM:
                return bounds.y + bounds.h;
            case BASELINE: {
                int baseline = node != null ? node.getBaseline() : -1;
                if (node == null) {
                    // This happens when you are dragging an element and we don't have
                    // a node (only an IDragElement) such as on a palette drag.
                    // For now just hack it.
                    baseline = (int) (bounds.h * 0.8f); // HACK
                }
                return bounds.y + baseline;
            }
            case UNKNOWN:
                assert false;
                return bounds.y;
            case RIGHT:
            case LEFT:
            case CENTER_HORIZONTAL:
            default:
                return bounds.y + bounds.h / 2;
        }
    }

    @Override
    public String toString() {
        return name();
    }
}
