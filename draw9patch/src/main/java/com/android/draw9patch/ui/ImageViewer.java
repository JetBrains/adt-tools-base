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

import java.awt.AWTEvent;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.TexturePaint;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.Area;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.JComponent;
import javax.swing.UIManager;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;

public class ImageViewer extends JComponent {
    private final Color CORRUPTED_COLOR = new Color(1.0f, 0.0f, 0.0f, 0.7f);
    private final Color LOCK_COLOR = new Color(0.0f, 0.0f, 0.0f, 0.7f);
    private final Color STRIPES_COLOR = new Color(1.0f, 0.0f, 0.0f, 0.5f);
    private final Color BACK_COLOR = UIManager.getColor("Panel.background").darker();
    private final Color PATCH_COLOR = new Color(1.0f, 0.37f, 0.99f, 0.5f);
    private final Color PATCH_ONEWAY_COLOR = new Color(0.37f, 1.0f, 0.37f, 0.5f);
    private final Color HIGHLIGHT_REGION_COLOR = new Color(0.5f, 0.5f, 0.5f, 0.5f);
    private final Color FOCUS_COLOR = Color.BLUE;

    private static final float STRIPES_WIDTH = 4.0f;
    private static final double STRIPES_SPACING = 6.0;
    private static final int STRIPES_ANGLE = 45;

    /** The fraction of the window size that the 9patch should occupy. */
    private static final float IDEAL_IMAGE_FRACTION_OF_WINDOW = 0.7f;

    /** Default zoom level for the 9patch image. */
    public static final int DEFAULT_ZOOM = 8;

    /** Minimum zoom level for the 9patch image. */
    public static final int MIN_ZOOM = 1;

    /** Maximum zoom level for the 9patch image. */
    public static final int MAX_ZOOM = 16;

    private final AWTEventListener mAwtKeyEventListener;

    /** Current 9patch zoom level, {@link #MIN_ZOOM} <= zoom <= {@link #MAX_ZOOM} */
    private int zoom = DEFAULT_ZOOM;
    private boolean showPatches;
    private boolean showLock = false;

    private final TexturePaint texture;
    private final Container container;
    private final StatusBar statusBar;

    private final Dimension size;

    private boolean locked;

    private int lastPositionX;
    private int lastPositionY;
    private boolean showCursor;

    private boolean eraseMode;
    private List<Rectangle> corruptedPatches;
    private boolean showBadPatches;

    private boolean drawingLine;
    private int lineFromX;
    private int lineFromY;
    private int lineToX;
    private int lineToY;
    private boolean showDrawingLine;

    // When this view is in focus, we want to support editing patches using the keyboard.
    // These fields maintain state of the currently focused pixel border location, and
    // pressing the space key would alter the pixel value at the currently focused location.
    private int focusX = 0;
    private int focusY = 0;

    private final List<Rectangle> hoverHighlightRegions = new ArrayList<Rectangle>();
    private String toolTipText;

    /**
     * Indicates whether we are currently in edit mode.
     * All fields with the prefix 'edit' are valid only when in edit mode.
     */
    private boolean isEditMode;

    /** Region being edited. */
    private UpdateRegion editRegion;

    /**
     * The start and end points corresponding to the region being edited.
     * During an edit sequence, the start point is constant and the end varies based on the
     * mouse location.
     */
    private final Pair<Integer> editSegment = new Pair<Integer>(0, 0);

    /** Regions to highlight based on the current edit. */
    private final List<Rectangle> editHighlightRegions = new ArrayList<Rectangle>();

    /** The actual patch location in the image being edited. */
    private Rectangle editPatchRegion = new Rectangle();

    private BufferedImage image;
    private PatchInfo patchInfo;

    /** The types of edit actions that can be performed on the image. */
    private enum DrawMode {
        PATCH,          // drawing a patch or a padding
        LAYOUT_BOUND,   // drawing layout bounds
        ERASE,          // erasing whatever has been drawn
    }

    /**
     * Current drawing mode. The mode is changed by using either the Shift or Ctrl keys while
     * drawing.
     */
    private DrawMode currentMode = DrawMode.PATCH;

    ImageViewer(Container container, TexturePaint texture, BufferedImage image,
                StatusBar statusBar) {
        this.container = container;
        this.texture = texture;
        this.image = image;
        this.statusBar = statusBar;

        setLayout(new GridBagLayout());
        setOpaque(true);
        setFocusable(true);

        // Exact size will be set by setZoom() in AncestorListener#ancestorMoved.
        size = new Dimension(0, 0);

        addAncestorListener(new AncestorListener() {
            @Override
            public void ancestorRemoved(AncestorEvent event) {
            }
            @Override
            public void ancestorMoved(AncestorEvent event) {
                removeAncestorListener(this);
                setDefaultZoom();
            }
            @Override
            public void ancestorAdded(AncestorEvent event) {
            }
        });

        updatePatchInfo();

        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                // Update the drawing mode looking at the current state of modifier (shift/ctrl)
                // keys. This is done here instead of retrieving it again in MouseDragged
                // below, because on linux, calling MouseEvent.getButton() for the drag
                // event returns 0, which appears to be technically correct (no button
                // changed state).
                updateDrawMode(event);

                int x = imageXCoordinate(event.getX());
                int y = imageYCoordinate(event.getY());

                startDrawingLine(x, y);

                if (currentMode == DrawMode.PATCH) {
                    startEditingRegion(x, y);
                } else {
                    hoverHighlightRegions.clear();
                    setCursor(Cursor.getDefaultCursor());
                    repaint();
                }
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                int x = imageXCoordinate(event.getX());
                int y = imageYCoordinate(event.getY());

                endDrawingLine();
                endEditingRegion(x, y);

                resetDrawMode();
            }
        });
        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent event) {
                int x = imageXCoordinate(event.getX());
                int y = imageYCoordinate(event.getY());

                if (!checkLockedRegion(x, y)) {
                    // use the stored button, see note above
                    moveLine(x, y);
                }

                updateEditRegion(x, y);
            }

            @Override
            public void mouseMoved(MouseEvent event) {
                int x = imageXCoordinate(event.getX());
                int y = imageYCoordinate(event.getY());

                checkLockedRegion(x, y);

                updateHoverRegion(x, y);
                repaint();
            }
        });

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                hoverHighlightRegions.clear();
                updateSize();
                repaint();
            }
        });

        // This provides a way to change the patch information via a keyboard. This feature works
        // as follows: When this component receives focus, the (focusX, focusY) pair indicates the
        // pixel that is in focus. The location of this focused point is painted with FOCUS_COLOR.
        // Users can move this pixel around using the right/left/up/down arrow keys. Pressing the
        // space key modifies the pixel data at the point that is in focus.
        addKeyListener(new KeyListener() {
            @Override
            public void keyTyped(KeyEvent e) {
            }

            @Override
            public void keyPressed(KeyEvent e) {
                int increment = e.isControlDown() ? 10 : 1;
                if (e.getKeyCode() == KeyEvent.VK_RIGHT) {
                    focusX = (focusX + increment) % ImageViewer.this.image.getWidth();
                } else if (e.getKeyCode() == KeyEvent.VK_LEFT) {
                    focusX = Math.max(focusX - increment, 0);
                } else if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                    focusY = (focusY + increment) % ImageViewer.this.image.getHeight();
                } else if (e.getKeyCode() == KeyEvent.VK_UP) {
                    focusY = Math.max(focusY - increment, 0);
                } else if (e.getKeyCode() == KeyEvent.VK_SPACE) {
                    int rgb = ImageViewer.this.image.getRGB(focusX, focusY);

                    // cycle across from BLACK -> RED -> transparent
                    if (rgb == PatchInfo.BLACK_TICK) {
                        rgb = PatchInfo.RED_TICK;
                    } else if (rgb == PatchInfo.RED_TICK) {
                        rgb = 0;
                    } else if (rgb == 0) {
                        rgb = PatchInfo.BLACK_TICK;
                    }
                    ImageViewer.this.image.setRGB(focusX, focusY, rgb);
                    patchesChanged();
                }
                repaint();
            }

            @Override
            public void keyReleased(KeyEvent e) {
            }
        });

        mAwtKeyEventListener = new AWTEventListener() {
            @Override
            public void eventDispatched(AWTEvent event) {
                enableEraseMode((KeyEvent) event);
            }
        };
        Toolkit.getDefaultToolkit()
                .addAWTEventListener(mAwtKeyEventListener, AWTEvent.KEY_EVENT_MASK);
    }

    public void setShowBadPatches(boolean en) {
        showBadPatches = en;
        corruptedPatches = en ? CorruptPatch.findBadPatches(image, patchInfo) : null;
        repaint();
    }

    private void updateDrawMode(MouseEvent event) {
        if (event.isShiftDown()) {
            currentMode = DrawMode.ERASE;
        } else if (event.isControlDown()) {
            currentMode = DrawMode.LAYOUT_BOUND;
        } else {
            currentMode = DrawMode.PATCH;
        }
    }

    private void resetDrawMode() {
        currentMode = DrawMode.PATCH;
    }

    private enum UpdateRegion {
        LEFT_PATCH,
        TOP_PATCH,
        RIGHT_PADDING,
        BOTTOM_PADDING,
    }

    private static class UpdateRegionInfo {
        public final UpdateRegion region;
        public final Pair<Integer> segment;

        private UpdateRegionInfo(UpdateRegion region, Pair<Integer> segment) {
            this.region = region;
            this.segment = segment;
        }
    }

    private UpdateRegionInfo findVerticalPatch(int x, int y) {
        List<Pair<Integer>> markers;
        UpdateRegion region;

        // Given the mouse x location, we need to determine if we need to map this edit to
        // the patch info at the left, or the padding info at the right. We make this decision
        // based on whichever is closer, so if the mouse x is in the left half of the image,
        // we are editing the left patch, else the right padding.
        if (x < image.getWidth() / 2) {
            markers = patchInfo.verticalPatchMarkers;
            region = UpdateRegion.LEFT_PATCH;
        } else {
            markers = patchInfo.verticalPaddingMarkers;
            region = UpdateRegion.RIGHT_PADDING;
        }

        return getContainingPatch(markers, y, region);
    }

    private UpdateRegionInfo findHorizontalPatch(int x, int y) {
        List<Pair<Integer>> markers;
        UpdateRegion region;

        if (y < image.getHeight() / 2) {
            markers = patchInfo.horizontalPatchMarkers;
            region = UpdateRegion.TOP_PATCH;
        } else {
            markers = patchInfo.horizontalPaddingMarkers;
            region = UpdateRegion.BOTTOM_PADDING;
        }

        return getContainingPatch(markers, x, region);
    }

    private UpdateRegionInfo getContainingPatch(List<Pair<Integer>> patches, int a,
                                                UpdateRegion region) {
        for (Pair<Integer> p: patches) {
            if (p.first <= a && p.second > a) {
                return new UpdateRegionInfo(region, p);
            }

            if (p.first > a) {
                break;
            }
        }

        return new UpdateRegionInfo(region, null);
    }

    private void updateHoverRegion(int x, int y) {
        // find regions to highlight based on the horizontal and vertical patches that
        // cover this (x, y)
        UpdateRegionInfo vertical = findVerticalPatch(x, y);
        UpdateRegionInfo horizontal = findHorizontalPatch(x, y);
        computeHoverHighlightRegions(vertical, horizontal);
        computeHoverRegionTooltip(vertical, horizontal);

        // change cursor if (x,y) is at the edge of either of the regions
        UpdateRegionInfo updateRegion = pickUpdateRegion(x, y, vertical, horizontal);
        setCursorForRegion(x, y, updateRegion);
    }

    private void startEditingRegion(int x, int y) {
        hoverHighlightRegions.clear();
        isEditMode = false;
        editRegion = null;

        UpdateRegionInfo vertical = findVerticalPatch(x, y);
        UpdateRegionInfo horizontal = findHorizontalPatch(x, y);
        UpdateRegionInfo updateRegion = pickUpdateRegion(x, y, vertical, horizontal);
        setCursorForRegion(x, y, updateRegion);

        if (updateRegion != null) { // edit an existing patch
            editRegion = updateRegion.region;
            isEditMode = true;

            Edge e = null;
            switch (this.editRegion) {
                case LEFT_PATCH:
                case RIGHT_PADDING:
                    e = getClosestEdge(y, updateRegion.segment);
                    break;
                case TOP_PATCH:
                case BOTTOM_PADDING:
                    e = getClosestEdge(x, updateRegion.segment);
                    break;
                default:
                    assert false : this.editRegion;
            }

            int first = updateRegion.segment.first;
            int second = updateRegion.segment.second;

            // The edge being edited should always be the end point in editSegment.
            boolean start = e == Edge.START;
            editSegment.first = start ? second : first;
            editSegment.second = start ? first : second;

            // clear the current patch data
            flushEditPatchData(0);
        } else if ((editRegion = findNewPatchRegion(x, y)) != null) { // create a new patch
            isEditMode = true;

            boolean verticalPatch = editRegion == UpdateRegion.LEFT_PATCH
                    || editRegion == UpdateRegion.RIGHT_PADDING;

            x = clamp(x, 1, image.getWidth() - 1);
            y = clamp(y, 1, image.getHeight() - 1);

            editSegment.first = editSegment.second = verticalPatch ? y : x;
        }

        if (isEditMode) {
            computeEditHighlightRegions();
        }

        repaint();
    }

    private void endEditingRegion(int x, int y) {
        if (!isEditMode) {
            return;
        }

        x = clamp(x, 1, image.getWidth() - 1);
        y = clamp(y, 1, image.getHeight() - 1);

        switch (editRegion) {
            case LEFT_PATCH:
            case RIGHT_PADDING:
                editSegment.second = y;
                break;
            case TOP_PATCH:
            case BOTTOM_PADDING:
                editSegment.second = x;
                break;
            default:
                assert false : editRegion;
        }

        flushEditPatchData(PatchInfo.BLACK_TICK);

        hoverHighlightRegions.clear();
        setCursor(Cursor.getDefaultCursor());
        patchesChanged();
        repaint();

        isEditMode = false;
        editRegion = null;
    }

    private void updateEditRegion(int x, int y) {
        if (!isEditMode) {
            return;
        }

        x = clamp(x, 1, image.getWidth() - 1);
        y = clamp(y, 1, image.getHeight() - 1);

        switch (editRegion) {
            case LEFT_PATCH:
            case RIGHT_PADDING:
                editSegment.second = y;
                break;
            case TOP_PATCH:
            case BOTTOM_PADDING:
                editSegment.second = x;
                break;
        }

        computeEditHighlightRegions();
        repaint();
    }

    private int clamp(int i, int min, int max) {
        if (i < min) {
            return min;
        }

        if (i > max) {
            return max;
        }

        return i;
    }

    /** Returns the type of patch that should be created given the initial mouse location. */
    private UpdateRegion findNewPatchRegion(int x, int y) {
        boolean verticalPatch = y >= 0 && y <= image.getHeight();
        boolean horizontalPatch = x >= 0 && x <= image.getWidth();

        // Heuristic: If the pointer is within the vertical bounds of the image,
        // then we create a patch on the left or right depending on which side of the image
        // the pointer is on
        if (verticalPatch) {
            if (x < 0) {
                return UpdateRegion.LEFT_PATCH;
            } else if (x > image.getWidth()) {
                return UpdateRegion.RIGHT_PADDING;
            }
        }

        // Similarly, if it is within the horizontal bounds of the image,
        // then create a patch at the top or bottom depending on its location relative to the image
        if (horizontalPatch) {
            if (y < 0) {
                return UpdateRegion.TOP_PATCH;
            } else if (y > image.getHeight()) {
                return UpdateRegion.BOTTOM_PADDING;
            }
        }

        return null;
    }

    private void computeHoverHighlightRegions(UpdateRegionInfo vertical,
                                              UpdateRegionInfo horizontal) {
        hoverHighlightRegions.clear();
        if (vertical != null && vertical.segment != null) {
            hoverHighlightRegions.addAll(
                    getHorizontalHighlightRegions(0,
                            vertical.segment.first,
                            image.getWidth(),
                            vertical.segment.second - vertical.segment.first));
        }
        if (horizontal != null && horizontal.segment != null) {
            hoverHighlightRegions.addAll(
                    getVerticalHighlightRegions(horizontal.segment.first,
                            0,
                            horizontal.segment.second - horizontal.segment.first,
                            image.getHeight()));
        }
    }

    private void computeHoverRegionTooltip(UpdateRegionInfo vertical, UpdateRegionInfo horizontal) {
        StringBuilder sb = new StringBuilder(50);

        if (vertical != null && vertical.segment != null) {
            if (vertical.region == UpdateRegion.LEFT_PATCH) {
                sb.append("Vertical Patch: ");
            } else {
                sb.append("Vertical Padding: ");
            }
            sb.append(String.format("%d - %d px",
                    vertical.segment.first, vertical.segment.second));
        }

        if (horizontal != null && horizontal.segment != null) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            if (horizontal.region == UpdateRegion.TOP_PATCH) {
                sb.append("Horizontal Patch: ");
            } else {
                sb.append("Horizontal Padding: ");
            }
            sb.append(String.format("%d - %d px",
                    horizontal.segment.first, horizontal.segment.second));
        }

        toolTipText = sb.length() > 0 ? sb.toString() : null;
    }

    private void computeEditHighlightRegions() {
        editHighlightRegions.clear();

        int f = editSegment.first;
        int s = editSegment.second;
        int min = Math.min(f, s);
        int diff = Math.abs(f - s);

        int imageWidth = image.getWidth();
        int imageHeight = image.getHeight();

        switch (editRegion) {
            case LEFT_PATCH:
                editPatchRegion = displayCoordinates(new Rectangle(0, min, 1, diff));
                editHighlightRegions.addAll(
                        getHorizontalHighlightRegions(0, min, imageWidth, diff));
                break;
            case RIGHT_PADDING:
                editPatchRegion = displayCoordinates(new Rectangle(imageWidth - 1, min, 1, diff));
                editHighlightRegions.addAll(
                        getHorizontalHighlightRegions(0, min, imageWidth, diff));
                break;
            case TOP_PATCH:
                editPatchRegion = displayCoordinates(new Rectangle(min, 0, diff, 1));
                editHighlightRegions.addAll(
                        getVerticalHighlightRegions(min, 0, diff, imageHeight));
                break;
            case BOTTOM_PADDING:
                editPatchRegion = displayCoordinates(new Rectangle(min, imageHeight - 1, diff, 1));
                editHighlightRegions.addAll(
                        getVerticalHighlightRegions(min, 0, diff, imageHeight));
                break;
            default:
                assert false : editRegion;
        }
    }

    private List<Rectangle> getHorizontalHighlightRegions(int x, int y, int w, int h) {
        List<Rectangle> l = new ArrayList<Rectangle>(3);

        // highlight the region within the image
        Rectangle r = displayCoordinates(new Rectangle(x, y, w, h));
        l.add(r);

        // add a 1 pixel line at the top and bottom that extends outside the image
        l.add(new Rectangle(0, r.y, getWidth(), 1));
        l.add(new Rectangle(0, r.y + r.height, getWidth(), 1));
        return l;
    }

    private List<Rectangle> getVerticalHighlightRegions(int x, int y, int w, int h) {
        List<Rectangle> l = new ArrayList<Rectangle>(3);


        // highlight the region within the image
        Rectangle r = displayCoordinates(new Rectangle(x, y, w, h));
        l.add(r);

        // add a 1 pixel line at the top and bottom that extends outside the image
        l.add(new Rectangle(r.x, 0, 1, getHeight()));
        l.add(new Rectangle(r.x + r.width, 0, 1, getHeight()));

        return l;
    }

    private void setCursorForRegion(int x, int y, UpdateRegionInfo region) {
        if (region != null) {
            Cursor c = getCursor(x, y, region);
            setCursor(c);
        } else {
            setCursor(Cursor.getDefaultCursor());
        }
    }

    private Cursor getCursor(int x, int y, UpdateRegionInfo editRegion) {
        Edge e;
        int cursor = Cursor.DEFAULT_CURSOR;
        switch (editRegion.region) {
            case LEFT_PATCH:
            case RIGHT_PADDING:
                e = getClosestEdge(y, editRegion.segment);
                cursor = (e == Edge.START) ? Cursor.N_RESIZE_CURSOR : Cursor.S_RESIZE_CURSOR;
                break;
            case TOP_PATCH:
            case BOTTOM_PADDING:
                e = getClosestEdge(x, editRegion.segment);
                cursor = (e == Edge.START) ? Cursor.W_RESIZE_CURSOR : Cursor.E_RESIZE_CURSOR;
                break;
            default:
                assert false : this.editRegion;
        }

        return Cursor.getPredefinedCursor(cursor);
    }

    /**
     * Returns whether the horizontal or the vertical region should be updated based on the
     * mouse pointer's location relative to the edges of either region. If no edge is close to
     * the mouse pointer, then it returns null.
     */
    private UpdateRegionInfo pickUpdateRegion(int x, int y, UpdateRegionInfo vertical,
                                              UpdateRegionInfo horizontal) {
        if (vertical != null && vertical.segment != null) {
            Edge e = getClosestEdge(y, vertical.segment);
            if (e != Edge.NONE) {
                return vertical;
            }
        }

        if (horizontal != null && horizontal.segment != null) {
            Edge e = getClosestEdge(x, horizontal.segment);
            if (e != Edge.NONE) {
                return horizontal;
            }
        }

        return null;
    }

    private enum Edge {
        START,
        END,
        NONE,
    }

    private static final int EDGE_DELTA = 1;
    private Edge getClosestEdge(int x, Pair<Integer> range) {
        if (Math.abs(x - range.first) <= EDGE_DELTA) {
            return Edge.START;
        } else if (Math.abs(range.second - x) <= EDGE_DELTA) {
            return Edge.END;
        } else {
            return Edge.NONE;
        }
    }

    private int imageYCoordinate(int y) {
        int top = (getHeight() - size.height) / 2;
        return (y - top) / zoom;
    }

    private int imageXCoordinate(int x) {
        int left = (getWidth() - size.width) / 2;
        return (x - left) / zoom;
    }

    private Point getImageOrigin() {
        int left = (getWidth() - size.width) / 2;
        int top = (getHeight() - size.height) / 2;
        return new Point(left, top);
    }

    private Rectangle displayCoordinates(Rectangle r) {
        Point imageOrigin = getImageOrigin();

        int x = r.x * zoom + imageOrigin.x;
        int y = r.y * zoom + imageOrigin.y;
        int w = r.width * zoom;
        int h = r.height * zoom;

        return new Rectangle(x, y, w, h);
    }

    private void updatePatchInfo() {
        patchInfo = new PatchInfo(image);
    }

    private void enableEraseMode(KeyEvent event) {
        eraseMode = event.isShiftDown();
    }

    private void startDrawingLine(int x, int y) {
        int width = image.getWidth();
        int height = image.getHeight();
        if (((x == 0 || x == width - 1) && (y > 0 && y < height - 1))
                || ((x > 0 && x < width - 1) && (y == 0 || y == height - 1))) {
            drawingLine = true;
            lineFromX = x;
            lineFromY = y;
            lineToX = x;
            lineToY = y;

            showDrawingLine = true;

            showCursor = false;

            repaint();
        }
    }

    private void moveLine(int x, int y) {
        if (!drawingLine) {
            return;
        }

        int width = image.getWidth();
        int height = image.getHeight();

        showDrawingLine = false;

        if (((x == lineFromX) && (y > 0 && y < height - 1))
                || ((x > 0 && x < width - 1) && (y == lineFromY))) {
            lineToX = x;
            lineToY = y;

            showDrawingLine = true;
        }

        repaint();
    }

    private void endDrawingLine() {
        if (!drawingLine) {
            return;
        }

        drawingLine = false;

        if (!showDrawingLine) {
            return;
        }

        int color;
        switch (currentMode) {
            case PATCH:
                color = PatchInfo.BLACK_TICK;
                break;
            case LAYOUT_BOUND:
                color = PatchInfo.RED_TICK;
                break;
            case ERASE:
                color = 0;
                break;
            default:
                return;
        }

        setPatchData(color, lineFromX, lineFromY, lineToX, lineToY, true);

        patchesChanged();
        repaint();
    }

    /**
     * Set the color of pixels on the line from (x1, y1) to (x2, y2) to given color.
     * @param inclusive indicates whether the range is inclusive. If true, the last pixel (x2, y2)
     *                  will be set to the given color as well.
     */
    private void setPatchData(int color, int x1, int y1, int x2, int y2, boolean inclusive) {
        int x = x1;
        int y = y1;

        int dx = 0;
        int dy = 0;

        if (x2 != x1) {
            dx = x2 > x1 ? 1 : -1;
        } else if (y2 != y1) {
            dy = y2 > y1 ? 1 : -1;
        }

        while (x != x2 || y != y2) {
            image.setRGB(x, y, color);
            x += dx;
            y += dy;
        }

        if (inclusive) {
            image.setRGB(x, y, color);
        }
    }

    /** Flushes current edit data to the image. */
    private void flushEditPatchData(int color) {
        int x1, y1, x2, y2;
        x1 = x2 = y1 = y2 = 0;
        int min = Math.min(editSegment.first, editSegment.second);
        int max = Math.max(editSegment.first, editSegment.second);
        switch (editRegion) {
            case LEFT_PATCH:
                x1 = x2 = 0;
                y1 = min;
                y2 = max;
                break;
            case RIGHT_PADDING:
                x1 = x2 = image.getWidth() - 1;
                y1 = min;
                y2 = max;
                break;
            case TOP_PATCH:
                x1 = min;
                x2 = max;
                y1 = y2 = 0;
                break;
            case BOTTOM_PADDING:
                x1 = min;
                x2 = max;
                y1 = y2 = image.getHeight() - 1;
                break;
            default:
                assert false : editRegion;
        }

        setPatchData(color, x1, y1, x2, y2, false);
    }

    private void patchesChanged() {
        updatePatchInfo();
        notifyPatchesUpdated();
        if (showBadPatches) {
            corruptedPatches = CorruptPatch.findBadPatches(image, patchInfo);
        }
    }

    private boolean checkLockedRegion(int x, int y) {
        int oldX = lastPositionX;
        int oldY = lastPositionY;
        lastPositionX = x;
        lastPositionY = y;

        int width = image.getWidth();
        int height = image.getHeight();

        statusBar.setPointerLocation(Math.max(0, Math.min(x, width - 1)),
                Math.max(0, Math.min(y, height - 1)));

        boolean previousLock = locked;
        locked = x > 0 && x < width - 1 && y > 0 && y < height - 1;

        boolean previousCursor = showCursor;
        showCursor =
                !drawingLine &&
                        ( ((x == 0 || x == width - 1) && (y > 0 && y < height - 1)) ||
                                ((x > 0 && x < width - 1) && (y == 0 || y == height - 1)) );

        if (locked != previousLock) {
            repaint();
        } else if (showCursor || (showCursor != previousCursor)) {
            Rectangle clip = new Rectangle(lastPositionX - 1 - zoom / 2,
                    lastPositionY - 1 - zoom / 2, zoom + 2, zoom + 2);
            clip = clip.union(new Rectangle(oldX - 1 - zoom / 2,
                    oldY - 1 - zoom / 2, zoom + 2, zoom + 2));
            repaint(clip);
        }

        return locked;
    }

    @Override
    protected void paintComponent(Graphics g) {
        int x = (getWidth() - size.width) / 2;
        int y = (getHeight() - size.height) / 2;

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setColor(BACK_COLOR);
        g2.fillRect(0, 0, getWidth(), getHeight());

        g2.translate(x, y);
        g2.setPaint(texture);
        g2.fillRect(0, 0, size.width, size.height);

        g2.scale(zoom, zoom);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
          RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2.drawImage(image, 0, 0, null);

        if (isFocusOwner()) {
            g2.setColor(FOCUS_COLOR);
            g2.drawRect(focusX, focusY, 1, 1);
        }

        if (showPatches) {
            g2.setColor(PATCH_COLOR);
            for (Rectangle patch : patchInfo.patches) {
                g2.fillRect(patch.x, patch.y, patch.width, patch.height);
            }
            g2.setColor(PATCH_ONEWAY_COLOR);
            for (Rectangle patch : patchInfo.horizontalPatches) {
                g2.fillRect(patch.x, patch.y, patch.width, patch.height);
            }
            for (Rectangle patch : patchInfo.verticalPatches) {
                g2.fillRect(patch.x, patch.y, patch.width, patch.height);
            }
        }

        if (corruptedPatches != null) {
            g2.setColor(CORRUPTED_COLOR);
            g2.setStroke(new BasicStroke(3.0f / zoom));
            for (Rectangle patch : corruptedPatches) {
                g2.draw(new RoundRectangle2D.Float(patch.x - 2.0f / zoom, patch.y - 2.0f / zoom,
                        patch.width + 2.0f / zoom, patch.height + 2.0f / zoom,
                        6.0f / zoom, 6.0f / zoom));
            }
        }

        if (showLock && locked) {
            int width = image.getWidth();
            int height = image.getHeight();

            g2.setColor(LOCK_COLOR);
            g2.fillRect(1, 1, width - 2, height - 2);

            g2.setColor(STRIPES_COLOR);
            g2.translate(1, 1);
            paintStripes(g2, width - 2, height - 2);
            g2.translate(-1, -1);
        }

        g2.dispose();

        if (drawingLine && showDrawingLine) {
            Graphics cursor = g.create();
            cursor.setXORMode(Color.WHITE);
            cursor.setColor(Color.BLACK);

            x = Math.min(lineFromX, lineToX);
            y = Math.min(lineFromY, lineToY);
            int w = Math.abs(lineFromX - lineToX) + 1;
            int h = Math.abs(lineFromY - lineToY) + 1;

            x = x * zoom;
            y = y * zoom;
            w = w * zoom;
            h = h * zoom;

            int left = (getWidth() - size.width) / 2;
            int top = (getHeight() - size.height) / 2;

            x += left;
            y += top;

            cursor.drawRect(x, y, w, h);
            cursor.dispose();
        }

        if (showCursor) {
            Graphics cursor = g.create();
            cursor.setXORMode(Color.WHITE);
            cursor.setColor(Color.BLACK);
            cursor.drawRect(lastPositionX - zoom / 2, lastPositionY - zoom / 2, zoom, zoom);
            cursor.dispose();
        }

        g2 = (Graphics2D) g.create();
        g2.setColor(HIGHLIGHT_REGION_COLOR);
        for (Rectangle r: hoverHighlightRegions) {
            g2.fillRect(r.x, r.y, r.width, r.height);
        }

        if (!hoverHighlightRegions.isEmpty()) {
            setToolTipText(toolTipText);
        } else {
            setToolTipText(null);
        }

        if (isEditMode && editRegion != null) {
            g2.setColor(HIGHLIGHT_REGION_COLOR);
            for (Rectangle r: editHighlightRegions) {
                g2.fillRect(r.x, r.y, r.width, r.height);
            }
            g2.setColor(Color.BLACK);
            g2.fillRect(editPatchRegion.x, editPatchRegion.y,
                    editPatchRegion.width, editPatchRegion.height);
        }

        g2.dispose();
    }

    private void paintStripes(Graphics2D g, int width, int height) {
        //draws pinstripes at the angle specified in this class
        //and at the given distance apart
        Shape oldClip = g.getClip();
        Area area = new Area(new Rectangle(0, 0, width, height));
        if(oldClip != null) {
            area = new Area(oldClip);
        }
        area.intersect(new Area(new Rectangle(0,0,width,height)));
        g.setClip(area);

        g.setStroke(new BasicStroke(STRIPES_WIDTH));

        double hypLength = Math.sqrt((width * width) +
                (height * height));

        double radians = Math.toRadians(STRIPES_ANGLE);
        g.rotate(radians);

        double spacing = STRIPES_SPACING;
        spacing += STRIPES_WIDTH;
        int numLines = (int)(hypLength / spacing);

        for (int i=0; i<numLines; i++) {
            double x = i * spacing;
            Line2D line = new Line2D.Double(x, -hypLength, x, hypLength);
            g.draw(line);
        }
        g.setClip(oldClip);
    }

    @Override
    public Dimension getPreferredSize() {
        return size;
    }

    private void setDefaultZoom() {
        int frameWidth = getWidth(), frameHeight = getHeight();
        int z = DEFAULT_ZOOM;
        if (frameWidth > 0 && frameHeight > 0) {
            float w = (float) image.getWidth() / frameWidth;
            float h = (float) image.getHeight() / frameHeight;

            float current = Math.max(w, h);
            float ideal = IDEAL_IMAGE_FRACTION_OF_WINDOW;

            z = clamp(Math.round(ideal / current), 1, MAX_ZOOM);
        }
        setZoom(z);
    }

    void setZoom(int value) {
        zoom = value;
        updateSize();
        if (!size.equals(getSize())) {
            setSize(size);
            container.validate();
            repaint();
        }
    }

    int getZoom() {
        return zoom;
    }

    private void updateSize() {
        int width = image.getWidth();
        int height = image.getHeight();

        if (size.height == 0 || (getHeight() - size.height) == 0) {
            size.setSize(width * zoom, height * zoom);
        } else {
            size.setSize(width * zoom, height * zoom);
        }
    }

    void setPatchesVisible(boolean visible) {
        showPatches = visible;
        updatePatchInfo();
        repaint();
    }

    void setLockVisible(boolean visible) {
        showLock = visible;
        repaint();
    }

    public void setImage(BufferedImage image) {
        this.image = image;
    }

    public BufferedImage getImage() {
        return image;
    }

    public PatchInfo getPatchInfo() {
        return patchInfo;
    }

    public interface StatusBar {
        void setPointerLocation(int x, int y);
    }

    public interface PatchUpdateListener {
        void patchesUpdated();
    }

    private final Set<PatchUpdateListener> listeners = new HashSet<PatchUpdateListener>();

    public void addPatchUpdateListener(PatchUpdateListener p) {
        listeners.add(p);
    }

    public void removePatchUpdateListener(PatchUpdateListener p) {
        listeners.remove(p);
    }

    private void notifyPatchesUpdated() {
        for (PatchUpdateListener p: listeners) {
            p.patchesUpdated();
        }
    }

    public void dispose() {
        Toolkit.getDefaultToolkit().removeAWTEventListener(mAwtKeyEventListener);
    }
}