/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.builder.internal.packaging.zip;

import com.android.annotations.NonNull;
import com.google.common.primitives.Ints;

import java.io.IOException;

/**
 * General purpose bit flags. Contains the encoding of the zip's general purpose bits.
 */
class GPFlags {

    /**
     * Is the entry encrypted?
     */
    private static final int BIT_ENCRYPTION = 1;

    /**
     * What is the compression method?
     */
    private static final int BIT_METHOD = (1 << 1) | (1 << 2);

    /**
     * Has CRC computation been deferred and, therefore, does a data description block exist?
     */
    private static final int BIT_DEFERRED_CRC = (1 << 3);

    /**
     * Is enhanced deflating used?
     */
    private static final int BIT_ENHANCED_DEFLATING = (1 << 4);

    /**
     * Does the entry contain patched data?
     */
    private static final int BIT_PATCHED_DATA = (1 << 5);

    /**
     * Is strong encryption used?
     */
    private static final int BIT_STRONG_ENCRYPTION = (1 << 6) | (1 << 13);

    /**
     * Unused bits.
     */
    private static final int BIT_UNUSED = (1 << 7) | (1 << 8) | (1 << 9) | (1 << 10)
            | (1 << 11) | (1 << 14) | (1 << 15);

    /**
     * Bit flag value.
     */
    private final long mValue;

    /**
     * Has the CRC computation beeen deferred?
     */
    private boolean mDeferredCrc;

    /**
     * Creates a new flags object.
     *
     * @param value the value of the bit mask
     */
    private GPFlags(long value) {
        mValue = value;

        mDeferredCrc = ((value & BIT_DEFERRED_CRC) != 0);
    }

    /**
     * Obtains the flags value.
     *
     * @return the value of the bit mask
     */
    public long getValue() {
        return mValue;
    }

    /**
     * Is the CRC computation deferred?
     *
     * @return is the CRC computation deferred?
     */
    public boolean isDeferredCrc() {
        return mDeferredCrc;
    }

    /**
     * Creates a new default bit mask.
     *
     * @return the new bit mask
     */
    @NonNull
    static GPFlags makeDefault() {
        return new GPFlags(0);
    }

    /**
     * Creates the flag information from a byte. This method will also validate that only
     * supported options are defined in the flag.
     *
     * @param bits the bit mask
     * @return the created flag information
     * @throws IOException unsupported options are used in the bit mask
     */
    @NonNull
    static GPFlags from(long bits) throws IOException {
        if ((bits & BIT_ENCRYPTION) != 0) {
            throw new IOException("Zip files with encrypted of entries not supported.");
        }

        if ((bits & BIT_ENHANCED_DEFLATING) != 0) {
            throw new IOException("Enhanced deflating not supported.");
        }

        if ((bits & BIT_PATCHED_DATA) != 0) {
            throw new IOException("Compressed patched data not supported.");
        }

        if ((bits & BIT_STRONG_ENCRYPTION) != 0) {
            throw new IOException("Strong encryption not supported.");
        }

        if ((bits & BIT_UNUSED) != 0) {
            throw new IOException("Unused bits set in directory entry. Weird. I don't know what's "
                    + "going on.");
        }

        int methodBit = Ints.checkedCast(bits & BIT_METHOD);
        if (methodBit != 0) {
            throw new IOException("Unsupported method bit: " + methodBit + ".");
        }

        if ((bits & 0xffffffff00000000L) != 0) {
            throw new IOException("Unsupported bits after 32.");
        }

        return new GPFlags(bits);
    }
}
