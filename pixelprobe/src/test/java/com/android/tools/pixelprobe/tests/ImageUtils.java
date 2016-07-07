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

package com.android.tools.pixelprobe.tests;

import com.android.tools.pixelprobe.Image;
import com.android.tools.pixelprobe.PixelProbe;
import com.android.tools.pixelprobe.decoder.Decoder;

import java.io.IOException;
import java.io.InputStream;

public final class ImageUtils {
    private ImageUtils() {
    }

    public static Image loadImage(String imageName) throws IOException {
        return loadImage(imageName, new Decoder.Options());
    }

    public static Image loadImage(String imageName, Decoder.Options options) throws IOException {
        try (InputStream in = ImageUtils.class.getResourceAsStream("/" + imageName)) {
            return PixelProbe.probe(in, options);
        }
    }
}
