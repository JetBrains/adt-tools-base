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
import java.util.Arrays;
import java.util.List;

public class CorruptPatch {
    public static List<Rectangle> findBadPatches(BufferedImage image, PatchInfo patchInfo) {
        List<Rectangle> corruptedPatches = new ArrayList<Rectangle>();

        for (Rectangle patch : patchInfo.patches) {
            if (corruptPatch(image, patch)) {
                corruptedPatches.add(patch);
            }
        }

        for (Rectangle patch : patchInfo.horizontalPatches) {
            if (corruptHorizontalPatch(image, patch)) {
                corruptedPatches.add(patch);
            }
        }

        for (Rectangle patch : patchInfo.verticalPatches) {
            if (corruptVerticalPatch(image, patch)) {
                corruptedPatches.add(patch);
            }
        }

        return corruptedPatches;
    }

    private static boolean corruptPatch(BufferedImage image, Rectangle patch) {
        int[] pixels = GraphicsUtilities.getPixels(image, patch.x, patch.y,
                patch.width, patch.height, null);

        if (pixels.length > 0) {
            int reference = pixels[0];
            for (int pixel : pixels) {
                if (pixel != reference) {
                    return true;
                }
            }
        }

        return false;
    }

    private static boolean corruptHorizontalPatch(BufferedImage image, Rectangle patch) {
        int[] reference = new int[patch.height];
        int[] column = new int[patch.height];
        reference = GraphicsUtilities.getPixels(image, patch.x, patch.y,
                1, patch.height, reference);

        for (int i = 1; i < patch.width; i++) {
            column = GraphicsUtilities.getPixels(image, patch.x + i, patch.y,
                    1, patch.height, column);
            if (!Arrays.equals(reference, column)) {
                return true;
            }
        }

        return false;
    }

    private static boolean corruptVerticalPatch(BufferedImage image, Rectangle patch) {
        int[] reference = new int[patch.width];
        int[] row = new int[patch.width];
        reference = GraphicsUtilities.getPixels(image, patch.x, patch.y,
                patch.width, 1, reference);

        for (int i = 1; i < patch.height; i++) {
            row = GraphicsUtilities.getPixels(image, patch.x, patch.y + i, patch.width, 1, row);
            if (!Arrays.equals(reference, row)) {
                return true;
            }
        }

        return false;
    }
}
