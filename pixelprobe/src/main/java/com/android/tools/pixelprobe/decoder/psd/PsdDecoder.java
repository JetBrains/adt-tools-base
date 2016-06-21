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

import com.android.tools.chunkio.ChunkIO;
import com.android.tools.pixelprobe.Image;
import com.android.tools.pixelprobe.decoder.Decoder;

import java.io.IOException;
import java.io.InputStream;

import static com.android.tools.pixelprobe.decoder.psd.PsdFile.Header;

/**
 * Decodes PSD (Adobe Photoshop) streams. Accepts the "psd" and "photoshop"
 * format strings. The PSB variant of the Photoshop format, used to store
 * large images (> 30,000 pixels in either dimension), is currently not
 * supported.
 */
public final class PsdDecoder extends Decoder {
    /**
     * The PsdDecoder only supports .psd files. There is no support for .psb
     * (large Photoshop documents) at the moment.
     */
    public PsdDecoder() {
        super("psd", "photoshop");
    }

    @Override
    public boolean accept(InputStream in) {
        try {
            // We only need to decode the header to validate the PSD format
            return ChunkIO.read(in, Header.class) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public Image decode(InputStream in) throws IOException {
        try {
            // The PsdFile class represents the entire document
            PsdFile psd = ChunkIO.read(in, PsdFile.class);
            return PsdImage.from(psd);
        } catch (Throwable t) {
            throw new IOException("Error while decoding PSD stream", t);
        }
    }
}
