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
 * Helper class to read a Unicode string, encoded in UTF-16.
 * The length, in characters, of the string is stored as a 32
 * bits unsigned integer. The string is made of length*2 bytes.
 */
@Chunked
final class UnicodeString {
    @Chunk(byteCount = 4)
    long length;

    @Chunk(dynamicByteCount = "unicodeString.length * 2", encoding = "UTF-16")
    String value;

    @Override
    public String toString() {
        if (value == null) return null;
        if (value.length() == 0) return "";
        int lastChar = value.length() - 1;
        if (value.charAt(lastChar) == '\0') {
            return value.substring(0, lastChar);
        }
        return value;
    }
}
