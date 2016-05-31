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

package com.android.tools.pixelprobe.decoder;

import com.android.tools.pixelprobe.util.Bytes;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/**
 * Decodes PNG streams. Accepts the "png" format string.
 */
final class PngDecoder extends Decoder {
    private static final byte[] PNG_HEADER = Bytes.fromHexString("89504e470d0a1a0a");

    PngDecoder() {
        super("png");
    }

    @Override
    public boolean accept(InputStream in) {
        try {
            // We assume the stream contains a valid PNG document if it begins
            // with the magic string "\211PNG\r\n\032\n"
            byte[] data = new byte[PNG_HEADER.length];
            int read = in.read(data);
            if (read == PNG_HEADER.length) {
                return Arrays.equals(data, PNG_HEADER);
            }
        } catch (IOException e) {
            // Ignore
        }
        return false;
    }
}
