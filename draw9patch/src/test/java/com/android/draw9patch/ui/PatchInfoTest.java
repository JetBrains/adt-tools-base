/*
 *
 *  Copyright (C) 2013 The Android Open Source Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package com.android.draw9patch.ui;

import junit.framework.TestCase;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;

public class PatchInfoTest extends TestCase {
    private BufferedImage createImage(String[] data) {
        int h = data.length;
        int w = data[0].length();
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        for (int row = 0; row < h; row++) {
            for (int col = 0; col < w; col++) {
                char c = data[row].charAt(col);
                int color = 0;
                if (c == '*') {
                    color = PatchInfo.BLACK_TICK;
                } else if (c == 'R') {
                    color = PatchInfo.RED_TICK;
                }
                image.setRGB(col, row, color);
            }
        }
        return image;
    }

    public void testPatchInfo() {
        BufferedImage image = createImage(new String[] {
                "0123**6789",
                "1........*",
                "*........*",
                "3........*",
                "412*****89",
        });
        PatchInfo pi = new PatchInfo(image);

        // The left and top patch markers don't begin from the first pixel
        assertFalse(pi.horizontalStartWithPatch);
        assertFalse(pi.verticalStartWithPatch);

        // There should be one patch in the middle where the left and top patch markers intersect
        assertEquals(1, pi.patches.size());
        assertEquals(new Rectangle(4, 2, 2, 1), pi.patches.get(0));

        // There should be 2 horizontal stretchable areas - area below the top marker but excluding
        // the main patch
        assertEquals(2, pi.horizontalPatches.size());
        assertEquals(new Rectangle(4, 1, 2, 1), pi.horizontalPatches.get(0));
        assertEquals(new Rectangle(4, 3, 2, 1), pi.horizontalPatches.get(1));

        // Similarly, there should be 2 vertical stretchable areas
        assertEquals(2, pi.verticalPatches.size());
        assertEquals(new Rectangle(1, 2, 3, 1), pi.verticalPatches.get(0));
        assertEquals(new Rectangle(6, 2, 3, 1), pi.verticalPatches.get(1));

        // The should be 4 fixed regions - the regions that don't fall under the patches
        assertEquals(4, pi.fixed.size());

        // The horizontal padding is described by the bottom bar.
        // In this case, there is a 2 pixel (pixels 1 & 2) padding at start and 1 pixel (pixel 8)
        // padding at end
        assertEquals(2, pi.horizontalPadding.first.intValue());
        assertEquals(1, pi.horizontalPadding.second.intValue());

        // The vertical padding is described by the bar at the right.
        // In this case, there is no padding as the content area matches the image area
        assertEquals(0, pi.verticalPadding.first.intValue());
        assertEquals(0, pi.verticalPadding.second.intValue());
    }

    public void testPadding() {
        BufferedImage image = createImage(new String[] {
                "0123**6789",
                "1.........",
                "2.........",
                "3........*",
                "4........*",
                "5***456789",
        });
        PatchInfo pi = new PatchInfo(image);

        // 0 pixel padding at start and 5 pixel padding at the end (pixels 4 through 8 inclusive)
        assertEquals(0, pi.horizontalPadding.first.intValue());
        assertEquals(5, pi.horizontalPadding.second.intValue());

        // 2 pixel padding at the start and 0 at the end
        assertEquals(2, pi.verticalPadding.first.intValue());
        assertEquals(0, pi.verticalPadding.second.intValue());
    }

    // make sure that the presence of layout bound markers doesn't affect patch/padding info
    public void testIgnoreLayoutBoundMarkers() {
        BufferedImage image = createImage(new String[] {
                "0RR3**6789",
                "R........R",
                "*.........",
                "*........*",
                "4........*",
                "5***456R89",
        });
        PatchInfo pi = new PatchInfo(image);

        assertFalse(pi.horizontalStartWithPatch);

        assertEquals(1, pi.patches.size());
        assertEquals(2, pi.verticalPatches.size());
        assertEquals(2, pi.horizontalPatches.size());
    }
}
