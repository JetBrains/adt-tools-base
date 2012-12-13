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

import com.android.annotations.Nullable;
import com.google.common.annotations.Beta;

/**
 * Structure returned by onDropEnter/Move and passed to over onDropXyz methods.
 * <p>
 * <b>NOTE: This is not a public or final API; if you rely on this be prepared
 * to adjust your code for the next tools release.</b>
 * </p>
 */
@Beta
public class DropFeedback {
    /**
     * User data that the rule can use in any way it wants to carry state from one
     * operation to another.
     * <p/>
     * Filled and owned by the view rule.
     */
    @Nullable
    public Object userData;

    /**
     * If true the next screen update will invoke the paint callback.
     * <p/>
     * Filled by the view rule to request a paint, and reset by the canvas after
     * the paint occurred.
     */
    public boolean requestPaint;

    /**
     * Set to false by the engine when entering a new view target.
     * The view rule should set this to true if the current view target is not
     * a valid drop zone.
     * <p/>
     * When set to true, the onDropped() method will not be called if the user releases
     * the mouse button. Depending on the platform or implementation, the mouse cursor
     * <em>may</em> reflect that the drop operation is invalid.
     * <p/>
     * Rationale: an operation like onDropEnter() is called each time the mouse enters
     * a new view target and is supposed to return null when the drop cannot happen
     * <em>at all</em> in that target. However a layout like RelativeLayout decorates
     * potential targets with "hot spots" that are suitable drop zones, whereas some
     * other parts of the view are not suitable drop zones. In this case the onDropEnter()
     * or onDropMove() operation would return a {@link DropFeedback} with
     * <code>invalidTarget=true</code>.
     */
    public boolean invalidTarget;

    /**
     * Painter invoked by the canvas to paint the feedback.
     * Filled by the view rule, called by the engine.
     * <p/>
     */
    @Nullable
    public IFeedbackPainter painter;

    /**
     * When set to a non-null valid rectangle, this informs the engine that a drag'n'drop
     * feedback wants to capture the mouse as long as it stays in the given area.
     * <p/>
     * When the mouse is captured, drop events will keep going to the rule that started the
     * capture and the current INode proxy will not change.
     * <p/>
     * Filled by the view rule, read by the engine.
     */
    @Nullable
    public Rect captureArea;

    /**
     * Set to true by the drag'n'drop engine when the current drag operation is a copy.
     * When false the operation is a move and <em>after</em> a successful drop the source
     * elements will be deleted.
     * <p/>
     * Filled by the engine, read by view rule.
     */
    public boolean isCopy;

    /**
     * The bounds of the drag, relative to the starting mouse position. For example, if
     * you have a rectangular view of size 100x80, and you start dragging at position
     * (15,20) from the top left corner of this rectangle, then the drag bounds would be
     * (-15,-20, 100x80).
     * <p>
     * NOTE: The coordinate units will be in layout/view coordinates. In other words, they
     * are unaffected by the canvas zoom.
     */
    @Nullable
    public Rect dragBounds;

    /**
     * The baseline of the primary dragged view. -1 means that the view does not have a baseline.
     */
    public int dragBaseline = -1;

    /**
     * Set to true when the drag'n'drop starts and ends in the same canvas of the
     * same Eclipse instance.
     * <p/>
     * Filled by the engine, read by view rule.
     */
    public boolean sameCanvas;

    /**
     * Density scale for pixels. To compute the dip (device independent pixel) in the
     * view from a layout coordinate, apply this scale.
     */
    public double dipScale = 1.0;

    /**
     * Initializes the drop feedback with the given user data and paint
     * callback. A paint is requested if the paint callback is non-null.
     *
     * @param userData Data stored for later retrieval by the client
     * @param painter A callback invoked to paint the drop feedback
     */
    public DropFeedback(@Nullable Object userData, @Nullable IFeedbackPainter painter) {
        this.userData = userData;
        this.painter = painter;
        this.requestPaint = painter != null;
        this.captureArea = null;
    }

    /**
     * A message to be displayed to the user, if any. Should not contain line separators.
     */
    @Nullable
    public String message;

    /**
     * An error message to be displayed to the user, if any. Should not contain line
     * separators.
     */
    @Nullable
    public String errorMessage;

    /**
     * A message to be displayed in a tooltip to the user, which should be short, but
     * can be multiple lines (use embedded newlines)
     */
    @Nullable
    public String tooltip;

    /**
     * Horizontal alignment for the tooltip, or null if no preference
     */
    @Nullable
    public SegmentType tooltipX;

    /**
     * Vertical alignment for the tooltip, or null if no preference
     */
    @Nullable
    public SegmentType tooltipY;

    /**
     * A mask of the currently held keyboard modifier keys - some combination of
     * {@link #MODIFIER1}, {@link #MODIFIER2}, {@link #MODIFIER3}, or none.
     */
    public int modifierMask;

    /** Bitmask value for modifier key 1 (Control on Windows/Linux, Command on Mac, etc) */
    public static final int MODIFIER1 = 1;
    /** Bitmask value for modifier key 2 (Shift) */
    public static final int MODIFIER2 = 2;
    /** Bitmask value for modifier key 3 (Alt on Windows/Linux, Option on Mac, etc) */
    public static final int MODIFIER3 = 4;
}
