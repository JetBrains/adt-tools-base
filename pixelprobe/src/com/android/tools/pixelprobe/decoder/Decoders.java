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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Stores the list of available decoders.
 * Decoders can be registered and then retrieved either by format String
 * or by guessing the format (reading the beginning of an InputStream).
 */
public final class Decoders {
    private static final int READ_AHEAD_LIMIT = 1024;
    private static final List<Decoder> decoders = new ArrayList<>();
    static {
        register(new JpegDecoder());
        register(new PngDecoder());
        register(new PsdDecoder());
    }

    private Decoders() {
    }

    /**
     * Registers a decoder.
     */
    private static void register(Decoder decoder) {
        decoders.add(decoder);
    }

    /**
     * Finds a decoder using the specified format String.
     *
     * @param format The desired decoder's format
     *
     * @return A Decoder instance, null if no suitable decoder can be found
     */
    public static Decoder find(String format) {
        for (Decoder decoder : decoders) {
            if (decoder.accept(format)) return decoder;
        }
        return null;
    }

    /**
     * Finds a decoder by asking each registered decoder whether it accepts
     * the specified stream.
     *
     * @param in The stream to decode
     *
     * @return A Decoder instance, null if no suitable decoder can be found
     */
    public static Decoder find(final InputStream in) {
        for (final Decoder decoder : decoders) {
            boolean accepted = markExecuteReset(in, inputStream -> decoder.accept(in));
            if (accepted) return decoder;
        }
        return null;
    }

    private static boolean markExecuteReset(InputStream in, Predicate<InputStream> op) {
        in.mark(READ_AHEAD_LIMIT);
        try {
            return op.test(in);
        } finally {
            try {
                in.reset();
            } catch (IOException e) {
                // Ignore
            }
        }
    }
}
