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

import com.google.common.annotations.Beta;
import com.android.annotations.NonNull;

/**
 * Set of margins - distances to outer left, top, right and bottom edges. These objects
 * can be used for both actual <b>margins</b> as well as insets - and in general any
 * deltas to the bounds of a rectangle.
 * <p>
 * <b>NOTE: This is not a public or final API; if you rely on this be prepared
 * to adjust your code for the next tools release.</b>
 */
@Beta
public class Margins {
    /** The left margin */
    public final int left;

    /** The right margin */
    public final int right;

    /** The top margin */
    public final int top;

    /** The bottom margin */
    public final int bottom;

    /**
     * Creates a new {@link Margins} instance.
     *
     * @param left the left side margin
     * @param right the right side margin
     * @param top the top margin
     * @param bottom the bottom margin
     */
    public Margins(int left, int right, int top, int bottom) {
        super();
        this.left = left;
        this.right = right;
        this.top = top;
        this.bottom = bottom;
    }

    @NonNull
    @Override
    public String toString() {
        return "Margins [left=" + left + ", right=" + right + ", top=" + top + ", bottom=" + bottom
                + "]";
    }
}
