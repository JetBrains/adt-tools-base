/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.builder.png;

import com.android.annotations.NonNull;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Builder for the Layout Bound chunk.
 */
class LayoutBoundChunkBuilder {

    /**
     * Chunk Type for the layout bound chunk.
     * This is part of the 9-patch 'spec' (if there was one).
     */
    private static final byte[] sChunkType = new byte[] { 'n', 'p', 'L', 'b' };

    private final int mLeft;
    private final int mTop;
    private final int mRight;
    private final int mBottom;

    LayoutBoundChunkBuilder(int left, int top, int right, int bottom) {
        mLeft = left;
        mTop = top;
        mRight = right;
        mBottom = bottom;
    }

    /**
     * Creates and returns a {@link com.android.builder.png.Chunk}
     */
    @NonNull
    Chunk getChunk() {
        ByteBuffer buffer = ByteBuffer.allocate(4 * Integer.SIZE/8);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(mLeft);
        buffer.putInt(mTop);
        buffer.putInt(mRight);
        buffer.putInt(mBottom);

        return new Chunk(sChunkType, buffer.array());
    }
}
