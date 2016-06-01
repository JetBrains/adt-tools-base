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

import com.android.tools.pixelprobe.ColorMode;
import com.android.tools.pixelprobe.Image;
import com.android.tools.pixelprobe.util.Strings;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Abstract image decoder interface. A decoder can be used to generate
 * an Image from an input stream. They also provide the ability to
 * indicate whether they are able to decode a specific stream using
 * the accept() method. A decoder can also be selected by checking for
 * supported formats.
 */
public abstract class Decoder {
    private final Set<String> formats = new HashSet<>();

    /**
     * Creates a new decoder that supports the specified list of formats.
     *
     * @param formats A list of formats
     */
    Decoder(String... formats) {
        for (String format : formats) {
            this.formats.add(format.toLowerCase(Locale.ROOT));
        }
    }

    /**
     * Returns true if the specified format is supported by this decoder.
     * The format comparison is case insensitive.
     *
     * @param format A format string
     */
    public boolean accept(String format) {
        return formats.contains(format.toLowerCase(Locale.ROOT));
    }

    /**
     * Returns true if the specified input stream contains data that
     * can be decoded with this decoder. It is the caller's responsibility
     * to rewind the stream after calling this method.
     *
     * @param in An input stream
     */
    public abstract boolean accept(InputStream in);

    /**
     * Decodes the specified input stream into an Image.
     *
     * @param in Input stream to decode
     *
     * @return An Image instance, never null. Might be marked invalid if
     *         an error occurred during the decoding process.
     */
    public Image decode(InputStream in) throws IOException {
        Image.Builder builder = new Image.Builder();
        BufferedImage bitmap = ImageIO.read(in);

        return builder
            .dimensions(bitmap.getWidth(), bitmap.getHeight())
            .flattenedBitmap(bitmap)
            .colorMode(ColorMode.RGB)
            .depth(8)
            .build();
    }

    @Override
    public String toString() {
        return "Decoder{" +
               "formats={" + Strings.join(formats, ",") + '}' +
               '}';
    }
}
