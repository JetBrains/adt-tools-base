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
import com.android.tools.chartlib.model.ContinuousSeries;
import com.android.tools.chartlib.model.LineChartData;
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
import java.util.Map;

public class LineChart extends AnimatedComponent {

    private static final Color[] COLORS = {
            new Color(0x6baed6),
            new Color(0xff0000),
            new Color(0xfd8d3c),
            new Color(0x00ffa2),
            new Color(0x000ff0),
            new Color(0xc7e9c0),
            new Color(0x9e9ac8),
            new Color(0xdadaeb),
            new Color(0x969696),
            new Color(0xd9d9d9),
    };

    /**
     * Transparency value to be applied in filled line charts.
     */
    private static final int ALPHA_MASK = 0x88000000;

    @NonNull
    private final LineChartData mData;

    @NonNull
    private final ArrayList<Path2D.Float> mPaths;

    public LineChart(@NonNull LineChartData data) {
        mData = data;
        mPaths = new ArrayList<Path2D.Float>();
    }

    @Override
    protected void updateData() {
        Map<Range, Long> max = new HashMap<Range, Long>();
        for (RangedContinuousSeries ranged : mData.series()) {
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
        for (RangedContinuousSeries ranged : mData.series()) {
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
                    if (ranged.isStepped()) {
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
            if (ranged.isFilled()) {
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
        for (int i = 0; i < mPaths.size(); i++) {
            assert i < mData.series().size();
            g2d.setColor(COLORS[i % COLORS.length]);
            if (mData.series().get(i).isDashed()) {
                g2d.setStroke(RangedContinuousSeries.DASHED_STROKE);
            } else {
                g2d.setStroke(RangedContinuousSeries.BASIC_STROKE);
            }
            Shape shape = scale.createTransformedShape(mPaths.get(i));
            if (mData.series().get(i).isFilled()) {
                // If the chart is filled, we want to set some transparency in its color
                // so the other charts can also be visible
                int newColorRGBA = 0x00ffffff & g2d.getColor().getRGB(); // reset alpha
                newColorRGBA |= ALPHA_MASK; // set new alpha
                g2d.setColor(new Color(newColorRGBA, true));
                g2d.fill(shape);
            } else {
                g2d.draw(shape);
            }
        }
    }
}

