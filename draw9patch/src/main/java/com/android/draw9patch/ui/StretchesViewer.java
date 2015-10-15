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

import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.TexturePaint;
import java.awt.image.BufferedImage;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;

public class StretchesViewer extends JPanel {
    public static final float DEFAULT_SCALE = 2.0f;
    private static final int MARGIN = 24;

    private final Container container;
    private final ImageViewer viewer;
    private final TexturePaint texture;

    private BufferedImage image;
    private PatchInfo patchInfo;

    private StretchView horizontal;
    private StretchView vertical;
    private StretchView both;

    private Dimension size;

    private float horizontalPatchesSum;
    private float verticalPatchesSum;

    private boolean showPadding;

    StretchesViewer(Container container, ImageViewer viewer, TexturePaint texture) {
        this.container = container;
        this.viewer = viewer;
        this.texture = texture;

        image = viewer.getImage();
        patchInfo = viewer.getPatchInfo();

        viewer.addPatchUpdateListener(new ImageViewer.PatchUpdateListener() {
            @Override
            public void patchesUpdated() {
                computePatches();
            }
        });

        setOpaque(false);
        setLayout(new GridBagLayout());
        setBorder(BorderFactory.createEmptyBorder(MARGIN, MARGIN, MARGIN, MARGIN));

        horizontal = new StretchView();
        vertical = new StretchView();
        both = new StretchView();

        setScale(DEFAULT_SCALE);

        add(vertical, new GridBagConstraints(0, 0, 1, 1, 1.0, 1.0, GridBagConstraints.CENTER,
                GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));
        add(horizontal, new GridBagConstraints(0, 1, 1, 1, 1.0, 1.0, GridBagConstraints.CENTER,
                GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));
        add(both, new GridBagConstraints(0, 2, 1, 1, 1.0, 1.0, GridBagConstraints.CENTER,
                GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setPaint(texture);
        g2.fillRect(0, 0, getWidth(), getHeight());
        g2.dispose();
    }

    void setScale(float scale) {
        int patchWidth = image.getWidth() - 2;
        int patchHeight = image.getHeight() - 2;

        int scaledWidth = (int) (patchWidth * scale);
        int scaledHeight = (int) (patchHeight * scale);

        horizontal.scaledWidth = scaledWidth;
        vertical.scaledHeight = scaledHeight;
        both.scaledWidth = scaledWidth;
        both.scaledHeight = scaledHeight;

        size = new Dimension(scaledWidth, scaledHeight);

        computePatches();
    }

    void computePatches() {
        image = viewer.getImage();
        patchInfo = viewer.getPatchInfo();

        boolean measuredWidth = false;
        boolean endRow = true;

        int remainderHorizontal = 0;
        int remainderVertical = 0;

        if (!patchInfo.fixed.isEmpty()) {
            int start = patchInfo.fixed.get(0).y;
            for (Rectangle rect : patchInfo.fixed) {
                if (rect.y > start) {
                    endRow = true;
                    measuredWidth = true;
                }
                if (!measuredWidth) {
                    remainderHorizontal += rect.width;
                }
                if (endRow) {
                    remainderVertical += rect.height;
                    endRow = false;
                    start = rect.y;
                }
            }
        } else {
            /* fully stretched without fixed regions (often single pixel high or wide). Since
             * width of vertical patches (and height of horizontal patches) are fixed, use them to
             * determine fixed space
             */
            for (Rectangle rect : patchInfo.verticalPatches) {
                remainderHorizontal += rect.width;
            }
            for (Rectangle rect : patchInfo.horizontalPatches) {
                remainderVertical += rect.height;
            }
        }

        horizontal.remainderHorizontal = horizontal.scaledWidth - remainderHorizontal;
        vertical.remainderHorizontal = vertical.scaledWidth - remainderHorizontal;
        both.remainderHorizontal = both.scaledWidth - remainderHorizontal;

        horizontal.remainderVertical = horizontal.scaledHeight - remainderVertical;
        vertical.remainderVertical = vertical.scaledHeight - remainderVertical;
        both.remainderVertical = both.scaledHeight - remainderVertical;

        horizontalPatchesSum = 0;
        if (!patchInfo.horizontalPatches.isEmpty()) {
            int start = -1;
            for (Rectangle rect : patchInfo.horizontalPatches) {
                if (rect.x > start) {
                    horizontalPatchesSum += rect.width;
                    start = rect.x;
                }
            }
        } else {
            int start = -1;
            for (Rectangle rect : patchInfo.patches) {
                if (rect.x > start) {
                    horizontalPatchesSum += rect.width;
                    start = rect.x;
                }
            }
        }

        verticalPatchesSum = 0;
        if (!patchInfo.verticalPatches.isEmpty()) {
            int start = -1;
            for (Rectangle rect : patchInfo.verticalPatches) {
                if (rect.y > start) {
                    verticalPatchesSum += rect.height;
                    start = rect.y;
                }
            }
        } else {
            int start = -1;
            for (Rectangle rect : patchInfo.patches) {
                if (rect.y > start) {
                    verticalPatchesSum += rect.height;
                    start = rect.y;
                }
            }
        }

        setSize(size);
        container.validate();
        repaint();
    }

    void setPaddingVisible(boolean visible) {
        showPadding = visible;
        repaint();
    }

    private class StretchView extends JComponent {
        private final Color PADDING_COLOR = new Color(0.37f, 0.37f, 1.0f, 0.5f);

        int scaledWidth;
        int scaledHeight;

        int remainderHorizontal;
        int remainderVertical;

        StretchView() {
            scaledWidth = image.getWidth();
            scaledHeight = image.getHeight();
        }

        @Override
        protected void paintComponent(Graphics g) {
            int x = (getWidth() - scaledWidth) / 2;
            int y = (getHeight() - scaledHeight) / 2;

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.translate(x, y);

            x = 0;
            y = 0;

            if (patchInfo.patches.isEmpty()) {
                g.drawImage(image, 0, 0, scaledWidth, scaledHeight, null);
                g2.dispose();
                return;
            }

            int fixedIndex = 0;
            int horizontalIndex = 0;
            int verticalIndex = 0;
            int patchIndex = 0;

            boolean hStretch;
            boolean vStretch;

            float vWeightSum = 1.0f;
            float vRemainder = remainderVertical;

            vStretch = patchInfo.verticalStartWithPatch;
            while (y < scaledHeight - 1) {
                hStretch = patchInfo.horizontalStartWithPatch;

                int height = 0;
                float vExtra = 0.0f;

                float hWeightSum = 1.0f;
                float hRemainder = remainderHorizontal;

                while (x < scaledWidth - 1) {
                    Rectangle r;
                    if (!vStretch) {
                        if (hStretch) {
                            r = patchInfo.horizontalPatches.get(horizontalIndex++);
                            float extra = r.width / horizontalPatchesSum;
                            int width = (int) (extra * hRemainder / hWeightSum);
                            hWeightSum -= extra;
                            hRemainder -= width;
                            g.drawImage(image, x, y, x + width, y + r.height, r.x, r.y,
                                    r.x + r.width, r.y + r.height, null);
                            x += width;
                        } else {
                            r = patchInfo.fixed.get(fixedIndex++);
                            g.drawImage(image, x, y, x + r.width, y + r.height, r.x, r.y,
                                    r.x + r.width, r.y + r.height, null);
                            x += r.width;
                        }
                        height = r.height;
                    } else {
                        if (hStretch) {
                            r = patchInfo.patches.get(patchIndex++);
                            vExtra = r.height / verticalPatchesSum;
                            height = (int) (vExtra * vRemainder / vWeightSum);
                            float extra = r.width / horizontalPatchesSum;
                            int width = (int) (extra * hRemainder / hWeightSum);
                            hWeightSum -= extra;
                            hRemainder -= width;
                            g.drawImage(image, x, y, x + width, y + height, r.x, r.y,
                                    r.x + r.width, r.y + r.height, null);
                            x += width;
                        } else {
                            r = patchInfo.verticalPatches.get(verticalIndex++);
                            vExtra = r.height / verticalPatchesSum;
                            height = (int) (vExtra * vRemainder / vWeightSum);
                            g.drawImage(image, x, y, x + r.width, y + height, r.x, r.y,
                                    r.x + r.width, r.y + r.height, null);
                            x += r.width;
                        }

                    }
                    hStretch = !hStretch;
                }
                x = 0;
                y += height;
                if (vStretch) {
                    vWeightSum -= vExtra;
                    vRemainder -= height;
                }
                vStretch = !vStretch;
            }

            if (showPadding) {
                g.setColor(PADDING_COLOR);
                g.fillRect(patchInfo.horizontalPadding.first,
                        patchInfo.verticalPadding.first,
                        scaledWidth - patchInfo.horizontalPadding.first
                                - patchInfo.horizontalPadding.second,
                        scaledHeight - patchInfo.verticalPadding.first
                                - patchInfo.verticalPadding.second);
            }

            g2.dispose();
        }

        @Override
        public Dimension getPreferredSize() {
            return size;
        }
    }
}