/*
 * Copyright (C) 2010 The Android Open Source Project
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

import com.google.common.annotations.Beta;

/**
 * Drawing styles are used to distinguish the visual appearance of selection,
 * hovers, anchors, etc. Each style may have different colors, line thickness,
 * dashing style, transparency, etc.
 * <p>
 * <b>NOTE: This is not a public or final API; if you rely on this be prepared
 * to adjust your code for the next tools release.</b>
 * </p>
 */
@Beta
public enum DrawingStyle {
    /**
     * The style used to draw the selected views
     */
    SELECTION,

    /**
     * The style used to draw guidelines - overlay lines which indicate
     * significant geometric positions.
     */
    GUIDELINE,

    /**
     * The style used to guideline shadows
     */
    GUIDELINE_SHADOW,

    /**
     * The style used to draw guidelines, in particular shared edges and center lines; this
     * is a dashed edge.
     */
    GUIDELINE_DASHED,

    /**
     * The style used to draw distance annotations
     */
    DISTANCE,

    /**
     * The style used to draw grids
     */
    GRID,

    /**
     * The style used for hovered views (e.g. when the mouse is directly on top
     * of the view)
     */
    HOVER,

    /**
     * The style used for hovered views (e.g. when the mouse is directly on top
     * of the view), when the hover happens to be the same object as the selection
     */
    HOVER_SELECTION,

    /**
     * The style used to draw anchors (lines to the other views the given view
     * is anchored to)
     */
    ANCHOR,

    /**
     * The style used to draw outlines (the structure of views)
     */
    OUTLINE,

    /**
     * The style used to draw the recipient/target View of a drop. This is
     * typically going to be the bounding-box of the view into which you are
     * adding a new child.
     */
    DROP_RECIPIENT,

    /**
     * The style used to draw a potential drop area <b>within</b> a
     * {@link #DROP_RECIPIENT}. For example, if you are dragging into a view
     * with a LinearLayout, the {@link #DROP_RECIPIENT} will be the view itself,
     * whereas each possible insert position between two children will be a
     * {@link #DROP_ZONE}. If the mouse is over a {@link #DROP_ZONE} it should
     * be drawn using the style {@link #DROP_ZONE_ACTIVE}.
     */
    DROP_ZONE,

    /**
     * The style used to draw a currently active drop zone within a drop
     * recipient. See the documentation for {@link #DROP_ZONE} for details on
     * the distinction between {@link #DROP_RECIPIENT}, {@link #DROP_ZONE} and
     * {@link #DROP_ZONE_ACTIVE}.
     */
    DROP_ZONE_ACTIVE,

    /**
     * The style used to draw a preview of where a dropped view would appear if
     * it were to be dropped at a given location.
     */
    DROP_PREVIEW,

    /**
     * The style used to preview a resize operation. Similar to {@link #DROP_PREVIEW}
     * but usually fainter to work better in combination with guidelines which
     * are often overlaid during resize.
     */
    RESIZE_PREVIEW,

    /**
     * The style used to show a proposed resize bound which is being rejected (for example,
     * because there is no near edge to attach to in a RelativeLayout).
     */
    RESIZE_FAIL,

    /**
     * The style used to draw help/hint text.
     */
    HELP,

    /**
     * The style used to draw illegal/error/invalid markers
     */
    INVALID,

    /**
     * The style used to highlight dependencies
     */
    DEPENDENCY,

    /**
     * The style used to draw an invalid cycle
     */
    CYCLE,

    /**
     * The style used to highlight the currently dragged views during a layout
     * move (if they are not hidden)
     */
    DRAGGED,

    /**
     * The style used to draw empty containers of zero bounds (which are padded
     * a bit to make them visible during a drag or selection).
     */
    EMPTY,

    /**
     * A style used for unspecified purposes; can be used by a client to have
     * yet another color that is domain specific; using this color constant
     * rather than your own hardcoded value means that you will be guaranteed to
     * pick up a color that is themed properly and will look decent with the
     * rest of the colors
     */
    CUSTOM1,

    /**
     * A second styled used for unspecified purposes; see {@link #CUSTOM1} for
     * details.
     */
    CUSTOM2
}
