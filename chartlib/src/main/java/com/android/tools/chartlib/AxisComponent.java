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
import gnu.trove.TIntArrayList;

import java.awt.*;
import java.awt.geom.Path2D;

/**
 * A component that draw an axis based on data from a {@link Range} object.
 * It handles drawing in different orientations and finds the optimal tick marker placement
 * based on data inside {@link Range}.
 */
public final class AxisComponent extends AnimatedComponent {

    public enum AxisOrientation {
        LEFT,
        BOTTOM,
        RIGHT,
        TOP
    }

    public interface MarkerFormatter {
        String getFormattedString(double value);
    }

    private static final Font DEFAULT_FONT = new Font("Sans", Font.PLAIN, 10);
    private static final Color TEXT_COLOR = new Color(128, 128, 128);
    private static final int MARKER_LENGTH = 5; // TODO customizable marker size?

    /**
     * The target number of pixels between markers.
     * This is used as a seed to calculate the optimal spacing.
     */
    private static final int TARGET_SPACING = 40;

    /**
     * The Range object that drives this axis.
     */
    @NonNull
    private final Range mRange;

    /**
     * Name of the axis.
     */
    @NonNull
    private final String mLabel;

    /**
     * Orientation of the axis.
     */
    @NonNull
    private final AxisOrientation mOrientation;

    /**
     * The base value used in calculating the magnitude and intervals of the tick markers.
     */
    private final int mBase;

    /**
     * The minimum interval between ticks on the axis.
     */
    private final int mMinInterval;

    /**
     * Margin before the start of the axis.
     */
    private final int mStartMargin;

    /**
     * Margin after the end of the axis.
     */
    private final int mEndMargin;

    /**
     * Formatter for major tick markers.
     */
    @NonNull
    private final MarkerFormatter mFormatter;

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
     * Calculated - Interval value per marker.
     */
    private int mInterval;

    /**
     * Calculated - A list of factors of mBase that are used to determine mInterval.
     */
    @NonNull
    private final TIntArrayList mBaseFactors;

    /**
     * Calculated - Number of pixels per interval.
     */
    private float mScale;

    /**
     * Calculated - Number of markers drawn.
     */
    private int mNumMarkers;

    /**
     * Calculated - Pixel offset of first marker.
     */
    private float mFirstMarkerOffset;

    /**
     * Calculated - Value of first marker.
     */
    private double mFirstMarkerValue;

    /**
     * @param range A Range object this AxisComponent listens to for the min/max values.
     * @param label The label/name of the axis.
     * @param orientation The orientation of the axis.
     * @param base The base system of the axis (e.g. base 2, base 10, etc).
     * @param minInterval The smallest possible interval for the tick markers.
     * @param startMargin Space (in pixels) before the start of the axis.
     * @param endMargin Space (in pixels) after the end of the axis.
     * @param formatter String formatter for each tick marker's value.
     */
    public AxisComponent(@NonNull Range range, @NonNull String label, @NonNull AxisOrientation orientation, int base, int minInterval,
                         int startMargin, int endMargin, @NonNull MarkerFormatter formatter) {
        mRange = range;
        mLabel = label;
        mOrientation = orientation;
        mBase = base;
        mBaseFactors = getBaseFactors(mBase);
        mMinInterval = minInterval;
        mFormatter = formatter;
        mStartPoint = new Point();
        mEndPoint = new Point();

        // Leaves space before and after the axis, this helps to prevent the start/end labels from being clipped.
        // TODO these margins complicate the draw code, an alternative is to implement the labels as a different Component,
        // so its draw region is not clipped by the length of the axis.
        mStartMargin = startMargin;
        mEndMargin = endMargin;
    }

    public String getLabel() {
        return mLabel;
    }

    public AxisOrientation getOrientation() {
        return mOrientation;
    }

    public int getNumMarkers() {
        return mNumMarkers;
    }

    public int getInterval() {
        return mInterval;
    }

    public float getScale() { return mScale; }

    public double getFirstMarkerValue() {
        return mFirstMarkerValue;
    }

    /**
     * Returns the position where a value would appear on this axis.
     */
    public float getPositionAtValue(double value) {
        float offset = (float)(mScale * (value - mCurrentMinValue) /  mInterval);
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

        return mCurrentMinValue + mInterval * offset / mScale;
    }

    /**
     * Returns the formatted value corresponding to a pixel position on the axis.
     * The formatting depends on the {@link MarkerFormatter} object associated
     * with this axis.
     *
     * e.g. For a value of 1500 in milliseconds, this will return "1.5s".
     */
    public @NonNull String getFormattedValueAtPosition(int position) {
        return formatValue(getValueAtPosition(position));
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

        mCurrentMinValue = mRange.getMin();
        mCurrentMaxValue = mRange.getMax();
        if (mDrawLength > 0) {
            // Get number of ticks that can be drawn given mDrawLength and TARGET_SPACING.
            int targetNumTicks = mDrawLength / TARGET_SPACING;
            double range = mCurrentMaxValue - mCurrentMinValue;

            // Estimate optimal interval from maxNumTicks, accounting for the mMinInterval constraint.
            mInterval = getOptimalInterval(range, targetNumTicks, mMinInterval, mBase, mBaseFactors);

            // Update scale.
            mScale = (float)(mInterval * mDrawLength / range);

            // Calculate the value and offset of the first marker.
            mFirstMarkerValue = Math.ceil(mCurrentMinValue / mInterval) * mInterval;
            mFirstMarkerOffset = (float)(mScale * (mFirstMarkerValue - mCurrentMinValue) / mInterval);

            // Calculate number of markers.
            mNumMarkers = (int)Math.floor((mDrawLength - mFirstMarkerOffset) / mScale) + 1;
        } else {
            mInterval = 0;
            mNumMarkers = 0;
            mScale = 0;
            mFirstMarkerValue = 0;
            mFirstMarkerOffset = 0;
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

        FontMetrics metrics = getFontMetrics(DEFAULT_FONT);
        int stringAscent = metrics.getAscent();
        Path2D.Float path = new Path2D.Float();

        // Min marker.
        String marker = formatValue(mCurrentMinValue);
        int stringLength = metrics.stringWidth(marker);
        drawMarkerSingle(g2d, path, 0, marker, stringAscent, stringLength);

        // Max marker.
        marker = formatValue(mCurrentMaxValue);
        stringLength = metrics.stringWidth(marker);
        drawMarkerSingle(g2d, path, mDrawLength, marker, stringAscent, stringLength);

        // TODO fade in/out markers.
        for (int i = 0; i < mNumMarkers; i++) {
            float markerOffset = mFirstMarkerOffset + i * mScale;
            double markerValue = mFirstMarkerValue + i * mInterval;

            marker = formatValue(markerValue);
            stringLength = metrics.stringWidth(marker);

            // Calculate reserved space for min/max labels.
            float reserved;
            switch (mOrientation) {
                case LEFT:
                case RIGHT:
                    reserved = stringAscent;
                    break;
                default: // case TOP/BOTTOM
                    reserved = stringLength;
                    break;
            }

            if (markerOffset - reserved <= 0 || markerOffset + reserved >= mDrawLength) {
                // Skip if the offset is too close to min/max labels.
                continue;
            }

            drawMarkerSingle(g2d, path, markerOffset, marker, stringAscent, stringLength);
        }

        g2d.draw(path);
    }

    private void drawMarkerSingle(Graphics2D g2d, Path2D.Float path, float markerOffset,
                                  String markerValue, int stringAscent, int stringLength) {
        float markerStartX, markerStartY, markerEndX, markerEndY;
        float labelX, labelY;
        switch (mOrientation) {
            case LEFT:
                markerStartX = mStartPoint.x - MARKER_LENGTH;
                markerStartY = markerEndY = mStartPoint.y - markerOffset;
                markerEndX = mStartPoint.x;
                labelX = mStartPoint.x - MARKER_LENGTH * 2 - stringLength;
                labelY = markerStartY + stringAscent * 0.5f;
                break;
            case RIGHT:
                markerStartX = 0;
                markerStartY = markerEndY = mStartPoint.y - markerOffset;
                markerEndX = MARKER_LENGTH;
                labelX = MARKER_LENGTH * 2;
                labelY = markerStartY + stringAscent * 0.5f;
                break;
            case TOP:
                markerStartX = markerEndX = mStartPoint.x + markerOffset;
                markerStartY = mStartPoint.y - MARKER_LENGTH;
                markerEndY = mStartPoint.y;
                labelX = markerStartX - stringLength * 0.5f;
                labelY = mStartPoint.y - MARKER_LENGTH * 2;
                break;
            default: // case BOTTOM
                markerStartX = markerEndX = mStartPoint.x + markerOffset;
                markerStartY = 0;
                markerEndY = MARKER_LENGTH;
                labelX = markerStartX - stringLength * 0.5f;
                labelY = MARKER_LENGTH * 2 + stringAscent;
                break;
        }

        path.moveTo(markerStartX, markerStartY);
        path.lineTo(markerEndX, markerEndY);
        g2d.drawString(markerValue, labelX, labelY);
    }

    private String formatValue(double value) {
        return mFormatter == null ? String.format("%.2f", value) : mFormatter.getFormattedString(value);
    }

    /**
     * This helper method determines the interval value that should be used for a particular range.
     * The return value is expected to be some nice factor or multiple of base depending on the
     * targetRange and targetNumTicks.
     *
     * @param targetRange The range to calculate intervals for.
     * @param targetNumTicks The estimated number of ticks to render within targetRange.
     * @param minInterval The minimal possible interval.
     * @param base The base system used by the intervals on the axis.
     * @param baseFactors A factor array of base in descending order. see mBaseFactor in AxisComponent.
     *
     * TODO consider refactoring into a separate class that encapsulates both the interval calculation and marker formatting
     * base on Domain. i.e. for time range: msec -> sec -> min -> hr -> day
     */
    private static int getOptimalInterval(double targetRange, int targetNumTicks, int minInterval, int base, TIntArrayList baseFactors) {
        // An estimated guess of the interval based on how many ticks we want to render within the range.
        double targetInterval = Math.max(minInterval, targetRange / targetNumTicks);

        // Order of magnitude of targetInterval relative to base.
        int power = (int)Math.floor(Math.log(targetInterval) / Math.log(base));
        int magnitude = (int)Math.pow(base, power);

        // Multiplier of targetInterval at the current magnitude
        // rounded up to at least 1, which is the smallest possible value in the baseFactors array.
        int multiplier = (int)(targetInterval / magnitude + 0.5);

        // Find the closest base factor bigger than multiplier and use that as the multiplier.
        // The idea behind using the factor is that it will add up nicely in the base system,
        // that way we always get integral intervals.
        if (multiplier > 1) {
            for (int i = 1; i < baseFactors.size(); i++) {
                if (multiplier > baseFactors.get(i)) {
                    multiplier = baseFactors.get(i - 1);
                    break;
                }
            }
        }

        return multiplier * magnitude;
    }

    /**
     * Creates a factor array for the value base. Note that this does not include all factors of base,
     * but it recursively finds the biggest factor that can divide the previous value in the array.
     * e.g. for a base of 10, the result would be {10, 5, 1}
     * e.g. for a base of 60, the result would be {60, 30, 15, 5, 1}
     */
    private static TIntArrayList getBaseFactors(int base) {
        TIntArrayList factors = new TIntArrayList();
        while (base > 1) {
            // Find the smallest factor that can divide base.
            int divider = 2;
            while (base % divider != 0) {
                ++divider;

                // If divider is bigger than the square root of base,
                // then base is prime and smallest factor is base.
                if (divider * divider > base) {
                    divider = base;
                    break;
                }
            }

            factors.add(base);
            base = base / divider;
        }
        factors.add(1); // Smallest possible factor of base.

        return factors;
    }
}
