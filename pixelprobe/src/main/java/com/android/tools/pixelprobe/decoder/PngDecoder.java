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
import com.android.tools.pixelprobe.util.Bytes;
import org.w3c.dom.NodeList;

import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import java.awt.color.ICC_ColorSpace;
import java.awt.color.ICC_Profile;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.zip.InflaterInputStream;

/**
 * Decodes PNG streams. Accepts the "png" format string.
 */
final class PngDecoder extends Decoder {
    private static final byte[] PNG_HEADER = Bytes.fromHexString("89504e470d0a1a0a");

    private static final float METERS_TO_INCHES = 39.3701f;

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

    @Override
    public void decodeMetadata(Image.Builder builder, IIOMetadata metadata) {
        IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(metadata.getNativeMetadataFormatName());

        // Read the standard header chunk
        NodeList list = root.getElementsByTagName("IHDR");
        if (list.getLength() > 0) {
            IIOMetadataNode header = (IIOMetadataNode) list.item(0);

            // Extract color depth
            try {
                builder.depth(Integer.parseInt(header.getAttribute("bitDepth")));
            } catch (NumberFormatException e) {
                // Ignore, use default value
            }

            // Convert color type
            builder.colorMode(toColorMode(header.getAttribute("colorType")));
        }

        // Resolution
        list = root.getElementsByTagName("pHYs");
        if (list.getLength() > 0) {
            IIOMetadataNode phys = (IIOMetadataNode) list.item(0);

            try {
                int ppuX = Integer.parseInt(phys.getAttribute("pixelsPerUnitXAxis"));
                int ppuY = Integer.parseInt(phys.getAttribute("pixelsPerUnitXAxis"));
                String unit = phys.getAttribute("unitSpecifier");
                if ("meter".equalsIgnoreCase(unit)) {
                    builder.resolution(ppuX / METERS_TO_INCHES, ppuY / METERS_TO_INCHES);
                } else {
                    builder.resolution(ppuX, ppuY);
                }
            } catch (NumberFormatException e) {
                // Ignore, use default value
            }
        }

        // Read the embedded color profile, if any
        list = root.getElementsByTagName("iCCP");
        if (list.getLength() > 0) {
            IIOMetadataNode colorProfile = (IIOMetadataNode) list.item(0);

            String compression = colorProfile.getAttribute("compressionMethod");
            if ("deflate".equalsIgnoreCase(compression)) {
                byte[] data = (byte[]) colorProfile.getUserObject();
                try (InputStream in  = new InflaterInputStream(new ByteArrayInputStream(data))) {
                    ICC_Profile iccProfile = ICC_Profile.getInstance(in);
                    builder.colorSpace(new ICC_ColorSpace(iccProfile));
                } catch (IOException e) {
                    // Ignore, use default profile
                }
            }
        }
    }

    private static ColorMode toColorMode(String colorType) {
        switch (colorType) {
            case "Grayscale": return ColorMode.GRAYSCALE;
            case "RGB": return ColorMode.RGB;
            case "Palette": return ColorMode.INDEXED;
            case "GrayAlpha": return ColorMode.GRAYSCALE;
            case "RGBAlpha": return ColorMode.RGB;
        }
        return ColorMode.RGB;
    }
}
