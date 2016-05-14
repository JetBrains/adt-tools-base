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

package com.android.tools.pixelprobe;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

/**
 * Abstract image decoder interface. A decoder can be used to generate
 * an Image from an input stream. They also provide the ability to
 * indicate whether they are able to decode a specific stream using
 * the accept() method. A decoder can also be selected by checking for
 * supported formats.
 */
abstract class Decoder {
    private final Set<String> mFormats = new HashSet<>();

    /**
     * Creates a new decoder that supports the specified list of formats.
     *
     * @param formats A list of formats
     */
    Decoder(String... formats) {
        for (String format : formats) {
            mFormats.add(format.toLowerCase());
        }
    }

    /**
     * Returns true if the specified format is supported by this decoder.
     * The format comparison is case insensitive.
     *
     * @param format A format string
     */
    boolean accept(String format) {
        return mFormats.contains(format.toLowerCase());
    }

    /**
     * Returns true if the specified input stream contains data that
     * can be decoded with this decoder. It is the caller's responsibility
     * to rewind the stream after calling this method.
     *
     * @param in An input stream
     */
    abstract boolean accept(InputStream in);

    /**
     * Decodes the specified input stream into an Image.
     *
     * @param in Input stream to decode
     *
     * @return An Image instance, never null. Might be marked invalid if
     *         an error occurred during the decoding process.
     */
    Image decode(InputStream in) {
        Image image = new Image();
        try {
            BufferedImage bitmap = ImageIO.read(in);

            image.setDimensions(bitmap.getWidth(), bitmap.getHeight());
            image.setFlattenedBitmap(bitmap);
            image.setColorMode(ColorMode.RGB);

            image.markValid();
        } catch (IOException e) {
            // Ignore, the image will be marked invalid
        }
        return image;
    }

    @Override
    public String toString() {
        return "Decoder{" +
                "formats={" + StringUtils.join(mFormats, ",") + '}' +
                '}';
    }
}
