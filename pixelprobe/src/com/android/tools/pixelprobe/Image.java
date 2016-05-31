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

import com.android.tools.pixelprobe.util.Lists;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An Image represents the decoded content of an image stream.
 * The values exposed by this class depends greatly on the format
 * of the source image. For instance, an Image decoded from a JPEG
 * will not provide layers or guides, whereas an Image decoded from
 * a PSD (Photoshop) will.
 *
 * You should always check whether an Image is valid before using
 * it. Invalid images may contain erroneous or meaningless values.
 */
public class Image {
    private final int width;
    private final int height;
    private final ColorMode colorMode;

    private final float horizontalResolution;
    private final float verticalResolution;

    private final BufferedImage flattenedBitmap;
    private final BufferedImage thumbnailBitmap;

    private final List<Guide> guides;
    private final List<Layer> layers;

    Image(Builder builder) {
        width = builder.width;
        height = builder.height;
        colorMode = builder.colorMode;

        horizontalResolution = builder.horizontalResolution;
        verticalResolution = builder.verticalResolution;

        flattenedBitmap = builder.flattenedBitmap;
        thumbnailBitmap = builder.thumbnailBitmap;

        guides = Lists.immutableCopy(builder.guides);
        layers = Lists.immutableCopy(builder.layers);
    }

    /**
     * Returns the width of the image in pixels.
     */
    public int getWidth() {
        return width;
    }

    /**
     * Returns the height of the image in pixels.
     */
    public int getHeight() {
        return height;
    }

    /**
     * Returns the horizontal resolution of this image in dpi.
     */
    public float getHorizontalResolution() {
        return horizontalResolution;
    }

    /**
     * Returns the vertical resolution of this image in dpi.
     */
    public float getVerticalResolution() {
        return verticalResolution;
    }

    /**
     * Returns the color mode of this image.
     */
    public ColorMode getColorMode() {
        return colorMode;
    }

    /**
     * Returns a flattened (or merged or composited) version of the image
     * as a bitmap. For images without layers, this is the actual image data.
     */
    public BufferedImage getFlattenedBitmap() {
        return flattenedBitmap;
    }

    /**
     * Returns a thumbnail for this image, if present. The returned value
     * might be null if no thumbnail was found in the original source.
     */
    public BufferedImage getThumbnailBitmap() {
        return thumbnailBitmap;
    }

    /**
     * Returns the list of guides for this image. The list is never null.
     */
    public List<Guide> getGuides() {
        return Collections.unmodifiableList(guides);
    }

    /**
     * Returns the list of layers for this image. The list is never null.
     */
    public List<Layer> getLayers() {
        return Collections.unmodifiableList(layers);
    }

    public static final class Builder {
        int width;
        int height;
        ColorMode colorMode = ColorMode.UNKNOWN;

        float horizontalResolution = 96.0f;
        float verticalResolution = 96.0f;

        BufferedImage flattenedBitmap;
        BufferedImage thumbnailBitmap;

        final List<Guide> guides = new ArrayList<>();
        final List<Layer> layers = new ArrayList<>();

        public int width() {
            return width;
        }

        public int height() {
            return height;
        }

        public float verticalResolution() {
            return verticalResolution;
        }

        public float horizontalResolution() {
            return horizontalResolution;
        }

        public Builder flattenedBitmap(BufferedImage flattenedBitmap) {
            this.flattenedBitmap = flattenedBitmap;
            return this;
        }

        public Builder dimensions(int width, int height) {
            this.width = width;
            this.height = height;
            return this;
        }

        public Builder resolution(float horizontal, float vertical) {
            horizontalResolution = horizontal;
            verticalResolution = vertical;
            return this;
        }

        public Builder colorMode(ColorMode mode) {
            colorMode = mode;
            return this;
        }

        public Builder thumbnail(BufferedImage thumbnail) {
            this.thumbnailBitmap = thumbnail;
            return this;
        }

        public Builder addGuide(Guide guide) {
            guides.add(guide);
            return this;
        }

        public Builder addLayer(Layer layer) {
            layers.add(layer);
            return this;
        }

        public Image build() {
            return new Image(this);
        }
    }

    @Override
    public String toString() {
        return "Image{" +
               "width=" + width +
               ", height=" + height +
               ", hRes=" + horizontalResolution +
               ", vRes=" + verticalResolution +
               ", colorMode=" + colorMode +
               ", guides=" + guides.size() +
               ", layers=" + layers.size() +
               ", hasThumbnail=" + (thumbnailBitmap != null) +
               '}';
    }
}
