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

import com.android.tools.pixelprobe.decoder.Decoder;
import com.android.tools.pixelprobe.decoder.Decoders;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * The PixelProbe class exposes functions to decode input streams
 * as images. If the desired format is not specified, PixelProbe
 * will attempt to guess the format of the stream's data.
 */
public final class PixelProbe {
    private PixelProbe() {
    }

    /**
     * Decodes an image from the specified input stream.
     * This method will attempt to guess the image format by reading
     * the beginning of the stream. It is therefore recommended to
     * pass an InputStream that supports mark/reset. If the specified
     * stream does not support mark/reset, this method will wrap the
     * stream with a BufferedInputStream.
     *
     * This method does not close the stream.
     *
     * The returned image is always non-null but you must check
     * the return value of Image.isValid() before attempting to use
     * it. If the image is marked invalid, an error occurred while
     * decoding the stream.
     *
     * @param in The input stream to decode an image from
     *
     * @return An Image instance, never null.
     *
     * @see #probe(InputStream, Decoder.Options)
     * @see #probe(String, InputStream)
     */
    public static Image probe(InputStream in) throws IOException {
        return probe(in, null);
    }

    /**
     * Decodes an image from the specified input stream.
     * This method will attempt to guess the image format by reading
     * the beginning of the stream. It is therefore recommended to
     * pass an InputStream that supports mark/reset. If the specified
     * stream does not support mark/reset, this method will wrap the
     * stream with a BufferedInputStream.
     *
     * This method does not close the stream.
     *
     * The returned image is always non-null but you must check
     * the return value of Image.isValid() before attempting to use
     * it. If the image is marked invalid, an error occurred while
     * decoding the stream.
     *
     * @param in The input stream to decode an image from
     * @param options The options to pass to the decoder, can be null
     *
     * @return An Image instance, never null.
     *
     * @see #probe(InputStream)
     * @see #probe(String, InputStream, Decoder.Options)
     */

    public static Image probe(InputStream in, Decoder.Options options) throws IOException {
        // Make sure we have rewind capabilities to run Decoder.accept()
        if (!in.markSupported()) {
            in = new BufferedInputStream(in);
        }

        Decoder decoder = Decoders.find(in);
        if (decoder == null) {
            throw new IOException("Unknown image format");
        }

        return decoder.decode(in, options != null ? options : new Decoder.Options());
    }

    /**
     * Decodes an image from the specified input stream, using the
     * specified format. The format can be a file extension or a
     * descriptive name. Examples: "png", "jpg", "jpeg", "psd",
     * "photoshop". Format matching is case insensitive. If a
     * suitable decoder cannot be found, this method will delegate
     * to {@link #probe(InputStream)}.
     *
     * This method does not close the stream.
     *
     * @param in The input stream to decode an image from
     * @param format The expected format of the image to decode from the stream
     *
     * @return An Image instance, never null.
     *
     * @see #probe(String, InputStream, Decoder.Options)
     * @see #probe(InputStream)
     */
    public static Image probe(String format, InputStream in) throws IOException {
        return probe(format, in, null);
    }

    /**
     * Decodes an image from the specified input stream, using the
     * specified format. The format can be a file extension or a
     * descriptive name. Examples: "png", "jpg", "jpeg", "psd",
     * "photoshop". Format matching is case insensitive. If a
     * suitable decoder cannot be found, this method will delegate
     * to {@link #probe(InputStream)}.
     *
     * This method does not close the stream.
     *
     * @param in The input stream to decode an image from
     * @param format The expected format of the image to decode from the stream
     * @param options The options to pass to the decoder, can be null
     *
     * @return An Image instance, never null.
     *
     * @see #probe(String, InputStream)
     * @see #probe(InputStream, Decoder.Options)
     */
    public static Image probe(String format, InputStream in, Decoder.Options options) throws IOException {
        Decoder decoder = Decoders.find(format);
        if (decoder == null) {
            return probe(in);
        }

        return decoder.decode(in, options != null ? options : new Decoder.Options());
    }
}
