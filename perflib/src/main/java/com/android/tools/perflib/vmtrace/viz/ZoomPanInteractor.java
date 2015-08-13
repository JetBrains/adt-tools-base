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

package com.android.tools.perflib.vmtrace.viz;

import com.android.annotations.NonNull;
import com.android.annotations.VisibleForTesting;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link ZoomPanInteractor} listens to mouse events, interprets dragging the mouse as an attempt
 * to pan the canvas, and mouse wheel rotation as an attempt to zoom in/out the canvas.
 * After such events, it updates its listeners with the updated transformation matrix corresponding
 * to the zoom & pan values.
 */
public class ZoomPanInteractor implements MouseListener, MouseMotionListener, MouseWheelListener {
    /**
     * The values from {@link java.awt.event.MouseWheelEvent#getWheelRotation()} are quite high even
     * for a small amount of scrolling. This is an arbitrary scale factor used to go from the wheel
     * rotation value to a zoom by factor. The scale is negated to take care of the common
     * expectation that scrolling down should zoom out, not zoom in.
     */
    private static final double WHEEL_UNIT_SCALE = -0.1;

    private final AffineTransform mTransform = new AffineTransform();
    private AffineTransform mInverseTransform;

    private final Point2D mTmpPoint = new Point2D.Double();

    private int mLastX;
    private int mLastY;

    private final List<ViewTransformListener> mListeners = new ArrayList<ViewTransformListener>();

    @Override
    public void mouseClicked(MouseEvent e) {
    }

    @Override
    public void mousePressed(MouseEvent e) {
        mLastX = e.getX();
        mLastY = e.getY();
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        int deltaX = e.getX() - mLastX;
        int deltaY = e.getY() - mLastY;

        translateBy(deltaX, deltaY);

        notifyTransformChange();

        mLastX = e.getX();
        mLastY = e.getY();
    }

    @VisibleForTesting
    void translateBy(int deltaX, int deltaY) {
        // Transform pixels by the current viewport scaling factor.
        // i.e when you have zoomed out by say 2x, and you drag by a pixel,
        // you expect it to move (the model space) by 2 pixels, not one.
        deltaX /= mTransform.getScaleX();
        deltaY /= mTransform.getScaleY();

        mTransform.translate(deltaX, deltaY);

        // Do not allow panning above the axis.
        // TODO: This actually encodes information about the canvas and what is drawn over here,
        // and should be moved out.
        if (mTransform.getTranslateY() > 0) {
            mTransform.translate(0, -mTransform.getTranslateY());
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
    }

    @Override
    public void mouseEntered(MouseEvent e) {
    }

    @Override
    public void mouseExited(MouseEvent e) {
    }

    @Override
    public void mouseMoved(MouseEvent e) {
    }

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        if (e.getScrollType() != MouseWheelEvent.WHEEL_UNIT_SCROLL) {
            return;
        }

        double scale = 1 + WHEEL_UNIT_SCALE * e.getWheelRotation();

        // convert mouse x, y from screen coordinates to absolute coordinates
        mTmpPoint.setLocation(e.getX(), e.getY());
        mInverseTransform.transform(mTmpPoint, mTmpPoint);

        zoomBy(scale, 1, mTmpPoint);

        notifyTransformChange();
    }

    @VisibleForTesting
    void zoomBy(double scaleX, double scaleY, Point2D location) {
        // When zooming, we want to zoom by the location the mouse currently points to.
        // So we translate the current location to the origin, apply the scale, and translate back
        mTransform.translate(location.getX(), location.getY());
        mTransform.scale(scaleX, scaleY);
        mTransform.translate(-location.getX(), -location.getY());
    }

    private void notifyTransformChange() {
        try {
            mInverseTransform = mTransform.createInverse();
        } catch (NoninvertibleTransformException ignored) {
            // The transform matrix is only scaled or translated, both of which are invertible.
        }

        for (ViewTransformListener l : mListeners) {
            l.transformChanged(mTransform);
        }
    }

    public void setToScaleX(double sx, double sy) {
        mTransform.setToScale(sx, sy);
        notifyTransformChange();
    }

    @VisibleForTesting
    AffineTransform getTransform() {
        return mTransform;
    }

    public interface ViewTransformListener {
        void transformChanged(@NonNull AffineTransform transform);
    }

    public void addViewTransformListener(@NonNull ViewTransformListener l) {
        mListeners.add(l);
    }
}
