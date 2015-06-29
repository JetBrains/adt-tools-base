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

package com.android.assetstudiolib.vectordrawable;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.common.base.Charsets;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;

/**
 * Generate a Image based on the VectorDrawable's XML content.
 *
 * <p>This class also contains a main method, which can be used to preview a vector drawable file.
 */
public class VdPreview {
    /**
     * This encapsulates the information used to determine the preview image size.
     * When {@value mUseWidth} is true, use {@code mImageWidth} as the final width and
     * keep the same aspect ratio.
     * Otherwise, use {@code mImageScale} to scale the image based on the XML's size information.
     */
    public static class Size {
        private boolean mUseWidth;
        private int mImageWidth;
        private float mImageScale;

        private Size(boolean useWidth, int imageWidth, float imageScale) {
            mUseWidth = useWidth;
            mImageWidth = imageWidth;
            mImageScale = imageScale;
        }

        public static Size createSizeFromWidth(int imageWidth) {
            return new Size(true, imageWidth, 0.0f);
        }

        public static Size createSizeFromScale(float imageScale) {
            return new Size(false, 0, imageScale);
        }
    }

    /**
     * This generates an image according to the VectorDrawable's content {@code xmlFileContent}.
     * At the same time, {@vdErrorLog} captures all the errors found during parsing.
     * The size of image is determined by the {@code size}.
     *
     * @param size the size of result image.
     * @param xmlFileContent  VectorDrawable's XML file's content.
     * @param vdErrorLog      log for the parsing errors and warnings.
     * @return an preview image according to the VectorDrawable's XML
     */
    @Nullable
    public static BufferedImage getPreviewFromVectorXml(@NonNull Size size,
            @Nullable String xmlFileContent,
            @Nullable StringBuilder vdErrorLog) {
        if (xmlFileContent == null || xmlFileContent.isEmpty()) {
            return null;
        }
        VdParser p = new VdParser();
        VdTree vdTree;

        InputStream inputStream = new ByteArrayInputStream(
                xmlFileContent.getBytes(Charsets.UTF_8));
        vdTree = p.parse(inputStream, vdErrorLog);
        if (vdTree == null) {
            return null;
        }

        // If the forceImageWidth is set (>0), then we honor that.
        // Otherwise, we will ask the vectorDrawable for the prefer size, then apply the imageScale.
        float vdWidth = vdTree.getBaseWidth();
        float vdHeight = vdTree.getBaseHeight();
        float imageWidth;
        float imageHeight;
        int forceImageWidth = size.mImageWidth;
        float imageScale = size.mImageScale;

        if (forceImageWidth > 0) {
            imageWidth = forceImageWidth;
            imageHeight = forceImageWidth * vdHeight / vdWidth;
        } else {
            imageWidth = vdWidth * imageScale;
            imageHeight = vdHeight * imageScale;
        }

        // Create the image according to the vectorDrawable's aspect ratio.
        BufferedImage image = new BufferedImage((int) imageWidth, (int) imageHeight,
                BufferedImage.TYPE_INT_ARGB);

        Graphics g = image.getGraphics();
        g.setColor(new Color(255, 255, 255, 0));
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        vdTree.draw(g, image.getWidth(), image.getHeight());
        return image;
    }

    public static void main(String[] args) {
        System.out.println("Hello from asset-lib.");
    }
}