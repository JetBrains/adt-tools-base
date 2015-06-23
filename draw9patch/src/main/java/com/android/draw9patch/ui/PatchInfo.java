/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.draw9patch.ui;

import com.android.draw9patch.graphics.GraphicsUtilities;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

public class PatchInfo {
    /** Color used to indicate stretch regions and padding. */
    public static final int BLACK_TICK = 0xFF000000;

    /** Color used to indicate layout bounds. */
    public static final int RED_TICK = 0xFFFF0000;

    /** Areas of the image that are stretchable in both directions. */
    public final List<Rectangle> patches;

    /** Areas of the image that are not stretchable in either direction. */
    public final List<Rectangle> fixed;

    /** Areas of image stretchable horizontally. */
    public final List<Rectangle> horizontalPatches;

    /** Areas of image stretchable vertically. */
    public final List<Rectangle> verticalPatches;

    /** Bounds of horizontal patch markers. */
    public final List<Pair<Integer>> horizontalPatchMarkers;

    /** Bounds of horizontal padding markers. */
    public final List<Pair<Integer>> horizontalPaddingMarkers;

    /** Bounds of vertical patch markers. */
    public final List<Pair<Integer>> verticalPatchMarkers;

    /** Bounds of vertical padding markers. */
    public final List<Pair<Integer>> verticalPaddingMarkers;

    public final boolean verticalStartWithPatch;
    public final boolean horizontalStartWithPatch;

    /** Beginning and end padding in the horizontal direction */
    public final Pair<Integer> horizontalPadding;

    /** Beginning and end padding in the vertical direction */
    public final Pair<Integer> verticalPadding;

    private BufferedImage image;

    public PatchInfo(BufferedImage image) {
        this.image = image;

        int width = image.getWidth();
        int height = image.getHeight();

        int[] row = GraphicsUtilities.getPixels(image, 0, 0, width, 1, null);
        int[] column = GraphicsUtilities.getPixels(image, 0, 0, 1, height, null);

        P left = getPatches(column);
        verticalStartWithPatch = left.startsWithPatch;
        verticalPatchMarkers = left.patches;

        P top = getPatches(row);
        horizontalStartWithPatch = top.startsWithPatch;
        horizontalPatchMarkers = top.patches;

        fixed = getRectangles(left.fixed, top.fixed);
        patches = getRectangles(left.patches, top.patches);

        if (!fixed.isEmpty()) {
            horizontalPatches = getRectangles(left.fixed, top.patches);
            verticalPatches = getRectangles(left.patches, top.fixed);
        } else {
            if (!top.fixed.isEmpty()) {
                horizontalPatches = new ArrayList<Rectangle>(0);
                verticalPatches = getVerticalRectangles(top.fixed);
            } else if (!left.fixed.isEmpty()) {
                horizontalPatches = getHorizontalRectangles(left.fixed);
                verticalPatches = new ArrayList<Rectangle>(0);
            } else {
                horizontalPatches = verticalPatches = new ArrayList<Rectangle>(0);
            }
        }

        row = GraphicsUtilities.getPixels(image, 0, height - 1, width, 1, row);
        column = GraphicsUtilities.getPixels(image, width - 1, 0, 1, height, column);

        top = PatchInfo.getPatches(row);
        horizontalPaddingMarkers = top.patches;
        horizontalPadding = getPadding(top.fixed);

        left = PatchInfo.getPatches(column);
        verticalPaddingMarkers = left.patches;
        verticalPadding = getPadding(left.fixed);
    }

    private List<Rectangle> getVerticalRectangles(List<Pair<Integer>> topPairs) {
        List<Rectangle> rectangles = new ArrayList<Rectangle>();
        for (Pair<Integer> top : topPairs) {
            int x = top.first;
            int width = top.second - top.first;

            rectangles.add(new Rectangle(x, 1, width, image.getHeight() - 2));
        }
        return rectangles;
    }

    private List<Rectangle> getHorizontalRectangles(List<Pair<Integer>> leftPairs) {
        List<Rectangle> rectangles = new ArrayList<Rectangle>();
        for (Pair<Integer> left : leftPairs) {
            int y = left.first;
            int height = left.second - left.first;

            rectangles.add(new Rectangle(1, y, image.getWidth() - 2, height));
        }
        return rectangles;
    }

    private Pair<Integer> getPadding(List<Pair<Integer>> pairs) {
        if (pairs.isEmpty()) {
            return new Pair<Integer>(0, 0);
        } else if (pairs.size() == 1) {
            if (pairs.get(0).first == 1) {
                return new Pair<Integer>(pairs.get(0).second - pairs.get(0).first, 0);
            } else {
                return new Pair<Integer>(0, pairs.get(0).second - pairs.get(0).first);
            }
        } else {
            int index = pairs.size() - 1;
            return new Pair<Integer>(pairs.get(0).second - pairs.get(0).first,
                    pairs.get(index).second - pairs.get(index).first);
        }
    }

    private List<Rectangle> getRectangles(List<Pair<Integer>> leftPairs,
                                          List<Pair<Integer>> topPairs) {
        List<Rectangle> rectangles = new ArrayList<Rectangle>();
        for (Pair<Integer> left : leftPairs) {
            int y = left.first;
            int height = left.second - left.first;
            for (Pair<Integer> top : topPairs) {
                int x = top.first;
                int width = top.second - top.first;

                rectangles.add(new Rectangle(x, y, width, height));
            }
        }
        return rectangles;
    }

    private static class P {
        public final List<Pair<Integer>> fixed;
        public final List<Pair<Integer>> patches;
        public final boolean startsWithPatch;

        private P(List<Pair<Integer>> f, List<Pair<Integer>> p, boolean s) {
            fixed = f;
            patches = p;
            startsWithPatch = s;
        }
    }

    private static P getPatches(int[] pixels) {
        int lastIndex = 1;
        int lastPixel;
        boolean first = true;
        boolean startWithPatch = false;

        List<Pair<Integer>> fixed = new ArrayList<Pair<Integer>>();
        List<Pair<Integer>> patches = new ArrayList<Pair<Integer>>();

        assert pixels.length > 2 : "Invalid 9-patch, cannot be less than 3 pixels in a dimension";
        // ignore layout bound markers for the purpose of patch calculation
        lastPixel = pixels[1] != PatchInfo.RED_TICK ? pixels[1] : 0;

        for (int i = 1; i < pixels.length - 1; i++) {
            // ignore layout bound markers for the purpose of patch calculation
            int pixel = pixels[i] != PatchInfo.RED_TICK ? pixels[i] : 0;

            if (pixel != lastPixel) {
                if (lastPixel == BLACK_TICK) {
                    if (first) startWithPatch = true;
                    patches.add(new Pair<Integer>(lastIndex, i));
                } else {
                    fixed.add(new Pair<Integer>(lastIndex, i));
                }
                first = false;

                lastIndex = i;
                lastPixel = pixel;
            }
        }
        if (lastPixel == BLACK_TICK) {
            if (first) startWithPatch = true;
            patches.add(new Pair<Integer>(lastIndex, pixels.length - 1));
        } else {
            fixed.add(new Pair<Integer>(lastIndex, pixels.length - 1));
        }

        if (patches.isEmpty()) {
            patches.add(new Pair<Integer>(1, pixels.length - 1));
            startWithPatch = true;
            fixed.clear();
        }

        return new P(fixed, patches, startWithPatch);
    }
}
