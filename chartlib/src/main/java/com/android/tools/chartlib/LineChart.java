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
import com.android.tools.chartlib.config.LineConfig;
import com.android.tools.chartlib.model.ContinuousSeries;
import com.android.tools.chartlib.model.RangedContinuousSeries;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LineChart extends AnimatedComponent {

    /**
     * Transparency value to be applied in filled line charts.
     */
    private static final int ALPHA_MASK = 0x88000000;

    /**
     * Maps the series to their correspondent visual line configuration.
     * The keys insertion order is preserved.
     */
    @NonNull
    private final Map<RangedContinuousSeries, LineConfig> mLinesConfig = new LinkedHashMap<>();

    @NonNull
    private final ArrayList<Path2D.Float> mPaths;

    /**
     * The color of the next line to be inserted, if not specified, is picked from {@code COLORS}
     * array of {@link LineConfig}. This field holds the color index.
     */
    private int mNextLineColorIndex;

    public LineChart() {
        mPaths = new ArrayList<>();
    }

    /**
     * Initialize a {@code LineChart} with a list of lines.
     */
    public LineChart(@NonNull List<RangedContinuousSeries> data) {
        this();
        addLines(data);
    }

    /**
     * Add a line to the line chart.
     * @param series data of the line to be inserted
     * @param config configuration of the line to be inserted
     */
    public void addLine(@NonNull RangedContinuousSeries series, @NonNull LineConfig config) {
        mLinesConfig.put(series, config);
    }

    /**
     * Add a line to the line chart with default configuration.
     * @param series series data of the line to be inserted
     */
    public void addLine(@NonNull RangedContinuousSeries series) {
        mLinesConfig.put(series, new LineConfig(LineConfig.COLORS[mNextLineColorIndex++]));
        mNextLineColorIndex %= LineConfig.COLORS.length;
    }

    /**
     * Add multiple lines with default configuration.
     */
    public void addLines(@NonNull List<RangedContinuousSeries> data) {
        data.forEach(this::addLine);
    }

    @NonNull
    public LineConfig getLineConfig(RangedContinuousSeries rangedContinuousSeries) {
        return mLinesConfig.get(rangedContinuousSeries);
    }

    @Override
    protected void updateData() {
        Map<Range, Long> max = new HashMap<>();
        for (RangedContinuousSeries ranged : mLinesConfig.keySet()) {
            ContinuousSeries series = ranged.getSeries();
            long maxY = series.getMaxY();
            Long m = max.get(ranged.getYRange());
            max.put(ranged.getYRange(), m == null ? maxY : Math.max(maxY, m));
        }

        for (Map.Entry<Range, Long> entry : max.entrySet()) {
            Range range = entry.getKey();
            range.setMaxTarget(entry.getValue());
        }
    }

    @Override
    public void postAnimate() {
        int p = 0;
        for (RangedContinuousSeries ranged : mLinesConfig.keySet()) {
            LineConfig config = mLinesConfig.get(ranged);
            Path2D.Float path;
            if (p == mPaths.size()) {
                path = new Path2D.Float();
                mPaths.add(path);
            } else {
                path = mPaths.get(p);
                path.reset();
            }

            double xMin = ranged.getXRange().getMin();
            double xMax = ranged.getXRange().getMax();
            double yMin = ranged.getYRange().getMin();
            double yMax = ranged.getYRange().getMax();

            double firstXd = 0f; // X coordinate of the first destination point
            // TODO optimize to not draw anything before or after min and max.
            int size = ranged.getSeries().size();
            for (int i = 0; i < size; i++) {
                long x = ranged.getSeries().getX(i);
                long y = ranged.getSeries().getY(i);
                double xd = (x - xMin) / (xMax - xMin);
                double yd = (y - yMin) / (yMax - yMin);
                if (i == 0) {
                    path.moveTo(xd, 1.0f);
                    firstXd = xd;
                } else {
                    // If the chart is stepped, a horizontal line should be drawn from the current
                    // point (e.g. (x0, y0)) to the destination's X value (e.g. (x1, y0)) before
                    // drawing a line to the destination point itself (e.g. (x1, y1)).
                    if (config.isStepped()) {
                        path.lineTo(xd, path.getCurrentPoint().getY());
                    }
                    path.lineTo(xd, 1.0f - yd);
                }
            }
            // If the chart is filled, draw a line from the last point to X axis and another one
            // from this new point to the first destination point. The resulting polygon is going to
            // be filled.
            // TODO: When stacked charts are implemented, the polygon shouldn't be drawn
            // until the X axis, but the Y value of the last path
            if (config.isFilled()) {
                path.lineTo(path.getCurrentPoint().getX(), 1.0f);
                path.lineTo(firstXd, 1.0f);
            }

            addDebugInfo("Range[%d] Max: %.2f", p, xMax);
            p++;
        }
        mPaths.subList(p, mPaths.size()).clear();
    }

    @Override
    protected void draw(Graphics2D g2d) {
        Dimension dim = getSize();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        AffineTransform scale = AffineTransform.getScaleInstance(dim.getWidth(), dim.getHeight());
        assert mPaths.size() == mLinesConfig.size();
        int i = 0;
        for (RangedContinuousSeries ranged : mLinesConfig.keySet()) {
            LineConfig config = mLinesConfig.get(ranged);
            g2d.setColor(config.getColor());
            if (config.isDashed()) {
                g2d.setStroke(LineConfig.DASHED_STROKE);
            } else {
                g2d.setStroke(LineConfig.BASIC_STROKE);
            }
            Shape shape = scale.createTransformedShape(mPaths.get(i));
            if (config.isFilled()) {
                // If the chart is filled, we want to set some transparency in its color
                // so the other charts can also be visible
                int newColorRGBA = 0x00ffffff & g2d.getColor().getRGB(); // reset alpha
                newColorRGBA |= ALPHA_MASK; // set new alpha
                g2d.setColor(new Color(newColorRGBA, true));
                g2d.fill(shape);
            } else {
                g2d.draw(shape);
            }
            i++;
        }
    }
}

