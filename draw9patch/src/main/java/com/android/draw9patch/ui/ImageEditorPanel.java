/*
 * Copyright (C) 2008 The Android Open Source Project
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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.TexturePaint;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;

import javax.swing.Box;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSplitPane;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

public class ImageEditorPanel extends JPanel {
    private static final String EXTENSION_9PATCH = ".9.png";

    private String name;
    private BufferedImage image;
    private boolean is9Patch;

    private ImageViewer viewer;
    private StretchesViewer stretchesViewer;
    private JLabel xLabel;
    private JLabel yLabel;

    private TexturePaint texture;
    private JSlider zoomSlider;

    public ImageEditorPanel(MainFrame mainFrame, BufferedImage image, String name) {
        this.image = image;
        this.name = name;

        if (mainFrame != null) {
            setTransferHandler(new ImageTransferHandler(mainFrame));
        }

        setOpaque(false);
        setLayout(new BorderLayout());

        is9Patch = name.endsWith(EXTENSION_9PATCH);
        if (!is9Patch) {
            this.image = convertTo9Patch(image);
            this.name = name.substring(0, name.lastIndexOf('.')) + ".9.png";
        } else {
            ensure9Patch(image);
        }

        loadSupport();
        buildImageViewer();
        buildStatusPanel();

        addAncestorListener(new AncestorListener() {
            @Override
            public void ancestorAdded(AncestorEvent event) {
            }

            @Override
            public void ancestorRemoved(AncestorEvent event) {
            }

            @Override
            public void ancestorMoved(AncestorEvent event) {
                // allow the image viewer to set the optimal zoom level and ensure that the
                // zoom slider's setting is in sync with the image viewer's zoom
                removeAncestorListener(this);
                synchronizeImageViewerZoomLevel();
            }
        });
    }

    private void synchronizeImageViewerZoomLevel() {
        zoomSlider.setValue(viewer.getZoom());
    }

    public ImageViewer getViewer() {
        return viewer;
    }

    private void loadSupport() {
        try {
            URL resource = getClass().getResource("/images/checker.png");
            BufferedImage checker = GraphicsUtilities.loadCompatibleImage(resource);
            texture = new TexturePaint(checker, new Rectangle2D.Double(0, 0,
                    checker.getWidth(), checker.getHeight()));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void buildImageViewer() {
        viewer = new ImageViewer(this, texture, image, new ImageViewer.StatusBar() {
            @Override
            public void setPointerLocation(int x, int y) {
                xLabel.setText(x + " px");
                yLabel.setText(y + " px");
            }
        });

        JSplitPane splitter = new JSplitPane();
        splitter.setContinuousLayout(true);
        splitter.setResizeWeight(0.8);
        splitter.setBorder(null);

        JScrollPane scroller = new JScrollPane(viewer);
        scroller.setOpaque(false);
        scroller.setBorder(null);
        scroller.getViewport().setBorder(null);
        scroller.getViewport().setOpaque(false);

        splitter.setLeftComponent(scroller);
        splitter.setRightComponent(buildStretchesViewer());

        add(splitter);
    }

    private JComponent buildStretchesViewer() {
        stretchesViewer = new StretchesViewer(this, viewer, texture);
        JScrollPane scroller = new JScrollPane(stretchesViewer);
        scroller.setBorder(null);
        scroller.getViewport().setBorder(null);
        scroller.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
        scroller.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        return scroller;
    }

    private void buildStatusPanel() {
        JPanel status = new JPanel(new GridBagLayout());

        JLabel label = new JLabel();
        label.setText("Zoom: ");
        label.putClientProperty("JComponent.sizeVariant", "small");
        status.add(label, new GridBagConstraints(0, 0, 1, 1, 0.0f, 0.0f,
                GridBagConstraints.LINE_END, GridBagConstraints.NONE,
                new Insets(0, 6, 0, 0), 0, 0));

        label = new JLabel();
        label.setText("100%");
        label.putClientProperty("JComponent.sizeVariant", "small");
        status.add(label, new GridBagConstraints(1, 0, 1, 1, 0.0f, 0.0f,
                GridBagConstraints.LINE_END, GridBagConstraints.NONE,
                new Insets(0, 0, 0, 0), 0, 0));

        zoomSlider = new JSlider(ImageViewer.MIN_ZOOM, ImageViewer.MAX_ZOOM,
                ImageViewer.DEFAULT_ZOOM);
        zoomSlider.putClientProperty("JComponent.sizeVariant", "small");
        zoomSlider.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent evt) {
                viewer.setZoom(((JSlider) evt.getSource()).getValue());
            }
        });
        status.add(zoomSlider, new GridBagConstraints(2, 0, 1, 1, 0.0f, 0.0f,
                GridBagConstraints.LINE_START, GridBagConstraints.NONE,
                new Insets(0, 0, 0, 0), 0, 0));

        JLabel maxZoomLabel = new JLabel();
        maxZoomLabel.putClientProperty("JComponent.sizeVariant", "small");
        maxZoomLabel.setText("800%");
        status.add(maxZoomLabel, new GridBagConstraints(3, 0, 1, 1, 0.0f, 0.0f,
                GridBagConstraints.LINE_START, GridBagConstraints.NONE,
                new Insets(0, 0, 0, 0), 0, 0));

        label = new JLabel();
        label.setText("Patch scale: ");
        label.putClientProperty("JComponent.sizeVariant", "small");
        status.add(label, new GridBagConstraints(0, 1, 1, 1, 0.0f, 0.0f,
                GridBagConstraints.LINE_START, GridBagConstraints.NONE,
                new Insets(0, 6, 0, 0), 0, 0));

        label = new JLabel();
        label.setText("2x");
        label.putClientProperty("JComponent.sizeVariant", "small");
        status.add(label, new GridBagConstraints(1, 1, 1, 1, 0.0f, 0.0f,
                GridBagConstraints.LINE_END, GridBagConstraints.NONE,
                new Insets(0, 0, 0, 0), 0, 0));

        JSlider jSlider = new JSlider(200, 600, (int) (StretchesViewer.DEFAULT_SCALE * 100.0f));
        jSlider.putClientProperty("JComponent.sizeVariant", "small");
        jSlider.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent evt) {
                stretchesViewer.setScale(((JSlider) evt.getSource()).getValue() / 100.0f);
            }
        });
        status.add(jSlider, new GridBagConstraints(2, 1, 1, 1, 0.0f, 0.0f,
                GridBagConstraints.LINE_START, GridBagConstraints.NONE,
                new Insets(0, 0, 0, 0), 0, 0));

        maxZoomLabel = new JLabel();
        maxZoomLabel.putClientProperty("JComponent.sizeVariant", "small");
        maxZoomLabel.setText("6x");
        status.add(maxZoomLabel, new GridBagConstraints(3, 1, 1, 1, 0.0f, 0.0f,
                GridBagConstraints.LINE_START, GridBagConstraints.NONE,
                new Insets(0, 0, 0, 0), 0, 0));

        JCheckBox showLock = new JCheckBox("Show lock");
        showLock.setOpaque(false);
        showLock.setSelected(false);
        showLock.putClientProperty("JComponent.sizeVariant", "small");
        showLock.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                viewer.setLockVisible(((JCheckBox) event.getSource()).isSelected());
            }
        });
        status.add(showLock, new GridBagConstraints(4, 0, 1, 1, 0.0f, 0.0f,
                GridBagConstraints.LINE_START, GridBagConstraints.NONE,
                new Insets(0, 12, 0, 0), 0, 0));

        JCheckBox showPatches = new JCheckBox("Show patches");
        showPatches.setOpaque(false);
        showPatches.putClientProperty("JComponent.sizeVariant", "small");
        showPatches.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                viewer.setPatchesVisible(((JCheckBox) event.getSource()).isSelected());
            }
        });
        status.add(showPatches, new GridBagConstraints(4, 1, 1, 1, 0.0f, 0.0f,
                GridBagConstraints.LINE_START, GridBagConstraints.NONE,
                new Insets(0, 12, 0, 0), 0, 0));

        JCheckBox showPadding = new JCheckBox("Show content");
        showPadding.setOpaque(false);
        showPadding.putClientProperty("JComponent.sizeVariant", "small");
        showPadding.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                stretchesViewer.setPaddingVisible(((JCheckBox) event.getSource()).isSelected());
            }
        });
        status.add(showPadding, new GridBagConstraints(5, 0, 1, 1, 0.0f, 0.0f,
                GridBagConstraints.LINE_START, GridBagConstraints.NONE,
                new Insets(0, 12, 0, 0), 0, 0));

        status.add(Box.createHorizontalGlue(), new GridBagConstraints(6, 0, 1, 1, 1.0f, 1.0f,
                GridBagConstraints.LINE_START, GridBagConstraints.BOTH,
                new Insets(0, 0, 0, 0), 0, 0));

        label = new JLabel("X: ");
        label.putClientProperty("JComponent.sizeVariant", "small");
        status.add(label, new GridBagConstraints(7, 0, 1, 1, 0.0f, 0.0f,
                GridBagConstraints.LINE_END, GridBagConstraints.NONE,
                new Insets(0, 0, 0, 0), 0, 0));

        xLabel = new JLabel("0px");
        xLabel.putClientProperty("JComponent.sizeVariant", "small");
        status.add(xLabel, new GridBagConstraints(8, 0, 1, 1, 0.0f, 0.0f,
                GridBagConstraints.LINE_END, GridBagConstraints.NONE,
                new Insets(0, 0, 0, 6), 0, 0));

        label = new JLabel("Y: ");
        label.putClientProperty("JComponent.sizeVariant", "small");
        status.add(label, new GridBagConstraints(7, 1, 1, 1, 0.0f, 0.0f,
                GridBagConstraints.LINE_END, GridBagConstraints.NONE,
                new Insets(0, 0, 0, 0), 0, 0));

        yLabel = new JLabel("0px");
        yLabel.putClientProperty("JComponent.sizeVariant", "small");
        status.add(yLabel, new GridBagConstraints(8, 1, 1, 1, 0.0f, 0.0f,
                GridBagConstraints.LINE_END, GridBagConstraints.NONE,
                new Insets(0, 0, 0, 6), 0, 0));

        add(status, BorderLayout.SOUTH);
    }

    private static void ensure9Patch(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        for (int i = 0; i < width; i++) {
            int pixel = image.getRGB(i, 0);
            if (pixel != 0 && pixel != PatchInfo.BLACK_TICK && pixel != PatchInfo.RED_TICK) {
                image.setRGB(i, 0, 0);
            }
            pixel = image.getRGB(i, height - 1);
            if (pixel != 0 && pixel != PatchInfo.BLACK_TICK && pixel != PatchInfo.RED_TICK) {
                image.setRGB(i, height - 1, 0);
            }
        }
        for (int i = 0; i < height; i++) {
            int pixel = image.getRGB(0, i);
            if (pixel != 0 && pixel != PatchInfo.BLACK_TICK && pixel != PatchInfo.RED_TICK) {
                image.setRGB(0, i, 0);
            }
            pixel = image.getRGB(width - 1, i);
            if (pixel != 0 && pixel != PatchInfo.BLACK_TICK && pixel != PatchInfo.RED_TICK) {
                image.setRGB(width - 1, i, 0);
            }
        }
    }

    private static BufferedImage convertTo9Patch(BufferedImage image) {
        BufferedImage buffer = GraphicsUtilities.createTranslucentCompatibleImage(
                image.getWidth() + 2, image.getHeight() + 2);

        Graphics2D g2 = buffer.createGraphics();
        g2.drawImage(image, 1, 1, null);
        g2.dispose();

        return buffer;
    }

    File chooseSaveFile() {
        if (is9Patch) {
            return new File(name);
        } else {
            JFileChooser chooser = new JFileChooser(
                    name.substring(0, name.lastIndexOf(File.separatorChar)));
            chooser.setFileFilter(new PngFileFilter());
            int choice = chooser.showSaveDialog(this);
            if (choice == JFileChooser.APPROVE_OPTION) {
                File file = chooser.getSelectedFile();
                if (!file.getAbsolutePath().endsWith(EXTENSION_9PATCH)) {
                    String path = file.getAbsolutePath();
                    if (path.endsWith(".png")) {
                        path = path.substring(0, path.lastIndexOf(".png")) + EXTENSION_9PATCH;
                    } else {
                        path = path + EXTENSION_9PATCH;
                    }
                    name = path;
                    is9Patch = true;
                    return new File(path);
                }
                is9Patch = true;
                return file;
            }
        }
        return null;
    }

    RenderedImage getImage() {
        return image;
    }

    public void dispose() {
        if (viewer != null) {
            viewer.dispose();
        }
    }
}
