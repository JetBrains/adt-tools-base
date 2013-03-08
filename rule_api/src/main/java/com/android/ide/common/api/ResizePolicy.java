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
import com.google.common.annotations.Beta;

/**
 * A {@link ResizePolicy} records state for whether a widget is resizable, and if so, in
 * which directions
 * <p>
 * <b>NOTE: This is not a public or final API; if you rely on this be prepared
 * to adjust your code for the next tools release.</b>
 */
@Beta
public class ResizePolicy {
    private static final int NONE = 0;
    private static final int LEFT_EDGE = 1;
    private static final int RIGHT_EDGE = 2;
    private static final int TOP_EDGE = 4;
    private static final int BOTTOM_EDGE = 8;
    private static final int PRESERVE_RATIO = 16;

    // Aliases
    private static final int HORIZONTAL = LEFT_EDGE | RIGHT_EDGE;
    private static final int VERTICAL = TOP_EDGE | BOTTOM_EDGE;
    private static final int ANY = HORIZONTAL | VERTICAL;

    // Shared objects for common policies

    private static final ResizePolicy sAny = new ResizePolicy(ANY);
    private static final ResizePolicy sNone = new ResizePolicy(NONE);
    private static final ResizePolicy sHorizontal = new ResizePolicy(HORIZONTAL);
    private static final ResizePolicy sVertical = new ResizePolicy(VERTICAL);
    private static final ResizePolicy sScaled = new ResizePolicy(ANY | PRESERVE_RATIO);

    private final int mFlags;


    // Use factory methods to construct
    private ResizePolicy(int flags) {
        mFlags = flags;
    }

    /**
     * Returns true if this policy allows resizing in at least one direction
     *
     * @return true if this policy allows resizing in at least one direction
     */
    public boolean isResizable() {
        return (mFlags & ANY) != 0;
    }

    /**
     * Returns true if this policy allows resizing the top edge
     *
     * @return true if this policy allows resizing the top edge
     */
    public boolean topAllowed() {
        return (mFlags & TOP_EDGE) != 0;
    }

    /**
     * Returns true if this policy allows resizing the right edge
     *
     * @return true if this policy allows resizing the right edge
     */
    public boolean rightAllowed() {
        return (mFlags & RIGHT_EDGE) != 0;
    }

    /**
     * Returns true if this policy allows resizing the bottom edge
     *
     * @return true if this policy allows resizing the bottom edge
     */
    public boolean bottomAllowed() {
        return (mFlags & BOTTOM_EDGE) != 0;
    }

    /**
     * Returns true if this policy allows resizing the left edge
     *
     * @return true if this policy allows resizing the left edge
     */
    public boolean leftAllowed() {
        return (mFlags & LEFT_EDGE) != 0;
    }

    /**
     * Returns true if this policy requires resizing in an aspect-ratio preserving manner
     *
     * @return true if this policy requires resizing in an aspect-ratio preserving manner
     */
    public boolean isAspectPreserving() {
        return (mFlags & PRESERVE_RATIO) != 0;
    }

    /**
     * Returns a resize policy allowing resizing in any direction
     *
     * @return a resize policy allowing resizing in any direction
     */
    @NonNull
    public static ResizePolicy full() {
        return sAny;
    }

    /**
     * Returns a resize policy not allowing any resizing
     *
     * @return a policy which does not allow any resizing
     */
    @NonNull
    public static ResizePolicy none() {
        return sNone;
    }

    /**
     * Returns a resize policy allowing horizontal resizing only
     *
     * @return a policy which allows horizontal resizing only
     */
    @NonNull
    public static ResizePolicy horizontal() {
        return sHorizontal;
    }

    /**
     * Returns a resize policy allowing vertical resizing only
     *
     * @return a policy which allows vertical resizing only
     */
    @NonNull
    public static ResizePolicy vertical() {
        return sVertical;
    }

    /**
     * Returns a resize policy allowing scaled / aspect-ratio preserving resizing only
     *
     * @return a resize policy allowing scaled / aspect-ratio preserving resizing only
     */
    @NonNull
    public static ResizePolicy scaled() {
        return sScaled;
    }

    /**
     * Returns a resize policy with the specified resizability along the edges and the
     * given aspect ratio behavior
     * @param top whether the top edge is resizable
     * @param right whether the right edge is resizable
     * @param bottom whether the bottom edge is resizable
     * @param left whether the left edge is resizable
     * @param preserve whether the policy requires the aspect ratio to be preserved
     * @return a resize policy recording the constraints required by the parameters
     */
    @NonNull
    public static ResizePolicy create(boolean top, boolean right, boolean bottom, boolean left,
            boolean preserve) {
        int mask = NONE;
        if (top) mask |= TOP_EDGE;
        if (right) mask |= RIGHT_EDGE;
        if (bottom) mask |= BOTTOM_EDGE;
        if (left) mask |= LEFT_EDGE;
        if (preserve) mask |= PRESERVE_RATIO;

        return new ResizePolicy(mask);
    }
}
