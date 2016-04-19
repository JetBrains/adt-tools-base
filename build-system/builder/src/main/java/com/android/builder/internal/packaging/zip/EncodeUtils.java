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

package com.android.builder.internal.packaging.zip;

import com.android.annotations.NonNull;
import com.google.common.base.Charsets;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

/**
 * Utilities to encode and decode file names in zips.
 */
public class EncodeUtils {

    /**
     * Utility class: no constructor.
     */
    private EncodeUtils() {
        /*
         * Nothing to do.
         */
    }

    /**
     * Decodes a file name.
     *
     * @param data the raw data
     * @param flags the zip entry flags
     * @return the decode file name
     */
    @NonNull
    public static String decode(@NonNull ByteBuffer bytes, int length, @NonNull GPFlags flags)
            throws IOException {
        if (bytes.remaining() < length) {
            throw new IOException("Only " + bytes.remaining() + " bytes exist in the buffer, but "
                    + "length is " + length + ".");
        }

        Charset charset = flagsCharset(flags);
        byte[] stringBytes = new byte[length];
        bytes.get(stringBytes);
        return charset.decode(ByteBuffer.wrap(stringBytes)).toString();
    }

    /**
     * Decodes a file name.
     *
     * @param data the raw data
     * @param flags the zip entry flags
     * @return the decode file name
     */
    @NonNull
    public static String decode(@NonNull byte[] data, @NonNull GPFlags flags) {
        Charset charset = flagsCharset(flags);
        return charset.decode(ByteBuffer.wrap(data)).toString();
    }

    /**
     * Encodes a file name.
     *
     * @param name the name to encode
     * @param flags the zip entry flags
     * @return the encoded file name
     */
    @NonNull
    public static byte[] encode(@NonNull String name, @NonNull GPFlags flags) {
        Charset charset = flagsCharset(flags);
        ByteBuffer bytes = charset.encode(name);
        byte[] result = new byte[bytes.remaining()];
        bytes.get(result);
        return result;
    }

    /**
     * Obtains the charset to encode and decode zip entries, given a set of flags.
     *
     * @param flags the flags
     * @return the charset to use
     */
    @NonNull
    private static Charset flagsCharset(@NonNull GPFlags flags) {
        if (flags.isUtf8FileName()) {
            return Charsets.UTF_8;
        } else {
            return Charsets.US_ASCII;
        }
    }

    /**
     * Checks if some text may be encoded using ASCII.
     *
     * @param text the text to check
     * @return can it be encoded using ASCII?
     */
    public static boolean canAsciiEncode(String text) {
        return Charsets.US_ASCII.newEncoder().canEncode(text);
    }
}
