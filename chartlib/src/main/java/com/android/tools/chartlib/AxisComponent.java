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
import gnu.trove.TFloatArrayList;

import java.awt.*;
import java.awt.geom.Line2D;

/**
 * A component that draw an axis based on data from a {@link Range} object.
 */
public final class AxisComponent extends AnimatedComponent {

    public enum AxisOrientation {
        LEFT,
        BOTTOM,
        RIGHT,
        TOP
    }

    private static final Font DEFAULT_FONT = new Font("Sans", Font.PLAIN, 10);
    private static final Color TEXT_COLOR = new Color(128, 128, 128);
    private static final int MAJOR_MARKER_LENGTH = 12;
    private static final int MINOR_MARKER_LENGTH = 3;
    private static final int MARKER_LABEL_MARGIN = 2;

    /**
     * The Range object that drives this axis.
     */
    @NonNull
    private final Range mRange;

    /**
     * The Global range object.
     */
    @NonNull
    private final Range mGlobalRange;

    /**
     * Name of the axis.
     */
    @NonNull
    private final String mLabel;

    /**
     * The font metrics of the tick labels.
     */
    @NonNull
    private final FontMetrics mMetrics;

    /**
     * Orientation of the axis.
     */
    @NonNull
    private final AxisOrientation mOrientation;

    /**
     * Margin before the start of the axis.
     */
    private final int mStartMargin;

    /**
     * Margin after the end of the axis.
     */
    private final int mEndMargin;

    /**
     * Display min/max values on the axis.
     */
    private boolean mShowMinMax;

    /**
     * Domain object for the axis.
     */
    @NonNull
    private final BaseAxisDomain mDomain;

    /**
     * Interpolated/Animated max value.
     */
    private double mCurrentMaxValue;

    /**
     * Interpolated/Animated min value.
     */
    private double mCurrentMinValue;

    /**
     * The starting point in pixel where the axis is drawn.
     */
    @NonNull
    private Point mStartPoint;

    /**
     * The ending point in pixel where the axis is drawn.
     */
    @NonNull
    private Point mEndPoint;

    /**
     * Length of the axis in pixels - used for internal calculation.
     */
    private int mDrawLength;

    /**
     * Calculated - Interval value per major marker.
     */
    private int mMajorInterval;

    /**
     * Calculated - Interval value per minor marker.
     */
    private int mMinorInterval;

    /**
     * Calculated - Number of pixels per major interval.
     */
    private float mMajorScale;

    /**
     * Calculated - Number of pixels per minor interval.
     */
    private float mMinorScale;

    /**
     * Calculated - Value of first major marker.
     */
    private double mFirstMarkerValue;

    /**
     * Cached major marker positions.
     */
    private final TFloatArrayList mMajorMarkerPositions;

    /**
     * Cached minor marker positions.
     */
    private final TFloatArrayList mMinorMarkerPositions;

    /**
     * @param range A Range object this AxisComponent listens to for the min/max values.
     * @param globalRange The global min/max range.
     * @param label The label/name of the axis.
     * @param orientation The orientation of the axis.
     * @param startMargin Space (in pixels) before the start of the axis.
     * @param endMargin Space (in pixels) after the end of the axis.
     * @param showMinMax If true, min/max values are shown on the axis.
     * @param domain Domain used for formatting the tick markers.
     */
    public AxisComponent(@NonNull Range range, @NonNull Range globalRange,
                         @NonNull String label, @NonNull AxisOrientation orientation,
                         int startMargin, int endMargin, boolean showMinMax, @NonNull BaseAxisDomain domain) {
        mRange = range;
        mGlobalRange = globalRange;
        mLabel = label;
        mOrientation = orientation;
        mShowMinMax = showMinMax;
        mDomain = domain;
        mStartPoint = new Point();
        mEndPoint = new Point();
        mMajorMarkerPositions = new TFloatArrayList();
        mMinorMarkerPositions = new TFloatArrayList();

        // Leaves space before and after the axis, this helps to prevent the start/end labels from being clipped.
        // TODO these margins complicate the draw code, an alternative is to implement the labels as a different Component,
        // so its draw region is not clipped by the length of the axis.
        mStartMargin = startMargin;
        mEndMargin = endMargin;

        mMetrics = getFontMetrics(DEFAULT_FONT);
    }

    @NonNull
    public AxisOrientation getOrientation() {
        return mOrientation;
    }

    @NonNull
    public TFloatArrayList getMajorMarkerPositions() {
        return mMajorMarkerPositions;
    }

    /**
     * Returns the position where a value would appear on this axis.
     */
    public float getPositionAtValue(double value) {
        float offset = (float)(mMinorScale * (value - mCurrentMinValue) / mMinorInterval);
        float ret = 0;
        switch (mOrientation) {
            case LEFT:
            case RIGHT:
                ret = mDrawLength - offset;
                break;
            case TOP:
            case BOTTOM:
                ret = offset;
                break;
        }

        return ret;
    }

    /**
     * Returns the value corresponding to a pixel position on the axis.
     */
    public double getValueAtPosition(int position) {
        int offset = 0;
        switch (mOrientation) {
            case LEFT:
            case RIGHT:
                offset =  mDrawLength - position;
                break;
            case TOP:
            case BOTTOM:
                offset =  position;
                break;
        }

        return mCurrentMinValue + mMinorInterval * offset / mMinorScale;
    }

    /**
     * Returns the formatted value corresponding to a pixel position on the axis.
     * The formatting depends on the {@link MarkerFormatter} object associated
     * with this axis.
     *
     * e.g. For a value of 1500 in milliseconds, this will return "1.5s".
     */
    @NonNull
    public String getFormattedValueAtPosition(int position) {
        return mDomain.getFormattedString(mGlobalRange.getLength(), getValueAtPosition(position));
    }

    @Override
    protected void updateData() {
        // Calculate drawing parameters.
        Dimension dimension = getSize();
        switch (mOrientation) {
            case LEFT:
                mStartPoint.x = mEndPoint.x = dimension.width - 1;   // TODO account for brush thickness.
                mStartPoint.y = dimension.height - mStartMargin - 1;
                mEndPoint.y = mEndMargin;
                mDrawLength = mStartPoint.y - mEndPoint.y;
                break;
            case BOTTOM:
                mStartPoint.x = mStartMargin;
                mEndPoint.x = dimension.width - mEndMargin - 1;
                mStartPoint.y = mEndPoint.y = 0;
                mDrawLength = mEndPoint.x - mStartPoint.x;
                break;
            case RIGHT:
                mStartPoint.x = mEndPoint.x = 0;
                mStartPoint.y = dimension.height - mStartMargin - 1;
                mEndPoint.y = mEndMargin;
                mDrawLength = mStartPoint.y - mEndPoint.y;
                break;
            case TOP:
                mStartPoint.x = mStartMargin;
                mEndPoint.x = dimension.width - mEndMargin - 1;
                mStartPoint.y = mEndPoint.y = dimension.height - 1;  // TODO account for brush thickness.
                mDrawLength = mEndPoint.x - mStartPoint.x;
                break;
        }

        mMajorMarkerPositions.reset();
        mMinorMarkerPositions.reset();
        mCurrentMinValue = mRange.getMin();
        mCurrentMaxValue = mRange.getMax();

        if (mDrawLength > 0) {
            double range = mRange.getLength();
            mMajorInterval = mDomain.getMajorInterval(range, mDrawLength);
            mMajorScale = (float)(mMajorInterval * mDrawLength / range);

            mMinorInterval = mDomain.getMinorInterval(mMajorInterval, (int)mMajorScale);
            mMinorScale = (float)(mMinorInterval * mDrawLength / range);

            // Calculate the value and offset of the first major marker
            mFirstMarkerValue = Math.floor(mCurrentMinValue / mMajorInterval) * mMajorInterval;
            // Pixel offset of first major marker.
            float firstMarkerOffset = (float)(mMinorScale * (mFirstMarkerValue - mCurrentMinValue) / mMinorInterval);

            // Calculate marker positions
            int numMarkers = (int)Math.floor((mCurrentMaxValue - mFirstMarkerValue) / mMinorInterval) + 1;
            int numMinorPerMajor = mMajorInterval / mMinorInterval;
            for (int i = 0; i < numMarkers; i++) {
                float markerOffset = firstMarkerOffset + i * mMinorScale;
                if (i % numMinorPerMajor == 0) {    // Major Tick.
                    mMajorMarkerPositions.add(markerOffset);
                } else {
                    mMinorMarkerPositions.add(markerOffset);
                }
            }
        } else {
            mMinorInterval = 0;
            mMajorInterval = 0;
            mMajorScale = 0;
            mMinorScale = 0;
            mFirstMarkerValue = 0;
        }
    }

    @Override
    protected void draw(Graphics2D g) {
        if (mDrawLength > 0) {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Draw axis.
            g.drawLine(mStartPoint.x, mStartPoint.y, mEndPoint.x, mEndPoint.y);

            drawMarkers(g);

            // TODO draw axis label.
        }
    }

    private void drawMarkers(Graphics2D g2d) {
        g2d.setFont(DEFAULT_FONT);
        g2d.setColor(TEXT_COLOR);

        if (mShowMinMax) {
            drawMarkerLabel(g2d, 0, mCurrentMinValue, true);
            drawMarkerLabel(g2d, mDrawLength, mCurrentMaxValue, true);
        }

        // TODO fade in/out markers.
        Line2D.Float line = new Line2D.Float();

        // Draw minor ticks.
        for (int i = 0; i < mMinorMarkerPositions.size(); i++) {
            if (mMinorMarkerPositions.get(i) >= 0) {
                drawMarkerLine(g2d, line, mMinorMarkerPositions.get(i), false);
            }
        }

        // Draw major ticks.
        for (int i = 0; i < mMajorMarkerPositions.size(); i++) {
            if (mMajorMarkerPositions.get(i) >= 0) {
                drawMarkerLine(g2d, line, mMajorMarkerPositions.get(i), true);

                double markerValue = mFirstMarkerValue + i * mMajorInterval;
                drawMarkerLabel(g2d, mMajorMarkerPositions.get(i), markerValue, !mShowMinMax);
            }
        }
    }

    private void drawMarkerLine(Graphics2D g2d, Line2D.Float line, float markerOffset, boolean isMajor) {
        float markerStartX = 0, markerStartY = 0, markerEndX = 0, markerEndY = 0;
        int markerLength = isMajor ? MAJOR_MARKER_LENGTH : MINOR_MARKER_LENGTH;
        switch (mOrientation) {
            case LEFT:
                markerStartX = mStartPoint.x - markerLength;
                markerStartY = markerEndY = mStartPoint.y - markerOffset;
                markerEndX = mStartPoint.x;
                break;
            case RIGHT:
                markerStartX = 0;
                markerStartY = markerEndY = mStartPoint.y - markerOffset;
                markerEndX = markerLength;
                break;
            case TOP:
                markerStartX = markerEndX = mStartPoint.x + markerOffset;
                markerStartY = mStartPoint.y - markerLength;
                markerEndY = mStartPoint.y;
                break;
            case BOTTOM:
                markerStartX = markerEndX = mStartPoint.x + markerOffset;
                markerStartY = 0;
                markerEndY = markerLength;
                break;
        }

        line.setLine(markerStartX, markerStartY, markerEndX, markerEndY);
        g2d.draw(line);
    }

    private void drawMarkerLabel(Graphics2D g2d, float markerOffset, double markerValue, boolean isMinMax) {
        String formattedValue = mDomain.getFormattedString(mGlobalRange.getLength(), markerValue);
        int stringAscent = mMetrics.getAscent();
        int stringLength = mMetrics.stringWidth(formattedValue);

        float labelX, labelY;
        float reserved; // reserved space for min/max labels.
        switch (mOrientation) {
            case LEFT:
                labelX = mStartPoint.x - MAJOR_MARKER_LENGTH - MARKER_LABEL_MARGIN - stringLength;
                labelY = mStartPoint.y - markerOffset + stringAscent * 0.5f;
                reserved = stringAscent;
                break;
            case RIGHT:
                labelX = MAJOR_MARKER_LENGTH + MARKER_LABEL_MARGIN;
                labelY = mStartPoint.y - markerOffset + stringAscent * 0.5f;
                reserved = stringAscent;
                break;
            case TOP:
                labelX = mStartPoint.x + markerOffset + MARKER_LABEL_MARGIN;
                labelY = mStartPoint.y - MINOR_MARKER_LENGTH;
                reserved = stringLength;
                break;
            case BOTTOM:
                labelX = mStartPoint.x + markerOffset + MARKER_LABEL_MARGIN;
                labelY = MINOR_MARKER_LENGTH + stringAscent;
                reserved = stringLength;
                break;
            default:
                throw new AssertionError("Unexpected orientation: " + mOrientation);
        }

        if (isMinMax || (markerOffset - reserved > 0 && markerOffset + reserved < mDrawLength)) {
            g2d.drawString(formattedValue, labelX, labelY);
        }
    }
}
