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
import com.android.annotations.VisibleForTesting;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

/**
 * Builder for the NinePatch chunk.
 */
class NinePatchChunkBuilder {

    /**
     * Chunk Type for the chunk containing the 9-patch info.
     * This is part of the 9-patch 'spec' (if there was one).
     */
    private static final byte[] sChunkType = new byte[] { 'n', 'p', 'T', 'c' };

    private final int mPaddingLeft;
    private final int mPaddingRight;
    private final int mPaddingTop;
    private final int mPaddingBottom;

    @NonNull
    private final byte[] mXDivs;
    @NonNull
    private final byte[] mYDivs;
    @NonNull
    private final byte [] mColors;

    NinePatchChunkBuilder(@NonNull int[] xDivs, int numXDivs, @NonNull int[] yDivs, int numYDivs,
            @NonNull int[] colors,
            int paddingLeft, int paddingRight, int paddingTop, int paddingBottom) {
        // fill the bytes array from the int array
        mXDivs = intToByteArray(xDivs, numXDivs);
        mYDivs = intToByteArray(yDivs, numYDivs);
        mColors = intToByteArray(colors, colors.length);

        mPaddingLeft = paddingLeft;
        mPaddingRight = paddingRight;
        mPaddingTop = paddingTop;
        mPaddingBottom = paddingBottom;
    }

    @VisibleForTesting
    @NonNull
    static byte[] intToByteArray(@NonNull int[] array, int length) {
        byte[] byteArray = new byte[length * 4];

        ByteBuffer byteBuffer = ByteBuffer.wrap(byteArray);
        IntBuffer intBuffer = byteBuffer.asIntBuffer();

        intBuffer.put(array, 0, length);

        return byteArray;
    }

    /**
     * Creates and returns a {@link com.android.builder.png.Chunk}
     */
    @NonNull
    Chunk getChunk() {
        int size = computeSize();
        ByteBuffer buffer = ByteBuffer.allocate(size);

        buffer.put((byte) 0); // was deserialized
        buffer.put((byte) (mXDivs.length / 4));
        buffer.put((byte) (mYDivs.length / 4));
        buffer.put((byte) (mColors.length / 4));

        // skip the pointers.
        buffer.putInt(0);
        buffer.putInt(0);

        buffer.putInt(mPaddingLeft);
        buffer.putInt(mPaddingRight);
        buffer.putInt(mPaddingTop);
        buffer.putInt(mPaddingBottom);

        // skip more pointers
        buffer.putInt(0);

        buffer.put(mXDivs);
        buffer.put(mYDivs);
        buffer.put(mColors);

        return new Chunk(sChunkType, buffer.array());
    }

    private int computeSize() {
        // The size of this struct is 32 bytes on the 32-bit target system
        // 4 * int8_t
        // 4 * int32_t
        // 3 * pointer

        return 32 + mXDivs.length + mYDivs.length + mColors.length;
    }
}
