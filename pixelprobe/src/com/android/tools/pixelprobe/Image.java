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

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An Image represents the decoded content of an image stream.
 * The values exposed by this class depends greatly on the format
 * of the source image. For instance, an Image decoded from a JPEG
 * will not provide layers or guides, which an Image decoded from
 * a PSD (Photoshop) will.
 *
 * You should always check whether an Image is valid before using
 * it. Invalid images may contain erroneous or meaningless values.
 */
public class Image {
    private int mWidth;
    private int mHeight;
    private ColorMode mColorMode = ColorMode.UNKNOWN;

    private float mHorizontalResolution = 96.0f;
    private float mVerticalResolution = 96.0f;

    private boolean mValid;

    private BufferedImage mFlattenedBitmap;
    private BufferedImage mThumbnail;

    private final List<Guide> mGuides = new ArrayList<>();
    private final List<Layer> mLayers = new ArrayList<>();

    Image() {
    }

    /**
     * Indicates whether this image is valid and can be used.
     *
     * @return True if this image is valid, false otherwise.
     */
    public boolean isValid() {
        return mValid;
    }

    /**
     * Returns the width of the image in pixels.
     */
    public int getWidth() {
        return mWidth;
    }

    /**
     * Returns the height of the image in pixels.
     */
    public int getHeight() {
        return mHeight;
    }

    /**
     * Returns the horizontal resolution of this image in dpi.
     */
    public float getHorizontalResolution() {
        return mHorizontalResolution;
    }

    /**
     * Returns the vertical resolution of this image in dpi.
     */
    public float getVerticalResolution() {
        return mVerticalResolution;
    }

    /**
     * Returns the color mode of this image.
     */
    public ColorMode getColorMode() {
        return mColorMode;
    }

    /**
     * Returns a flattened (or merged or composited) version of the image
     * as a bitmap. For images without layers, this is the actual image data.
     */
    public BufferedImage getFlattenedBitmap() {
        return mFlattenedBitmap;
    }

    /**
     * Returns a thumbnail for this image, if present. The returned value
     * might be null if no thumbnail was found in the original source.
     */
    public BufferedImage getThumbnailBitmap() {
        return mThumbnail;
    }

    /**
     * Returns the list of guides for this image. The list is never null.
     */
    public List<Guide> getGuides() {
        return Collections.unmodifiableList(mGuides);
    }

    /**
     * Returns the list of layers for this image. The list is never null.
     */
    public List<Layer> getLayers() {
        return Collections.unmodifiableList(mLayers);
    }

    void markValid() {
        mValid = true;
    }

    void setFlattenedBitmap(BufferedImage flattenedBitmap) {
        mFlattenedBitmap = flattenedBitmap;
    }

    void setDimensions(int width, int height) {
        mWidth = width;
        mHeight = height;
    }

    void setResolution(float horizontal, float vertical) {
        mHorizontalResolution = horizontal;
        mVerticalResolution = vertical;
    }

    void addGuide(Guide guide) {
        mGuides.add(guide);
    }

    void setColorMode(ColorMode mode) {
        mColorMode = mode;
    }

    void setThumbnail(BufferedImage thumbnail) {
        mThumbnail = thumbnail;
    }

    void addLayer(Layer layer) {
        mLayers.add(layer);
    }

    @Override
    public String toString() {
        return "Image{" +
                "width=" + mWidth +
                ", height=" + mHeight +
                ", hRes=" + mHorizontalResolution +
                ", vRes=" + mVerticalResolution +
                ", colorMode=" + mColorMode +
                ", guides=" + mGuides.size() +
                ", layers=" + mLayers.size() +
                ", hasThumbnail=" + (mThumbnail != null) +
                ", valid=" + mValid +
                '}';
    }
}
