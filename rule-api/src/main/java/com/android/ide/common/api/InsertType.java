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
 * An enumerated type of different insertion events, such as an insertion from a
 * copy/paste operation or as the first half of a move operation.
 * <p>
 * <b>NOTE: This is not a public or final API; if you rely on this be prepared
 * to adjust your code for the next tools release.</b>
 */
@Beta
public enum InsertType {
    /** The view is newly created (by for example a palette drag) */
    CREATE,

    /**
     * Same as {@link #CREATE} but when the views are constructed for previewing, for
     * example as part of a palette drag.
     */
    CREATE_PREVIEW,

    /** The view is being inserted here because it was moved from somewhere else within
     * the same layout */
    MOVE_WITHIN,

    /** The view is being inserted here because it was moved from some other layout */
    MOVE_INTO,

    /**
     * The view is being inserted here as a result of a copy/paste from elsewhere
     * (including drags, but not from the palette)
     */
    PASTE;

    /**
     * Returns true if this insert type is for a newly created view (for example a by
     * palette drag). Note that this includes both normal create events as well as well as
     * views created as part of previewing operations.
     *
     * @return true if this {@link InsertType} is for a newly created view
     */
    public boolean isCreate() {
        return this == CREATE || this == CREATE_PREVIEW;
    }
}
