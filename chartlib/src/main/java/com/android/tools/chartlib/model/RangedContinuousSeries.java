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

package com.android.tools.chartlib.model;

import com.android.annotations.NonNull;
import com.android.tools.chartlib.Range;

import java.awt.BasicStroke;
import java.awt.Stroke;

/**
 * Represents a view into a continuous series, where the data in view is only
 * within given x and y ranged.
 */
public class RangedContinuousSeries {

    /**
     * Stroke style to be used in dashed line.
     */
    public static final Stroke DASHED_STROKE = new BasicStroke(1, BasicStroke.CAP_BUTT,
            BasicStroke.JOIN_MITER, 10, new float[]{10}, 0);

    /**
     * Stroke style to be used in continuos line.
     */
    public static final Stroke BASIC_STROKE = new BasicStroke();

    /**
     * Whether the series should be represented by dashed lines.
     */
    private boolean mIsDashed = false;

    /**
     * Whether the series should be represented by a stepped chart.
     * In case it is not, a straight line is drawn between points (e.g. (x0, y0) and (x1, y1)).
     * Otherwise, a line is drawn from (x0, y0) to (x0, y1) and another one is drawn from (x0, y1)
     * to (x1, y1).
     */
    private boolean mIsStepped = false;

    /**
     * Whether the series should be represented by a filled chart, instead of only lines.
     */
    private boolean mIsFilled = false;

    @NonNull
    private final Range mXRange;

    @NonNull
    private final Range mYRange;

    @NonNull
    private final ContinuousSeries mSeries;

    public RangedContinuousSeries(@NonNull Range xRange, @NonNull Range yRange) {
        mXRange = xRange;
        mYRange = yRange;
        mSeries = new ContinuousSeries();
    }

    @NonNull
    public ContinuousSeries getSeries() {
        return mSeries;
    }

    @NonNull
    public Range getYRange() {
        return mYRange;
    }

    @NonNull
    public Range getXRange() {
        return mXRange;
    }

    public void setDashed(boolean isDashed) {
        mIsDashed = isDashed;
    }

    public boolean isDashed() {
        return mIsDashed;
    }

    public void setStepped(boolean isStepped) {
        mIsStepped = isStepped;
    }

    public boolean isStepped() {
        return mIsStepped;
    }

    public void setFilled(boolean isFilled) {
        mIsFilled = isFilled;
    }

    public boolean isFilled() {
        return mIsFilled;
    }
}
