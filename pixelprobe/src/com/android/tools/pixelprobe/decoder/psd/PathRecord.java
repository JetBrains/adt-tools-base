/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.tools.pixelprobe.decoder.psd;

import com.android.tools.chunkio.Chunk;
import com.android.tools.chunkio.Chunked;

/**
 * A path records is either a single point in a path, a subpath
 * marker or a command. A subpath can be closed or open.
 */
@Chunked
final class PathRecord {
    // Marks the beginning of a closed subpath
    static final int CLOSED_SUBPATH_LENGTH = 0;
    // Linked/unlinked matters only to interactive editing
    // Linked just means that the two control points of a knot
    // move together when one of them moves
    static final int CLOSED_SUBPATH_KNOT_LINKED = 1;
    static final int CLOSED_SUBPATH_KNOT_UNLINKED = 2;
    // Marks the beginning of an open subpath
    static final int OPEN_SUBPATH_LENGTH = 3;
    static final int OPEN_SUBPATH_KNOT_LINKED = 4;
    static final int OPEN_SUBPATH_KNOT_UNLINKED = 5;
    // Photoshop only deal with even/odd fill rules, we can ignore it
    static final int PATH_FILL_RULE = 6;
    // Not sure what this does
    static final int CLIPBOARD = 7;
    // Initial fill rule, always present as first item in the list
    // of path records
    static final int INITIAL_FILL_RULE = 8;

    /**
     * A curve (or path) is made of a series of Bézier knot.
     * Each knot is made of an anchor (point on the curve/path)
     * and of two control points. One defines the slope of the
     * curve before the anchor, the other the slope of the curve
     * after the anchor.
     */
    @Chunked
    static final class BezierKnot {
        @Chunk
        int controlEnterY;
        @Chunk
        int controlEnterX;
        @Chunk
        int anchorY;
        @Chunk
        int anchorX;
        @Chunk
        int controlExitY;
        @Chunk
        int controlExitX;
    }

    // Indicates the path record type
    @Chunk
    short selector;

    @Chunk(byteCount = 24,
        switchType = {
            @Chunk.Case(test = "pathRecord.selector == 0 || pathRecord.selector == 3",
                    type = int.class),
            @Chunk.Case(test = "pathRecord.selector == 1 || pathRecord.selector == 2 || " +
                    "pathRecord.selector == 4 || pathRecord.selector == 5",
                    type = BezierKnot.class)
        }
    )
    Object data;
}
