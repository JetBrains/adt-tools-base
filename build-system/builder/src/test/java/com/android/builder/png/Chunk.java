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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.google.common.base.Charsets;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.zip.CRC32;

/**
 * A Png Chunk.
 *
 * A chunk contains a 4-byte length, a 4-byte type, a number of bytes for the payload, and finally
 * a 4-byte CRC32.
 *
 * The length value is only the length of the payload. If there is no payload, it is 0, but
 * the type and CRC32 are still there.
 * Length is unsigned but max value is (2^31)-1.
 *
 * The CRC32 is computed on the type and payload but not length.
 *
 * Reference: http://tools.ietf.org/html/rfc2083#section-3.2
 *
 * The type of the chunk follows a very specific naming scheme.

 * Reference: http://tools.ietf.org/html/rfc2083#section-3.3
 *
 */
class Chunk {

    /** Chunk type for the Image-Header chunk. */
    public static final byte[] IHDR = new byte[] { 'I', 'H', 'D', 'R' };

    @NonNull
    private final byte[] mType;
    @Nullable
    private final byte[] mData;
    private final long mCrc32;

    @VisibleForTesting
    Chunk(@NonNull byte[] type, @Nullable byte[] data, long crc32) {
        checkNotNull(type);
        checkArgument(type.length == 4);

        mType = type;
        mData = data;
        mCrc32 = crc32;
    }

    Chunk(@NonNull byte[] type, @Nullable byte[] data) {
        this(type, data, computeCrc32(type, data));
    }

    Chunk(@NonNull byte[] type) {
        this(type, null);
    }

    /**
     * Return the length info about the chunk. This is the length that
     * is written in the PNG file.
     */
    int getDataLength() {
        return mData != null ? mData.length : 0;
    }

    /**
     * returns the size of the chunk in the file.
     */
    int size() {
        // 4 bytes for each length, type and crc32.
        // then add the data length.
        return 12 + getDataLength();
    }

    @NonNull
    byte[] getType() {
        return mType;
    }

    String getTypeAsString() {
        return new String(mType, Charsets.US_ASCII);
    }

    @Nullable
    byte[] getData() {
        return mData;
    }

    long getCrc32() {
        return mCrc32;
    }

    private static long computeCrc32(@NonNull byte[] type, @Nullable byte[] data) {
        CRC32 checksum = new CRC32();
        checksum.update(type);
        if (data != null) {
            checksum.update(data);
        }

        return checksum.getValue();
    }

    void write(@NonNull OutputStream outStream) throws IOException {
        ByteUtils utils = ByteUtils.Cache.get();

        //write the length
        outStream.write(utils.getIntAsArray(getDataLength()));

        // write the type
        outStream.write(mType);

        // write the data if applicable
        if (mData != null) {
            outStream.write(mData);
        }

        // write the CRC32. This is a long converted to a 8 byte array,
        // but we only care about the last 4 bytes.
        outStream.write(utils.getLongAsIntArray(mCrc32), 4, 4);
    }

    @Override
    public boolean equals(Object o) {

        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Chunk chunk = (Chunk) o;

        if (mCrc32 != chunk.mCrc32) {
            return false;
        }
        if (!Arrays.equals(mData, chunk.mData)) {
            return false;
        }
        if (!Arrays.equals(mType, chunk.mType)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(mType);
        result = 31 * result + (mData != null ? Arrays.hashCode(mData) : 0);
        result = 31 * result + (int) (mCrc32 ^ (mCrc32 >>> 32));
        return result;
    }

    @Override
    public String toString() {
        if (Arrays.equals(mType, IHDR)) {
            ByteBuffer buffer = ByteBuffer.wrap(mData);
            return "Chunk{" +
                    "mType=" + getTypeAsString() +
                    ", mData=" + buffer.getInt() + "x" + buffer.getInt() + ":" + buffer.get() +
                            "-" + buffer.get() + "-" + buffer.get() + "-" + buffer.get() +
                            "-" + buffer.get() +
                    '}';
        }

        return "Chunk{" +
                "mType=" + getTypeAsString() +
                (getDataLength() <= 200 ? ", mData=" + getArray() : "") +
                ", mData-Length=" + getDataLength() +
                '}';
    }

    private String getArray() {
        int len = getDataLength();
        StringBuilder sb = new StringBuilder(len * 2);
        if (mData != null) {
            for (int i = 0 ; i < len ; i++) {
                sb.append(String.format("%02X", mData[i]));
            }
        }

        return sb.toString();
    }
}
