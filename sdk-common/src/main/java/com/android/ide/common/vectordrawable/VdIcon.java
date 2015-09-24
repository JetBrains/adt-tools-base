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

package com.android.ide.common.vectordrawable;

import com.android.ide.common.util.AssetUtil;

import javax.swing.*;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;

/**
 * VdIcon wrap every vector drawable from Material Library into an icon. All of them are shown in a
 * table for developer to pick.
 */
public class VdIcon implements Icon, Comparable<VdIcon> {

    private VdTree mVdTree;

    private final String mName;

    private final URL mUrl;

    private boolean mDrawCheckerBoardBackground;

    private Rectangle myRectangle = new Rectangle();

    private static final Color CHECKER_COLOR = new Color(238, 238, 238);

    public VdIcon(URL url) {
        mVdTree = parseVdTree(url);
        mUrl = url;
        String fileName = url.getFile();
        mName = fileName.substring(fileName.lastIndexOf("/") + 1);
    }

    public String getName() {
        return mName;
    }

    public URL getURL() {
        return mUrl;
    }

    private VdTree parseVdTree(URL url) {
        final VdParser p = new VdParser();
        VdTree result = null;
        try {
            result = p.parse(url.openStream(), null);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }

    /**
     * TODO: Merge this code back with GraphicsUtil in idea.
     * Paints a checkered board style background. Each grid square is {@code cellSize} pixels.
     */
    public static void paintCheckeredBackground(Graphics g, Color backgroundColor,
            Color checkeredColor, Shape clip, int cellSize) {
        final Shape savedClip = g.getClip();
        ((Graphics2D)g).clip(clip);

        final Rectangle rect = clip.getBounds();
        g.setColor(backgroundColor);
        g.fillRect(rect.x, rect.y, rect.width, rect.height);
        g.setColor(checkeredColor);
        for (int dy = 0; dy * cellSize < rect.height; dy++) {
            for (int dx = dy % 2; dx * cellSize < rect.width; dx += 2) {
                g.fillRect(rect.x + dx * cellSize, rect.y + dy * cellSize, cellSize, cellSize);
            }
        }

        g.setClip(savedClip);
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        // Draw the checker board first, even when the tree is empty.
        myRectangle.setBounds(0, 0, c.getWidth(), c.getHeight());
        if (mDrawCheckerBoardBackground) {
            paintCheckeredBackground(g, Color.LIGHT_GRAY, CHECKER_COLOR, myRectangle, 8);
        }

        if (mVdTree == null) {
            return;
        }
        int minSize = Math.min(c.getWidth(), c.getHeight());
        final BufferedImage image = AssetUtil.newArgbBufferedImage(minSize, minSize);
        mVdTree.drawIntoImage(image);

        // Draw in the center of the component.
        Rectangle rect = new Rectangle(0, 0, c.getWidth(), c.getHeight());
        AssetUtil.drawCenterInside((Graphics2D) g, image, rect);
    }

    @Override
    public int getIconWidth() {
        return (int) (mVdTree != null ? mVdTree.getPortWidth() : 0);
    }

    @Override
    public int getIconHeight() {
        return (int) (mVdTree != null ? mVdTree.getPortHeight() : 0);
    }

    @Override
    public int compareTo(VdIcon other) {
        return mName.compareTo(other.mName);
    }

    public void enableCheckerBoardBackground(boolean enable) {
        mDrawCheckerBoardBackground = enable;
    }
}