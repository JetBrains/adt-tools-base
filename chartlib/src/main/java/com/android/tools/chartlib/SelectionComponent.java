/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.chartlib;

import com.android.annotations.NonNull;
import com.android.tools.chartlib.model.Range;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.Random;

/**
 * A component for performing/rendering selection and any overlay information (e.g. Tooltip).
 */
public final class SelectionComponent extends AnimatedComponent {

    private static final Color SELECTION_FORECOLOR = new Color(0x88aae2);
    private static final Color SELECTION_BACKCOLOR = new Color(0x5588aae2, true);

    private double mSelectionStartX, mSelectionEndX;
    private boolean mIsSelecting, mIsSelected;

    @NonNull
    private Point mMousePosition;

    @NonNull
    private final AxisComponent mAxis;

    /**
     * The range being selected.
     */
    @NonNull
    private final Range mSelectionRange;

    /**
     * The global range for clamping selection.
     */
    @NonNull
    private final Range mGlobalRange;

    /**
     * The current viewing range which gets shifted when user drags
     * the selection box beyond the component's dimension.
     */
    @NonNull
    private final Range mCurrentRange;

    public SelectionComponent(@NonNull AxisComponent axis,
                              @NonNull Range selectionRange,
                              @NonNull Range globalRange,
                              @NonNull Range currentRange) {
        mAxis = axis;
        mSelectionRange = selectionRange;
        mGlobalRange = globalRange;
        mCurrentRange = currentRange;
        mMousePosition = new Point();

        // TODO mechanism to cancel/undo the selection.
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                // Begin Selection - mark the selection start point.
                mMousePosition.x = e.getX();
                mMousePosition.y = e.getY();
                mSelectionStartX = mAxis.getValueAtPosition(mMousePosition.x);
                mIsSelecting = true;
                mIsSelected = true;
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                // End Selection.
                mIsSelecting = false;
            }
        });

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                mMousePosition.x = e.getX();
                mMousePosition.y = e.getY();
            }
        });
    }

    @Override
    protected void updateData() {
        if (!mIsSelecting) {
            return;
        }

        // If user is actively selecting, update the selection range based on the last mouse position.
        Dimension dim = getSize();
        mSelectionEndX = mAxis.getValueAtPosition(mMousePosition.x);
        double startX = Math.max(mGlobalRange.getMin(), Math.min(mSelectionStartX, mSelectionEndX));
        double endX = Math.min(mGlobalRange.getMax(), Math.max(mSelectionStartX, mSelectionEndX));
        boolean minChanged = mSelectionRange.getMin() != startX;
        boolean maxChanged = mSelectionRange.getMax() != endX;
        if (minChanged) {
            mSelectionRange.setMin(startX);
        }
        if (maxChanged) {
            mSelectionRange.setMax(endX);
        }

        // Extends current range if it does not contain the whole selection range.
        // This happens when the user drags the selection box beyond the current dimension.
        if (mMousePosition.x < 0) {
            double currentRange = mCurrentRange.getLength();
            if (minChanged) {
                mCurrentRange.setMin(startX);
                mCurrentRange.setMax(startX + currentRange);
            } else if (maxChanged) {
                mCurrentRange.setMin(endX);
                mCurrentRange.setMax(endX + currentRange);
            }
        }
        else if (mMousePosition.x >= dim.width) {
            double currentRange = mCurrentRange.getLength();
            if (minChanged) {
                mCurrentRange.setMax(startX);
                mCurrentRange.setMin(startX - currentRange);
            } else if (maxChanged) {
                mCurrentRange.setMax(endX);
                mCurrentRange.setMin(endX - currentRange);
            }
        }
    }

    @Override
    protected void draw(Graphics2D g) {
        Dimension dim = getSize();
        g.setFont(DEFAULT_FONT);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // A selected region exists so render the region.
        if (mIsSelected) {
            g.setColor(SELECTION_BACKCOLOR);
            float startXPos = mAxis.getPositionAtValue(mSelectionRange.getMin());
            float endXPos = mAxis.getPositionAtValue(mSelectionRange.getMax());

            Rectangle2D.Float rect = new Rectangle2D.Float();
            rect.setRect(startXPos, 0, endXPos - startXPos, dim.height);
            g.fill(rect);

            g.setColor(SELECTION_FORECOLOR);
            Path2D.Float path = new Path2D.Float();
            path.moveTo(startXPos, 0);
            path.lineTo(startXPos, dim.height);
            path.moveTo(endXPos, dim.height);
            path.lineTo(endXPos, 0);
            g.draw(path);
        }
    }
}
