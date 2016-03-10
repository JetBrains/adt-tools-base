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
import com.android.tools.chartlib.model.Range;
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
            new Color(0xc6dbef),
            new Color(0xfd8d3c),
            new Color(0xfdd0a2),
            new Color(0x74c476),
            new Color(0xc7e9c0),
            new Color(0x9e9ac8),
            new Color(0xdadaeb),
            new Color(0x969696),
            new Color(0xd9d9d9),
    };

    @NonNull
    private final LineChartData mData;

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

        int count = 0;
        for (Map.Entry<Range, Long> entry : max.entrySet()) {
            Range range = entry.getKey();
            double currentMax = Choreographer.lerp(range.getMax(), entry.getValue(),
                                                   .95f, mFrameLength);
            addDebugInfo("Range%d Max: %.2f", count++, currentMax);
            range.setMax(currentMax);
        }

        int p = 0;
        for (RangedContinuousSeries ranged : mData.series()) {
            Path2D.Float path;
            if (p == mPaths.size()) {
                path = new Path2D.Float();
                mPaths.add(path);
            } else {
                path = mPaths.get(p);
            }
            path.reset();
            double a = ranged.getXRange().getMin();
            double b = ranged.getXRange().getMax();

            double c = ranged.getYRange().getMin();
            double d = ranged.getYRange().getMax();
            int size = ranged.getSeries().size();
            for (int i = 0; i < size; i++) {
                long x = ranged.getSeries().getX(i);
                long y = ranged.getSeries().getY(i);
                double xd = (x - a) / (b - a);
                double yd = (y - c) / (d - c);
                if (i == 0) {
                    path.moveTo(xd, 1.0f);
                }
                path.lineTo(xd, 1.0f - yd);
            }
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
            g2d.setColor(COLORS[i % COLORS.length]);
            Shape shape = scale.createTransformedShape(mPaths.get(i));
            g2d.draw(shape);
        }
    }
}

